// Deterministic demo flows (no LLM needed). They exercise the features worth showing on the Compose side:
// progressive rendering, two-way data binding, client functions & checks, List templates,
// live data-model pushes (fine-grained recomposition) and self-healing after a client VALIDATION_FAILED.
import {
  BASIC_CATALOG_ID, createSurface, updateComponents, updateDataModel, deleteSurface,
} from '../a2ui.js';
import { sleep } from '../sse.js';

// Structure of this file:
//   helpers      – small builders that keep the component JSON readable
//   triggers     – matchScripted(): chat text → flow name
//   FLOWS        – chat-triggered flows (menu, trip, selfheal)
//   ACTIONS      – button-triggered handlers, keyed by action.name
// See docs/04-demo-guide.md for what each flow shows, and docs/05-extending.md to add one.

// Surfaces created here are tagged with this owner so that their actions/errors are routed back to this file.
const OWNER = 'scripted';
const THEME = { primaryColor: '#00796B', agentDisplayName: 'Trip Planner' };

// ---------------------------------------------------------------- helpers
/** Client-side string interpolation, e.g. fmt('Hi ${/name}') – evaluated on the device. */
const fmt = (value) => ({ call: 'formatString', args: { value }, returnType: 'string' });
/** Client-side currency formatting of the number at `path`. */
const usd = (path) => ({ call: 'formatCurrency', args: { value: { path }, currency: 'USD', decimals: 0 }, returnType: 'string' });
/** A check rule: the input shows `message` (and a Button is disabled) while the value at `path` is empty. */
const required = (path, message) => ({ condition: { call: 'required', args: { value: { path } } }, message });
const label = (id, text) => ({ id, component: 'Text', text });
/**
 * A Button needs a separate child component for its label, so this returns two components:
 * the Button `id` and its Text `id-label`. `action` is the `event` object ({name, context}).
 */
const button = (id, text, action, extra = {}) => [
  { id, component: 'Button', child: `${id}-label`, action: { event: action }, ...extra },
  label(`${id}-label`, text),
];

/** Sends a message and throws if the server-side validator rejects it (a bug in the script). */
function send(out, msg, opts) {
  const res = out.a2ui(msg, { owner: OWNER, ...opts });
  if (!res.ok) throw new Error(`scripted message rejected: ${res.errors.join('; ')}`);
}
/** (Re)creates a surface so a flow can be re-run: createSurface fails if the id already exists. */
function freshSurface(out, session, surfaceId, theme = THEME) {
  if (session.surfaces.has(surfaceId)) send(out, deleteSurface(surfaceId));
  send(out, createSurface(surfaceId, BASIC_CATALOG_ID, { theme, sendDataModel: true }));
}

// ---------------------------------------------------------------- triggers
export function matchScripted(text) {
  const t = text.toLowerCase();
  if (/self.?heal|broken|error demo|weather/.test(t)) return 'selfheal';
  if (/\b(trip|travel|vacation|holiday|getaway|stay|hotel)\b/.test(t)) return 'trip';
  if (/^(hi|hello|hey|help|menu|start|demo|demos)\b/.test(t)) return 'menu';
  return null;
}

export async function runScripted(name, session, out, text = '') {
  if (!session.catalogs.some((c) => c.catalogId === BASIC_CATALOG_ID)) {
    out.text('The scripted demos need the A2UI basic catalog, which this client did not advertise.');
    return;
  }
  return FLOWS[name](session, out, text);
}

export async function handleScriptedAction(action, session, out) {
  const handler = ACTIONS[action.name];
  if (!handler) { out.text(`I don't know how to handle "${action.name}" yet.`); return; }
  return handler(action, session, out);
}

export async function handleScriptedError(error, session, out) {
  if (error.surfaceId === 'weather') return healWeather(error, session, out);
  out.text(`The app reported a problem on "${error.surfaceId}": ${error.message}`);
}

