# 4. Demo guide

A walkthrough of the scripted demo, about 3 minutes long. For each step it covers what to do, what the audience
sees, which A2UI feature it demonstrates, and where the code lives. Use it to present the POC, or to learn the
protocol by following real messages.

Run the server with `npm run mock` so that no step depends on the LLM.

| Step | You do | Feature shown | Code (`src/agent/scripted.js`) |
|---|---|---|---|
| 1 | Type **"hi"** | Surfaces, theming, buttons with actions | `FLOWS.menu` |
| 2 | Tap **Plan a trip** (or type "plan a trip to Lisbon") | Progressive rendering, two-way binding, client functions, checks | `FLOWS.trip` |
| 3 | Tap **Find stays** | Actions with context, progressive List, late-arriving data | `ACTIONS.search_stays` |
| 4 | Tap **Book** on a stay | Live server pushes, fine-grained recomposition | `ACTIONS.book_stay` |
| 5 | Tap **Done** | Deleting surfaces | `ACTIONS.close_booking` |
| 6 | Type **"self-heal demo"** | Client validation errors and agent self-correction | `FLOWS.selfheal`, `healWeather` |
| 7 | Type anything else (hybrid mode) | Free-form generative UI from the LLM | `src/agent/llm.js` |

---

## Step 1: Welcome card

**On screen:** a purple Material 3 card with a title, a caption, a divider and two buttons.

**Messages:** `createSurface("welcome", theme: {primaryColor: "#6750A4"})`, then one `updateComponents`.

**Talking point:** "The server sent JSON describing a card. Compose drew it with real Material 3 components and
the theme color the agent picked."

---

## Step 2: Trip form, built progressively

**On screen:** the card appears **in three visible steps**, about 400 ms apart: the header image and title first,
then the date pickers, then the guests field, budget slider, vibe chips and the "Find stays" button.

**Messages:**

```
createSurface trip
updateComponents  root, trip-body (lists all 10 children, most not sent yet), trip-hero, trip-title
  … 400 ms …
updateComponents  trip-sub, trip-dates, trip-in, trip-out
  … 400 ms …
updateComponents  trip-guests, trip-budget, trip-budget-value, trip-vibes, trip-div, trip-search(+label)
updateDataModel   / = {destination, checkIn, checkOut, guests, budget: 180, vibes: [beach, food]}
```

**Things to try on the device:**

- **Move the budget slider.** The price text below it (`formatCurrency` bound to `/budget`) updates instantly,
  **with no server call**. The slider writes `/budget` locally and the text reads it.
- **Clear the check-in date.** "Find stays" disables, because its `checks` (`required(/checkIn)`) now fail.
- The title "Weekend in Lisbon" comes from `formatString("Weekend in ${/destination}")`. The destination was
  taken from your chat message.

**Talking point:** "The layout streamed in before the content existed. The renderer buffers references to
components that haven't arrived yet. The slider and price label stay in sync on the device, and the agent is
only contacted when the user commits."

---

## Step 3: Search results streamed into a List

**On screen:** a heading reads "0 stays in Lisbon · 3 nights", with loading placeholders in the list. After about
1 s it changes to "3 stays…", and three result cards (image, name, rating, price, Book button) replace the placeholders.

**Messages:**

```
(app → server) action search_stays, context = {destination, checkIn, checkOut, guests, budget, vibes}
createSurface stays
updateComponents  root, st-title, st-list: List { children: ["st-card-casa", "st-card-palm", "st-card-fort"] }
updateDataModel   / = {destination, nights, count: 0, stays: []}
  … 900 ms …
updateDataModel   /stays = [ {id, name, area, rating, price, image}, ×3 ]
updateDataModel   /count = 3
updateComponents  st-card-casa, st-row-casa, …, st-book-casa (one card per stay)
```

**Things to notice:**

- The list arrives **before** its cards: the renderer shows placeholders for child ids it hasn't received yet,
  then fills them in (progressive rendering).
- Card text binds to the data model with absolute paths: `{"path": "/stays/0/name"}`, `"★ ${/stays/0/rating} · …"`.
- Each **Book** button carries its stay as literal values (`{stayId, name, price}`), so the next step knows
  exactly which stay was chosen.
