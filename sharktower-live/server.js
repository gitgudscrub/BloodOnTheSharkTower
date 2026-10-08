import http from 'node:http';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT || 3000);
const CLIENT_ID = process.env.DISCORD_CLIENT_ID;
const CLIENT_SECRET = process.env.DISCORD_CLIENT_SECRET;
const GUILD_ID = process.env.DISCORD_GUILD_ID;
const REDIRECT_URI = process.env.DISCORD_REDIRECT_URI;
const SESSION_SECRET = process.env.SESSION_SECRET;
const BRIDGE_TOKEN = process.env.BRIDGE_TOKEN;
const SECURE = process.env.COOKIE_SECURE === '1';
if (![CLIENT_ID, CLIENT_SECRET, GUILD_ID, REDIRECT_URI, SESSION_SECRET, BRIDGE_TOKEN].every(Boolean) || SESSION_SECRET.length < 32 || BRIDGE_TOKEN.length < 32) {
  console.error('Set Discord OAuth, guild, redirect, SESSION_SECRET (32+ chars) and BRIDGE_TOKEN (32+ chars) environment variables.');
  process.exit(1);
}
const sessions = new Map();
const pending = new Map();
const emptyGame = () => ({ live: false, gameId: null, phase: null, day: null, night: null, players: [], conversations: [] });
let publicGame = emptyGame();
let lastBridgeUpdate = 0;
const BRIDGE_TIMEOUT_MS = 15_000;
const STREAM_HEARTBEAT_MS = 10_000;
const streams = new Set();
let lastPublishedState = JSON.stringify(emptyGame());

function currentGame() {
  return Date.now() - lastBridgeUpdate < BRIDGE_TIMEOUT_MS ? publicGame : emptyGame();
}
function pushGameUpdate() {
  const serialized = JSON.stringify(currentGame());
  if (serialized === lastPublishedState) return;
  lastPublishedState = serialized;
  for (const res of streams) {
    if (res.destroyed || res.writableEnded) { streams.delete(res); continue; }
    res.write('event: game\n' + 'data: ' + serialized + '\n\n');
  }
}
// No stale game data after the Minecraft bridge goes offline.
setInterval(pushGameUpdate, 1_000).unref();

async function liveEvents(req, res) {
  if (!await currentUser(req)) return json(res, 401, { error: 'Login required' });
  if (streams.size >= 100) return json(res, 503, { error: 'Too many spectators' });
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-store, no-transform',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
    'X-Content-Type-Options': 'nosniff'
  });
  res.flushHeaders();
  res.write('retry: 2000\n\n');
  res.write('event: game\ndata: ' + JSON.stringify(currentGame()) + '\n\n');
  streams.add(res);
  let checking = false;
  const heartbeat = setInterval(async () => {
    if (checking || res.writableEnded || res.destroyed) return;
    checking = true;
    try {
      // Revalidate session and Discord guild membership during an open stream.
      if (!await currentUser(req)) { res.end(); return; }
      res.write(': keepalive\n\n');
    } catch {
      res.end();
    } finally { checking = false; }
  }, STREAM_HEARTBEAT_MS);
  heartbeat.unref();
  res.on('close', () => { clearInterval(heartbeat); streams.delete(res); });
}