// ---------------------------------------------------------------- flows
const FLOWS = {
  async menu(session, out) {
    out.text('Hi! Pick a demo, or just ask me for any UI.');
    freshSurface(out, session, 'welcome', { primaryColor: '#6750A4', agentDisplayName: 'A2UI Demo Agent' });
    send(out, updateComponents('welcome', [
      { id: 'root', component: 'Card', child: 'w-body' },
      { id: 'w-body', component: 'Column', children: ['w-head', 'w-sub', 'w-div', 'w-trip', 'w-heal'] },
      { id: 'w-head', component: 'Text', text: 'A2UI × Jetpack Compose', variant: 'h3' },
      { id: 'w-sub', component: 'Text', text: 'Native Material 3 UI, streamed by an agent over SSE.', variant: 'caption' },
      { id: 'w-div', component: 'Divider' },
      ...button('w-trip', '✈️  Plan a trip', { name: 'start_demo', context: { demo: 'trip' } }, { variant: 'primary' }),
      ...button('w-heal', '🩹  Self-healing UI', { name: 'start_demo', context: { demo: 'selfheal' } }),
    ]));
  },

  async trip(session, out, text) {
    const destination = (text.match(/\b(?:to|in|for)\s+([A-Z][\p{L}]+(?:\s[A-Z][\p{L}]+)?)/u)?.[1]) ?? 'Goa';
    out.text(`Let's plan ${destination}. Tweak anything below.`);
    freshSurface(out, session, 'trip');
    const s = out.signal;

    // Progressive rendering: root + layout first (children ids not yet defined are buffered by the client).
    send(out, updateComponents('trip', [
      { id: 'root', component: 'Card', child: 'trip-body' },
      { id: 'trip-body', component: 'Column', children: [
        'trip-hero', 'trip-title', 'trip-sub', 'trip-dates', 'trip-guests',
        'trip-budget', 'trip-budget-value', 'trip-vibes', 'trip-div', 'trip-search'] },
      { id: 'trip-hero', component: 'Image', url: `https://picsum.photos/seed/${encodeURIComponent(destination)}/800/400`,
        description: destination, variant: 'header', fit: 'cover' },
      { id: 'trip-title', component: 'Text', text: fmt('Weekend in ${/destination}'), variant: 'h2' },
    ]));
    await sleep(400, s);
    send(out, updateComponents('trip', [
      { id: 'trip-sub', component: 'Text', text: 'Dates, budget and vibe', variant: 'caption' },
      { id: 'trip-dates', component: 'Row', children: ['trip-in', 'trip-out'], justify: 'spaceBetween' },
      { id: 'trip-in', component: 'DateTimeInput', label: 'Check-in', value: { path: '/checkIn' },
        enableDate: true, weight: 1, checks: [required('/checkIn', 'Pick a check-in date')] },
      { id: 'trip-out', component: 'DateTimeInput', label: 'Check-out', value: { path: '/checkOut' }, enableDate: true, weight: 1 },
    ]));
    await sleep(400, s);
    send(out, updateComponents('trip', [
      { id: 'trip-guests', component: 'TextField', label: 'Guests', variant: 'number', value: { path: '/guests' },
        checks: [{ condition: { call: 'numeric', args: { value: { path: '/guests' }, min: 1, max: 8 } }, message: '1–8 guests' }] },
      { id: 'trip-budget', component: 'Slider', label: 'Nightly budget', min: 50, max: 500, value: { path: '/budget' } },
      // Re-formats on every slider move: client-side function + fine-grained recomposition.
      { id: 'trip-budget-value', component: 'Text', text: usd('/budget'), variant: 'h4' },
      { id: 'trip-vibes', component: 'ChoicePicker', label: 'Vibe', variant: 'multipleSelection', displayStyle: 'chips',
        value: { path: '/vibes' }, options: [
          { label: 'Beach', value: 'beach' }, { label: 'Food', value: 'food' }, { label: 'Nightlife', value: 'nightlife' },
          { label: 'Heritage', value: 'heritage' }, { label: 'Wellness', value: 'wellness' }] },
      { id: 'trip-div', component: 'Divider' },
      ...button('trip-search', 'Find stays', {
        name: 'search_stays',
        context: { destination: { path: '/destination' }, checkIn: { path: '/checkIn' }, checkOut: { path: '/checkOut' },
          guests: { path: '/guests' }, budget: { path: '/budget' }, vibes: { path: '/vibes' } },
      }, { variant: 'primary', checks: [required('/checkIn', 'Pick a check-in date')] }),
    ]));
    send(out, updateDataModel('trip', '/', {
      destination, checkIn: '2026-12-18', checkOut: '2026-12-21', guests: '2', budget: 180, vibes: ['beach', 'food'],
    }));
  },

  async selfheal(session, out) {
    out.text('Rendering a weather card… (one component is intentionally malformed)');
    freshSurface(out, session, 'weather', { primaryColor: '#1565C0', agentDisplayName: 'Weather' });
    send(out, updateComponents('weather', [
      { id: 'root', component: 'Card', child: 'wx-body' },
      { id: 'wx-body', component: 'Column', children: ['wx-city', 'wx-temp', 'wx-desc'], align: 'center' },
      { id: 'wx-city', component: 'Text', text: { path: '/city' }, variant: 'h3' },
      // BROKEN ON PURPOSE: Text requires "text"; "content" is not a property. The Compose client's schema
      // validation rejects it and reports VALIDATION_FAILED back to POST /api/agent/event.
      { id: 'wx-temp', component: 'Text', content: '31°' },
      { id: 'wx-desc', component: 'Text', text: { path: '/desc' }, variant: 'caption' },
    ]), { skipValidation: true });
    send(out, updateDataModel('weather', '/', { city: 'Panaji', temp: 31, desc: 'Sunny · humidity 64%' }));
  },
};

