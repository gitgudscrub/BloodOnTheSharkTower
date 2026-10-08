// Used only by the local/CI smoke test process. Never loaded in production.
const originalFetch = globalThis.fetch;
globalThis.fetch = async (input, options) => {
  const url = new URL(String(input));
  if (url.hostname === 'discord.com') {
    if (url.pathname === '/api/v10/oauth2/token') {
      return Response.json({ access_token: 'mock-token', expires_in: 3600 });
    }
    if (url.pathname === '/api/v10/users/@me/guilds/test-guild/member') {
      return Response.json({ user: { id: 'test-user' } });
    }
    if (url.pathname === '/api/v10/users/@me') {
      return Response.json({ id: 'test-user', username: 'TestSpectator' });
    }
    throw new Error('Unexpected Discord API request: ' + url.pathname);
  }
  return originalFetch(input, options);
};
