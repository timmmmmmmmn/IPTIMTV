// IPTIMTV-server: accounts en kijkgeschiedenis voor de app, plus een kleine downloadpagina.
const enc = new TextEncoder();
const RELEASES = 'https://github.com/timmmmmmmmn/IPTIMTV/releases/latest';

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

const page = `<!doctype html><html lang="nl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>IPTIMTV</title><style>
body{margin:0;min-height:100vh;display:grid;place-items:center;background:linear-gradient(160deg,#0f8a4a,#00806e);font:16px system-ui,sans-serif;color:#fff;text-align:center}
main{padding:32px}h1{font-size:56px;margin:0 0 8px;letter-spacing:2px;font-weight:900}p{opacity:.85;max-width:420px;margin:0 auto 28px}
a{display:inline-block;margin:6px;padding:14px 24px;border-radius:999px;background:#fff;color:#0b5a3f;font-weight:700;text-decoration:none}
</style></head><body><main><h1>IPTIMTV</h1><p>Simpele IPTV-app voor je tv en telefoon. Log in de app in met je IPTIMTV-account om je kijkgeschiedenis overal te zien.</p>
<a href="${RELEASES}">Download de app</a></main></body></html>`;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/')) {
      try {
        return await api(request, env, url.pathname.slice(5).replace(/\/$/, ''));
      } catch (e) {
        return fail(500, 'Serverfout');
      }
    }
    if (url.pathname === '/') return new Response(page, { headers: { 'content-type': 'text/html; charset=utf-8' } });
    return new Response('Niet gevonden', { status: 404 });
  }
};