// ---------------------------------------------------------------- actions
const STAYS = [
  { id: 'casa', name: 'Casa Azul Beach House', area: 'Candolim', rating: 4.8, base: 1.0, seed: 'casa-azul' },
  { id: 'palm', name: 'Palm Grove Cottages', area: 'Palolem', rating: 4.6, base: 0.75, seed: 'palm-grove' },
  { id: 'fort', name: 'Old Fort Heritage Inn', area: 'Fontainhas', rating: 4.7, base: 0.9, seed: 'old-fort' },
];

/**
 * Components of one result card. Text binds to the stay's entry in /stays (absolute paths), and the
 * Book button carries the stay's values as literals so the action never depends on binding resolution.
 */
function stayCard(stay, index) {
  const p = (field) => `/stays/${index}/${field}`;
  const id = (part) => `st-${part}-${stay.id}`;
  return [
    { id: id('card'), component: 'Card', child: id('row') },
    { id: id('row'), component: 'Row', children: [id('img'), id('info')], align: 'center' },
    { id: id('img'), component: 'Image', url: { path: p('image') }, variant: 'smallFeature', fit: 'cover' },
    { id: id('info'), component: 'Column', children: [id('name'), id('meta'), id('price'), id('book')], weight: 1 },
    { id: id('name'), component: 'Text', text: { path: p('name') }, variant: 'h5' },
    { id: id('meta'), component: 'Text', text: fmt(`★ \${${p('rating')}} · \${${p('area')}}`), variant: 'caption' },
    { id: id('price'), component: 'Text', text: fmt(`\${formatCurrency(value:\${${p('price')}}, currency:'USD')} / night`), variant: 'body' },
    ...button(id('book'), 'Book', {
      name: 'book_stay', context: { stayId: stay.id, name: stay.name, price: stay.price },
    }),
  ];
}

