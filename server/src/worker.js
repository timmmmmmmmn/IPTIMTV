// IPTIMTV-server: accounts en kijkgeschiedenis voor de app, plus een kleine downloadpagina.
const enc = new TextEncoder();

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }
  });
const fail = (status, error) => json({ error }, status);

const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
const randomHex = (n) => hex(crypto.getRandomValues(new Uint8Array(n)));
const sha256 = async (s) => hex(await crypto.subtle.digest('SHA-256', enc.encode(s)));

async function hashPassword(password, salt) {
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt: enc.encode(salt), iterations: 100000 },
    key,
    256
  );
  return hex(bits);
}

// Vergelijking in constante tijd.
function same(a, b) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

async function newSession(env, userId, device) {
  const token = randomHex(32);
  const now = Date.now();
  await env.DB.prepare('INSERT INTO sessions (token_hash, user_id, device, created, last_seen) VALUES (?, ?, ?, ?, ?)')
    .bind(await sha256(token), userId, (device || '').slice(0, 60), now, now)
    .run();
  return token;
}

async function currentUser(request, env) {
  const auth = request.headers.get('authorization') || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
  if (!/^[0-9a-f]{64}$/.test(token)) return null;
  const hash = await sha256(token);
  const row = await env.DB.prepare(
    'SELECT u.id, u.username FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?'
  ).bind(hash).first();
  if (row) {
    await env.DB.prepare('UPDATE sessions SET last_seen = ? WHERE token_hash = ?').bind(Date.now(), hash).run();
    row.tokenHash = hash;
  }
  return row;
}

async function body(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

const clip = (v, n) => (typeof v === 'string' ? v.slice(0, n) : null);
const int = (v) => (Number.isFinite(Number(v)) ? Math.max(0, Math.round(Number(v))) : 0);

function credentials(b) {
  const username = typeof b.username === 'string' ? b.username.trim() : '';
  const password = typeof b.password === 'string' ? b.password : '';
  if (!/^[A-Za-z0-9_.-]{3,32}$/.test(username)) return { error: 'Gebruikersnaam: 3-32 tekens, letters, cijfers, . _ of -' };
  if (password.length < 6) return { error: 'Wachtwoord moet minstens 6 tekens hebben' };
  return { username, password };
}

async function stats(env, userId, tzOffsetMin) {
  const day = (d) => new Date(Date.now() - d * 86400000 - tzOffsetMin * 60000).toISOString().slice(0, 10);
  const sum = async (where, ...args) =>
    (await env.DB.prepare(`SELECT COALESCE(SUM(seconds), 0) AS s FROM daily WHERE user_id = ? ${where}`)
      .bind(userId, ...args).first()).s;
  const perKind = await env.DB.prepare(
    'SELECT kind, SUM(seconds) AS s FROM daily WHERE user_id = ? AND day >= ? GROUP BY kind'
  ).bind(userId, day(6)).all();
  return {
    today: await sum('AND day = ?', day(0)),
    week: await sum('AND day >= ?', day(6)),
    total: await sum(''),
    weekByKind: Object.fromEntries(perKind.results.map((r) => [r.kind, r.s]))
  };
}

// ---------- versleuteling van gesynchroniseerde logins ----------
// SECRET_KEY is een Cloudflare-secret; zonder sleutel (lokaal testen) wordt niet versleuteld.
const keyCache = {};
async function aesKey(env) {
  if (!env.SECRET_KEY) return null;
  if (!keyCache.aes) {
    const raw = await crypto.subtle.digest('SHA-256', enc.encode('sync:' + env.SECRET_KEY));
    keyCache.aes = await crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);
  }
  return keyCache.aes;
}
const b64 = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf)));
const unb64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

async function seal(env, text) {
  const key = await aesKey(env);
  if (!key) return text;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, enc.encode(text));
  return 'v1:' + b64(iv) + ':' + b64(data);
}
async function unseal(env, stored) {
  if (!stored.startsWith('v1:')) return stored;
  const key = await aesKey(env);
  const [, iv, data] = stored.split(':');
  const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(iv) }, key, unb64(data));
  return new TextDecoder().decode(plain);
}

