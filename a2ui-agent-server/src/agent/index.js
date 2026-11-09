// Routes a chat turn or a client event (action / error) to the scripted demos or the LLM agent.
import { llmAvailable, runLlmTurn } from './llm.js';
import { matchScripted, runScripted, handleScriptedAction, handleScriptedError } from './scripted.js';

// Agent modes:
//   hybrid – scripted demos for known prompts, LLM for everything else
//   mock   – scripted only (no API key needed, deterministic for live demos)
//   llm    – LLM only
// The server default comes from AGENT_MODE; a client may override it per request (`mode` in the body).
export const AGENT_MODES = ['mock', 'hybrid', 'llm'];
export const DEFAULT_MODE = AGENT_MODES.includes(process.env.AGENT_MODE?.toLowerCase())
  ? process.env.AGENT_MODE.toLowerCase()
  : 'hybrid';
const MAX_CLIENT_REPAIRS_PER_SURFACE = 2;

/** Returns `requested` if it is a known mode, otherwise the server default. */
export const resolveMode = (requested) => (AGENT_MODES.includes(requested) ? requested : DEFAULT_MODE);

export async function handleChat(session, text, out) {
  const mode = session.mode ?? DEFAULT_MODE;
  const scripted = mode !== 'llm' ? matchScripted(text) : null;
  if (scripted) return runScripted(scripted, session, out, text);
  if (mode !== 'mock' && llmAvailable()) return runLlmTurn(session, text, out);
  if (mode !== 'mock') out.text('No LLM provider is configured on the server, so only the scripted demos are available.');
  return runScripted('menu', session, out);
}

export async function handleAction(session, action, out) {
  const surface = session.surfaces.get(action.surfaceId);
  if (surface?.owner === 'scripted') return handleScriptedAction(action, session, out);
  if (!llmAvailable()) return out.text(`Received "${action.name}", but the LLM agent is not configured.`);
  return runLlmTurn(session, `[UI action] ${JSON.stringify(action)}\n`
    + `Current data model of "${action.surfaceId}": ${JSON.stringify(surface?.dataModel ?? {})}`, out);
}

export async function handleClientError(session, error, out) {
  console.warn('[client error]', error);
  const surface = session.surfaces.get(error.surfaceId);
  if (!surface) return out.text(`The app reported an error on an unknown surface: ${error.message}`);
  surface.repairs = (surface.repairs ?? 0) + 1;
  if (surface.repairs > MAX_CLIENT_REPAIRS_PER_SURFACE) {
    return out.text(`Giving up on repairing "${error.surfaceId}" after ${MAX_CLIENT_REPAIRS_PER_SURFACE} attempts.`);
  }
  if (surface.owner === 'scripted') return handleScriptedError(error, session, out);
  if (!llmAvailable()) return out.text(`Client error: ${error.message}`);
  return runLlmTurn(session, `[Client error] ${JSON.stringify(error)}\n`
    + `Offending surface components: ${JSON.stringify([...surface.components.values()])}`, out);
}
