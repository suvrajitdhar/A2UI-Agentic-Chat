// A2UI v0.9 agent server (POC) for a Jetpack Compose A2UI client.
//
//   POST /api/agent/chat   {sessionId?, text, mode?, metadata?}     -> text/event-stream
//   POST /api/agent/event  {sessionId, message, mode?, metadata?}   -> text/event-stream
//        message = {"version":"v0.9","action":{...}} | {"version":"v0.9","error":{...}}
//        mode    = "mock" | "hybrid" | "llm" (optional; overrides AGENT_MODE for this session)
//   GET  /health
//
// metadata (sent with every request, per the A2UI spec):
//   a2uiClientCapabilities: {"v0.9": {"supportedCatalogIds": [...], "inlineCatalogs": [...]}}
//   a2uiClientDataModel:    {"version": "v0.9", "surfaces": {...}}  or (androidx.a2ui) {"v0.9": {"surfaces": {...}}}
import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { BASIC_CATALOG_ID, validateClientMessage } from './src/a2ui.js';
import { getSession } from './src/session.js';
import { openSse } from './src/sse.js';
import {
  handleChat, handleAction, handleClientError, resolveMode, AGENT_MODES, DEFAULT_MODE,
} from './src/agent/index.js';
import { llmAvailable, llmInfo } from './src/agent/llm.js';

const app = express();
app.use(cors({ exposedHeaders: ['X-Session-Id'] }));
app.use(express.json({ limit: '256kb' }));
app.use((req, _res, next) => { if (req.path.startsWith('/api')) console.log(req.method, req.path); next(); });

app.get('/health', (_req, res) => res.json({
  ok: true,
  mode: DEFAULT_MODE,
  modes: AGENT_MODES,
  llm: llmAvailable(),
  llmProvider: llmInfo()?.provider ?? null,
  llmModel: llmInfo()?.model ?? null,
  basicCatalogId: BASIC_CATALOG_ID,
}));

/** Opens the SSE stream, runs the turn, and always terminates the stream with done/error. */
async function runTurn(req, res, fn) {
  const session = getSession(req.body.sessionId, req.body.metadata);
  // The mode chosen in the app wins; otherwise keep the session's mode, else the server default.
  session.mode = req.body.mode ? resolveMode(req.body.mode) : (session.mode ?? DEFAULT_MODE);
  syncClientDataModel(session, req.body.metadata);
  const out = openSse(req, res, session);
  try {
    await fn(session, out);
  } catch (err) {
    console.error('[turn failed]', err);
    out.error(err?.message ?? 'Agent failed');
  } finally {
    out.done();
  }
}

// With sendDataModel: true, the client attaches each surface's data model; keep the mirror current.
// Accepts the spec shape {version, surfaces} and the androidx.a2ui shape {"v0.9": {surfaces}}.
function syncClientDataModel(session, metadata) {
  const dm = metadata?.a2uiClientDataModel;
  const surfaces = dm?.surfaces ?? dm?.['v0.9']?.surfaces ?? {};
  for (const [sid, model] of Object.entries(surfaces)) {
    const surface = session.surfaces.get(sid);
    if (surface && model && typeof model === 'object') surface.dataModel = model;
  }
}

app.post('/api/agent/chat', (req, res) => {
  const text = typeof req.body?.text === 'string' ? req.body.text.trim() : '';
  if (!text) return res.status(400).json({ error: '"text" is required' });
  return runTurn(req, res, (session, out) => handleChat(session, text, out));
});

app.post('/api/agent/event', (req, res) => {
  const message = req.body?.message;
  const v = validateClientMessage(message);
  if (!v.ok) return res.status(400).json({ error: 'Invalid A2UI client message', details: v.errors });
  return runTurn(req, res, (session, out) => (message.action
    ? handleAction(session, message.action, out)
    : handleClientError(session, message.error, out)));
});

const PORT = Number(process.env.PORT) || 3000;
const HOST = process.env.HOST || '0.0.0.0';
app.listen(PORT, HOST, () => {
  const llm = llmInfo();
  console.log(`A2UI agent server on http://${HOST}:${PORT} (default mode=${DEFAULT_MODE}, llm=${llm ? `${llm.provider}/${llm.model}` : 'none'})`);
  console.log('Android emulator: http://10.0.2.2:' + PORT);
});
