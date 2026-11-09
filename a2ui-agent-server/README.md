# A2UI Agent Server

A small Node.js server that acts as an **AI agent that answers with native UI**. It streams
[A2UI v0.9](https://a2ui.org/) messages over **Server-Sent Events (SSE)** to an Android app, which renders them
with the [Jetpack Compose A2UI renderer](https://developer.android.com/develop/ui/compose/agentic)
(`androidx.a2ui.compose`, Material 3 basic catalog).

This is a **proof of concept**. It is meant to show what the Compose renderer can do, not to run in production.

```
"plan a trip to Lisbon"  ──▶  server  ──SSE──▶  createSurface, updateComponents ×3, updateDataModel  ──▶  native Material 3 form
         tap "Book"      ──▶  server  ──SSE──▶  live status updates every 1.2 s                     ──▶  only bound views recompose
```

## What it demonstrates

| Feature | What you see on the device |
|---|---|
| Progressive rendering | A form assembles itself in three visible steps |
| Two-way data binding | A slider and its formatted price stay in sync with no server call |
| Client-side checks | The "Find stays" button disables when the date is cleared |
| Actions | Button taps send form values to the agent |
| Progressive lists | A result list shows placeholders, then cards stream in |
| Live server pushes | A booking status, icon and step counter update in place |
| Self-healing UI | A broken component is reported by the app and fixed by the agent |
| Generative UI (optional) | Any prompt is turned into UI by an LLM (Gemini, Claude or a custom model), validated line by line |

## Quick start

Requirements: **Node.js 20+**. An LLM is only needed for free-form prompts: a Gemini or Anthropic API key, or any
OpenAI-compatible endpoint (e.g. a local Ollama). See [LLM providers](#llm-providers).

```bash
npm install
cp .env.example .env          # optional: configure an LLM provider
npm run mock                  # scripted demos, no key needed → http://localhost:3000
```

In a second terminal:

```bash
curl localhost:3000/health                       # {"ok":true,"mode":"mock",...}
npm run smoke                                    # walks through every demo and validates every message
curl -N -X POST localhost:3000/api/agent/chat \
  -H 'Content-Type: application/json' -d '{"text":"plan a trip to Lisbon"}'   # watch the raw stream
```

From the Android emulator, the server is at **`http://10.0.2.2:3000`**.

## Documentation

New to A2UI? Read these in order.

| # | Guide | Read it to… |
|---|---|---|
| 1 | [Concepts](docs/01-concepts.md) | Understand A2UI: surfaces, components, data binding, actions, catalogs (with a glossary) |
| 2 | [Architecture](docs/02-architecture.md) | Learn how this server works: request lifecycle, validation, sessions, the LLM loop |
| 3 | [API reference](docs/03-api.md) | Build the Android client: endpoints, metadata, SSE events, a Ktor example |
| 4 | [Demo guide](docs/04-demo-guide.md) | Present the POC: each step, what it shows, talking points |
| 5 | [Extending and troubleshooting](docs/05-extending.md) | Add demo flows and actions, tune the prompt, fix common problems |

## API at a glance

| Endpoint | Body | Response |
|---|---|---|
| `POST /api/agent/chat` | `{ sessionId?, text, mode?, metadata }` | SSE stream |
| `POST /api/agent/event` | `{ sessionId, message: {version, action} \| {version, error}, mode?, metadata }` | SSE stream |
| `GET /health` | – | JSON status |

SSE events: `a2ui` (one A2UI message), `text` (chat bubble), `status` (progress hint), `error`, and `done`
(carries the `sessionId`). Full details are in the [API reference](docs/03-api.md).

## Configuration

Set these in `.env` (see `.env.example`) or in the environment.

| Variable | Default | Meaning |
|---|---|---|
| `AGENT_MODE` | `hybrid` | Default mode: `mock` = scripted demos only · `hybrid` = demos for known prompts + LLM for the rest · `llm` = LLM only. A client can override it per request with `mode` |
| `PORT` | `3000` | HTTP port |
| `HOST` | `0.0.0.0` | Bind address (`0.0.0.0` lets physical devices on your LAN connect) |
| `LLM_PROVIDER` | auto | `gemini`, `claude` or `custom`. Unset: the first configured provider, in that order |

### LLM providers

The agent talks to the model through one small interface (`src/agent/providers/index.js`): each provider streams
text chunks, and the prompt, parsing, validation and repair loop are shared. `GET /health` reports the active
`llmProvider` and `llmModel`, and the Android home screen shows them.

| Provider | Variables | Notes |
|---|---|---|
| `gemini` | `GEMINI_API_KEY` (required), `GEMINI_MODEL` (default `gemini-2.5-flash`) | Official `@google/genai` SDK; thinking disabled for latency |
| `claude` | `ANTHROPIC_API_KEY`, `CLAUDE_MODEL` (default `claude-opus-5-5`), `CLAUDE_EFFORT` (default `low`) | Official `@anthropic-ai/sdk`. With `LLM_PROVIDER=claude`, the SDK's credential lookup is trusted (`ANTHROPIC_AUTH_TOKEN` or an `ant auth login` profile also work). Server-side refusal fallbacks are on: if Claude's safety classifiers decline, the API retries on a recommended model in the same stream |
| `custom` | `CUSTOM_LLM_BASE_URL` + `CUSTOM_LLM_MODEL` (required), `CUSTOM_LLM_API_KEY`, `CUSTOM_LLM_TEMPERATURE` | Any **OpenAI-compatible** `/chat/completions` streaming endpoint: Ollama (`http://localhost:11434/v1`), LM Studio, vLLM, llama.cpp server, OpenRouter, OpenAI, Azure OpenAI |

The prompt asks for strict JSON Lines, so output quality varies by model. Small local models need more repair
rounds (`[llm] repair` in the logs) than hosted frontier models.

## Scripts

| Command | What it does |
|---|---|
| `npm start` | Run the server |
| `npm run dev` | Run with auto-restart on file changes |
| `npm run mock` | `dev` with `AGENT_MODE=mock` |
| `npm run smoke` | End-to-end test against a running server (`BASE_URL` to override) |
| `npm run test:providers` | Offline tests of the LLM providers against local fake servers (no keys needed) |

## Project layout

```
server.js                 HTTP routes and the request ("turn") lifecycle
src/
  a2ui.js                 A2UI protocol: message builders, schema validation, tree checks
  catalog.js              Catalog negotiation; turns catalog schemas into prompt text
  session.js              In-memory sessions and a mirror of what each surface shows
  sse.js                  SSE stream wrapper; every A2UI message passes its validation gate
  agent/
    index.js              Routes each turn to the scripted demos or the LLM
    scripted.js           Hand-written demo flows (welcome, trip → stays → booking, self-heal)
    llm.js                LLM streaming → validated A2UI, with an automatic repair loop
    providers/            LLM provider interface + gemini.js, claude.js, custom.js (OpenAI-compatible)
    prompt.js             Builds the LLM system prompt
schemas/v0_9/             Official A2UI v0.9 JSON Schemas (from a2ui.org)
scripts/smoke.mjs         End-to-end test that behaves like the Android client
docs/                     The guides listed above
```

## Limitations

In-memory sessions, no authentication, a single process, and no SSE resume. See
[Architecture → Known limitations](docs/02-architecture.md#known-limitations).

## References

- A2UI specification v0.9: https://a2ui.org/specification/v0.9-a2ui/
- A2UI catalogs and negotiation: https://a2ui.org/concepts/catalogs/
- Compose A2UI renderer guide: https://developer.android.com/develop/ui/compose/agentic
- Release notes: [a2ui-compose](https://developer.android.com/jetpack/androidx/releases/a2ui-compose) · [a2ui](https://developer.android.com/jetpack/androidx/releases/a2ui)
