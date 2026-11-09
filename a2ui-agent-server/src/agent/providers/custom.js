// Any OpenAI-compatible Chat Completions endpoint (Ollama, LM Studio, vLLM, llama.cpp server,
// OpenRouter, OpenAI, Azure OpenAI, most self-hosted gateways).
//   CUSTOM_LLM_BASE_URL     required, e.g. http://localhost:11434/v1 (Ollama), http://localhost:1234/v1 (LM Studio)
//   CUSTOM_LLM_MODEL        required, e.g. qwen2.5:14b
//   CUSTOM_LLM_API_KEY      optional; sent as "Authorization: Bearer <key>"
//   CUSTOM_LLM_TEMPERATURE  optional, e.g. 0.4
//
// Uses plain fetch + Server-Sent Events: POST {base}/chat/completions with stream: true, then reads
// `data: {"choices":[{"delta":{"content":"..."}}]}` lines until `data: [DONE]`.

/**
 * @param {Record<string, string | undefined>} env
 * @returns {import('./index.js').LlmProvider}
 */
export function createCustomProvider(env) {
  const baseUrl = (env.CUSTOM_LLM_BASE_URL || '').replace(/\/+$/, '');
  const model = env.CUSTOM_LLM_MODEL || '';
  const apiKey = env.CUSTOM_LLM_API_KEY;
  const temperature = env.CUSTOM_LLM_TEMPERATURE ? Number(env.CUSTOM_LLM_TEMPERATURE) : undefined;

  return {
    name: 'custom',
    model,
    isConfigured: () => Boolean(baseUrl && model),

    async *streamText({ system, history, signal }) {
      if (!baseUrl || !model) throw new Error('Set CUSTOM_LLM_BASE_URL and CUSTOM_LLM_MODEL to use the custom provider.');

      const response = await fetch(`${baseUrl}/chat/completions`, {
        method: 'POST',
        signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(apiKey && { Authorization: `Bearer ${apiKey}` }),
        },
        body: JSON.stringify({
          model,
          stream: true,
          ...(temperature !== undefined && { temperature }),
          messages: [
            { role: 'system', content: system },
            ...history.map((turn) => ({ role: turn.role, content: turn.text })),
          ],
        }),
      });
      if (!response.ok) {
        throw new Error(`Custom LLM returned HTTP ${response.status}: ${(await response.text()).slice(0, 300)}`);
      }

      yield* readChatCompletionChunks(response.body);
    },
  };
}

/**
 * Parses an OpenAI-style streaming body and yields each `choices[0].delta.content` string.
 * Exported for tests.
 *
 * @param {AsyncIterable<Uint8Array>} body the fetch response body.
 */
export async function* readChatCompletionChunks(body) {
  const decoder = new TextDecoder();
  let buffer = '';
  for await (const bytes of body) {
    buffer += decoder.decode(bytes, { stream: true });
    let newline;
    while ((newline = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, newline).trim();
      buffer = buffer.slice(newline + 1);
      if (!line.startsWith('data:')) continue; // blank lines, comments, `event:` lines
      const data = line.slice(5).trim();
      if (data === '[DONE]') return;
      let chunk;
      try { chunk = JSON.parse(data); } catch { continue; }
      if (chunk.error) throw new Error(`Custom LLM error: ${chunk.error.message ?? JSON.stringify(chunk.error)}`);
      const text = chunk.choices?.[0]?.delta?.content;
      if (text) yield text;
    }
  }
}
