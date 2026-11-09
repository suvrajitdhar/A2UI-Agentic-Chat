// A2UI v0.9 protocol helpers: message builders, schema validation, tree checks.
// Schemas are bundled from https://a2ui.org/specification/v0_9/ (see schemas/v0_9).
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCHEMA_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'schemas', 'v0_9');
const load = (f) => JSON.parse(fs.readFileSync(path.join(SCHEMA_DIR, f), 'utf8'));

export const A2UI_VERSION = 'v0.9';
export const BASIC_CATALOG = load('basic_catalog.json');
export const BASIC_CATALOG_ID = BASIC_CATALOG.catalogId; // https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json

export const SERVER_MESSAGE_KEYS = ['createSurface', 'updateComponents', 'updateDataModel', 'deleteSurface'];

// ---------- Builders ----------
export const createSurface = (surfaceId, catalogId, { theme, sendDataModel = true } = {}) => ({
  version: A2UI_VERSION,
  createSurface: { surfaceId, catalogId, ...(theme && { theme }), sendDataModel },
});
export const updateComponents = (surfaceId, components) => ({
  version: A2UI_VERSION,
  updateComponents: { surfaceId, components },
});
export const updateDataModel = (surfaceId, path, value) => ({
  version: A2UI_VERSION,
  updateDataModel: { surfaceId, ...(path !== undefined && { path }), ...(value !== undefined && { value }) },
});
export const deleteSurface = (surfaceId) => ({ version: A2UI_VERSION, deleteSurface: { surfaceId } });

export function messageKind(msg) {
  return SERVER_MESSAGE_KEYS.find((k) => msg && typeof msg === 'object' && k in msg);
}
export const surfaceIdOf = (msg) => msg?.[messageKind(msg)]?.surfaceId;

// ---------- Schema validation ----------
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addSchema(load('common_types.json'));
// server_to_client.json and common_types.json reference "catalog.json" relative to the v0_9 base.
ajv.addSchema({ ...BASIC_CATALOG, $id: 'https://a2ui.org/specification/v0_9/catalog.json' });
const validateServer = ajv.compile(load('server_to_client.json'));
const validateClient = ajv.compile(load('client_to_server.json'));

// oneOf over every component type produces noisy errors; keep the most specific ones.
function summarize(errors = []) {
  const meaningful = errors.filter((e) => !['oneOf', 'anyOf', 'if', 'const'].includes(e.keyword) || errors.length === 1);
  const maxDepth = Math.max(...meaningful.map((e) => e.instancePath.split('/').length));
  return [...new Set(meaningful
    .filter((e) => e.instancePath.split('/').length === maxDepth)
    .map((e) => `${e.instancePath || '/'} ${e.message}${e.params?.additionalProperty ? ` '${e.params.additionalProperty}'` : ''}${e.params?.allowedValues ? ` ${JSON.stringify(e.params.allowedValues)}` : ''}`))]
    .slice(0, 5);
}

/**
 * Validates a server->client message. Components are checked against the basic catalog schema
 * only when the surface uses the basic catalog; custom/inline catalogs get structural checks.
 */
export function validateServerMessage(msg, { catalogId = BASIC_CATALOG_ID } = {}) {
  const kind = messageKind(msg);
  if (!kind) return { ok: false, errors: [`message must contain one of ${SERVER_MESSAGE_KEYS.join(', ')}`] };
  if (msg.version !== A2UI_VERSION) return { ok: false, errors: [`version must be "${A2UI_VERSION}"`] };

  if (kind === 'updateComponents' && catalogId !== BASIC_CATALOG_ID) {
    const comps = msg.updateComponents?.components;
    const bad = Array.isArray(comps) && comps.length
      ? comps.filter((c) => typeof c?.id !== 'string' || typeof c?.component !== 'string')
      : null;
    if (!bad) return { ok: false, errors: ['updateComponents.components must be a non-empty array'] };
    if (bad.length) return { ok: false, errors: ['every component needs string "id" and "component"'] };
    return { ok: true, errors: [] };
  }
  if (validateServer(msg)) return { ok: true, errors: [] };
  // For component errors, re-validate each component against its own type for precise, LLM-repairable messages.
  if (kind === 'updateComponents' && Array.isArray(msg.updateComponents?.components)) {
    const precise = msg.updateComponents.components.flatMap(componentErrors);
    if (precise.length) return { ok: false, errors: precise };
  }
  return { ok: false, errors: summarize(validateServer.errors) };
}

/** Splits basic-catalog components into valid ones and per-component errors (for partial forwarding). */
export function partitionComponents(components) {
  const valid = [];
  const errors = [];
  components.forEach((c, i) => {
    const errs = componentErrors(c, i);
    if (errs.length) errors.push(...errs); else valid.push(c);
  });
  return { valid, errors };
}

const CATALOG_REF ='https://a2ui.org/specification/v0_9/catalog.json#/components/';
function componentErrors(c, index) {
  const where = `component[${index}] "${c?.id}"`;
  if (!BASIC_CATALOG.components[c?.component]) {
    return [`${where}: unknown component "${c?.component}" (allowed: ${Object.keys(BASIC_CATALOG.components).join(', ')})`];
  }
  const v = ajv.getSchema(CATALOG_REF + c.component);
  if (v(c)) return [];
  return [...new Set(v.errors.map((e) => {
    const extra = e.params?.unevaluatedProperty ?? e.params?.additionalProperty;
    const allowed = e.params?.allowedValues ? ` ${JSON.stringify(e.params.allowedValues)}` : '';
    return `${where} (${c.component})${e.instancePath}: ${extra ? `unknown property "${extra}"` : e.message + allowed}`;
  }))].slice(0, 4);
}

export function validateClientMessage(msg) {
  return validateClient(msg) ? { ok: true, errors: [] } : { ok: false, errors: summarize(validateClient.errors) };
}

// ---------- Tree checks (across all updateComponents for a surface) ----------
/** Collects every component id referenced by a component (children, child, Tabs, Modal, templates). */
export function referencedIds(c) {
  const refs = [];
  if (Array.isArray(c.children)) refs.push(...c.children);
  else if (c.children?.componentId) refs.push(c.children.componentId);
  for (const k of ['child', 'trigger', 'content']) if (typeof c[k] === 'string') refs.push(c[k]);
  if (Array.isArray(c.tabs)) refs.push(...c.tabs.map((t) => t?.child).filter(Boolean));
  return refs;
}

/** Returns problems with a surface's component map: missing root, dangling references. */
export function checkTree(components /* Map<id, component> */) {
  const problems = [];
  if (!components.has('root')) problems.push('no component with id "root"');
  for (const c of components.values()) {
    for (const ref of referencedIds(c)) {
      if (!components.has(ref)) problems.push(`component "${c.id}" references missing id "${ref}"`);
    }
  }
  return problems;
}

// ---------- JSON Pointer helpers for the server-side mirror of each surface's data model ----------
const decode = (seg) => seg.replace(/~1/g, '/').replace(/~0/g, '~');
export function applyDataModelUpdate(model, pointer, value, remove) {
  if (!pointer || pointer === '/') return remove ? {} : structuredClone(value);
  const segs = pointer.split('/').slice(1).map(decode);
  const root = model ?? {};
  let node = root;
  for (const seg of segs.slice(0, -1)) {
    if (node[seg] === null || typeof node[seg] !== 'object') node[seg] = {};
    node = node[seg];
  }
  const last = segs.at(-1);
  if (remove) delete node[last];
  else node[last] = structuredClone(value);
  return root;
}
