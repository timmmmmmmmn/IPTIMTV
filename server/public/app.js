// IPTIMTV web: dezelfde opzet als de app, met je IPTIMTV-account als basis.
// Logins, favorieten en groepen komen via je account (dezelfde als op de tv en telefoon).

const $ = (s, el = document) => el.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const app = $('#app');

// ---------- iconen (Material-vormen) ----------
const P = {
  home: 'M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z',
  tv: 'M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z',
  ball: 'M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,5.3l1.35,-0.95c1.82,0.56 3.37,1.76 4.38,3.34l-0.39,1.34 -1.35,0.46L13,6.7V5.3zM9.65,4.35L11,5.3v1.4L7.01,9.49l-1.35,-0.46 -0.39,-1.34c1.01,-1.57 2.56,-2.77 4.38,-3.34zM7.08,17.11l-1.14,0.1C4.73,15.81 4,13.99 4,12c0,-0.12 0.01,-0.23 0.02,-0.35l1,-0.73 1.38,0.48 1.46,4.34 -0.78,1.37zM14.5,19.59c-0.79,0.26 -1.63,0.41 -2.5,0.41s-1.71,-0.15 -2.5,-0.41l-0.69,-1.49 0.64,-1.1h5.11l0.64,1.11 -0.7,1.48zM14.27,15L9.73,15l-1.35,-4.02L12,8.44l3.63,2.54L14.27,15zM18.06,17.21l-1.14,-0.1 -0.79,-1.37 1.46,-4.34 1.39,-0.47 1,0.73c0.01,0.11 0.02,0.22 0.02,0.34 0,1.99 -0.73,3.81 -1.94,5.21z',
  movie: 'M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z',
  series: 'M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,14.5v-9l6,4.5 -6,4.5z',
  search: 'M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z',
  gear: 'M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z',
  back: 'M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z',
  play: 'M8,5v14l11,-7z',
  star: 'M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z',
  prev: 'M6,6h2v12H6zM9.5,12l8.5,6V6z',
  next: 'M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z',
};
const ic = (n) => `<svg class="i" viewBox="0 0 24 24"><path d="${P[n]}"/></svg>`;

// ---------- toestand ----------
const S = {
  token: localStorage.getItem('token'),
  username: localStorage.getItem('username'),
  profiles: [],
  activeId: localStorage.getItem('active'),
  user: {},           // per login: { favorites, groups, liveOrder, hidden }
  stamps: {},         // laatste bijwerktijd per sync-sleutel
  cat: null,          // catalogus van de actieve login
  loading: null,
  history: [],
  stats: null,
  tab: 'home',
  liveCat: null, movieCat: null, showCat: null,
  query: '',
  matches: null, sportDay: 0,
};
const active = () => S.profiles.find((p) => p.id === S.activeId) || S.profiles[0];
const udata = () => {
  const p = active();
  if (!p) return { favorites: [], groups: [], liveOrder: [], hidden: [] };
  return (S.user[p.id] ||= { favorites: [], groups: [], liveOrder: [], hidden: [] });
};

function toast(text) {
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = text;
  document.body.append(t);
  setTimeout(() => t.remove(), 3000);
}