const ACTIONS = {
  async start_demo(action, session, out) {
    return runScripted(action.context?.demo === 'selfheal' ? 'selfheal' : 'trip', session, out, '');
  },

  async search_stays(action, session, out) {
    const ctx = action.context ?? {};
    const budget = Number(ctx.budget) || 180;
    const nights = Math.max(1, Math.round((Date.parse(ctx.checkOut) - Date.parse(ctx.checkIn)) / 86_400_000) || 3);
    const stays = STAYS.map((x) => ({
      id: x.id, name: x.name, area: x.area, rating: x.rating,
      price: Math.round(budget * x.base), image: `https://picsum.photos/seed/${x.seed}/300/300`,
    }));
    out.status('Searching stays…');
    freshSurface(out, session, 'stays');

    // Progressive rendering: the list and its child ids arrive first; the client shows loading
    // placeholders for the cards until their components arrive below.
    //
    // Why one card per stay instead of a List template ({componentId, path}): in androidx.a2ui
    // 1.0.0-alpha02, a Button inside a template dispatches its action context unresolved against the
    // template item, so relative paths like {"path": "name"} reach the server empty. Literal context
    // values on per-item buttons avoid that, and display text still binds to the data model.
    send(out, updateComponents('stays', [
      { id: 'root', component: 'Column', children: ['st-title', 'st-list'] },
      { id: 'st-title', component: 'Text', text: fmt('${/count} stays in ${/destination} · ${/nights} nights'), variant: 'h4' },
      { id: 'st-list', component: 'List', direction: 'vertical', children: stays.map((s) => `st-card-${s.id}`) },
    ]));
    send(out, updateDataModel('stays', '/', { destination: ctx.destination ?? 'Goa', nights, count: 0, stays: [] }));
    await sleep(900, out.signal);

    send(out, updateDataModel('stays', '/stays', stays));
    send(out, updateDataModel('stays', '/count', stays.length));
    send(out, updateComponents('stays', stays.flatMap(stayCard)));
    session.trip = { nights };
    out.text('Here are three picks within your budget.');
  },

  async book_stay(action, session, out) {
    const { name = 'Your stay', price = 150 } = action.context ?? {};
    const nights = session.trip?.nights ?? 3;
    freshSurface(out, session, 'booking');
    send(out, updateComponents('booking', [
      { id: 'root', component: 'Card', child: 'bk-body' },
      { id: 'bk-body', component: 'Column', children: ['bk-head', 'bk-name', 'bk-status-row', 'bk-step', 'bk-total', 'bk-done'] },
      { id: 'bk-head', component: 'Text', text: 'Booking', variant: 'h3' },
      { id: 'bk-name', component: 'Text', text: { path: '/name' }, variant: 'h5' },
      { id: 'bk-status-row', component: 'Row', children: ['bk-icon', 'bk-status'], align: 'center' },
      { id: 'bk-icon', component: 'Icon', name: { path: '/icon' } },
      { id: 'bk-status', component: 'Text', text: { path: '/status' }, weight: 1 },
      { id: 'bk-step', component: 'Text', text: fmt('Step ${/step} of 4'), variant: 'caption' },
      { id: 'bk-total', component: 'Text', text: fmt(`Total \${formatCurrency(value:\${/total}, currency:'USD')} for ${nights} nights`) },
      // Disabled (check fails) until the server pushes /confirmed = true.
      ...button('bk-done', 'Done', { name: 'close_booking', context: {} },
        { variant: 'primary', checks: [{ condition: { path: '/confirmed' }, message: 'Waiting for confirmation' }] }),
    ]));
    send(out, updateDataModel('booking', '/', {
      name, total: price * nights, status: 'Checking availability…', icon: 'refresh', step: 1, confirmed: false,
    }));

    // Live pushes over the open SSE stream: only the bound Icon/Text recompose.
    const steps = [
      { status: 'Holding your room…', icon: 'lock' },
      { status: 'Processing payment…', icon: 'payment' },
      { status: `Confirmed · Ref ${String(name).slice(0, 3).toUpperCase()}-${Math.floor(1000 + Math.random() * 9000)}`, icon: 'check' },
    ];
    for (const [i, step] of steps.entries()) {
      await sleep(1200, out.signal);
      if (out.closed) return;
      send(out, updateDataModel('booking', '/status', step.status));
      send(out, updateDataModel('booking', '/icon', step.icon));
      send(out, updateDataModel('booking', '/step', i + 2));
    }
    send(out, updateDataModel('booking', '/confirmed', true));
    out.text('You are booked! 🎉');
  },

  async close_booking(action, session, out) {
    for (const id of ['booking', 'stays', 'trip']) if (session.surfaces.has(id)) send(out, deleteSurface(id));
    out.text('Have a great trip!');
  },
};

async function healWeather(error, session, out) {
  out.status('Repairing component…');
  await sleep(500, out.signal);
  send(out, updateComponents('weather', [
    { id: 'wx-temp', component: 'Text', text: fmt('${/temp}°C'), variant: 'h1' },
  ]));
  out.text(`The app reported ${error.code} (${error.message}). I re-sent a corrected "wx-temp" component.`);
}
