// Offline tests for the LLM providers: real SDK/fetch code paths against local fake servers.
// Run: npm run test:providers   (no API keys or network needed)
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { selectProvider } from '../src/agent/providers/index.js';
import { createClaudeProvider } from '../src/agent/providers/claude.js';
import { createCustomProvider } from '../src/agent/providers/custom.js';

const servers = [];
// closeAllConnections: keep-alive sockets would otherwise keep the test process running.
after(() => servers.forEach((s) => { s.closeAllConnections(); s.close(); }));

/** Starts a local HTTP server; `handler(req, body, res)` gets the parsed JSON body. */
async function fakeServer(handler) {
  const server = http.createServer((req, res) => {
    let raw = '';
    req.on('data', (c) => { raw += c; });
    req.on('end', () => handler(req, raw ? JSON.parse(raw) : null, res));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  servers.push(server);
  return `http://127.0.0.1:${server.address().port}`;
}

const collect = async (iterable) => { let out = ''; for await (const t of iterable) out += t; return out; };
const HISTORY = [
  { role: 'user', text: 'hi' },
  { role: 'assistant', text: '{"say":"hello"}' },
  { role: 'user', text: 'make a card' },
];

// ---------------------------------------------------------------- selection
test('selectProvider: auto-detects in order gemini → claude → custom', () => {
  assert.equal(selectProvider({}), null);
  assert.equal(selectProvider({ GEMINI_API_KEY: 'g', ANTHROPIC_API_KEY: 'a' }).name, 'gemini');
  assert.equal(selectProvider({ ANTHROPIC_API_KEY: 'a' }).name, 'claude');
  assert.equal(selectProvider({ CUSTOM_LLM_BASE_URL: 'http://x/v1', CUSTOM_LLM_MODEL: 'm' }).name, 'custom');
});

test('selectProvider: LLM_PROVIDER wins and unknown names fail fast', () => {
  const p = selectProvider({ LLM_PROVIDER: 'Claude', GEMINI_API_KEY: 'g' });
  assert.equal(p.name, 'claude');
  assert.equal(p.isConfigured(), true, 'explicit claude trusts the SDK credential lookup');
  assert.equal(p.model, 'claude-opus-5-5');
  assert.throws(() => selectProvider({ LLM_PROVIDER: 'bogus' }), /Unknown LLM_PROVIDER/);
  assert.equal(selectProvider({ LLM_PROVIDER: 'custom' }).isConfigured(), false);
});

// ---------------------------------------------------------------- claude
function anthropicStream(res, texts, stopReason = 'end_turn') {
  res.writeHead(200, { 'Content-Type': 'text/event-stream' });
  const send = (type, data) => res.write(`event: ${type}\ndata: ${JSON.stringify({ type, ...data })}\n\n`);
  send('message_start', { message: {
    id: 'msg_test', type: 'message', role: 'assistant', model: 'claude-opus-5-5', content: [],
    stop_reason: null, stop_sequence: null, usage: { input_tokens: 10, output_tokens: 0 },
  } });
  send('content_block_start', { index: 0, content_block: { type: 'text', text: '' } });
  for (const text of texts) send('content_block_delta', { index: 0, delta: { type: 'text_delta', text } });
  send('content_block_stop', { index: 0 });
  send('message_delta', { delta: { stop_reason: stopReason, stop_sequence: null }, usage: { output_tokens: 5 } });
  send('message_stop', {});
  res.end();
}

test('claude: streams text deltas and sends the expected request', async () => {
  let seen;
  const base = await fakeServer((req, body, res) => {
    seen = { url: req.url, beta: req.headers['anthropic-beta'], body };
    anthropicStream(res, ['{"say":', '"hi"}\n', '{"version":"v0.9"}']);
  });
  process.env.ANTHROPIC_BASE_URL = base;
  process.env.ANTHROPIC_API_KEY = 'test-key';

  const provider = createClaudeProvider({ ANTHROPIC_API_KEY: 'test-key', CLAUDE_EFFORT: 'medium' }, { explicit: false });
  const text = await collect(provider.streamText({ system: 'SYS', history: HISTORY, signal: new AbortController().signal }));

  assert.equal(text, '{"say":"hi"}\n{"version":"v0.9"}');
  assert.match(seen.url, /^\/v1\/messages/);
  assert.match(seen.beta, /server-side-fallback-2026-07-01/);
  assert.equal(seen.body.model, 'claude-opus-5-5');
  assert.equal(seen.body.system, 'SYS');
  assert.equal(seen.body.fallbacks, 'default');
  assert.deepEqual(seen.body.output_config, { effort: 'medium' });
  assert.deepEqual(seen.body.messages.map((m) => m.role), ['user', 'assistant', 'user']);
  assert.equal(seen.body.stream, true);
});

test('claude: a final refusal becomes an error', async () => {
  const base = await fakeServer((_req, _body, res) => anthropicStream(res, ['partial'], 'refusal'));
  process.env.ANTHROPIC_BASE_URL = base;
  const provider = createClaudeProvider({ ANTHROPIC_API_KEY: 'k' }, { explicit: false });
  await assert.rejects(
    collect(provider.streamText({ system: 's', history: HISTORY, signal: new AbortController().signal })),
    /declined/,
  );
});

// ---------------------------------------------------------------- custom (OpenAI-compatible)
test('custom: streams chat-completion deltas with auth, model and system message', async () => {
  let seen;
  const base = await fakeServer((req, body, res) => {
    seen = { url: req.url, auth: req.headers.authorization, body };
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    for (const content of ['{"say":"hel', 'lo"}\n', '{"x":1}']) {
      res.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta: { content } }] })}\n\n`);
    }
    res.write('data: [DONE]\n\n');
    res.end();
  });
  const provider = createCustomProvider({
    CUSTOM_LLM_BASE_URL: `${base}/v1/`, CUSTOM_LLM_MODEL: 'local-model', CUSTOM_LLM_API_KEY: 'sk-test', CUSTOM_LLM_TEMPERATURE: '0.3',
  });
  const text = await collect(provider.streamText({ system: 'SYS', history: HISTORY, signal: new AbortController().signal }));

  assert.equal(text, '{"say":"hello"}\n{"x":1}');
  assert.equal(seen.url, '/v1/chat/completions');
  assert.equal(seen.auth, 'Bearer sk-test');
  assert.equal(seen.body.model, 'local-model');
  assert.equal(seen.body.stream, true);
  assert.equal(seen.body.temperature, 0.3);
  assert.deepEqual(seen.body.messages.map((m) => m.role), ['system', 'user', 'assistant', 'user']);
  assert.equal(seen.body.messages[0].content, 'SYS');
});

test('custom: HTTP errors are reported with status and body', async () => {
  const base = await fakeServer((_req, _body, res) => { res.writeHead(401); res.end('{"error":"bad key"}'); });
  const provider = createCustomProvider({ CUSTOM_LLM_BASE_URL: base, CUSTOM_LLM_MODEL: 'm' });
  await assert.rejects(
    collect(provider.streamText({ system: 's', history: HISTORY, signal: new AbortController().signal })),
    /HTTP 401.*bad key/,
  );
});

// ---------------------------------------------------------------- end to end through the agent
test('agent: an LLM turn via the custom provider builds a validated surface', async () => {
  const lines = [
    { say: 'Here is your list.' },
    { version: 'v0.9', createSurface: { surfaceId: 'todo', catalogId: 'https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json', sendDataModel: true } },
    { version: 'v0.9', updateComponents: { surfaceId: 'todo', components: [
      { id: 'root', component: 'Column', children: ['title'] },
      { id: 'title', component: 'Text', text: { path: '/title' }, variant: 'h2' },
    ] } },
    { version: 'v0.9', updateDataModel: { surfaceId: 'todo', path: '/', value: { title: 'Todos' } } },
  ].map((l) => JSON.stringify(l)).join('\n');
  const base = await fakeServer((_req, _body, res) => {
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    // Split mid-line on purpose: the agent must reassemble lines across chunks.
    for (const content of [lines.slice(0, 50), lines.slice(50, 200), lines.slice(200)]) {
      res.write(`data: ${JSON.stringify({ choices: [{ delta: { content } }] })}\n\n`);
    }
    res.end('data: [DONE]\n\n');
  });

  // The agent selects its provider at import time, so configure the env first.
  Object.assign(process.env, { LLM_PROVIDER: 'custom', CUSTOM_LLM_BASE_URL: base, CUSTOM_LLM_MODEL: 'm' });
  const { runLlmTurn, llmInfo } = await import('../src/agent/llm.js');
  const { getSession } = await import('../src/session.js');
  const { openSse } = await import('../src/sse.js');

  const events = [];
  const fakeRes = {
    writeHead() {}, on() {}, end() {}, writableEnded: false,
    write(chunk) { const m = chunk.match(/^event: (\w+)\ndata: (.*)$/m); if (m) events.push([m[1], JSON.parse(m[2])]); },
  };
  const session = getSession(undefined, undefined);
  const out = openSse({}, fakeRes, session);
  await runLlmTurn(session, 'make a todo list', out);
  out.done(); // stops the SSE heartbeat timer

  assert.deepEqual(llmInfo(), { provider: 'custom', model: 'm' });
  assert.deepEqual(events.filter(([e]) => e === 'text').map(([, d]) => d.text), ['Here is your list.']);
  assert.equal(events.filter(([e]) => e === 'a2ui').length, 3);
  assert.equal(session.surfaces.get('todo').dataModel.title, 'Todos');
  assert.deepEqual(session.history.map((t) => t.role), ['user', 'assistant']);
});