- Prices are derived from the **budget the user chose** in step 2. The action's `context` delivered it.

> **Why not a List template?** A template (`children: {componentId, path}`) would define the card once. But in
> androidx.a2ui 1.0.0-alpha02, a Button inside a template sends its action context **unresolved** against the
> item, so `{"path": "name"}` reaches the server empty. Display bindings in templates work; button actions don't.

**Talking point:** "The button sent the form values with it. The agent answered with the list first and the
results a moment later. The renderer shows placeholders until they arrive."

---

## Step 4: Live booking status

**On screen:** a booking card. Every 1.2 s the icon and status change:
🔄 *Checking availability…* → 🔒 *Holding your room…* → 💳 *Processing payment…* → ✅ *Confirmed · Ref CAS-1234*.
"Step 1 of 4" counts up. The **Done** button stays disabled until confirmation.

**Messages:** after the initial surface, only small data updates on the same open stream:

```
updateDataModel /status "Holding your room…"
updateDataModel /icon   "lock"
updateDataModel /step   2
… (×3)
updateDataModel /confirmed true      ← enables Done (its check is {"path": "/confirmed"})
```

**Talking point:** "The server pushes values, not UI. Because the Compose renderer is built on snapshot state,
only the icon and the two text components recompose. The rest of the card is untouched." Layout Inspector's
recomposition counts make this visible.

---

## Step 5: Done

**On screen:** the booking, results and trip surfaces disappear. A chat bubble says "Have a great trip!".

**Messages:** `deleteSurface` × 3.

---

## Step 6: Self-healing UI

**On screen:** a blue weather card shows the city and description, and the temperature component fails (it shows
whatever error state the renderer uses). About half a second after the app reports the problem, the temperature
appears as "31°C". A chat bubble explains what happened.

**What happens:**

```mermaid
sequenceDiagram
  participant S as Server
  participant A as App (A2UI engine)
  S->>A: updateComponents … {"id":"wx-temp","component":"Text","content":"31°"}  ← "content" is wrong; "text" is required
  A->>A: schema validation fails for wx-temp
  A->>S: POST /event {"error":{"code":"VALIDATION_FAILED","surfaceId":"weather",…}}
  S->>A: updateComponents {"id":"wx-temp","component":"Text","text":"${/temp}°C","variant":"h1"}
  S->>A: text "The app reported VALIDATION_FAILED … I re-sent a corrected component."
```

The scripted flow sends the broken component **on purpose** (`skipValidation: true` bypasses the server's own
check). With the LLM, the same loop is automatic: the error is sent back to the model, which re-emits the
component. Repairs are capped at 2 per surface.

**Prerequisite:** the Android app must forward the engine's validation errors to `/api/agent/event`. If nothing
happens after the card renders, check that first.

**Talking point:** "LLMs sometimes generate invalid UI. The app never crashes. It reports the exact problem, and
the agent fixes it. The server also validates every LLM message *before* sending it, so most mistakes never
reach the device."

---

## Step 7: Free-form prompts (hybrid mode, needs an LLM provider)

Try prompts like:

- "Show me a workout tracker for this week"
- "A login form with email validation"
- "Compare three phone plans"
- Follow-ups: "make it dark green", "add a notes field", "remove the second plan"

Behind the scenes, the LLM (Gemini, Claude or a custom model) writes one A2UI message per line. Each line is validated and streamed to the app as
soon as it's complete, so the UI assembles itself while the model is still writing. See
[Architecture → The LLM agent](02-architecture.md#the-llm-agent-in-detail).

**Tip for live demos:** LLM output varies. Rehearse the exact prompts you'll use, and keep steps 1–6 as the
reliable backbone.

---

## Presenter checklist

- [ ] Server running in `mock` mode (or `hybrid` with a key, if you'll show step 7)
- [ ] `curl localhost:3000/health` returns `ok: true`
- [ ] `npm run smoke` passes
- [ ] The device can reach the server (open `http://10.0.2.2:3000/health` in the emulator's browser)
- [ ] Images load (the device needs internet access for `picsum.photos`)
- [ ] Layout Inspector open, if you want to show recomposition counts in step 4

Next: [5. Extending and troubleshooting](05-extending.md).
