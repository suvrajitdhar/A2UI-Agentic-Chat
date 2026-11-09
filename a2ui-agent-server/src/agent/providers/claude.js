// Anthropic Claude via the official @anthropic-ai/sdk.
//   ANTHROPIC_API_KEY  credentials (the SDK also accepts ANTHROPIC_AUTH_TOKEN or an `ant auth login` profile)
//   CLAUDE_MODEL       default claude-opus-5-5
//   CLAUDE_EFFORT      low | medium | high | xhigh | max (default low: fastest first component for live UI)
import Anthropic from '@anthropic-ai/sdk';

/**
 * @param {Record<string, string | undefined>} env
 * @param {{ explicit: boolean }} options `explicit` is true when LLM_PROVIDER=claude was set.
 * @returns {import('./index.js').LlmProvider}
 */
export function createClaudeProvider(env, { explicit }) {
  const model = env.CLAUDE_MODEL || 'claude-opus-5-5';
  const effort = env.CLAUDE_EFFORT || 'low';
  let client;

  return {
    name: 'claude',
    model,
    // When chosen explicitly, trust the SDK's own credential lookup (env vars or an `ant auth login`
    // profile). Auto-detection only picks Claude when a credential env var is present.
    isConfigured: () => explicit || Boolean(env.ANTHROPIC_API_KEY || env.ANTHROPIC_AUTH_TOKEN),

    async *streamText({ system, history, signal }) {
      client ??= new Anthropic();
      const stream = client.beta.messages.stream(
        {
          model,
          max_tokens: 64000,
          system,
          messages: history.map((turn) => ({ role: turn.role, content: turn.text })),
          // Thinking is always on for Claude Opus 5.5; effort controls depth and latency.
          output_config: { effort },
          // If Claude's safety classifiers decline the request, the API re-runs it on a recommended
          // fallback model within the same stream instead of returning a refusal.
          betas: ['server-side-fallback-2026-07-01'],
          fallbacks: 'default',
        },
        { signal },
      );

      for await (const event of stream) {
        if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
          yield event.delta.text;
        }
      }

      const message = await stream.finalMessage();
      if (message.stop_reason === 'refusal') {
        const category = message.stop_details?.category;
        throw new Error(`Claude declined this request${category ? ` (${category})` : ''}.`);
      }
      if (message.stop_reason === 'max_tokens') {
        console.warn('[claude] reply hit max_tokens; the last UI line may be incomplete');
      }
    },
  };
}
