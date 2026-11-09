# 1. A2UI concepts (start here)

This page explains the ideas you need before reading the code. It assumes no prior knowledge of A2UI.

- [What problem does A2UI solve?](#what-problem-does-a2ui-solve)
- [The five building blocks](#the-five-building-blocks)
- [Messages: the server → client protocol](#messages-the-server--client-protocol)
- [Components: a flat list, not a tree](#components-a-flat-list-not-a-tree)
- [Data model and data binding](#data-model-and-data-binding)
- [Client functions and checks](#client-functions-and-checks)
- [Actions and errors: the client → server protocol](#actions-and-errors-the-client--server-protocol)
- [Catalogs and catalog negotiation](#catalogs-and-catalog-negotiation)
- [Glossary](#glossary)

---

## What problem does A2UI solve?

A chat agent normally answers with text. Sometimes a **UI** is the better answer: a booking form, a list of
results with "Book" buttons, a live order status. A2UI (**Agent-to-UI**) is an open protocol
([a2ui.org](https://a2ui.org/)) that lets an agent *describe* a UI as JSON. The app then renders that UI with its
own **native** components.

```
 ┌──────────────┐  A2UI JSON messages (what to show)   ┌─────────────────────────────┐
 │ Agent server │ ───────────────────────────────────▶ │ Android app (Compose)       │
 │ (this repo)  │                                      │  A2UI renderer → Material 3 │
 │              │ ◀─────────────────────────────────── │  native UI                  │
 └──────────────┘  actions / errors (what the user did)└─────────────────────────────┘
```

Key properties:

- **No code is executed.** The agent sends data, not code. The app decides how each component looks.
- **Native look and feel.** On Android, the Jetpack Compose renderer (`androidx.a2ui.compose`) maps every A2UI
  component to a real Compose/Material 3 composable.
- **Safe by contract.** The agent may only use components the app has declared in a **catalog**.

This server speaks **A2UI v0.9**. The `version` field in every message is `"v0.9"`; the Compose renderer
implements the v0.9.1 spec.

---

## The five building blocks

| Concept | One-line explanation | Where in this repo |
|---|---|---|
| **Surface** | One independent piece of UI (a card, a form, a screen) with an id such as `"trip"` | `createSurface` messages |
| **Component** | One UI element (`Text`, `Button`, `Column`, …) with a unique `id` | `updateComponents` messages |
| **Data model** | A JSON object per surface holding the values the UI shows and edits | `updateDataModel` messages |
| **Action** | A message the app sends when the user taps a button | `POST /api/agent/event` |
| **Catalog** | The list of components and functions the app supports (the contract) | `schemas/v0_9/basic_catalog.json` |

---

## Messages: the server → client protocol

The server never sends "a whole screen". It sends a **sequence of small messages**. Each message has
`"version": "v0.9"` and **exactly one** of four keys:

| Message | Purpose | Example |
|---|---|---|
| `createSurface` | Start a new surface; choose its catalog and theme | `{"version":"v0.9","createSurface":{"surfaceId":"trip","catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json","theme":{"primaryColor":"#00796B"},"sendDataModel":true}}` |
| `updateComponents` | Add or replace components on a surface | `{"version":"v0.9","updateComponents":{"surfaceId":"trip","components":[{"id":"root","component":"Text","text":"Hello"}]}}` |
| `updateDataModel` | Set (or delete) a value in the surface's data model | `{"version":"v0.9","updateDataModel":{"surfaceId":"trip","path":"/budget","value":180}}` |
| `deleteSurface` | Remove a surface | `{"version":"v0.9","deleteSurface":{"surfaceId":"trip"}}` |

Rules worth remembering:

1. `createSurface` must come first for a surface. Sending it again for an existing id is an error, so delete the surface first.
2. A surface's `catalogId` cannot change after creation.
3. `updateComponents` can be sent **many times**. A component with an existing `id` is **replaced**; a new `id` is **added**.
4. `updateDataModel` with no `value` **removes** the key at `path`. A `path` of `/` (or no path) means the whole model.

Because the UI arrives in pieces, the app can render **progressively**: the layout first, then details, then data.
That progressive rendering is a big part of what this POC shows off.

---

## Components: a flat list, not a tree

You might expect nested JSON like `{"Column": {"children": [{"Text": ...}]}}`. A2UI does **not** do that.
Components form a **flat list**, and parents point to children **by id** (an "adjacency list"):

```json
[
  { "id": "root",     "component": "Card",   "child": "body" },
  { "id": "body",     "component": "Column", "children": ["title", "go"] },
  { "id": "title",    "component": "Text",   "text": "Weekend in Goa", "variant": "h2" },
  { "id": "go",       "component": "Button", "child": "go-label",
    "action": { "event": { "name": "search_stays", "context": {} } } },
  { "id": "go-label", "component": "Text",   "text": "Find stays" }
]
```

renders as:

```
root (Card)
└── body (Column)
    ├── title (Text)  "Weekend in Goa"
    └── go (Button)
        └── go-label (Text) "Find stays"
```

Why flat? It makes **streaming and partial updates** easy. The server can send `root` and `body` first, even
though `title` does not exist yet. The client buffers the missing references and fills them in when they arrive.
Later, the server can replace just `title` without resending the rest.

Rules:

- Every surface needs exactly one component with `"id": "root"`.
- References use `children` (array of ids), `child` (one id), `Tabs.tabs[].child`, or `Modal.trigger` / `Modal.content`.
- Every referenced id must eventually exist. This server checks that (`checkTree()` in `src/a2ui.js`).

### Repeating items: List templates

To render a list of items from data, `children` can be a **template** instead of an array:

```json
{ "id": "st-list", "component": "List", "children": { "componentId": "st-card", "path": "/stays" } }
```

The renderer creates one copy of `st-card` (and its subtree) **per element** of the `/stays` array. Inside
the template, paths **without** a leading `/` are relative to the current item, so `{"path": "name"}` means
"this stay's name".

### Component reference (basic catalog)

| Component | Required props | Useful optional props |
|---|---|---|
| `Text` | `text` | `variant`: h1–h5, body, caption |
| `Image` | `url` | `fit`, `variant` (icon, avatar, smallFeature, …, header), `description` |
| `Icon` | `name` | e.g. `check`, `lock`, `payment`, `search` (see the catalog for the full list) |
| `Row` / `Column` | `children` | `justify`, `align` |
| `List` | `children` | `direction` |
| `Card` | `child` | |
| `Tabs` | `tabs` | |
| `Modal` | `trigger`, `content` | |
| `Divider` | | `axis` |
| `Button` | `child`, `action` | `variant` (default, primary, borderless), `checks` |
| `TextField` | `label` | `value`, `variant` (shortText, longText, number, obscured), `checks` |
| `CheckBox` | `label`, `value` | `checks` |
| `ChoicePicker` | `options`, `value` | `variant`, `displayStyle` (checkbox, chips), `checks` |
| `Slider` | `value`, `max` | `min`, `label`, `checks` |
| `DateTimeInput` | `value` | `enableDate`, `enableTime`, `label`, `checks` |
| `Video`, `AudioPlayer` | `url` | the app must plug in a media player; this POC doesn't use them |

Any component inside a `Row`/`Column` may set `weight` (like CSS `flex-grow`). The source of truth is
`schemas/v0_9/basic_catalog.json`.

---

## Data model and data binding

Each surface has a **data model**: a plain JSON object. Instead of hard-coding values, components can **bind**
to it with a [JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901) path:

```json
{ "id": "trip-budget", "component": "Slider", "min": 50, "max": 500, "value": { "path": "/budget" } }
{ "id": "bk-status",   "component": "Text",   "text": { "path": "/status" } }
```

The binding works **in both directions**:

- **Server → UI.** `updateDataModel {path: "/status", value: "Confirmed"}` changes the text on screen. The
  Compose renderer is built on the Snapshot state system, so **only the components bound to `/status`
  recompose**. The booking demo pushes a status update every 1.2 s to show this.
- **UI → data model.** When the user moves the slider, the client writes the new number to `/budget`
  **locally**. The server does **not** hear about it until an action fires.

With `"sendDataModel": true` in `createSurface`, the client attaches the current data model of its surfaces to
every request it sends (as `metadata.a2uiClientDataModel`). See [API → metadata](03-api.md#the-metadata-object).

A dynamic property (types named `DynamicString`, `DynamicNumber`, … in the schema) accepts one of three forms:

| Form | Example |
|---|---|
| Literal | `"text": "Hello"` |
| Binding | `"text": { "path": "/user/name" }` |
| Function call | `"text": { "call": "formatCurrency", "args": { "value": { "path": "/budget" }, "currency": "USD" }, "returnType": "string" }` |

---

## Client functions and checks

The catalog also defines **functions that run on the device**, so simple logic needs no server round trip:

- Formatting: `formatString`, `formatNumber`, `formatCurrency`, `formatDate`, `pluralize`
- Validation: `required`, `regex`, `length`, `numeric`, `email`, plus `and` / `or` / `not`
- Side effects: `openUrl`

`formatString` interpolates values with `${...}`:

```json
{ "call": "formatString", "args": { "value": "Weekend in ${/destination}" }, "returnType": "string" }
```

**Checks** are validation rules on inputs and buttons. When a check's `condition` is false, the input shows the
`message`, and a `Button` is disabled:

```json
{ "id": "trip-search", "component": "Button", "child": "trip-search-label",
  "checks": [ { "condition": { "call": "required", "args": { "value": { "path": "/checkIn" } } },
                "message": "Pick a check-in date" } ],
  "action": { "event": { "name": "search_stays", "context": {} } } }
```

---

## Actions and errors: the client → server protocol

The app talks back with exactly two kinds of message.

### Action: "the user did something"

A `Button` declares what to send:

```json
"action": { "event": { "name": "book_stay", "context": { "stayId": { "path": "id" }, "price": { "path": "price" } } } }
```

When it's tapped, the client **resolves the bindings** in `context` and sends:

```json
{ "version": "v0.9",
  "action": { "name": "book_stay", "surfaceId": "stays", "sourceComponentId": "st-book",
              "timestamp": "2026-11-04T10:00:00Z", "context": { "stayId": "casa", "price": 180 } } }
```

(An action can also be `{"functionCall": {...}}`, which runs a client function such as `openUrl` and never
reaches the server.)

### Error: "your UI was broken"

The Compose engine validates every incoming component against the catalog. If one is invalid, the app reports it:

```json
{ "version": "v0.9",
  "error": { "code": "VALIDATION_FAILED", "surfaceId": "weather",
             "path": "/components/3/text", "message": "Missing required property 'text' in component 'Text'." } }
```

The server can then fix the component and resend it. That is the "self-healing UI" demo.

---

## Catalogs and catalog negotiation

A **catalog** is the contract between agent and app. It's a JSON Schema listing every component, its
properties, and the client functions available. It is identified by a `catalogId` (a URI that doesn't need to
resolve):

```
https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json      ← the standard "basic catalog"
```

On Android, `androidx.compose.material3:material3-a2ui` implements the basic catalog with Material 3. Apps can
also define **custom catalogs** (for example, a `FlightCard` built with their own design system).

**Negotiation:** with every request, the app tells the server which catalogs it can render:

```json
"metadata": { "a2uiClientCapabilities": { "v0.9": {
  "supportedCatalogIds": ["https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"],
  "inlineCatalogs": [ { "catalogId": "https://example.com/my-catalog/v1", "components": { "FlightCard": { } } } ]
} } }
```

The server picks one of these for each new surface. `inlineCatalogs` carry the full schema of custom catalogs.
This server injects them into the LLM prompt, so the agent learns the app's custom components automatically.

---

## Glossary

| Term | Meaning |
|---|---|
| **A2UI** | Agent-to-UI protocol. JSON messages that describe native UI |
| **Agent** | The server side that decides what UI to show (this repo) |
| **Renderer / client** | The app that turns A2UI messages into native UI (the Compose app) |
| **Surface** | An independently managed piece of UI, identified by `surfaceId` |
| **Component** | One UI element in a surface's flat list, identified by `id` |
| **Adjacency list** | Representing a tree as a flat list where parents reference children by id |
| **Data model** | Per-surface JSON object that components read and write via paths |
| **JSON Pointer** | A path syntax like `/user/name` or `/stays/0/price` |
| **Binding** | `{"path": "..."}` in place of a literal value |
| **Template** | `children: {componentId, path}`: render one component per array item |
| **Catalog** | A schema of allowed components and functions, identified by `catalogId` |
| **Basic catalog** | The standard A2UI catalog (Text, Button, Column, …) |
| **Check** | A client-side validation rule (`condition` + `message`) |
| **Action** | A client → server event triggered by the user |
| **SSE** | Server-Sent Events: a one-way HTTP stream of events from server to client |
| **JSONL** | JSON Lines: one JSON object per line; how the LLM writes its output here |

Next: [2. Architecture](02-architecture.md), which shows how this server produces these messages.
