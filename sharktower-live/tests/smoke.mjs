import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import net from 'node:net';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';

const reserved = net.createServer();
reserved.listen(0, '127.0.0.1');
await once(reserved, 'listening');
const port = reserved.address().port;
await new Promise(resolve => reserved.close(resolve));

const secret = 'a'.repeat(64);
const child = spawn(process.execPath, ['server.js'], {
  cwd: new URL('..', import.meta.url).pathname,
  env: {
    ...process.env,
    PORT: String(port),
    DISCORD_CLIENT_ID: 'test-id',
    DISCORD_CLIENT_SECRET: 'test-secret',
    DISCORD_GUILD_ID: 'test-guild',
    DISCORD_REDIRECT_URI: 'http://localhost/test',
    SESSION_SECRET: secret,
    BRIDGE_TOKEN: 'b'.repeat(64),
    COOKIE_SECURE: '0'
  },
  stdio: ['ignore', 'pipe', 'pipe']
});

try {
  let ready = false;
  for (let i = 0; i < 50; i++) {
    if (child.exitCode !== null) throw new Error('Website exited unexpectedly');
    try {
      const r = await fetch('http://127.0.0.1:' + port + '/api/me');
      ready = r.ok;
      if (ready) break;
    } catch {}
    await delay(100);
  }
  assert.equal(ready, true, 'website did not start');

  const api = 'http://127.0.0.1:' + port;
  const withoutToken = await fetch(api + '/api/bridge/state', {method: 'POST', body: '{}'});
  assert.equal(withoutToken.status, 403);

  const sample = {
    live: true, phase: 'day', day: 3, gameId: 'test',
    storytellers: [{id:'narrator',name:'The Narrator',secretRole:'DEMON'}],
    players: [{id:'one',name:'Alice',alive:true,chatGroup:'town-square',role:'DEMON',alignment:'EVIL'}],
    conversations: [{id:'town-square',name:'Town Square',playerIds:['one']}],
    grimoire: {secret:'never expose'}
  };
  const accepted = await fetch(api + '/api/bridge/state', {
    method: 'POST',
    headers: {'Authorization': 'Bearer ' + 'b'.repeat(64), 'Content-Type':'application/json'},
    body: JSON.stringify(sample)
  });
  assert.equal(accepted.status, 200);
  const outsiders = await fetch(api + '/api/game');
  assert.equal(outsiders.status, 401, 'game data must be behind Discord login');
  const events = await fetch(api + '/api/events');
  assert.equal(events.status, 401, 'live game events must require Discord login');
  const identity = await fetch(api + '/api/me').then(r => r.json());
  assert.equal(identity.user, null);
  console.log('PASS: server starts, bridge rejects anonymous posts, accepts authenticated posts, and protects game state.');
} finally {
  child.kill();
}
