// LLM provider abstraction. The agent (src/agent/llm.js) only needs a stream of text chunks; everything
// else (prompt, JSONL parsing, validation, repair) is provider-independent.
//
// Select a provider with LLM_PROVIDER=gemini|claude|custom. When unset, the first configured provider
// is used, in the order gemini → claude → custom.
import { createGeminiProvider } from './gemini.js';
import { createClaudeProvider } from './claude.js';
import { createCustomProvider } from './custom.js';

/**
 * One turn of provider-neutral chat history.
 * @typedef {{ role: 'user' | 'assistant', text: string }} ChatTurn
 */

/**
 * The contract every provider implements.
 * @typedef {Object} LlmProvider
 * @property {string} name          Short id shown in logs and /health ("gemini", "claude", "custom").
 * @property {string} model         The model the provider will call.
 * @property {() => boolean} isConfigured  Whether credentials/endpoint are present.
 * @property {(args: { system: string, history: ChatTurn[], signal: AbortSignal }) => AsyncIterable<string>} streamText
 *   Streams the model's reply as raw text chunks (chunk boundaries are arbitrary). `history` ends with
 *   the new user turn. Must stop promptly when `signal` aborts, and throw on provider errors.
 */

/** Provider factories by name. Each takes (env, { explicit }) and returns an {@link LlmProvider}. */
export const PROVIDERS = {
  gemini: createGeminiProvider,
  claude: createClaudeProvider,
  custom: createCustomProvider,
};

/** Order used to auto-detect a provider when LLM_PROVIDER is not set. */
const AUTO_ORDER = ['gemini', 'claude', 'custom'];

/**
 * Picks the provider to use.
 *
 * @param {Record<string, string | undefined>} env usually `process.env`.
 * @returns {LlmProvider | null} the explicitly requested provider (even if it reports itself unconfigured,
 *   so startup can say what's missing), else the first configured one, else `null`.
 * @throws {Error} if LLM_PROVIDER names an unknown provider.
 */
export function selectProvider(env = process.env) {
  const requested = env.LLM_PROVIDER?.trim().toLowerCase();
  if (requested) {
    const factory = PROVIDERS[requested];
    if (!factory) {
      throw new Error(`Unknown LLM_PROVIDER "${env.LLM_PROVIDER}". Use one of: ${Object.keys(PROVIDERS).join(', ')}.`);
    }
    return factory(env, { explicit: true });
  }
  for (const name of AUTO_ORDER) {
    const provider = PROVIDERS[name](env, { explicit: false });
    if (provider.isConfigured()) return provider;
  }
  return null;
}
