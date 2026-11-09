// In-memory sessions (POC only). Each session mirrors the surfaces it has sent to the client so that
// the agent can edit them on later turns and repair them after client VALIDATION_FAILED errors.
import { randomUUID } from 'node:crypto';
import { applyDataModelUpdate, messageKind } from './a2ui.js';
import { negotiateCatalogs } from './catalog.js';

const TTL_MS = 30 * 60 * 1000;
const sessions = new Map();

export function getSession(sessionId, metadata) {
  const now = Date.now();
  for (const [id, s] of sessions) if (now - s.touchedAt > TTL_MS) sessions.delete(id);

  const id = sessionId || randomUUID();
  let s = sessions.get(id);
  if (!s) {
    // history: provider-neutral chat turns [{ role: 'user' | 'assistant', text }] for the LLM agent.
    s = { id, history: [], surfaces: new Map(), catalogs: negotiateCatalogs(undefined) };
    sessions.set(id, s);
  }
  // Capabilities travel in the metadata of every client message; refresh them each time.
  if (metadata?.a2uiClientCapabilities) s.catalogs = negotiateCatalogs(metadata.a2uiClientCapabilities);
  s.touchedAt = now;
  return s;
}

/** Updates the server-side mirror after a message has been sent to the client. */
export function applyToSession(session, msg, owner) {
  const kind = messageKind(msg);
  const body = msg[kind];
  if (kind === 'createSurface') {
    session.surfaces.set(body.surfaceId, {
      catalogId: body.catalogId, owner, components: new Map(), dataModel: {},
    });
    return;
  }
  const surface = session.surfaces.get(body.surfaceId);
  if (!surface) return;
  if (kind === 'updateComponents') for (const c of body.components) surface.components.set(c.id, c);
  if (kind === 'updateDataModel') {
    surface.dataModel = applyDataModelUpdate(surface.dataModel, body.path, body.value, !('value' in body));
  }
  if (kind === 'deleteSurface') session.surfaces.delete(body.surfaceId);
}

/** Compact snapshot of live surfaces, used as LLM context for follow-up edits. */
export function describeSurfaces(session) {
  if (!session.surfaces.size) return 'No surfaces are currently displayed.';
  return [...session.surfaces].map(([id, s]) => JSON.stringify({
    surfaceId: id, catalogId: s.catalogId, components: [...s.components.values()], dataModel: s.dataModel,
  })).join('\n');
}
