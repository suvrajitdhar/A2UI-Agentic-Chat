// Catalog negotiation (https://a2ui.org/concepts/catalogs/#catalog-negotiation) and
// compact catalog descriptions for the LLM prompt.
import { BASIC_CATALOG, BASIC_CATALOG_ID } from './a2ui.js';

/**
 * Reads `metadata.a2uiClientCapabilities` sent by the Compose client (A2uiClientCapabilities).
 * Shape per client_capabilities.json: { "v0.9": { supportedCatalogIds: [...], inlineCatalogs?: [Catalog] } }
 * (a flat { supportedCatalogIds } object is tolerated too).
 * Returns the catalogs the server can target, in the client's order of preference.
 *  - supportedCatalogIds: ids the client can render
 *  - inlineCatalogs: catalog definitions advertised at runtime (lets the agent learn custom components)
 */
export function negotiateCatalogs(capabilities) {
  const caps = capabilities?.['v0.9'] ?? capabilities;
  const supported = caps?.supportedCatalogIds ?? [BASIC_CATALOG_ID];
  const inline = new Map();
  for (const cat of caps?.inlineCatalogs ?? []) {
    const id = cat?.catalogId ?? cat?.$id;
    if (id && cat.components) inline.set(id, cat);
  }

  const usable = [];
  for (const id of supported) {
    if (id === BASIC_CATALOG_ID) usable.push({ catalogId: id, schema: BASIC_CATALOG, validated: true });
    else if (inline.has(id)) usable.push({ catalogId: id, schema: inline.get(id), validated: false });
  }
  // Inline catalogs not listed in supportedCatalogIds are still renderable by the client.
  for (const [id, schema] of inline) {
    if (!usable.some((u) => u.catalogId === id)) usable.push({ catalogId: id, schema, validated: false });
  }
  return usable;
}

// ---------- Prompt-friendly catalog summary, generated from the JSON schema ----------
function typeOf(s, depth = 0) {
  if (!s || depth > 3) return 'any';
  if (s.$ref) return s.$ref.split('/').pop();
  if (s.enum) return s.enum.length > 12 ? `enum(${s.enum.slice(0, 12).join('|')}|…)` : `enum(${s.enum.join('|')})`;
  if (s.const !== undefined) return JSON.stringify(s.const);
  if (s.oneOf) return s.oneOf.map((x) => typeOf(x, depth + 1)).join(' | ');
  if (s.type === 'array') return `${typeOf(s.items, depth + 1)}[]`;
  if (s.type === 'object' && s.properties) {
    return `{${Object.entries(s.properties).map(([k, v]) => `${k}: ${typeOf(v, depth + 1)}`).join(', ')}}`;
  }
  return s.type ?? 'any';
}

function componentProps(def) {
  const props = {};
  let required = [];
  for (const part of def.allOf ?? [def]) {
    if (part.$ref?.endsWith('Checkable')) props.checks = { $ref: 'CheckRule[]' };
    Object.assign(props, part.properties ?? {});
    required = required.concat(part.required ?? []);
  }
  delete props.component;
  return { props, required: new Set(required) };
}

export function describeCatalog(catalog) {
  const lines = [`Catalog "${catalog.catalogId ?? catalog.$id}"`];
  if (catalog.$defs?.CatalogComponentCommon) {
    // Shared by every component via $ref, so componentProps() doesn't see them; state them once.
    lines.push('Every component also accepts: id (required), accessibility?: {label?, description?}, '
      + 'weight?: number (flex-grow; only for direct children of Row/Column).');
  }
  lines.push('Components:');
  for (const [name, def] of Object.entries(catalog.components ?? {})) {
    const { props, required } = componentProps(def);
    const fields = Object.entries(props).map(([k, v]) => `${k}${required.has(k) ? '' : '?'}: ${typeOf(v)}`);
    lines.push(`- ${name} { ${fields.join(', ')} }`);
  }
  // Basic catalog: functions is a map of JSON schemas. Inline catalogs: an array of FunctionDefinition.
  const fns = Array.isArray(catalog.functions)
    ? catalog.functions.map((f) => [f.name, Object.keys(f.parameters?.properties ?? {}), f.returnType])
    : Object.entries(catalog.functions ?? {}).map(([name, f]) => [
      name, Object.keys(f.properties?.args?.properties ?? {}), f.properties?.returnType?.const ?? 'any']);
  if (fns.length) {
    lines.push('Client functions (use as {"call": name, "args": {...}, "returnType": ...}):');
    for (const [name, args, ret] of fns) lines.push(`- ${name}(${args.join(', ')}) -> ${ret}`);
  }
  return lines.join('\n');
}
