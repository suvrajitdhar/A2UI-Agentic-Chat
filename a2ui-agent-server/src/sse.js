// Server-Sent Events channel. Each A2UI message is one `event: a2ui` with a single-line JSON `data:`,
// which the Compose client can hand straight to A2uiJsonMessageParser.
//
// Events:  a2ui   – one A2UI v0.9 server->client message
//          text   – {"text": "..."} assistant chat bubble
//          status – {"text": "..."} transient progress hint ("Thinking…")
//          error  – {"message": "..."} turn failed
//          done   – {"sessionId": "..."} turn complete; the server closes the stream
import { applyToSession } from './session.js';
import { validateServerMessage, messageKind, surfaceIdOf, BASIC_CATALOG_ID } from './a2ui.js';

const HEARTBEAT_MS = 15_000;

export function openSse(req, res, session) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
    'X-Session-Id': session.id,
  });
  res.write(': connected\n\n');

  const abort = new AbortController();
  const heartbeat = setInterval(() => res.write(': ping\n\n'), HEARTBEAT_MS);
  res.on('close', () => { clearInterval(heartbeat); abort.abort(); });

  let seq = 0;
  const write = (event, data) => {
    if (res.writableEnded || abort.signal.aborted) return false;
    res.write(`id: ${++seq}\nevent: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    return true;
  };

  return {
    signal: abort.signal,
    get closed() { return abort.signal.aborted || res.writableEnded; },
    text: (text) => write('text', { text }),
    status: (text) => write('status', { text }),

    /**
     * Validates, sends and mirrors an A2UI message. Returns {ok, errors}.
     * `skipValidation` exists only so the self-healing demo can ship a deliberately broken component.
     */
    a2ui(msg, { owner = 'llm', skipValidation = false } = {}) {
      const kind = messageKind(msg);
      const sid = surfaceIdOf(msg);
      const surface = session.surfaces.get(sid);
      if (kind && kind !== 'createSurface' && !surface) {
        return { ok: false, errors: [`surface "${sid}" does not exist; send createSurface first`] };
      }
      if (kind === 'createSurface' && surface) {
        return { ok: false, errors: [`surface "${sid}" already exists; use updateComponents or deleteSurface first`] };
      }
      const catalogId = kind === 'createSurface' ? msg.createSurface.catalogId : surface?.catalogId;
      if (!skipValidation) {
        const v = validateServerMessage(msg, { catalogId: catalogId ?? BASIC_CATALOG_ID });
        if (!v.ok) return v;
      }
      if (!write('a2ui', msg)) return { ok: false, errors: ['client disconnected'] };
      applyToSession(session, msg, owner);
      return { ok: true, errors: [] };
    },

    error: (message) => write('error', { message }),
    done() {
      write('done', { sessionId: session.id });
      clearInterval(heartbeat);
      if (!res.writableEnded) res.end();
    },
  };
}

export const sleep = (ms, signal) => new Promise((resolve) => {
  const t = setTimeout(resolve, ms);
  signal?.addEventListener('abort', () => { clearTimeout(t); resolve(); }, { once: true });
});
