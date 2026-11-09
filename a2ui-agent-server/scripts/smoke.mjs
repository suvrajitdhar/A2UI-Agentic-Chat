// End-to-end smoke test acting like the Compose client: walks every scripted demo over SSE,
// validates each A2UI message against the official v0.9 schemas and checks each surface's tree.
// Usage: AGENT_MODE=mock npm start  (in another shell)  then  npm run smoke
import { validateServerMessage, messageKind, checkTree, BASIC_CATALOG_ID } from '../src/a2ui.js';

const BASE = process.env.BASE_URL || 'http://localhost:3000';
const metadata = { a2uiClientCapabilities: { 'v0.9': { supportedCatalogIds: [BASIC_CATALOG_ID] } } };
const surfaces = new Map();
let sessionId;
let failures = 0;

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ sessionId, metadata, ...body }),
  });
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${await res.text()}`);
  sessionId = res.headers.get('x-session-id');
  const events = [];
  const t0 = Date.now();
  let buf = '';
  for await (const chunk of res.body) {
    buf += Buffer.from(chunk).toString('utf8');
    let i;
    while ((i = buf.indexOf('\n\n')) >= 0) {
      const raw = buf.slice(0, i); buf = buf.slice(i + 2);
      const event = raw.match(/^event: (.*)$/m)?.[1];
      const data = raw.match(/^data: (.*)$/m)?.[1];
      if (!event) continue;
      const payload = JSON.parse(data);
      events.push({ event, payload, ms: Date.now() - t0 });
      if (event === 'a2ui') track(payload);
    }
  }
  for (const e of events) {
    const what = e.event === 'a2ui' ? `${messageKind(e.payload)} ${JSON.stringify(e.payload[messageKind(e.payload)]).slice(0, 90)}` : JSON.stringify(e.payload);
    console.log(`  +${String(e.ms).padStart(5)}ms ${e.event.padEnd(6)} ${what}`);
  }
  return events;
}

function track(msg) {
  const kind = messageKind(msg);
  const body = msg[kind];
  if (kind === 'createSurface') surfaces.set(body.surfaceId, new Map());
  if (kind === 'deleteSurface') surfaces.delete(body.surfaceId);
  if (kind === 'updateComponents') for (const c of body.components) surfaces.get(body.surfaceId)?.set(c.id, c);
  const v = validateServerMessage(msg);
  if (!v.ok) console.log(`  !! schema rejects ${kind} on "${body.surfaceId}": ${v.errors.join('; ')}`);
}

function expectTree(sid, { broken = false } = {}) {
  const problems = checkTree(surfaces.get(sid) ?? new Map());
  if (problems.length) { failures++; console.log(`  FAIL tree "${sid}": ${problems.join('; ')}`); }
  else console.log(`  ok   tree "${sid}" (${surfaces.get(sid).size} components)${broken ? '' : ''}`);
}

const action = (name, surfaceId, sourceComponentId, context = {}) => ({
  message: { version: 'v0.9', action: { name, surfaceId, sourceComponentId, timestamp: new Date().toISOString(), context } },
});

console.log('chat: "hi"');                 await post('/api/agent/chat', { text: 'hi' }); expectTree('welcome');
console.log('action: start_demo(trip)');    await post('/api/agent/event', action('start_demo', 'welcome', 'w-trip', { demo: 'trip' })); expectTree('trip');
console.log('chat: "plan a trip to Lisbon"'); await post('/api/agent/chat', { text: 'plan a trip to Lisbon' }); expectTree('trip');
console.log('action: search_stays');        await post('/api/agent/event', action('search_stays', 'trip', 'trip-search',
  { destination: 'Lisbon', checkIn: '2026-12-18', checkOut: '2026-12-21', guests: '2', budget: 200, vibes: ['food'] })); expectTree('stays');
{
  // Book buttons must carry literal context (no bindings): androidx.a2ui alpha02 doesn't resolve
  // relative paths in action contexts inside templates.
  const ctx = surfaces.get('stays')?.get('st-book-casa')?.action?.event?.context;
  if (ctx?.name !== 'Casa Azul Beach House' || ctx?.price !== 200) {
    failures++; console.log(`  FAIL st-book-casa context is not literal: ${JSON.stringify(ctx)}`);
  } else console.log('  ok   Book buttons carry literal stay values');
}
console.log('action: book_stay');           await post('/api/agent/event', action('book_stay', 'stays', 'st-book', { stayId: 'casa', name: 'Casa Azul Beach House', price: 200 })); expectTree('booking');
console.log('action: close_booking');       await post('/api/agent/event', action('close_booking', 'booking', 'bk-done'));
if (surfaces.has('booking')) { failures++; console.log('  FAIL booking surface not deleted'); }
console.log('chat: "self-heal demo"');      await post('/api/agent/chat', { text: 'self-heal demo' });
console.log('error: VALIDATION_FAILED');    await post('/api/agent/event', { message: { version: 'v0.9', error: {
  code: 'VALIDATION_FAILED', surfaceId: 'weather', path: '/components/3/text', message: "Missing required property 'text' in component 'Text'." } } });
const temp = surfaces.get('weather')?.get('wx-temp');
if (!temp?.text) { failures++; console.log('  FAIL wx-temp not repaired'); } else console.log('  ok   wx-temp repaired');
console.log('chat: unknown prompt with mode=mock (per-request override)');
const mockEvents = await post('/api/agent/chat', { text: 'build me a calculator', mode: 'mock' });
if (!mockEvents.some((e) => e.event === 'a2ui' && e.payload.createSurface?.surfaceId === 'welcome')) {
  failures++; console.log('  FAIL mode=mock did not fall back to the scripted menu');
} else console.log('  ok   mode=mock answered with the scripted menu');
console.log('bad request: invalid client message');
const bad = await fetch(BASE + '/api/agent/event', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: { action: {} } }) });
console.log(`  ${bad.status === 400 ? 'ok  ' : 'FAIL'} status ${bad.status}`); if (bad.status !== 400) failures++;

console.log(failures ? `\n${failures} failure(s)` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
