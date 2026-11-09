// Google Gemini via the official @google/genai SDK.
//   GEMINI_API_KEY  required
//   GEMINI_MODEL    default gemini-2.5-flash
import { GoogleGenAI } from '@google/genai';

/**
 * @param {Record<string, string | undefined>} env
 * @returns {import('./index.js').LlmProvider}
 */
export function createGeminiProvider(env) {
  const model = env.GEMINI_MODEL || 'gemini-2.5-flash';
  let client;

  return {
    name: 'gemini',
    model,
    isConfigured: () => Boolean(env.GEMINI_API_KEY),

    async *streamText({ system, history, signal }) {
      client ??= new GoogleGenAI({ apiKey: env.GEMINI_API_KEY });
      const stream = await client.models.generateContentStream({
        model,
        // Gemini calls the assistant role "model".
        contents: history.map((turn) => ({
          role: turn.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: turn.text }],
        })),
        config: {
          systemInstruction: system,
          temperature: 0.4,
          thinkingConfig: { thinkingBudget: 0 }, // lowest latency to the first component
          abortSignal: signal,
        },
      });
      for await (const chunk of stream) {
        if (chunk.text) yield chunk.text;
      }
    },
  };
}