function cookieValue(req, key) {
  const raw = req.headers.cookie || '';
  return raw.split(';').map(s => s.trim()).find(s => s.startsWith(key + '='))?.slice(key.length + 1);
}
function signed(token) { return token + '.' + crypto.createHmac('sha256', SESSION_SECRET).update(token).digest('hex'); }
function validCookie(value) {
  if (!value) return null;
  const index = value.lastIndexOf('.');
  if (index < 0) return null;
  const token = value.slice(0, index), signature = value.slice(index + 1);
  const expected = signed(token).slice(token.length + 1);
  if (signature.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) return null;
  return token;
}
const baseCookie = 'Path=/; HttpOnly; SameSite=Lax' + (SECURE ? '; Secure' : '');
function json(res, status, data, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', ...headers });
  res.end(JSON.stringify(data));
}
function redirect(res, path, headers = {}) { res.writeHead(302, { Location: path, 'Cache-Control': 'no-store', ...headers }); res.end(); }
function html(res, data) {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; connect-src 'self'; script-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'", 'X-Content-Type-Options': 'nosniff' });
  res.end(data);
}
async function membership(token) {
  const r = await fetch('https://discord.com/api/v10/users/@me/guilds/' + encodeURIComponent(GUILD_ID) + '/member', { headers: { Authorization: 'Bearer ' + token } });
  return r.status === 200;
}
async function currentUser(req) {
  const sid = validCookie(cookieValue(req, 'shark_session'));
  const session = sid && sessions.get(sid);
  if (!session || session.expires < Date.now()) { if (sid) sessions.delete(sid); return null; }
  // Revalidate periodically; active users are not permanently authorised by stale login data.
  if (session.lastCheck + 60_000 < Date.now()) {
    if (!await membership(session.token)) { sessions.delete(sid); return null; }
    session.lastCheck = Date.now();
  }
  return session.user;
}
function readJson(req, limit = 64_000) {
  return new Promise((resolve, reject) => {
    let body = '', count = 0;
    req.on('data', chunk => { count += chunk.length; if (count > limit) { reject(new Error('Payload too large')); req.destroy(); } else body += chunk; });
    req.on('end', () => { try { resolve(JSON.parse(body)); } catch { reject(new Error('Invalid JSON')); } });
    req.on('error', reject);
  });
}
function sanitizeGame(input) {
  if (!input || typeof input !== 'object') throw new Error('Invalid game state');
  const str = (x, max = 80) => typeof x === 'string' ? x.slice(0, max) : '';
  if (!Array.isArray(input.players) || input.players.length > 100 || !Array.isArray(input.conversations) || input.conversations.length > 50) throw new Error('Invalid lists');
  return {
    live: input.live === true, gameId: str(input.gameId), phase: ['day', 'night', 'setup', 'ended'].includes(input.phase) ? input.phase : 'setup',
    day: Number.isInteger(input.day) && input.day >= 0 ? input.day : 0,
    night: Number.isInteger(input.night) && input.night >= 0 ? input.night : 0,
    players: input.players.map(p => ({ id: str(p.id), name: str(p.name), alive: p.alive === true, chatGroup: p.chatGroup === null ? null : str(p.chatGroup) })),
    conversations: input.conversations.map(c => ({ id: str(c.id), name: str(c.name), playerIds: Array.isArray(c.playerIds) ? c.playerIds.slice(0, 100).map(id => str(id)) : [] }))
  };
}
const page = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Sharktower Live</title><style>body{background:#111827;color:#f9fafb;font:16px system-ui;margin:0;padding:40px 18px}main{max-width:760px;margin:auto}.card{border:1px solid #374151;border-radius:16px;padding:22px;margin:16px 0;background:#1f2937}a{color:#ddd6fe}button{padding:12px 18px;background:#5865f2;color:white;border:0;border-radius:9px;cursor:pointer}small{color:#9ca3af}li{padding:5px}</style></head><body><main><h1>Sharktower Live</h1><p>Private spectator hub for Blood on the Sharktower.</p><div id="main" class="card">Checking membership…</div><div id="game" class="card" hidden><h2>Current game</h2><div id="status"></div><h3>Players</h3><ul id="players"></ul><h3>Conversations</h3><ul id="conversations"></ul><small>Live voice and Grimoire viewing are not yet enabled.</small></div></main><script>
const main=document.getElementById('main'),game=document.getElementById('game');
let stream=null, fallback=null;
const status=document.getElementById('status');
function renderGame(data){
  game.hidden=false;
  status.textContent=!data.live?'No active game':data.phase==='setup'?'Game setup in progress':data.phase==='night'?'Night '+data.night:'Day '+data.day;
  for(const [id,items,render] of [
    ['players',data.players,p=>p.name+(p.alive?'':' (dead)')],
    ['conversations',data.conversations,c=>c.name+' ('+c.playerIds.length+' players)']
  ]){
    const list=document.getElementById(id);list.replaceChildren();
    for(const item of items){const li=document.createElement('li');li.textContent=render(item);list.append(li);}
  }
}
async function fetchGame(){
  const response=await fetch('/api/game');
  if(response.status===401){location.reload();return;}
  if(response.ok)renderGame(await response.json());
}
function startFallback(){
  if(!fallback)fallback=setInterval(()=>fetchGame().catch(()=>{}),3000);
}
function stopFallback(){if(fallback){clearInterval(fallback);fallback=null;}}
async function connect(){
  const response=await fetch('/api/me');
  const me=await response.json();
  if(!me.user){
    if(stream){stream.close();stream=null;}
    main.replaceChildren();const a=document.createElement('a');
    a.href='/auth/login';a.textContent='Sign in with Discord (Clocktower server members only)';
    main.append(a);game.hidden=true;return;
  }
  main.textContent='Signed in as '+me.user.username+' · ';
  const logout=document.createElement('a');logout.href='/auth/logout';logout.textContent='Sign out';main.append(logout);
  await fetchGame();
  if(stream)return;
  if(!window.EventSource){startFallback();return;}
  stream=new EventSource('/api/events');
  stream.addEventListener('game',event=>{try{renderGame(JSON.parse(event.data));stopFallback();}catch{}});
  stream.onopen=()=>stopFallback();
  stream.onerror=()=>startFallback(); // EventSource automatically reconnects.
}
connect().catch(()=>{main.textContent='Unable to load spectator status.';});
setInterval(async()=>{
  try{const result=await fetch('/api/me');const body=await result.json();if(!body.user)location.reload();}
  catch{}
},60000);
</script></body></html>`;
http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/auth/login' && req.method === 'GET') {
      const state = crypto.randomBytes(32).toString('hex');
      pending.set(state, Date.now() + 300_000);
      const p = new URLSearchParams({ client_id: CLIENT_ID, redirect_uri: REDIRECT_URI, response_type: 'code', scope: 'identify guilds.members.read', state });
      return redirect(res, 'https://discord.com/oauth2/authorize?' + p, { 'Set-Cookie': 'shark_oauth=' + signed(state) + '; Max-Age=300; ' + baseCookie });
    }
    if (url.pathname === '/auth/callback' && req.method === 'GET') {
      const state = url.searchParams.get('state'), code = url.searchParams.get('code');
      const expiry = pending.get(state); pending.delete(state);
      const cookieState = validCookie(cookieValue(req, 'shark_oauth'));
      if (state !== cookieState) return json(res, 403, { error: 'OAuth state does not match this browser' }, { 'Set-Cookie': 'shark_oauth=; Max-Age=0; ' + baseCookie });
      if (!code || !expiry || expiry < Date.now()) return json(res, 403, { error: 'Invalid or expired login state' });
      const oauth = await fetch('https://discord.com/api/v10/oauth2/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ client_id: CLIENT_ID, client_secret: CLIENT_SECRET, grant_type: 'authorization_code', code, redirect_uri: REDIRECT_URI }) });
      if (!oauth.ok) return json(res, 401, { error: 'Discord login failed' });
      const tokens = await oauth.json();
      if (!await membership(tokens.access_token)) return json(res, 403, { error: 'Clocktower server membership required' });
      const r = await fetch('https://discord.com/api/v10/users/@me', { headers: { Authorization: 'Bearer ' + tokens.access_token } });
      if (!r.ok) return json(res, 502, { error: 'Discord user lookup failed' });
      const who = await r.json(), sid = crypto.randomBytes(32).toString('hex');
      sessions.set(sid, { user: { id: who.id, username: who.global_name || who.username }, token: tokens.access_token, expires: Date.now() + Math.min((tokens.expires_in || 3600) * 1000, 3_600_000), lastCheck: Date.now() });
      return redirect(res, '/', { 'Set-Cookie': ['shark_session=' + signed(sid) + '; Max-Age=3600; ' + baseCookie, 'shark_oauth=; Max-Age=0; ' + baseCookie] });
    }
    if (url.pathname === '/auth/logout') {
      const sid = validCookie(cookieValue(req, 'shark_session')); if (sid) sessions.delete(sid);
      return redirect(res, '/', { 'Set-Cookie': 'shark_session=; Max-Age=0; ' + baseCookie });
    }
    if (url.pathname === '/api/bridge/state' && req.method === 'POST') {
      const expected = Buffer.from(BRIDGE_TOKEN), provided = Buffer.from((req.headers.authorization || '').replace(/^Bearer /, ''));
      if (expected.length !== provided.length || !crypto.timingSafeEqual(expected, provided)) return json(res, 403, { error: 'Forbidden' });
      try { publicGame = sanitizeGame(await readJson(req)); lastBridgeUpdate = Date.now(); pushGameUpdate(); return json(res, 200, { ok: true }); }
      catch { return json(res, 400, { error: 'Invalid game payload' }); }
    }
    if (url.pathname === '/') return html(res, page);
    if (url.pathname === '/api/events' && req.method === 'GET') return await liveEvents(req, res);
    const user = await currentUser(req);
    if (url.pathname === '/api/me') return json(res, 200, { user });
    if (url.pathname === '/api/game' && req.method === 'GET') return user ? json(res, 200, currentGame()) : json(res, 401, { error: 'Login required' });
    return json(res, 404, { error: 'Not found' });
  } catch (e) { console.error('Request failed:', e?.message); return json(res, 500, { error: 'Request failed' }); }
}).listen(PORT, '127.0.0.1', () => console.log('Sharktower Live listening on http://127.0.0.1:' + PORT));
