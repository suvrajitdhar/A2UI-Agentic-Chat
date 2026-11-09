// LLM-backed agent. The model streams JSON Lines; each complete line is validated and forwarded
// over SSE immediately, so the Compose client renders the UI progressively as it is generated.
// Invalid lines are held back and fed into a repair turn (server-side self-correction).
//
// The model itself is behind the provider interface in ./providers (Gemini, Claude, or any
// OpenAI-compatible endpoint), chosen with LLM_PROVIDER.
import {
  BASIC_CATALOG_ID, checkTree, partitionComponents, surfaceIdOf, updateComponents,
} from '../a2ui.js';
import { buildSystemPrompt } from './prompt.js';
import { selectProvider } from './providers/index.js';

const MAX_REPAIRS = 2;
const MAX_HISTORY = 16; // even, so history always starts with a user turn

// Chosen once at startup; an unknown LLM_PROVIDER fails fast here.
const provider = selectProvider(process.env);

/** Whether a configured LLM provider is available. */
export const llmAvailable = () => Boolean(provider?.isConfigured());

/** The active provider and model, e.g. `{ provider: 'claude', model: 'claude-opus-5-5' }`, or null. */
export const llmInfo = () => (llmAvailable() ? { provider: provider.name, model: provider.model } : null);

/**
 * Runs one agent turn for `userText`, streaming results to `out`.
 * Up to MAX_REPAIRS extra attempts are made: after each attempt, rejected lines and incomplete component trees
 * are described back to the model, which is asked to emit only the corrections.
 * `userText` may be plain chat, or a prefixed system note: "[UI action] …", "[Client error] …".
 */
export async function runLlmTurn(session, userText, out) {
  let input = userText;
  for (let attempt = 0; attempt <= MAX_REPAIRS && !out.closed; attempt++) {
    out.status(attempt === 0 ? 'Thinking…' : 'Fixing a few components…');
    const { rejected, touched, raw } = await streamOnce(session, input, out);
    pushHistory(session, input, raw);

    const problems = [...rejected];
    for (const sid of touched) {
      const surface = session.surfaces.get(sid);
      if (surface) problems.push(...checkTree(surface.components).map((p) => `surface "${sid}": ${p}`));
    }
    if (!problems.length) return;
    console.warn(`[llm] repair ${attempt + 1}:`, problems);
    input = `[Server validation] These lines were rejected or left the UI incomplete:\n- ${problems.join('\n- ')}\n`
      + 'Emit ONLY the corrected A2UI lines (no "say" line). Do not re-create surfaces that already exist.';
  }
}

/**
 * One streaming model call. Text chunks are buffered and split on newlines; each complete line is handled
 * immediately (that's what makes the UI appear progressively on the device).
 * Returns { rejected: string[] errors for the repair prompt, touched: Set of surfaceIds updated, raw: model text }.
 */
async function streamOnce(session, input, out) {
  const chunks = provider.streamText({
    system: buildSystemPrompt(session),
    history: [...session.history, { role: 'user', text: input }],
    signal: out.signal,
  });

  let buf = '';
  let raw = '';
  const rejected = [];
  const touched = new Set();

  const handleLine = (line) => {
    line = line.trim();
    if (!line || line.startsWith('```')) return;
    let obj;
    try { obj = JSON.parse(line); } catch { rejected.push(`not valid JSON: ${line.slice(0, 120)}`); return; }
    if (typeof obj.say === 'string') { out.text(obj.say); return; }
    let res = out.a2ui(obj, { owner: 'llm' });
    // One bad component shouldn't block its valid siblings: forward those, repair the rest later.
    const comps = obj.updateComponents?.components;
    const surface = session.surfaces.get(obj.updateComponents?.surfaceId);
    if (!res.ok && Array.isArray(comps) && surface?.catalogId === BASIC_CATALOG_ID) {
      const { valid, errors } = partitionComponents(comps);
      if (valid.length && errors.length) {
        res = out.a2ui(updateComponents(obj.updateComponents.surfaceId, valid), { owner: 'llm' });
        if (res.ok) res = { ok: false, partial: true, errors };
      }
    }
    if (res.ok || res.partial) touched.add(surfaceIdOf(obj));
    if (!res.ok) rejected.push(res.partial ? res.errors.join('; ') : `${line.slice(0, 200)} -> ${res.errors.join('; ')}`);
  };

  try {
    for await (const text of chunks) {
      raw += text;
      buf += text;
      let nl;
      while ((nl = buf.indexOf('\n')) >= 0) {
        handleLine(buf.slice(0, nl));
        buf = buf.slice(nl + 1);
      }
    }
    handleLine(buf);
  } catch (err) {
    if (!out.closed) throw err; // aborted by client disconnect -> ignore
  }
  return { rejected, touched, raw };
}

/** Appends one exchange in the provider-neutral {role, text} shape (see providers/index.js ChatTurn). */
function pushHistory(session, userText, modelText) {
  session.history.push(
    { role: 'user', text: userText },
    { role: 'assistant', text: modelText || '{}' },
  );
  if (session.history.length > MAX_HISTORY) session.history.splice(0, session.history.length - MAX_HISTORY);
}