// ---------- proxy voor de webversie ----------
async function hmac(env, text) {
  if (!keyCache.hmac) {
    keyCache.hmac = await crypto.subtle.importKey('raw', enc.encode('proxy:' + (env.SECRET_KEY || 'dev')),
      { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  }
  return hex(await crypto.subtle.sign('HMAC', keyCache.hmac, enc.encode(text))).slice(0, 32);
}

// Hosts uit de logins van een gebruiker (alleen daarheen mag de proxy zonder handtekening).
const hostCache = new Map();
async function allowedHosts(env, userId, fresh = false) {
  const hit = hostCache.get(userId);
  if (!fresh && hit && hit.at > Date.now() - 60000) return hit.hosts;
  const row = await env.DB.prepare("SELECT value FROM sync WHERE user_id = ? AND key = 'profiles'").bind(userId).first();
  const hosts = new Set();
  if (row) {
    try {
      for (const p of JSON.parse(await unseal(env, row.value))) {
        for (const u of [p.server, p.m3uUrl, p.epgUrl]) {
          try { if (u) hosts.add(new URL(u).host); } catch {}
        }
      }
    } catch {}
  }
  hostCache.set(userId, { at: Date.now(), hosts });
  return hosts;
}

const sessionCache = new Map();
async function userFromToken(env, token) {
  if (!/^[0-9a-f]{64}$/.test(token || '')) return null;
  const hit = sessionCache.get(token);
  if (hit && hit.at > Date.now() - 300000) return hit.user;
  const row = await env.DB.prepare('SELECT u.id FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?')
    .bind(await sha256(token)).first();
  sessionCache.set(token, { at: Date.now(), user: row ? row.id : null });
  return row ? row.id : null;
}

const cors = { 'access-control-allow-origin': '*', 'access-control-expose-headers': 'content-length,content-range' };

async function proxied(env, base, token, target) {
  const u = new URL(target, base).toString();
  return `/proxy?t=${token}&s=${await hmac(env, u)}&u=${encodeURIComponent(u)}`;
}

/** HLS-playlist herschrijven zodat alle stukjes ook via de proxy gaan. */
async function rewritePlaylist(env, text, base, token) {
  const out = [];
  for (const line of text.split(/\r?\n/)) {
    const t = line.trim();
    if (!t) { out.push(line); continue; }
    if (t.startsWith('#')) {
      const m = t.match(/URI="([^"]+)"/);
      out.push(m ? t.replace(m[1], await proxied(env, base, token, m[1])) : t);
    } else {
      out.push(await proxied(env, base, token, t));
    }
  }
  return out.join('\n');
}

// Cloudflare staat geen verbindingen naar een kaal IP-adres toe (fout 1003). Veel IPTV-servers gebruiken
// juist dat; via sslip.io (een naam die naar hetzelfde IP wijst) gaat het wel.
const IPV4 = /^\d{1,3}(\.\d{1,3}){3}$/;
function reachable(u) {
  const x = new URL(u);
  if (IPV4.test(x.hostname)) x.hostname = x.hostname + '.sslip.io';
  return x.toString();
}

/** Ophalen en doorverwijzingen zelf volgen (die wijzen bij providers vaak ook naar een IP-adres). */
async function fetchFollow(url, headers) {
  let current = url;
  for (let i = 0; i < 6; i++) {
    const res = await fetch(reachable(current), { headers, redirect: 'manual' });
    const loc = res.headers.get('location');
    if ([301, 302, 303, 307, 308].includes(res.status) && loc) {
      current = new URL(loc, current).toString();
      continue;
    }
    return { res, finalUrl: current };
  }
  return { res: null, finalUrl: current };
}

async function proxy(request, env, url) {
  const token = url.searchParams.get('t');
  const target = url.searchParams.get('u');
  const sig = url.searchParams.get('s');
  const userId = await userFromToken(env, token);
  if (!userId || !target) return new Response('Niet ingelogd', { status: 401, headers: cors });
  let t;
  try { t = new URL(target); } catch { return new Response('Ongeldig', { status: 400, headers: cors }); }
  if (!/^https?:$/.test(t.protocol)) return new Response('Ongeldig', { status: 400, headers: cors });
  const signed = sig && sig === (await hmac(env, t.toString()));
  // Net een login toegevoegd? Dan is het lijstje in het geheugen misschien nog oud: opnieuw kijken.
  if (!signed && !(await allowedHosts(env, userId)).has(t.host) && !(await allowedHosts(env, userId, true)).has(t.host)) {
    return new Response('Deze server hoort niet bij je logins', { status: 403, headers: cors });
  }
  const headers = { 'user-agent': url.searchParams.get('ua') || 'IPTIMTV/1.0' };
  const range = request.headers.get('range');
  if (range) headers.range = range;
  const { res, finalUrl } = await fetchFollow(t.toString(), headers);
  if (!res) return new Response('Te veel doorverwijzingen', { status: 502, headers: cors });
  const type = res.headers.get('content-type') || '';
  if (/mpegurl/i.test(type) || /\.m3u8(\?|$)/i.test(finalUrl)) {
    const text = await res.text();
    if (text.trimStart().startsWith('#EXTM3U') && /#EXT-X-/.test(text)) {
      return new Response(await rewritePlaylist(env, text, finalUrl, token), {
        status: res.status, headers: { ...cors, 'content-type': 'application/vnd.apple.mpegurl', 'cache-control': 'no-store' }
      });
    }
    return new Response(text, { status: res.status, headers: { ...cors, 'content-type': type || 'text/plain' } });
  }
  const h = new Headers(cors);
  for (const k of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
    const v = res.headers.get(k);
    if (v) h.set(k, v);
  }
  h.set('cache-control', 'no-store');
  return new Response(res.body, { status: res.status, headers: h });
}

async function api(request, env, path) {
  const m = request.method;

  if (path === 'register' && m === 'POST') {
    const b = await body(request);
    const c = credentials(b);
    if (c.error) return fail(400, c.error);
    const exists = await env.DB.prepare('SELECT 1 FROM users WHERE username = ?').bind(c.username).first();
    if (exists) return fail(409, 'Deze gebruikersnaam is al bezet');
    const salt = randomHex(16);
    const res = await env.DB.prepare('INSERT INTO users (username, pass_hash, salt, created) VALUES (?, ?, ?, ?)')
      .bind(c.username, await hashPassword(c.password, salt), salt, Date.now())
      .run();
    const id = res.meta.last_row_id;
    return json({ token: await newSession(env, id, b.device), username: c.username });
  }

  if (path === 'login' && m === 'POST') {
    const b = await body(request);
    const username = typeof b.username === 'string' ? b.username.trim() : '';
    const user = await env.DB.prepare('SELECT id, username, pass_hash, salt FROM users WHERE username = ?').bind(username).first();
    const hash = user ? await hashPassword(String(b.password || ''), user.salt) : await hashPassword('x', 'y');
    if (!user || !same(hash, user.pass_hash)) return fail(401, 'Gebruikersnaam of wachtwoord klopt niet');
    return json({ token: await newSession(env, user.id, b.device), username: user.username });
  }

  const user = await currentUser(request, env);
  if (!user) return fail(401, 'Niet ingelogd');

  if (path === 'logout' && m === 'POST') {
    await env.DB.prepare('DELETE FROM sessions WHERE token_hash = ?').bind(user.tokenHash).run();
    return json({ ok: true });
  }

  if (path === 'me' && m === 'GET') {
    // tz = minuten verschil met UTC zoals JavaScript het geeft (Nederland in de winter: -60).
    const tz = parseInt(new URL(request.url).searchParams.get('tz') || '0', 10) || 0;
    return json({ username: user.username, stats: await stats(env, user.id, tz) });
  }

  // Synchroniseren: logins ("profiles") en per login favorieten/groepen ("user:<id>"). Nieuwste wint.
  if (path === 'sync' && m === 'GET') {
    const rows = await env.DB.prepare('SELECT key, value, updated FROM sync WHERE user_id = ?').bind(user.id).all();
    const items = [];
    for (const r of rows.results) {
      try { items.push({ key: r.key, value: JSON.parse(await unseal(env, r.value)), updated: r.updated }); } catch {}
    }
    return json({ items });
  }

  if (path === 'sync' && m === 'PUT') {
    const b = await body(request);
    const key = clip(b.key, 100);
    if (!key || !/^(profiles|user:[A-Za-z0-9_-]{1,40})$/.test(key) || b.value === undefined) return fail(400, 'Onvolledige gegevens');
    const text = JSON.stringify(b.value);
    if (text.length > 512 * 1024) return fail(413, 'Te groot');
    const updated = int(b.updated) || Date.now();
    await env.DB.prepare(
      `INSERT INTO sync (user_id, key, value, updated) VALUES (?, ?, ?, ?)
       ON CONFLICT (user_id, key) DO UPDATE SET value = excluded.value, updated = excluded.updated
       WHERE excluded.updated > sync.updated`
    ).bind(user.id, key, await seal(env, text), updated).run();
    if (key === 'profiles') hostCache.delete(user.id);
    return json({ ok: true });
  }

  if (path === 'history' && m === 'GET') {
    const limit = Math.min(int(new URL(request.url).searchParams.get('limit')) || 40, 200);
    const rows = await env.DB.prepare(
      'SELECT item_key AS key, kind, parent, title, subtitle, image, position, duration, watched, updated FROM history WHERE user_id = ? ORDER BY updated DESC LIMIT ?'
    ).bind(user.id, limit).all();
    return json({ items: rows.results });
  }

  if (path === 'history' && m === 'DELETE') {
    await env.DB.batch([
      env.DB.prepare('DELETE FROM history WHERE user_id = ?').bind(user.id),
      env.DB.prepare('DELETE FROM daily WHERE user_id = ?').bind(user.id)
    ]);
    return json({ ok: true });
  }

  // De app stuurt regelmatig de voortgang en hoeveel seconden er sinds de vorige keer gekeken is.
  if (path === 'progress' && m === 'POST') {
    const b = await body(request);
    const key = clip(b.key, 200);
    const kind = ['live', 'movie', 'episode', 'recording'].includes(b.kind) ? b.kind : null;
    if (!key || !kind || !b.title) return fail(400, 'Onvolledige gegevens');
    const watched = Math.min(int(b.watched), 3600);
    const now = Date.now();
    const day = typeof b.day === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(b.day) ? b.day : new Date().toISOString().slice(0, 10);
    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO history (user_id, item_key, kind, parent, title, subtitle, image, position, duration, watched, updated)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT (user_id, item_key) DO UPDATE SET
           kind = excluded.kind, parent = excluded.parent, title = excluded.title, subtitle = excluded.subtitle,
           image = excluded.image, position = excluded.position, duration = excluded.duration,
           watched = history.watched + excluded.watched, updated = excluded.updated`
      ).bind(user.id, key, kind, clip(b.parent, 200), clip(b.title, 200), clip(b.subtitle, 200), clip(b.image, 500),
        int(b.position), int(b.duration), watched, now),
      env.DB.prepare(
        `INSERT INTO daily (user_id, day, kind, seconds) VALUES (?, ?, ?, ?)
         ON CONFLICT (user_id, day, kind) DO UPDATE SET seconds = daily.seconds + excluded.seconds`
      ).bind(user.id, day, kind, watched)
    ]);
    return json({ ok: true });
  }

  if (path === 'history/item' && m === 'DELETE') {
    const key = clip(new URL(request.url).searchParams.get('key'), 200);
    await env.DB.prepare('DELETE FROM history WHERE user_id = ? AND item_key = ?').bind(user.id, key).run();
    return json({ ok: true });
  }

  return fail(404, 'Onbekend');
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: { ...cors, 'access-control-allow-headers': 'authorization,content-type,range', 'access-control-allow-methods': 'GET,POST,PUT,DELETE' } });
    }
    if (url.pathname.startsWith('/api/')) {
      try {
        return await api(request, env, url.pathname.slice(5).replace(/\/$/, ''));
      } catch (e) {
        return fail(500, 'Serverfout');
      }
    }
    if (url.pathname === '/proxy') {
      try {
        return await proxy(request, env, url);
      } catch (e) {
        return new Response('Kon de server van je provider niet bereiken', { status: 502, headers: cors });
      }
    }
    // De webapp (statische bestanden in public/).
    return env.ASSETS.fetch(request);
  }
};