// ---------- server ----------
// De http-versie (poort 8080) is voor providers die alleen http hebben en de IPTIMTV-server blokkeren:
// de browser haalt dan alles zelf bij de provider op. Het account blijft via https gaan.
const onHttp = location.protocol === 'http:';
const API = onHttp && location.hostname === 'iptv.timvanbergen.nl' ? 'https://iptv.timvanbergen.nl/api/' : '/api/';
async function api(path, opts = {}) {
  const res = await fetch(API + path, {
    ...opts,
    headers: { 'content-type': 'application/json', ...(S.token ? { authorization: 'Bearer ' + S.token } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && S.token && !path.startsWith('login')) { logout(); throw new Error('Opnieuw inloggen'); }
  if (!res.ok) throw new Error(data.error || 'Er ging iets mis');
  return data;
}
const px = (url) => `/proxy?t=${S.token}&u=${encodeURIComponent(url)}`;
/** Vanuit de browser rechtstreeks naar de provider (kan alleen op de http-versie). */
const browserDirect = (p) => p && isDirect(p) && onHttp;
const pfetch = (url) => fetch(browserDirect(active()) ? url : px(url));

/** Naar de http-versie van de site, ingelogd en met dezelfde login actief. */
function goHttp() {
  const data = btoa(JSON.stringify({ token: S.token, username: S.username, active: S.activeId, direct: S.activeId }));
  location.replace(`http://${location.hostname}:8080/#t=${data}`);
}

async function pullSync() {
  const { items } = await api('sync');
  for (const it of items) {
    S.stamps[it.key] = it.updated;
    if (it.key === 'profiles') S.profiles = it.value || [];
    else if (it.key.startsWith('user:')) S.user[it.key.slice(5)] = it.value;
  }
  if (!S.profiles.some((p) => p.id === S.activeId)) S.activeId = S.profiles[0]?.id || null;
}
async function pushSync(key, value) {
  const updated = Date.now();
  S.stamps[key] = updated;
  await api('sync', { method: 'PUT', body: { key, value, updated } }).catch(() => toast('Opslaan in je account mislukt'));
}
const saveUser = () => pushSync('user:' + active().id, udata());

// ---------- catalogus ----------
const enc = encodeURIComponent;
const xbase = (p) => `${p.server}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}`;

/** Haalt een lijst op via de proxy, met een cache van 12 uur in de browser. */
async function cached(p, name, url, force) {
  const key = `/cache/${p.id}/${name}`;
  const cache = 'caches' in window ? await caches.open('iptimtv-data') : null;
  if (cache && !force) {
    const hit = await cache.match(key);
    if (hit && Date.now() - Number(hit.headers.get('x-at') || 0) < 12 * 3600e3) return hit.text();
  }
  let res;
  for (let attempt = 0; attempt < 4; attempt++) {
    res = browserDirect(p) ? await fetch(url) : await fetch(px(url) + (force ? '&fresh=1' : ''));
    if (res.status !== 429) break;
    // Provider remt af: steeds iets langer wachten.
    S.loading = 'Je provider vraagt om even te wachten…';
    render();
    await new Promise((r) => setTimeout(r, (attempt + 1) * 4000));
  }
  if (res.status === 429) throw new Error('Je provider krijgt even te veel verzoeken (429). Wacht een paar minuten en probeer het opnieuw.');
  if (!res.ok) throw new Error(`Server ${res.status}: ${(await res.text()).slice(0, 120)}`);
  const text = await res.text();
  if (cache) await cache.put(key, new Response(text, { headers: { 'x-at': String(Date.now()) } })).catch(() => {});
  return text;
}

function parseM3u(text) {
  const live = [], movies = [], liveCats = new Map(), movieCats = new Map();
  let attrs = {}, title = '';
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (line.startsWith('#EXTINF')) {
      attrs = Object.fromEntries([...line.matchAll(/([\w-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
      title = line.slice(line.lastIndexOf(',') + 1).trim() || attrs['tvg-name'] || '';
    } else if (line && !line.startsWith('#')) {
      const g = attrs['group-title'] || 'Overig';
      if (/\/(movie|series)\/|\.(mp4|mkv|avi)(\?|$)/i.test(line)) {
        movieCats.set(g, { id: g, name: g });
        movies.push({ id: line, name: title, poster: attrs['tvg-logo'], cat: g, url: line });
      } else {
        liveCats.set(g, { id: g, name: g });
        live.push({ id: line, name: title, logo: attrs['tvg-logo'], cat: g, url: line, num: live.length + 1 });
      }
      attrs = {}; title = '';
    }
  }
  return { liveCats: [...liveCats.values()], live, movieCats: [...movieCats.values()], movies, showCats: [], shows: [] };
}

// Providers die de IPTIMTV-server blokkeren: lijsten komen dan uit je account (geüpload door de app)
// en de stream speelt rechtstreeks vanaf je eigen internet af.
const isDirect = (p) => localStorage.getItem('direct:' + p.id) === '1';
const setDirect = (p, on) => { if (on) localStorage.setItem('direct:' + p.id, '1'); else localStorage.removeItem('direct:' + p.id); };

async function gunzipJson(res) {
  const stream = res.body.pipeThrough(new DecompressionStream('gzip'));
  return JSON.parse(await new Response(stream).text());
}

/** De lijsten die de tv- of telefoon-app in je account heeft gezet (null als die er niet zijn). */
async function fromAccount(p) {
  const res = await fetch(API + 'catalog/' + p.id, { headers: { authorization: 'Bearer ' + S.token } });
  if (!res.ok) return null;
  const d = await gunzipJson(res);
  const m3u = p.type === 'M3U';
  const cats = (a) => (a || []).map(([id, name]) => ({ id, name }));
  return {
    updated: Number(res.headers.get('x-updated')) || 0,
    liveCats: cats(d.liveCats),
    live: (d.live || []).map(([id, name, logo, cat, archive, epg, url], i) => ({
      id, name, logo, cat, archive, num: i + 1, url: m3u ? url : `${p.server}/live/${p.username}/${p.password}/${id}.m3u8`,
    })),
    movieCats: cats(d.movieCats),
    movies: (d.movies || []).map(([id, name, poster, cat, rating, added, ext]) => ({
      id, name, poster, cat, rating, added, url: m3u ? ext : `${p.server}/movie/${p.username}/${p.password}/${id}.${ext || 'mp4'}`,
    })),
    showCats: cats(d.showCats),
    shows: (d.shows || []).map(([id, name, poster, cat, rating, plot]) => ({ id, name, poster, cat, rating, plot })),
  };
}

async function loadCatalog(force = false) {
  const p = active();
  if (!p) return;
  S.loading = 'Kanalen en films laden…';
  render();
  if (isDirect(p) && !onHttp) return goHttp();
  const gen = (S.loadGen = (S.loadGen || 0) + 1);
  try {
    if (p.type === 'M3U') {
      S.cat = parseM3u(await cached(p, 'm3u', p.m3uUrl, force));
    } else {
      const get = async (a) => {
        const text = await cached(p, a, `${xbase(p)}&action=${a}`, force);
        try {
          return JSON.parse(text || '[]');
        } catch {
          // Geen lijst maar bijvoorbeeld een foutpagina: niet onthouden, en laten zien wat er terugkwam.
          if ('caches' in window) (await caches.open('iptimtv-data')).delete(`/cache/${p.id}/${a}`);
          throw new Error('Je provider gaf geen lijst terug: ' + String(text).slice(0, 100));
        }
      };
      // Eén voor één (providers remmen af bij te veel tegelijk); eerst live tv, films en series daarna.
      const lc = await get('get_live_categories').catch(() => []);
      const ls = await get('get_live_streams');
      const arr = (x) => (Array.isArray(x) ? x : []);
      const later = async () => {
        const out = [];
        for (const a of ['get_vod_categories', 'get_vod_streams', 'get_series_categories', 'get_series']) {
          out.push(await get(a).catch(() => []));
        }
        const [vc, vs, sc, ss] = out;
        if (gen !== S.loadGen) return;
        Object.assign(S.cat, buildVod(vc, vs, sc, ss));
        render();
      };
      const buildVod = (vc, vs, sc, ss) => ({
        movieCats: arr(vc).map((c) => ({ id: String(c.category_id), name: c.category_name })),
        movies: arr(vs).map((m) => ({
          id: String(m.stream_id), name: m.name, poster: m.stream_icon, cat: String(m.category_id), rating: m.rating, added: Number(m.added) || 0,
          url: `${p.server}/movie/${p.username}/${p.password}/${m.stream_id}.${m.container_extension || 'mp4'}`,
        })),
        showCats: arr(sc).map((c) => ({ id: String(c.category_id), name: c.category_name })),
        shows: arr(ss).map((s) => ({ id: String(s.series_id), name: s.name, poster: s.cover, cat: String(s.category_id), plot: s.plot, rating: s.rating })),
      });
      S.vodLoading = true;
      setTimeout(() => later().finally(() => { S.vodLoading = false; render(); }), 500);
      S.cat = {
        liveCats: arr(lc).map((c) => ({ id: String(c.category_id), name: c.category_name })),
        live: arr(ls).map((c, i) => ({
          id: String(c.stream_id), name: c.name, logo: c.stream_icon, cat: String(c.category_id), num: i + 1,
          url: `${p.server}/live/${p.username}/${p.password}/${c.stream_id}.m3u8`,
        })),
        movieCats: [], movies: [], showCats: [], shows: [],
      };
    }
    S.byId = new Map(S.cat.live.map((c) => [c.id, c]));
    S.loadError = null;
  } catch (e) {
    const msg = String(e.message || e);
    // De IPTIMTV-server komt niet bij je provider: dan haalt je browser het zelf op (via de http-versie).
    if (!isDirect(p) && /^Server (403|5\d\d)/.test(msg)) {
      setDirect(p, true);
      return onHttp ? loadCatalog() : goHttp();
    }
    // Ook de browser komt er niet bij: dan de lijsten die de app in je account heeft gezet.
    if (isDirect(p)) {
      const cat = await fromAccount(p).catch(() => null);
      if (cat) {
        S.vodLoading = false;
        S.cat = cat;
        S.byId = new Map(cat.live.map((c) => [c.id, c]));
        S.loadError = null;
        S.loading = null;
        return render();
      }
    }
    S.loadError = (isDirect(p) && /fetch|network|load/i.test(msg)
      ? 'Je browser kon je provider niet bereiken. Open de tv- of telefoon-app één keer (ingelogd met je IPTIMTV-account); die zet je lijsten in je account.'
      : msg).slice(0, 300);
    toast('Kon je lijsten niet laden');
    S.cat = { liveCats: [], live: [], movieCats: [], movies: [], showCats: [], shows: [] };
    S.byId = new Map();
  }
  S.loading = null;
  render();
}

// Gids-verzoeken één tegelijk met een korte pauze; na een 429 een minuut niet.
let epgChain = Promise.resolve();
let epgPauseUntil = 0;
function epgQueue(task) {
  const run = epgChain.then(async () => {
    if (Date.now() < epgPauseUntil) throw new Error('pauze');
    await new Promise((r) => setTimeout(r, 300));
    return task();
  });
  epgChain = run.catch(() => {});
  return run;
}

// Nu-op-tv per kanaal (Xtream short EPG), alleen voor wat in beeld komt.
const epgCache = new Map();
async function nowOn(ch) {
  const p = active();
  if (!p || p.type !== 'XTREAM' || (isDirect(p) && !onHttp)) return null;
  if (epgCache.has(ch.id)) return epgCache.get(ch.id);
  const promise = epgQueue(() => pfetch(`${xbase(p)}&action=get_short_epg&stream_id=${ch.id}&limit=2`))
    .then((r) => { if (r.status === 429) { epgPauseUntil = Date.now() + 60000; epgCache.delete(ch.id); throw new Error(); } return r.json(); })
    .then((d) => {
      const dec = (s) => { try { return decodeURIComponent(escape(atob(s))); } catch { return s; } };
      const now = Date.now() / 1000;
      const l = (d.epg_listings || []).map((e) => ({ title: dec(e.title), start: +e.start_timestamp, stop: +e.stop_timestamp }));
      return l.find((e) => e.start <= now && e.stop > now) || null;
    })
    .catch(() => null);
  epgCache.set(ch.id, promise);
  return promise;
}
const observer = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    observer.unobserve(e.target);
    const ch = S.byId?.get(e.target.dataset.epg);
    if (!ch) continue;
    nowOn(ch).then((n) => {
      if (!n) return;
      const now = e.target.querySelector('.now');
      if (now) now.textContent = n.title;
      const bar = e.target.querySelector('.bar i');
      if (bar) bar.style.width = Math.min(100, ((Date.now() / 1000 - n.start) / (n.stop - n.start)) * 100) + '%';
    });
  }
});

// ---------- weergave ----------
const initials = (n) => String(n || '').replace(/^\W*[A-Za-z]{2,3}\s*[|:]\s*/, '').split(/[\s\-|:]+/).filter((w) => /^[\p{L}\d]/u.test(w)).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
const logoBox = (url, name) => `<div class="logo-box">${url ? `<img loading="lazy" src="${esc(url)}" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'initials',textContent:'${esc(initials(name))}'}))">` : `<span class="initials">${esc(initials(name))}</span>`}</div>`;
const fmt = (s) => { s = Math.max(0, Math.floor(s)); const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60; return h ? `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}` : `${m}:${String(x).padStart(2, '0')}`; };
const clock = (ms) => new Date(ms).toLocaleTimeString('nl-NL', { hour: '2-digit', minute: '2-digit' });
const watchTime = (sec) => { const m = Math.floor(sec / 60); return m < 60 ? `${m} min` : `${Math.floor(m / 60)} u ${m % 60} min`; };

const tabs = [['home', 'Start', 'home'], ['live', 'Live TV', 'tv'], ['sport', 'Sport', 'ball'], ['movies', 'Films', 'movie'], ['series', 'Series', 'series'], ['search', 'Zoeken', 'search']];

function render() {
  if (!S.token) return renderWelcome();
  if (!S.profiles.length) return renderAddLogin(true);
  app.innerHTML = `
    <header class="top">
      <img class="logo" src="/logo.png" alt="IPTIMTV">
      <nav class="tabs">${tabs.map(([k, l]) => `<button class="tab ${S.tab === k ? 'on' : ''}" data-tab="${k}">${l}</button>`).join('')}</nav>
      <button class="icon-btn ${S.tab === 'settings' ? 'on' : ''}" data-tab="settings" aria-label="Instellingen">${ic('gear')}</button>
    </header>
    <main>${S.loading && !S.cat ? `<div class="empty"><b>${esc(S.loading)}</b>Even geduld…</div>` : view()}</main>
    <nav class="navbar">${tabs.map(([k, l, i]) => `<button class="${S.tab === k ? 'on' : ''}" data-tab="${k}">${ic(i)}${l === 'Live TV' ? 'Live' : l}</button>`).join('')}</nav>`;
  app.querySelectorAll('[data-epg]').forEach((el) => observer.observe(el));
}

function view() {
  switch (S.tab) {
    case 'live': return viewLive();
    case 'sport': return viewSport();
    case 'movies': return viewVod('movies');
    case 'series': return viewVod('series');
    case 'search': return viewSearch();
    case 'settings': return viewSettings();
    default: return viewHome();
  }
}

const channelTile = (c, list) => `
  <button class="tile" data-play="${esc(c.id)}" data-list="${list}" data-epg="${esc(c.id)}">
    ${logoBox(c.logo, c.name)}
    <div class="meta"><div class="name">${esc(c.name)}</div><div class="now">&nbsp;</div><div class="bar"><i style="width:0"></i></div></div>
  </button>`;

function viewHome() {
  const u = udata(), cat = S.cat;
  if (!cat) return '';
  if (!cat.live.length && !cat.movies.length && S.loadError) {
    return `<div class="empty"><b>Je lijsten konden niet geladen worden</b>${esc(S.loadError)}<br><br>
      <div class="btns" style="justify-content:center"><button class="btn" data-act="reload">Opnieuw proberen</button><button class="btn primary" data-act="diag">Verbinding testen</button></div></div>`;
  }
  const fav = u.favorites.map((id) => S.byId.get(id)).filter(Boolean);
  const cont = S.history.filter((h) => (h.kind === 'movie' || h.kind === 'episode') && h.position > 60e3 && (!h.duration || h.position < h.duration - 180e3));
  let html = `<h2>Mijn kanalen</h2>`;
  html += fav.length ? `<div class="row">${fav.map((c) => channelTile(c, 'fav')).join('')}</div>`
    : `<p class="section dim">Nog geen kanalen. Tik bij Live TV op het sterretje om er een toe te voegen.</p>`;
  if (cont.length) {
    html += `<h2>Verder kijken ${S.stats ? `<small>Deze week ${watchTime(S.stats.week)} gekeken</small>` : ''}</h2><div class="row">${cont.slice(0, 20).map((h) => `
      <button class="tile wide" data-resume="${esc(h.key)}">
        <div class="thumb" style="background-image:url('${esc(h.image || '')}')"><div class="play">${ic('play')}</div><span class="left">nog ${fmt((h.duration - h.position) / 1000)}</span></div>
        <div class="bar" style="margin:0"><i style="width:${h.duration ? (h.position / h.duration) * 100 : 0}%"></i></div>
        <div class="meta" style="padding-top:10px"><div class="name">${esc(h.title)}</div><div class="now">${esc(h.subtitle || '')}</div></div>
      </button>`).join('')}</div>`;
  }
  for (const g of u.groups || []) {
    const chs = (g.channels || []).map((id) => S.byId.get(id)).filter(Boolean);
    if (chs.length) html += `<h2>${esc(g.name)}</h2><div class="row">${chs.map((c) => channelTile(c, 'g:' + g.id)).join('')}</div>`;
  }
  return html;
}

function orderedLiveCats() {
  const u = udata(), cats = S.cat.liveCats, by = new Map(cats.map((c) => [c.id, c]));
  const ordered = (u.liveOrder || []).map((id) => by.get(id)).filter(Boolean);
  const seen = new Set(ordered.map((c) => c.id));
  return [...ordered, ...cats.filter((c) => !seen.has(c.id))].filter((c) => !(u.hidden || []).includes(c.id));
}

const channelLine = (c, list, n) => `
  <div class="line" data-epg="${esc(c.id)}">
    <span class="num">${n ?? ''}</span>${logoBox(c.logo, c.name)}
    <button class="txt" data-play="${esc(c.id)}" data-list="${list}" style="text-align:left"><b>${esc(c.name)}</b><span class="now">&nbsp;</span></button>
    <button class="star ${udata().favorites.includes(c.id) ? 'on' : ''}" data-fav="${esc(c.id)}" aria-label="Mijn kanalen">${ic('star')}</button>
  </div>`;

function viewLive() {
  const cats = orderedLiveCats();
  if (!cats.length) return `<div class="empty"><b>Geen live kanalen</b></div>`;
  const cur = cats.find((c) => c.id === S.liveCat) || cats[0];
  const chs = S.cat.live.filter((c) => c.cat === cur.id);
  return `
    <div class="chips only-mobile">${cats.map((c) => `<button class="chip ${c.id === cur.id ? 'on' : ''}" data-livecat="${esc(c.id)}">${esc(c.name)}</button>`).join('')}</div>
    <div class="split">
      <div class="cats">${cats.map((c) => `<button class="${c.id === cur.id ? 'on' : ''}" data-livecat="${esc(c.id)}">${esc(c.name)}</button>`).join('')}</div>
      <div class="list">${chs.map((c, i) => channelLine(c, 'cat:' + cur.id, i + 1)).join('')}</div>
    </div>`;
}

function viewVod(kind) {
  const movies = kind === 'movies';
  const cats = movies ? S.cat.movieCats : S.cat.showCats;
  const items = movies ? S.cat.movies : S.cat.shows;
  if (!items.length) return `<div class="empty"><b>${S.vodLoading ? (movies ? 'Films' : 'Series') + ' laden…' : 'Geen ' + (movies ? 'films' : 'series')}</b></div>`;
  const all = [{ id: '__new', name: 'Nieuw toegevoegd' }, ...cats];
  const curId = (movies ? S.movieCat : S.showCat) || '__new';
  const list = curId === '__new' ? [...items].sort((a, b) => (b.added || 0) - (a.added || 0)).slice(0, 120) : items.filter((m) => m.cat === curId);
  const attr = movies ? 'data-moviecat' : 'data-showcat';
  return `
    <div class="chips only-mobile">${all.map((c) => `<button class="chip ${c.id === curId ? 'on' : ''}" ${attr}="${esc(c.id)}">${esc(c.name)}</button>`).join('')}</div>
    <div class="split">
      <div class="cats">${all.map((c) => `<button class="${c.id === curId ? 'on' : ''}" ${attr}="${esc(c.id)}">${esc(c.name)}</button>`).join('')}</div>
      <div class="grid">${list.slice(0, 400).map((m) => poster(m, movies ? 'movie' : 'show')).join('')}</div>
    </div>`;
}
const poster = (m, kind) => `
  <button class="poster" data-${kind}="${esc(m.id)}">
    <div class="img" style="background-image:url('${esc(m.poster || '')}')">${m.poster ? '' : `<span>${esc(m.name)}</span>`}${m.rating && m.rating !== '0' ? `<i class="rate">★ ${esc(String(m.rating).slice(0, 3))}</i>` : ''}</div>
    <div class="t">${esc(m.name)}</div>
  </button>`;

function viewSearch() {
  const q = S.query.trim().toLowerCase();
  let html = `<div class="section" style="padding-top:8px"><input class="input" id="q" placeholder="Zoek een kanaal, film of serie…" value="${esc(S.query)}" autocomplete="off"></div>`;
  if (q.length < 2) return html;
  const ch = S.cat.live.filter((c) => c.name.toLowerCase().includes(q)).slice(0, 40);
  const mv = S.cat.movies.filter((c) => c.name.toLowerCase().includes(q)).slice(0, 40);
  const sh = S.cat.shows.filter((c) => c.name.toLowerCase().includes(q)).slice(0, 40);
  if (!ch.length && !mv.length && !sh.length) return html + `<div class="empty">Niets gevonden voor "${esc(S.query)}"</div>`;
  if (ch.length) html += `<h2>Kanalen</h2><div class="list">${ch.map((c) => channelLine(c, 'search')).join('')}</div>`;
  if (mv.length) html += `<h2>Films</h2><div class="row">${mv.map((m) => `<div style="width:120px;flex:0 0 auto">${poster(m, 'movie')}</div>`).join('')}</div>`;
  if (sh.length) html += `<h2>Series</h2><div class="row">${sh.map((m) => `<div style="width:120px;flex:0 0 auto">${poster(m, 'show')}</div>`).join('')}</div>`;
  return html;
}

// ---------- sport ----------
// [id, naam, land, woorden in de gids, zenders die hem meestal uitzenden] — gelijk aan Sport.kt in de app.
const UEFA_TV = ['ziggo sport', 'rtl 7', 'tnt sports', 'amazon prime', 'prime video', 'sky sport', 'sky sports', 'canal+', 'movistar', 'dazn', 'rmc sport', 'bein', 'cbs sports', 'paramount', 'sport tv', 'veronica'];
const LEAGUES = [
  ['ned.1', 'Eredivisie', 'NL', ['eredivisie'], ['espn']],
  ['ned.2', 'Keuken Kampioen Divisie', 'NL', ['keuken kampioen', 'kkd', 'eerste divisie'], ['espn']],
  ['ned.cup', 'KNVB Beker', 'NL', ['knvb beker', 'knvb-beker', 'toto knvb'], ['espn', 'npo 1']],
  ['uefa.champions', 'Champions League', null, ['champions league', 'uefa champions', 'ucl'], UEFA_TV],
  ['uefa.europa', 'Europa League', null, ['europa league', 'uel'], UEFA_TV],
  ['uefa.europa.conf', 'Conference League', null, ['conference league', 'uecl'], UEFA_TV],
  ['eng.1', 'Premier League', 'UK', ['premier league', 'epl', 'barclays'], ['viaplay', 'sky sports', 'tnt sports', 'amazon prime', 'prime video', 'canal+', 'nbc', 'peacock', 'usa network', 'dazn', 'bein', 'supersport', 'optus', 'premier league']],
  ['esp.1', 'La Liga', 'ES', ['la liga', 'laliga'], ['ziggo sport', 'movistar', 'dazn', 'laliga tv', 'premier sports', 'espn', 'bein']],
  ['ita.1', 'Serie A', 'IT', ['serie a'], ['ziggo sport', 'dazn', 'sky sport', 'tnt sports', 'cbs sports', 'paramount', 'bein']],
  ['ger.1', 'Bundesliga', 'DE', ['bundesliga'], ['viaplay', 'sky sport', 'sky sports', 'dazn', 'espn', 'sport1']],
  ['fra.1', 'Ligue 1', 'FR', ['ligue 1'], ['ligue 1+', 'dazn', 'bein', 'canal+', 'espn']],
  ['por.1', 'Liga Portugal', 'PT', ['liga portugal', 'primeira liga', 'liga betclic'], ['sport tv', 'btv', 'benfica tv', 'dazn', 'eleven']],
];

async function loadMatches() {
  const d = new Date(Date.now() + S.sportDay * 864e5);
  const day = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  const results = await Promise.all(LEAGUES.map(async ([id, name, country, words, tv]) => {
    try {
      const j = await (await fetch(`https://site.api.espn.com/apis/site/v2/sports/soccer/${id}/scoreboard?dates=${day}`)).json();
      return (j.events || []).map((e) => {
        const c = e.competitions[0];
        const t = (side) => { const x = c.competitors.find((k) => k.homeAway === side); return { name: x.team.displayName, short: x.team.shortDisplayName, logo: x.team.logo, score: x.score }; };
        return { league: name, country, words, tv, home: t('home'), away: t('away'), start: Date.parse(e.date), state: e.status.type.state, detail: e.status.type.shortDetail };
      });
    } catch { return []; }
  }));
  S.matches = results.flat();
  render();
}

function viewSport() {
  if (!S.matches) { loadMatches(); return `<div class="empty"><b>Wedstrijden laden…</b></div>`; }
  const days = [[0, 'Vandaag'], [1, 'Morgen'], [-1, 'Gisteren']];
  let html = `<div class="chips">${days.map(([d, l]) => `<button class="chip ${S.sportDay === d ? 'on' : ''}" data-day="${d}">${l}</button>`).join('')}</div>`;
  if (!S.matches.length) return html + `<div class="empty"><b>Geen wedstrijden</b>Op deze dag zijn er geen wedstrijden in de grote competities.</div>`;
  let last = null;
  for (const m of S.matches) {
    if (m.league !== last) { html += `<h2 class="league-title">${esc(m.league)}</h2>`; last = m.league; }
    const st = m.state === 'in' ? `<span class="live-tag">LIVE</span><br><small class="dim">${esc(m.detail)}</small>` : m.state === 'post' ? '<small class="dim">Afgelopen</small>' : `<b>${clock(m.start)}</b>`;
    html += `<div class="list" style="margin-bottom:6px"><button class="line match" data-match="${S.matches.indexOf(m)}">
      <div>${st}</div>
      <div class="team home"><b>${esc(m.home.short || m.home.name)}</b>${m.home.logo ? `<img src="${esc(m.home.logo)}">` : ''}</div>
      <div class="score">${m.state === 'pre' ? '–' : `${m.home.score} - ${m.away.score}`}</div>
      <div class="team">${m.away.logo ? `<img src="${esc(m.away.logo)}">` : ''}<b>${esc(m.away.short || m.away.name)}</b></div>
    </button></div>`;
  }
  return html;
}

// Zoeken naar zenders, net als in de app: van zeker (beide clubs) naar waarschijnlijk (zender van de competitie).
const plainText = (s) => String(s || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
const wordRe = (list) => new RegExp('(^|[^a-z0-9])(' + list.map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|') + ')($|[^a-z0-9])');
const NOISE = new Set(['fc', 'afc', 'sc', 'cf', 'sv', 'as', 'ss', 'ac', 'fk', 'sk', 'bv', 'vv', 'cd', 'rc', 'rcd', 'ud', 'sd', '1.', 'calcio', 'club', 'de']);
const AMBIGUOUS = new Set(['manchester', 'real', 'athletic', 'atletico', 'inter', 'sporting', 'borussia', 'olympique', 'racing', 'dynamo', 'union', 'young', 'royal', 'stade', 'west', 'sheffield', 'nottingham', 'queens']);
function teamNames(t) {
  const words = plainText(t.name).split(/[\s-]+/).filter((w) => w && !NOISE.has(w));
  const first = words.length > 1 && words[0].length >= 5 && !AMBIGUOUS.has(words[0]) ? words[0] : null;
  return [...new Set([plainText(t.name), plainText(t.short), words.join(' '), first].filter((x) => x && x.length >= 2))];
}
const COUNTRY = { NL: ['nl', 'nld', 'netherlands', 'nederland', 'holland', 'dutch'], UK: ['uk', 'gb', 'en', 'eng', 'england', 'english'], DE: ['de', 'ger', 'germany', 'deutschland'],
  ES: ['es', 'esp', 'spain', 'espana'], IT: ['it', 'ita', 'italy', 'italia'], FR: ['fr', 'fra', 'france'], PT: ['pt', 'por', 'portugal'], BE: ['be', 'bel', 'belgium'], US: ['us', 'usa'],
  AR: ['ar', 'arg', 'argentina'], TR: ['tr', 'tur', 'turkey', 'turkiye'], AU: ['au', 'aus', 'australia'], MX: ['mx', 'mex', 'mexico'], BR: ['br', 'bra', 'brasil', 'brazil'],
  CA: ['ca', 'can', 'canada'], LATAM: ['latam', 'latino'], AR2: ['ara', 'arab', 'arabic', 'mena'] };
function countryOfChannel(c) {
  const group = S.cat.liveCats.find((g) => g.id === c.cat)?.name || '';
  for (const text of [c.name, group]) {
    const code = plainText(text).match(/^\W*([a-z]{2,3})\s*[|:\]\-/]/)?.[1];
    const hit = code && Object.entries(COUNTRY).find(([, w]) => w.includes(code));
    if (hit) return hit[0];
  }
  return null;
}

function matchHits(m) {
  const home = wordRe(teamNames(m.home)), away = wordRe(teamNames(m.away)), tv = wordRe(m.tv || []);
  const own = m.country || 'NL';
  const hits = new Map();
  for (const c of S.cat.live) {
    const n = plainText(c.name);
    if (home.test(n) && away.test(n)) hits.set(c.id, { c, score: 90, reason: 'Kanaal speciaal voor deze wedstrijd', land: countryOfChannel(c) });
    else if (tv.test(n)) {
      const land = countryOfChannel(c);
      if (!land || [own, 'NL', 'UK'].includes(land)) hits.set(c.id, { c, score: 30, reason: `Zendt de ${m.league} meestal uit`, land });
    }
  }
  return { hits, home, away };
}
const sortHits = (list, own) => {
  const level = (s) => (s >= 75 ? 0 : s >= 45 ? 1 : 2);
  const tier = (l) => (l === own ? 0 : l === 'NL' || l === 'UK' ? 1 : 2);
  return list.sort((a, b) => level(a.score) - level(b.score) || tier(a.land) - tier(b.land) || b.score - a.score || a.c.name.localeCompare(b.c.name)).slice(0, 60);
};

function openMatch(m) {
  const { hits, home, away } = matchHits(m);
  const own = m.country || 'NL';
  const leagueRe = wordRe(m.words || []);
  const draw = () => {
    const list = sortHits([...hits.values()], own);
    const box = $('#matchhits');
    if (!box) return;
    box.innerHTML = list.length
      ? `<h2>Hier is hij (waarschijnlijk) te zien</h2><div class="list">${list.map((h) => channelLine(h.c, 'search').replace('<span class="now">&nbsp;</span>', `<span class="now" style="color:var(--accent)">${esc(h.reason)}</span>`)).join('')}</div>`
      : '<p class="section dim">Geen zender gevonden die deze wedstrijd uitzendt.</p>';
  };
  sheet(`<div class="section"><div class="dim">${esc(m.league)}</div><h1 style="margin:6px 0">${esc(m.home.name)} – ${esc(m.away.name)}</h1>
    <p class="dim">${m.state === 'pre' ? 'Aftrap ' + clock(m.start) : esc(m.detail)}</p></div><div id="matchhits"></div>`);
  draw();
  // In de korte gids van de waarschijnlijke zenders kijken of de wedstrijd er echt op staat.
  const p = active();
  if (!p || p.type !== 'XTREAM' || (isDirect(p) && !onHttp)) return;
  const dec = (s) => { try { return decodeURIComponent(escape(atob(s))); } catch { return s; } };
  const kick = m.start / 1000;
  const check = sortHits([...hits.values()].filter((h) => h.score < 90), own).slice(0, 25);
  for (const h of check) {
    epgQueue(() => pfetch(`${xbase(p)}&action=get_short_epg&stream_id=${h.c.id}&limit=12`)).then((r) => r.json()).then((d) => {
      for (const e of d.epg_listings || []) {
        const start = +e.start_timestamp, stop = +e.stop_timestamp;
        if (!((start >= kick - 3600 && start <= kick + 1200) || (start <= kick && stop >= kick + 1800))) continue;
        const t = plainText(dec(e.title) + ' ' + dec(e.description || ''));
        const what = `${dec(e.title)} (${clock(start * 1000)})`;
        const hh = home.test(t), aa = away.test(t);
        const score = hh && aa ? 100 : hh || aa ? 75 : leagueRe.test(t) ? 55 : 0;
        if (score > h.score) { h.score = score; h.reason = (score === 55 ? `${m.league} in de gids: ` : 'In de gids: ') + what; draw(); }
      }
    }).catch(() => {});
  }
}

// ---------- instellingen ----------
function viewSettings() {
  const p = active();
  return `<div class="section" style="max-width:760px">
    <h2 style="margin-left:0">Account</h2>
    <div class="card"><b>Ingelogd als ${esc(S.username)}</b>
      <p class="dim" style="margin:6px 0 14px">${S.stats ? `Deze week ${watchTime(S.stats.week)} gekeken · in totaal ${watchTime(S.stats.total)}` : 'Je logins, kanalen en kijkgeschiedenis staan in je account.'}</p>
      <div class="btns"><button class="btn danger" data-act="logout">Uitloggen</button></div></div>
    <h2 style="margin-left:0">Logins</h2>
    <div class="list" style="padding:0">${S.profiles.map((x) => `
      <div class="line"><div class="txt"><b>${esc(x.name)}</b><span>${x.type === 'M3U' ? 'M3U-playlist' : 'Xtream · ' + esc(x.server.replace(/^https?:\/\//, ''))}</span></div>
        ${x.id === p.id ? '<b style="color:var(--accent)">In gebruik</b>' : `<button class="btn" data-use="${esc(x.id)}">Gebruiken</button>`}
        <button class="btn danger" data-del="${esc(x.id)}">Verwijderen</button></div>`).join('')}</div>
    <div class="btns" style="margin-top:12px"><button class="btn primary" data-act="add">Login toevoegen</button><button class="btn" data-act="reload">Alles opnieuw ophalen</button><button class="btn" data-act="diag">Verbinding testen</button></div>
    <h2 style="margin-left:0">Apps</h2>
    <div class="card"><p class="dim" style="margin-top:0">Op de iPhone: tik in Safari op <b>Delen</b> en kies <b>Zet op beginscherm</b>. Voor Android-tv en telefoon:</p>
      <div class="btns"><a class="btn" href="https://github.com/timmmmmmmmn/IPTIMTV/releases/latest" target="_blank" rel="noopener">Download de Android-apps</a></div></div>
  </div>`;
}

function renderWelcome(mode = 'login', error = '') {
  app.innerHTML = `<div class="welcome">
    <img class="logo" src="/logo.png" alt="IPTIMTV">
    <p>${mode === 'login' ? 'Log in met je IPTIMTV-account. Je logins, kanalen en kijkgeschiedenis staan daar al in.' : 'Maak een IPTIMTV-account aan.'}</p>
    <form class="card form" id="auth">
      <input class="input" name="username" placeholder="Gebruikersnaam" autocomplete="username" autocapitalize="off" required>
      <input class="input" name="password" type="password" placeholder="Wachtwoord" autocomplete="${mode === 'login' ? 'current-password' : 'new-password'}" required>
      ${error ? `<div class="err">${esc(error)}</div>` : ''}
      <button class="btn primary" style="justify-content:center">${mode === 'login' ? 'Inloggen' : 'Account maken'}</button>
      <button type="button" class="btn" style="justify-content:center" data-mode="${mode === 'login' ? 'register' : 'login'}">${mode === 'login' ? 'Nog geen account? Maak er een' : 'Ik heb al een account'}</button>
    </form>
  </div>`;
  $('#auth').onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      const r = await api(mode === 'login' ? 'login' : 'register', { method: 'POST', body: { username: f.get('username'), password: f.get('password'), device: 'Web' } });
      S.token = r.token; S.username = r.username;
      localStorage.setItem('token', r.token); localStorage.setItem('username', r.username);
      await boot();
    } catch (err) { renderWelcome(mode, err.message); }
  };
  app.querySelector('[data-mode]').onclick = (e) => renderWelcome(e.target.dataset.mode);
}

function renderAddLogin(first, error = '', type = 'XTREAM') {
  const html = `<div class="${first ? 'welcome' : 'section'}" style="${first ? '' : 'max-width:560px'}">
    ${first ? '<img class="logo" src="/logo.png" alt="IPTIMTV"><p>Voeg je IPTV-login toe. Hij wordt bewaard in je account, zodat al je apparaten hem hebben.</p>' : '<h2 style="margin-left:0">Login toevoegen</h2>'}
    <form class="card form" id="addlogin">
      <div class="seg"><button type="button" class="chip ${type === 'XTREAM' ? 'on' : ''}" data-type="XTREAM">Xtream</button><button type="button" class="chip ${type === 'M3U' ? 'on' : ''}" data-type="M3U">M3U-link</button></div>
      <input class="input" name="name" placeholder="Naam, bijvoorbeeld: Thuis">
      ${type === 'XTREAM' ? `<input class="input" name="server" placeholder="Server, bijvoorbeeld http://server.com:8080" autocapitalize="off" required>
        <input class="input" name="username" placeholder="Gebruikersnaam" autocapitalize="off" required>
        <input class="input" name="password" type="password" placeholder="Wachtwoord" required>`
      : `<input class="input" name="m3u" placeholder="Playlist-url (http://…)" autocapitalize="off" required>`}
      ${error ? `<div class="err">${esc(error)}</div>` : ''}
      <div class="btns"><button class="btn primary">Opslaan</button>${first ? '<button type="button" class="btn" data-act="logout">Uitloggen</button>' : '<button type="button" class="btn" data-act="cancel">Annuleren</button>'}</div>
    </form></div>`;
  if (first) app.innerHTML = html; else sheet(html);
  const root = first ? app : $('.sheet');
  root.querySelectorAll('[data-type]').forEach((b) => (b.onclick = () => (first ? renderAddLogin(true, '', b.dataset.type) : (closeSheet(), renderAddLogin(false, '', b.dataset.type)))));
  root.querySelector('[data-act="cancel"]')?.addEventListener('click', closeSheet);
  root.querySelector('[data-act="logout"]')?.addEventListener('click', logout);
  $('#addlogin').onsubmit = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    const id = Math.random().toString(36).slice(2, 10);
    let server = (f.server || '').trim().replace(/\/+$/, '');
    if (server && !/^https?:\/\//.test(server)) server = 'http://' + server;
    server = server.replace(/\/(player_api\.php|get\.php)$/, '');
    const p = { id, name: f.name?.trim() || (type === 'XTREAM' ? 'IPTV' : 'Playlist'), type, server, username: (f.username || '').trim(), password: (f.password || '').trim(), m3uUrl: (f.m3u || '').trim(), epgUrl: '', liveFormat: 'ts' };
    S.profiles = [...S.profiles, p];
    await pushSync('profiles', S.profiles);
    if (type === 'XTREAM') {
      let problem = null;
      try {
        const res = await fetch(px(xbase(p)));
        const text = await res.text();
        if (res.status === 403 || res.status >= 500) setDirect(p, true);
        else if (!res.ok) problem = `De server kon je provider niet bereiken (${res.status}): ${text.slice(0, 120)}`;
        else {
          const info = JSON.parse(text);
          if (String(info?.user_info?.auth) !== '1') problem = 'Je provider zegt dat gebruikersnaam of wachtwoord niet klopt.';
        }
      } catch {
        problem = 'Je provider gaf een onverwacht antwoord. Mogelijk weigert hij verbindingen vanaf de IPTIMTV-server.';
      }
      if (problem) {
        S.profiles = S.profiles.filter((x) => x.id !== id);
        await pushSync('profiles', S.profiles);
        return first ? renderAddLogin(true, problem, type) : toast(problem);
      }
    }
    S.activeId = id; localStorage.setItem('active', id);
    closeSheet();
    S.tab = 'home';
    loadCatalog();
  };
}

// ---------- detailschermen ----------
function sheet(html) {
  closeSheet();
  const el = document.createElement('div');
  el.className = 'sheet';
  el.innerHTML = `<div class="head"><button class="icon-btn" data-act="close" aria-label="Terug">${ic('back')}</button></div>${html}`;
  document.body.append(el);
  el.querySelectorAll('[data-epg]').forEach((x) => observer.observe(x));
  history.pushState({ sheet: 1 }, '');
}
function closeSheet() { $('.sheet')?.remove(); }

function openMovie(m) {
  const key = 'movie:' + m.url.split('/').pop();
  const h = S.history.find((x) => x.key === key && x.position > 60e3);
  sheet(`<div class="detail"><div class="cover" style="background-image:url('${esc(m.poster || '')}')"></div><div>
    <h1>${esc(m.name)}</h1>${m.rating && m.rating !== '0' ? `<p>★ ${esc(m.rating)}</p>` : ''}<p class="dim" id="plot"></p>
    <div class="btns">${h ? `<button class="btn primary" data-act="movie-resume">${ic('play')} Verder kijken (${fmt(h.position / 1000)})</button><button class="btn" data-act="movie-start">Vanaf het begin</button>`
      : `<button class="btn primary" data-act="movie-start">${ic('play')} Afspelen</button>`}</div></div></div>`);
  const item = { title: m.name, url: m.url, key, kind: 'movie', image: m.poster };
  $('.sheet [data-act="movie-start"]').onclick = () => play([item], 0, false, 0);
  $('.sheet [data-act="movie-resume"]')?.addEventListener('click', () => play([item], 0, false, h.position));
  const p = active();
  if (p.type === 'XTREAM' && (!isDirect(p) || onHttp)) pfetch(`${xbase(p)}&action=get_vod_info&vod_id=${m.id}`).then((r) => r.json()).then((d) => { const el = $('#plot'); if (el) el.textContent = d?.info?.plot || ''; }).catch(() => {});
}

async function openShow(s) {
  sheet(`<div class="detail"><div class="cover" style="background-image:url('${esc(s.poster || '')}')"></div><div style="flex:1;min-width:0">
    <h1>${esc(s.name)}</h1><p class="dim">${esc(s.plot || '')}</p><div id="eps" class="dim">Afleveringen laden…</div></div></div>`);
  const p = active();
  try {
    const eps = [];
    const add = (e, season) => eps.push({
      id: String(e.id), season: Number(e.season) || season, num: Number(e.episode_num) || 0, title: e.title || `Aflevering ${e.episode_num}`,
      url: `${p.server}/series/${p.username}/${p.password}/${e.id}.${e.container_extension || 'mp4'}`, image: e.info?.movie_image,
    });
    let d = null;
    if (!isDirect(p) || onHttp) d = await pfetch(`${xbase(p)}&action=get_series_info&series_id=${s.id}`).then((r) => r.json()).catch(() => null);
    if (!d && isDirect(p)) {
      const res = await fetch(`${API}catalog/${p.id}/series/${s.id}`, { headers: { authorization: 'Bearer ' + S.token } });
      if (!res.ok) { $('#eps').textContent = 'Open deze serie één keer in de tv- of telefoon-app, dan staan de afleveringen hier ook.'; return; }
      for (const [id, season, num, title, ext, image] of await gunzipJson(res)) {
        add({ id, season, episode_num: num, title, container_extension: ext, info: { movie_image: image } }, season);
      }
    } else {
      if (!d) throw new Error();
      if (Array.isArray(d.episodes)) d.episodes.forEach((list, i) => (Array.isArray(list) ? list : [list]).forEach((e) => add(e, i + 1)));
      else Object.entries(d.episodes || {}).forEach(([k, list]) => list.forEach((e) => add(e, Number(k))));
    }
    eps.sort((a, b) => a.season - b.season || a.num - b.num);
    const seasons = [...new Set(eps.map((e) => e.season))];
    const show = (season) => {
      const list = eps.filter((e) => e.season === season);
      $('#eps').innerHTML = `<div class="chips" style="padding:4px 0 12px">${seasons.map((n) => `<button class="chip ${n === season ? 'on' : ''}" data-season="${n}">Seizoen ${n}</button>`).join('')}</div>
        <div class="list" style="padding:0">${list.map((e, i) => {
          const h = S.history.find((x) => x.key === 'episode:' + e.url.split('/').pop());
          return `<button class="line" data-ep="${i}"><div class="txt"><b>${e.num}. ${esc(e.title)}</b><span>${h && h.position > 60e3 ? 'Verder vanaf ' + fmt(h.position / 1000) : ''}</span></div>${ic('play')}</button>`;
        }).join('')}</div>`;
      $('#eps').querySelectorAll('[data-season]').forEach((b) => (b.onclick = () => show(Number(b.dataset.season))));
      $('#eps').querySelectorAll('[data-ep]').forEach((b) => (b.onclick = () => {
        const items = list.map((e) => ({ title: e.title, url: e.url, key: 'episode:' + e.url.split('/').pop(), kind: 'episode', parent: s.id, subtitle: `${s.name} · S${e.season} A${e.num}`, image: e.image || s.poster }));
        const i = Number(b.dataset.ep);
        const h = S.history.find((x) => x.key === items[i].key);
        play(items, i, false, h && h.position > 60e3 ? h.position : 0);
      }));
    };
    if (seasons.length) show(seasons[0]); else $('#eps').textContent = 'Geen afleveringen gevonden.';
  } catch { $('#eps').textContent = 'Kon de afleveringen niet laden.'; }
}

// ---------- speler ----------
let hls = null, mts = null;
let mpegtsPromise = null;
/** mpegts.js pas laden als het nodig is (voor losse .ts-streams). */
const loadMpegts = () => (mpegtsPromise ||= new Promise((ok) => {
  if (window.mpegts) return ok(true);
  const sc = document.createElement('script');
  sc.src = 'https://cdn.jsdelivr.net/npm/mpegts.js@1.8.0/dist/mpegts.js';
  sc.onload = () => ok(true); sc.onerror = () => ok(false);
  document.head.append(sc);
}));

/**
 * Rechtstreeks afspelen vanaf de provider. Die streamt alleen via http, en een https-pagina mag dat niet laden;
 * daarom een eigen afspeelpagina op http-poort 8080 (die browsers niet naar https omzetten).
 */
function playDirect(items, index, live, startAt) {
  // Bij lange kanaallijsten alleen de buren meegeven, anders wordt het adres te lang.
  const from = Math.max(0, index - 150);
  const data = { items: items.slice(from, index + 150), i: index - from, live, startAt, token: S.token, back: location.origin + '/' };
  const bytes = new TextEncoder().encode(JSON.stringify(data));
  let bin = '';
  for (let k = 0; k < bytes.length; k += 8192) bin += String.fromCharCode(...bytes.subarray(k, k + 8192));
  const b64 = btoa(bin);
  location.href = `http://${location.hostname}:8080/play#${b64}`;
}

function play(items, index, live, startAt = 0) {
  const p = active();
  if (p && isDirect(p) && items[index]?.url?.startsWith('http:') && location.protocol === 'https:') return playDirect(items, index, live, startAt);
  closeSheet();
  $('.player')?.remove();
  const el = document.createElement('div');
  el.className = 'player';
  el.innerHTML = `<div class="bar-top">
      <button class="icon-btn" data-act="pclose" aria-label="Stoppen">${ic('back')}</button>
      <div class="txt"><b></b><span></span></div>
      ${live ? `<button class="icon-btn" data-act="pprev" aria-label="Vorig kanaal">${ic('prev')}</button><button class="icon-btn" data-act="pnext" aria-label="Volgend kanaal">${ic('next')}</button>` : ''}
    </div>
    <video playsinline controls autoplay></video><div class="msg hidden"></div>`;
  document.body.append(el);
  history.pushState({ player: 1 }, '');
  const video = $('video', el);
  let i = index, watched = 0, lastTick = Date.now(), idle;

  const wake = () => { el.classList.remove('idle'); clearTimeout(idle); idle = setTimeout(() => el.classList.add('idle'), 3500); };
  el.addEventListener('pointermove', wake); el.addEventListener('touchstart', wake, { passive: true }); wake();

  const flush = (finished = false) => {
    const it = items[i];
    if (!it?.key) return;
    const dur = isFinite(video.duration) ? Math.round(video.duration * 1000) : 0;
    const body = { key: it.key, kind: it.kind, title: it.title, subtitle: it.subtitle, image: it.image, parent: it.parent,
      position: finished ? dur : it.kind === 'live' ? 0 : Math.round(video.currentTime * 1000), duration: dur, watched: Math.round(watched),
      day: new Date().toLocaleDateString('sv') };
    watched = 0;
    api('progress', { method: 'POST', body }).catch(() => {});
    const h = S.history.find((x) => x.key === it.key);
    if (h) Object.assign(h, body, { updated: Date.now() }); else if (it.kind !== 'live') S.history.unshift({ ...body, updated: Date.now() });
  };
  const timer = setInterval(() => {
    const now = Date.now();
    if (!video.paused) watched += (now - lastTick) / 1000;
    lastTick = now;
    if (watched >= 30) flush();
  }, 1000);

  // Probeert de stream op meerdere manieren: HLS (.m3u8), daarna de losse .ts-stream via mpegts.js.
  let attempt = 0, tries = [], errors = [], stallTimer;
  const stopEngines = () => {
    clearTimeout(stallTimer);
    if (hls) { hls.destroy(); hls = null; }
    if (mts) { try { mts.destroy(); } catch {} mts = null; }
    video.removeAttribute('src');
  };
  const load = () => {
    const it = items[i];
    $('.txt b', el).textContent = live ? `${S.cat.live.indexOf(S.byId.get(it.id)) + 1 || ''}  ${it.title}` : it.title;
    $('.txt span', el).textContent = it.subtitle || '';
    $('.msg', el).classList.add('hidden');
    const direct = isDirect(active());
    const src = (u) => (direct ? u : px(u));
    tries = [it.url];
    if (/\/live\/.+\.m3u8$/i.test(it.url)) tries.push(it.url.replace(/\.m3u8$/i, '.ts'));
    else if (/\/live\/.+\.ts$/i.test(it.url)) tries.unshift(it.url.replace(/\.ts$/i, '.m3u8'));
    tries = tries.map(src);
    attempt = 0; errors = [];
    start(it);
    if (live && it.id) {
      const ch = S.byId.get(it.id);
      if (ch) nowOn(ch).then((n) => { if (n && items[i] === it) $('.txt span', el).textContent = 'Nu: ' + n.title; });
    }
  };
  const start = async (it) => {
    stopEngines();
    const url = tries[attempt], my = attempt;
    const stale = () => my !== attempt || items[i] !== it;
    const isHls = /\.m3u8(\?|$|&)/i.test(decodeURIComponent(url));
    const isTs = /\.ts(\?|$|&)/i.test(decodeURIComponent(url));
    // hls.js overal waar het kan; iPhone/iPad speelt HLS zelf af.
    const ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    try {
      if (isHls && !ios && window.Hls?.isSupported()) {
        hls = new Hls({ lowLatencyMode: false, backBufferLength: 30 });
        hls.loadSource(url);
        hls.attachMedia(video);
        hls.on(Hls.Events.ERROR, (_, d) => { if (d.fatal && !stale()) next(`HLS: ${d.details}${d.response?.code ? ' ' + d.response.code : ''}`); });
      } else if (isTs && (await loadMpegts()) && mpegts.isSupported()) {
        if (stale()) return;
        mts = mpegts.createPlayer({ type: 'mpegts', isLive: live, url }, { enableWorker: false, liveBufferLatencyChasing: live, lazyLoad: false });
        mts.attachMediaElement(video);
        mts.on(mpegts.Events.ERROR, (type, detail, info) => { if (!stale()) next(`TS: ${type} ${detail}${info?.code ? ' ' + info.code : ''}`); });
        mts.load();
      } else {
        video.src = url;
      }
    } catch (e) { return next('start: ' + e.message); }
    if (startAt && !live) video.addEventListener('loadedmetadata', () => { video.currentTime = startAt / 1000; startAt = 0; }, { once: true });
    video.play().catch(() => {});
    // Na 15 seconden zonder beeld de volgende vorm proberen.
    stallTimer = setTimeout(() => { if (!stale() && video.readyState < 3) next('geen beeld na 15 s'); }, 15000);
  };
  const next = (why) => {
    errors.push(`${tries[attempt].match(/\.(m3u8|ts|mp4|mkv|avi)\b/i)?.[0] || 'stream'}: ${why}`);
    if (++attempt < tries.length) return start(items[i]);
    stopEngines();
    fail();
  };
  video.addEventListener('playing', () => clearTimeout(stallTimer));
  const fail = async () => {
    const m = $('.msg', el);
    const it = items[i];
    let extra = '';
    if (/\.(mkv|avi)$/i.test(it.url)) extra = 'Dit bestand (.mkv/.avi) kunnen browsers niet afspelen.';
    else if (isDirect(active())) {
      // Even kijken wat de provider op dit adres terugstuurt.
      extra = await fetch(it.url, { method: 'GET', cache: 'no-store' })
        .then((r) => { r.body?.cancel(); return `Je provider antwoordt met ${r.status}${r.status === 403 ? ' (geweigerd)' : ''}.`; })
        .catch((e) => `Je browser kreeg geen antwoord van je provider (${e.message}).`);
    }
    m.innerHTML = `Dit kan in de browser niet worden afgespeeld. Probeer de tv- of telefoon-app.<br><small style="opacity:.7">${esc(extra)}<br>${esc(errors.join(' · '))}</small>`;
    m.classList.remove('hidden');
  };
  // Met hls.js/mpegts.js melden die zelf hun fouten; anders de fout van de video zelf.
  video.addEventListener('error', () => { if (hls || mts || !video.getAttribute('src')) return; next('video ' + (video.error?.code || '')); });
  video.addEventListener('ended', () => { flush(true); if (!live && i < items.length - 1) { i++; load(); } else close(); });

  const close = () => {
    flush();
    clearInterval(timer);
    stopEngines();
    video.pause(); video.load();
    el.remove();
    render();
  };
  const go = (d) => { flush(); i = (i + d + items.length) % items.length; load(); };
  $('[data-act="pclose"]', el).onclick = () => history.back();
  $('[data-act="pprev"]', el)?.addEventListener('click', () => go(-1));
  $('[data-act="pnext"]', el)?.addEventListener('click', () => go(1));
  el._close = close;
  load();
}

function playChannel(id, list) {
  const u = udata();
  let chs;
  if (list === 'fav') chs = u.favorites.map((x) => S.byId.get(x)).filter(Boolean);
  else if (list?.startsWith('g:')) chs = (u.groups.find((g) => g.id === list.slice(2))?.channels || []).map((x) => S.byId.get(x)).filter(Boolean);
  else if (list?.startsWith('cat:')) chs = S.cat.live.filter((c) => c.cat === list.slice(4));
  else chs = [S.byId.get(id)].filter(Boolean);
  const idx = Math.max(0, chs.findIndex((c) => c.id === id));
  play(chs.map((c) => ({ id: c.id, title: c.name, url: c.url, key: 'live:' + c.id, kind: 'live', image: c.logo })), idx, true);
}

function resume(key) {
  const h = S.history.find((x) => x.key === key);
  const p = active();
  if (!h || p.type !== 'XTREAM') return;
  const seg = key.slice(key.indexOf(':') + 1);
  const url = `${p.server}/${h.kind === 'episode' ? 'series' : 'movie'}/${p.username}/${p.password}/${seg}`;
  play([{ ...h, url }], 0, false, h.position);
}

// ---------- klikken ----------
document.addEventListener('click', async (e) => {
  const t = e.target.closest('button, a');
  if (!t) return;
  const d = t.dataset;
  if (d.tab) { S.tab = d.tab; window.scrollTo(0, 0); render(); }
  else if (d.play) playChannel(d.play, d.list);
  else if (d.resume) resume(d.resume);
  else if (d.fav) {
    const u = udata();
    u.favorites = u.favorites.includes(d.fav) ? u.favorites.filter((x) => x !== d.fav) : [...u.favorites, d.fav];
    t.classList.toggle('on');
    toast(u.favorites.includes(d.fav) ? 'Toegevoegd aan Mijn kanalen' : 'Weggehaald uit Mijn kanalen');
    saveUser();
  }
  else if (d.livecat) { S.liveCat = d.livecat; render(); window.scrollTo(0, 0); }
  else if (d.moviecat) { S.movieCat = d.moviecat; render(); }
  else if (d.showcat) { S.showCat = d.showcat; render(); }
  else if (d.movie) openMovie(S.cat.movies.find((m) => m.id === d.movie));
  else if (d.show) openShow(S.cat.shows.find((m) => m.id === d.show));
  else if (d.day) { S.sportDay = Number(d.day); S.matches = null; render(); }
  else if (d.match) openMatch(S.matches[Number(d.match)]);
  else if (d.use) { S.activeId = d.use; localStorage.setItem('active', d.use); S.cat = null; S.tab = 'home'; loadCatalog(); }
  else if (d.del) {
    if (!confirm('Deze login verwijderen (op al je apparaten)?')) return;
    S.profiles = S.profiles.filter((p) => p.id !== d.del);
    await pushSync('profiles', S.profiles);
    if (S.activeId === d.del) { S.activeId = S.profiles[0]?.id; S.cat = null; if (S.activeId) loadCatalog(); }
    render();
  }
  else if (d.act === 'close') history.back();
  else if (d.act === 'logout') logout();
  else if (d.act === 'add') renderAddLogin(false);
  else if (d.act === 'reload') { epgCache.clear(); loadCatalog(true); }
  else if (d.act === 'diag') runDiag();
});
document.addEventListener('input', (e) => {
  if (e.target.id !== 'q') return;
  S.query = e.target.value;
  const pos = e.target.selectionStart;
  render();
  const q = $('#q'); q.focus(); q.setSelectionRange(pos, pos);
});
// Terug-knop (ook de terug-veeg op de iPhone) sluit eerst de speler of het detailscherm.
window.addEventListener('popstate', () => {
  const pl = $('.player');
  if (pl) return pl._close();
  if ($('.sheet')) return closeSheet();
});

/** Laat zien wat de server terugkrijgt van je provider (zonder wachtwoord). */
async function runDiag() {
  sheet(`<div class="section" style="max-width:900px"><h1 style="margin:0 0 8px">Verbinding testen</h1>
    <p class="dim">De IPTIMTV-server probeert nu je provider te bereiken. Dit duurt ongeveer 10 seconden.</p>
    <pre id="diag" class="card" style="white-space:pre-wrap;font-size:13px;line-height:1.5">Bezig…</pre>
    <div class="btns"><button class="btn primary" id="copydiag">Kopiëren</button></div></div>`);
  const lines = [];
  try {
    const r = await api('diag');
    lines.push(...r.lines);
  } catch (e) {
    lines.push('Servertest mislukt: ' + e.message);
  }
  // Ook vanuit deze browser zelf (alleen mogelijk op de http-versie).
  const p = active();
  if (p?.type === 'XTREAM' && onHttp) {
    lines.push('', `Vanuit je browser (${navigator.userAgent.match(/(Chrome|Firefox|Safari|Edg)\/[\d.]+/)?.[0] || 'browser'}):`);
    const url = xbase(p);
    const tries = [['gewoon', {}], ['zonder referer', { referrerPolicy: 'no-referrer' }], ['ondoorzichtig', { mode: 'no-cors' }]];
    for (const [name, opt] of tries) {
      const t0 = performance.now();
      try {
        const res = await fetch(url, { cache: 'no-store', ...opt });
        const ms = Math.round(performance.now() - t0);
        if (res.type === 'opaque') lines.push(`  ${name}: bereikbaar, maar antwoord niet leesbaar · ${ms} ms`);
        else {
          const text = await res.text();
          lines.push(`  ${name}: ${res.status} · ${ms} ms · cors: ${res.headers.get('access-control-allow-origin') || '-'} · ${text.slice(0, 120).replace(/\s+/g, ' ')}`);
        }
      } catch (e) {
        lines.push(`  ${name}: ${e.name}: ${e.message} · ${Math.round(performance.now() - t0)} ms`);
      }
    }
  } else if (p?.type === 'XTREAM') lines.push('', 'Browsertest: open dit op http://' + location.hostname + ':8080 om ook je browser te testen.');
  const pw = p?.password;
  $('#diag').textContent = lines.join('\n').split(pw || '\u0000').join('***');
  $('#copydiag').onclick = () => navigator.clipboard?.writeText($('#diag').textContent).then(() => toast('Gekopieerd'));
}

function logout() {
  api('logout', { method: 'POST' }).catch(() => {});
  localStorage.clear();
  Object.assign(S, { token: null, username: null, profiles: [], user: {}, cat: null, history: [], stats: null, activeId: null });
  render();
}

async function boot() {
  if (location.hash.startsWith('#t=')) {
    try {
      const d = JSON.parse(atob(location.hash.slice(3)));
      Object.assign(S, { token: d.token, username: d.username, activeId: d.active });
      localStorage.setItem('token', d.token); localStorage.setItem('username', d.username || '');
      if (d.active) localStorage.setItem('active', d.active);
      if (d.direct) localStorage.setItem('direct:' + d.direct, '1');
    } catch { /* ongeldig */ }
    history.replaceState(null, '', location.pathname);
  }
  if (!S.token) return render();
  S.loading = 'Je account laden…';
  try {
    await pullSync();
  } catch { /* uitgelogd of offline */ }
  if (!S.token) return render();
  api('history?limit=80').then((r) => { S.history = r.items || []; render(); }).catch(() => {});
  const tz = new Date().getTimezoneOffset();
  api('me?tz=' + tz).then((r) => { S.stats = r.stats; }).catch(() => {});
  S.loading = null;
  if (active()) { S.activeId = active().id; localStorage.setItem('active', S.activeId); loadCatalog(); } else render();
}

// Terug van de afspeelpagina: kijkgeschiedenis bijwerken.
window.addEventListener('pageshow', (e) => {
  if (e.persisted && S.token) api('history?limit=80').then((r) => { S.history = r.items || []; render(); }).catch(() => {});
});

if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
boot();
