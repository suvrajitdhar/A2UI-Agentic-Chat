# 5. Extending and troubleshooting

Recipes for common changes, followed by fixes for common problems.

- [Recipe: add a scripted demo flow](#recipe-add-a-scripted-demo-flow)
- [Recipe: handle a new button action](#recipe-handle-a-new-button-action)
- [Recipe: let the agent use custom Compose components](#recipe-let-the-agent-use-custom-compose-components)
- [Recipe: tune the LLM prompt](#recipe-tune-the-llm-prompt)
- [Recipe: switch the LLM model or provider](#recipe-switch-the-llm-model-or-provider)
- [Recipe: update to a new A2UI spec version](#recipe-update-to-a-new-a2ui-spec-version)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Coding conventions](#coding-conventions)

---

## Recipe: add a scripted demo flow

Example: a coffee-ordering card, triggered by typing "coffee". All code goes in `src/agent/scripted.js`.

**1. Add a trigger** in `matchScripted()` (order matters; the first match wins):

```js
if (/\bcoffee\b/.test(t)) return 'coffee';
```

**2. Add the flow** to `FLOWS`:

```js
async coffee(session, out) {
  out.text('What can I get you?');                       // chat bubble
  freshSurface(out, session, 'coffee');                  // deletes an old 'coffee' surface, then createSurface
  send(out, updateComponents('coffee', [
    { id: 'root', component: 'Card', child: 'cf-body' },
    { id: 'cf-body', component: 'Column', children: ['cf-title', 'cf-size', 'cf-qty', 'cf-order'] },
    { id: 'cf-title', component: 'Text', text: 'Order a coffee', variant: 'h3' },
    { id: 'cf-size', component: 'ChoicePicker', label: 'Size', variant: 'mutuallyExclusive', value: { path: '/size' },
      options: [{ label: 'Small', value: 'S' }, { label: 'Medium', value: 'M' }, { label: 'Large', value: 'L' }] },
    { id: 'cf-qty', component: 'Slider', label: 'Cups', min: 1, max: 5, value: { path: '/cups' } },
    ...button('cf-order', 'Order', {
      name: 'order_coffee',
      context: { size: { path: '/size' }, cups: { path: '/cups' } },
    }, { variant: 'primary' }),
  ]));
  send(out, updateDataModel('coffee', '/', { size: ['M'], cups: 1 }));   // initial values
},
```

**3. Restart and try it:**
`curl -N -X POST localhost:3000/api/agent/chat -H 'Content-Type: application/json' -d '{"text":"coffee"}'`

If you made a mistake (a typo in a component name, a missing required prop), `send()` throws and you'll see
an `event: error` such as `scripted message rejected: component[3] "cf-size" (ChoicePicker): must have required property 'options'`.

Tips:

- Component ids must be unique **within a surface**. Prefix them per flow (`cf-…`) to keep them readable.
- `ChoicePicker.value` is always a **list** of strings, even with `mutuallyExclusive`.
- Use `await sleep(ms, out.signal)` between messages to make progressive rendering visible.
- Unsure about a component's properties? See the table in [Concepts](01-concepts.md#component-reference-basic-catalog)
  or search `schemas/v0_9/basic_catalog.json`.

---

## Recipe: handle a new button action

The button above sends `order_coffee`. Add a handler to `ACTIONS` in `src/agent/scripted.js`. The key must equal
the action `name`:

```js
async order_coffee(action, session, out) {
  const { size = ['M'], cups = 1 } = action.context ?? {};   // bindings were resolved by the app
  out.status('Brewing…');
  await sleep(800, out.signal);
  send(out, updateComponents('coffee', [                      // same id = replace that component
    { id: 'cf-title', component: 'Text', text: `${cups} × ${size[0]} coffee ordered ☕`, variant: 'h3' },
  ]));
  out.text('On its way!');
},
```

Action routing is automatic: `handleAction` in `src/agent/index.js` sends actions from surfaces created by
scripted code to `handleScriptedAction`, which looks up `ACTIONS[action.name]`.

---

## Recipe: let the agent use custom Compose components

This is mostly **client** work; the server already supports it.

1. On Android, build a custom or hybrid catalog with your own `A2uiComponent`s (see the
   [Compose docs: manage catalogs](https://developer.android.com/develop/ui/compose/agentic/manage-catalogs)).
2. Advertise it in every request:
   - add its `catalogId` to `a2uiClientCapabilities["v0.9"].supportedCatalogIds`
   - include its schema in `inlineCatalogs` (`{catalogId, components: {Name: JSONSchema}, functions: [...]}`)
3. That's it. `negotiateCatalogs()` (`src/catalog.js`) registers it, and `describeCatalog()` adds its components to
   the LLM prompt. The model can now create surfaces with that `catalogId`.

Caveats:

- The server checks custom-catalog components only **structurally** (`id` and `component` strings). The app's
  engine does the full validation, and its `VALIDATION_FAILED` reports drive repairs.
- The scripted demos always use the basic catalog.
- To have the server fully validate a custom catalog, compile its schema with Ajv in `src/a2ui.js`, the same way
  `BASIC_CATALOG` is registered.

---

## Recipe: tune the LLM prompt

Edit `src/agent/prompt.js`. To see the exact prompt the model receives:

```bash
node -e 'import("./src/session.js").then(async ({getSession}) => {
  const {buildSystemPrompt} = await import("./src/agent/prompt.js");
  console.log(buildSystemPrompt(getSession()));
})'
```

Guidelines:

- Keep rules short and imperative. The model follows the **example** more closely than the rules, so update
  `EXAMPLE` when you change conventions.
- When you see a recurring mistake in the server logs (`[llm] repair …`), add a rule for it.
- The catalog section is generated from the schema. Don't hand-write component lists.

---

## Recipe: switch the LLM model or provider

- **Switch provider:** set `LLM_PROVIDER=gemini|claude|custom` and that provider's variables (see the README's
  *LLM providers* table and `.env.example`).
- **Different model:** `GEMINI_MODEL`, `CLAUDE_MODEL` or `CUSTOM_LLM_MODEL`.
- **Local model:** run Ollama (or LM Studio) and set `LLM_PROVIDER=custom`,
  `CUSTOM_LLM_BASE_URL=http://localhost:11434/v1`, `CUSTOM_LLM_MODEL=<model>`.
- **Add a provider:** create `src/agent/providers/<name>.js` exporting a factory that returns
  `{ name, model, isConfigured(), async *streamText({ system, history, signal }) }` (yield text chunks, stop on
  `signal`, throw on errors), and register it in `PROVIDERS` in `providers/index.js`. History arrives as
  `[{role: 'user' | 'assistant', text}]`, ending with the new user turn. Add a case to
  `scripts/providers.test.mjs` with a local fake server.

---

## Recipe: update to a new A2UI spec version

1. Download the new schemas (`server_to_client.json`, `client_to_server.json`, `common_types.json`,
   `basic_catalog.json` (from `catalogs/basic/catalog.json`), `client_capabilities.json`, `client_data_model.json`)
   into a new folder such as `schemas/v0_10/`.
2. In `src/a2ui.js`, update `SCHEMA_DIR`, `A2UI_VERSION`, and the alias `$id` that the basic catalog is registered
   under (`server_to_client.json` refers to it as `catalog.json`).
3. Update the `'v0.9'` key in `negotiateCatalogs()` (`src/catalog.js`) and the hard-coded versions in
   `src/agent/scripted.js` and `scripts/smoke.mjs`.
4. Run `npm run smoke`. Schema changes to components will surface as rejected scripted messages.

Make sure the Compose library version supports the new spec version first.

---

## Testing

| What | How |
|---|---|
| Full scripted flow, schema-validated | `npm run mock` in one terminal, `npm run smoke` in another |
| Against another host or port | `BASE_URL=http://localhost:3123 npm run smoke` |
| Watch raw events | `curl -N -X POST …/api/agent/chat …` (see [API](03-api.md#trying-it-with-curl)) |
| Validate a message by hand | `node -e 'import("./src/a2ui.js").then(m => console.log(m.validateServerMessage({...})))'` |
| LLM providers without keys | `npm run test:providers`: Claude and custom providers against local fake servers, plus an end-to-end agent turn |

`scripts/smoke.mjs` acts as the Android client. It walks through every demo step, validates every received
message against the official schema, checks each surface's component tree, and exits non-zero on failure.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| App can't connect | Wrong host for the emulator, or cleartext HTTP blocked | Use `http://10.0.2.2:3000`; add a debug network-security config; check `/health` in the emulator browser |
| Physical device can't connect | Firewall, or the device isn't on the same network | Use `adb reverse tcp:3000 tcp:3000` and `http://localhost:3000` |
| UI appears all at once at the end | The client (or a proxy) buffers the response | Read the stream incrementally (Ktor `sse {}`); don't call `bodyAsText()`; disable logging interceptors that read the body |
| Stream stops after ~10–30 s | Client read or socket timeout | Raise or disable timeouts for SSE requests |
| Nothing renders; logs show events | Catalog id mismatch | The app's basic catalog id must equal `https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json` (`GET /health` shows the server's) |
| "surface … does not exist" in the logs | The app didn't send `sessionId`, so the server started a new session | Persist and resend `sessionId` |
| Self-heal demo never repairs | The app doesn't forward engine errors | Post `VALIDATION_FAILED` errors to `/api/agent/event` |
| Images are blank | No image loader plugged into the catalog, or no internet | Wire Coil into `materialA2uiBasicCatalogV1(image = …)`; check device connectivity |
| `No LLM provider is configured` bubble | No provider configured, in hybrid or llm mode | Configure one in `.env` (see the README), or use `npm run mock` |
| Server exits with `Unknown LLM_PROVIDER` | Typo in `LLM_PROVIDER` | Use `gemini`, `claude` or `custom` |
| `Claude declined this request` error | Claude's safety classifiers refused and no fallback could serve it | Rephrase, or switch provider |
| `[llm] repair …` in the logs | The model produced invalid lines; the server is fixing them | Normal occasionally. If frequent, improve the prompt (see above) |
| `400 Invalid A2UI client message` | The `/event` body doesn't match `client_to_server.json` | Read `details`; all of `name`, `surfaceId`, `sourceComponentId`, `timestamp`, `context` are required |
| Port already in use | Another server instance is running | `PORT=3001 npm start`, or stop the other process |

Server logs print every request (`POST /api/agent/chat`), client errors (`[client error]`), LLM repairs
(`[llm] repair`) and failed turns (`[turn failed]`).

---

## Coding conventions

- ES modules, Node ≥ 20, no build step.
- All A2UI output goes through `out.a2ui()`. Never write to `res` directly.
- Use the builders (`createSurface`, `updateComponents`, …) from `src/a2ui.js` rather than writing the
  `version`/envelope by hand.
- Protocol logic belongs in `src/a2ui.js`, conversation state in `src/session.js`, and "what to show" in `src/agent/*`.
- Run `npm run smoke` before committing.
