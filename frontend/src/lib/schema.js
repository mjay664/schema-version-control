/* ===========================================================================
   Schema document helpers.

   A schema document is  { tables: [ { name, columns: [...], indexes: [...] } ] }
   A column is           { name, type, primaryKey, nullable, unique,
                           defaultValue, foreignKey, engineConstraint }
   An index is           { name, columns: [string], unique }
   =========================================================================== */

export const EMPTY_SCHEMA = '{"tables":[]}';

/** Parse a schemaData string into a table array; never throws. */
export function parseTables(schemaData) {
  if (!schemaData) return [];
  try {
    const parsed = JSON.parse(schemaData);
    return Array.isArray(parsed?.tables) ? parsed.tables : [];
  } catch (_) {
    return [];
  }
}

export const serializeTables = (tables) => JSON.stringify({ tables });

/** Locate the schema of a specific version inside a version-history list. */
export function tablesForVersion(versions, versionId) {
  if (!versionId || !Array.isArray(versions)) return [];
  const version = versions.find((v) => v.id === versionId);
  return version ? parseTables(version.schemaData) : [];
}

/* --- Data types --------------------------------------------------------- */

/** "VARCHAR(500)" -> "VARCHAR". Comparisons between a column's type and the
    engine catalogue always run on this, because the catalogue names
    parameterised types with a default argument baked in ("VARCHAR(255)"). */
export const typeBaseName = (value) => String(value ?? '').split('(')[0].trim();

/** Split "DECIMAL(12,4)" into its base name and its argument. */
export function parseDataType(value) {
  const raw = String(value ?? '').trim();
  const match = raw.match(/^([^(]+?)\s*(?:\((.*)\))?$/);
  if (!match) return { base: raw, param: '' };
  return { base: match[1].trim(), param: (match[2] ?? '').trim() };
}

export const buildDataType = (base, param) => {
  const name = typeBaseName(base);
  const arg = String(param ?? '').trim();
  return arg ? `${name}(${arg})` : name;
};

/** The catalogue entry whose base name matches this type, if there is one. */
export const findDataType = (value, dataTypes = []) => {
  const base = typeBaseName(value).toUpperCase();
  return dataTypes.find((d) => typeBaseName(d.name).toUpperCase() === base) ?? null;
};

/** Whether a type takes an argument — per the catalogue, or evidently so. */
export const isParameterized = (value, dataTypes = []) =>
  Boolean(findDataType(value, dataTypes)?.parameterized) || String(value ?? '').includes('(');

/* --- Column constraint flags -------------------------------------------- */

/** Constraint flags on a column, as short labels for diff rendering. */
export function columnFlags(col = {}) {
  const flags = [];
  if (col.primaryKey) flags.push('PK');
  if (col.nullable === false) flags.push('NOT NULL');
  if (col.unique) flags.push('UQ');
  if (col.engineConstraint) flags.push(col.engineConstraint);
  return flags;
}

/** True when two columns differ in any way the editor can express. */
export function columnsDiffer(a, b) {
  if (!a || !b) return true;
  return (
    a.type !== b.type ||
    Boolean(a.primaryKey) !== Boolean(b.primaryKey) ||
    Boolean(a.unique) !== Boolean(b.unique) ||
    a.nullable !== b.nullable ||
    (a.engineConstraint || '') !== (b.engineConstraint || '') ||
    (a.defaultValue || '') !== (b.defaultValue || '')
  );
}

/* --- Diff --------------------------------------------------------------- */

/** How one side moved relative to the point the branches diverged. */
export const CHANGE = {
  ABSENT: 'absent',
  ADDED: 'added',
  REMOVED: 'removed',
  MODIFIED: 'modified',
  UNCHANGED: 'unchanged',
};

const changeOf = (before, after, differs) => {
  if (!before && !after) return CHANGE.ABSENT;
  if (!before) return CHANGE.ADDED;
  if (!after) return CHANGE.REMOVED;
  return differs(before, after) ? CHANGE.MODIFIED : CHANGE.UNCHANGED;
};

const indexKey = (table) =>
  JSON.stringify(
    (table.indexes || [])
      .map((i) => [i.name, [...(i.columns || [])].sort(), Boolean(i.unique)])
      .sort((a, b) => String(a[0]).localeCompare(String(b[0])))
  );

function tablesDiffer(a, b) {
  if (indexKey(a) !== indexKey(b)) return true;
  const aCols = new Map((a.columns || []).map((c) => [c.name, c]));
  const bCols = new Map((b.columns || []).map((c) => [c.name, c]));
  if (aCols.size !== bCols.size) return true;
  for (const [name, col] of aCols) {
    const other = bCols.get(name);
    if (!other || columnsDiffer(col, other)) return true;
  }
  return false;
}

const isQuiet = (change) => change === CHANGE.UNCHANGED || change === CHANGE.ABSENT;

const SIDE_WORD = {
  [CHANGE.ADDED]: 'Added',
  [CHANGE.REMOVED]: 'Dropped',
  [CHANGE.MODIFIED]: 'Modified',
};

/** One badge describing what each branch did, e.g. "Dropped in target · Modified in source". */
function summarise(targetChange, sourceChange) {
  if (isQuiet(targetChange) && isQuiet(sourceChange)) {
    return { status: 'unchanged', label: 'Unchanged', tone: 'neutral' };
  }

  const tone =
    targetChange === CHANGE.REMOVED || sourceChange === CHANGE.REMOVED
      ? 'del'
      : targetChange === CHANGE.ADDED || sourceChange === CHANGE.ADDED
        ? 'add'
        : 'mod';

  const parts = [];
  if (!isQuiet(targetChange)) parts.push(`${SIDE_WORD[targetChange]} in target`);
  if (!isQuiet(sourceChange)) parts.push(`${SIDE_WORD[sourceChange]} in source`);

  return { status: 'changed', label: parts.join(' \u00b7 '), tone };
}

/**
 * Compare two branch heads, describing each side against their common ancestor.
 *
 * Pass `ancestorTables` for a true three-way reading — each side is then labelled
 * by what it actually did since the branches diverged, so a table the target
 * dropped reads as dropped rather than as "added by source". With no ancestor it
 * falls back to comparing source against target, which is the right frame for an
 * uncommitted editor buffer.
 *
 * Returns { tables, stats } where each table carries `targetChange`/`sourceChange`
 * plus a combined `status`, `label` and `tone`, and each column row the same.
 */
export function diffSchemas(targetTables = [], sourceTables = [], ancestorTables = null) {
  const ancestor = ancestorTables ?? targetTables;

  const ancMap = new Map((ancestor || []).map((t) => [t.name, t]));
  const targetMap = new Map((targetTables || []).map((t) => [t.name, t]));
  const sourceMap = new Map((sourceTables || []).map((t) => [t.name, t]));
  const names = [...new Set([...ancMap.keys(), ...targetMap.keys(), ...sourceMap.keys()])].sort();

  const stats = { added: 0, removed: 0, modified: 0 };

  const tables = names.map((name) => {
    const anc = ancMap.get(name) ?? null;
    const target = targetMap.get(name) ?? null;
    const source = sourceMap.get(name) ?? null;

    const targetChange = changeOf(anc, target, tablesDiffer);
    const sourceChange = changeOf(anc, source, tablesDiffer);

    if (sourceChange === CHANGE.ADDED) stats.added += 1;
    else if (sourceChange === CHANGE.REMOVED) stats.removed += 1;
    else if (sourceChange === CHANGE.MODIFIED) stats.modified += 1;

    return {
      name,
      target,
      source,
      targetChange,
      sourceChange,
      ...summarise(targetChange, sourceChange),
      columns: columnRows(anc, target, source),
    };
  });

  return { tables, stats };
}

function columnRows(ancTable, targetTable, sourceTable) {
  const ancCols = new Map((ancTable?.columns || []).map((c) => [c.name, c]));
  const targetCols = new Map((targetTable?.columns || []).map((c) => [c.name, c]));
  const sourceCols = new Map((sourceTable?.columns || []).map((c) => [c.name, c]));
  const names = [...new Set([...ancCols.keys(), ...targetCols.keys(), ...sourceCols.keys()])];

  return names.map((name) => {
    const anc = ancCols.get(name) ?? null;
    const target = targetCols.get(name) ?? null;
    const source = sourceCols.get(name) ?? null;

    const targetChange = changeOf(anc, target, columnsDiffer);
    const sourceChange = changeOf(anc, source, columnsDiffer);

    return {
      name,
      target,
      source,
      targetChange,
      sourceChange,
      ...summarise(targetChange, sourceChange),
    };
  });
}

/** Roll a DiffResultDto from the backend into { added, removed, modified } counts. */
export function diffStatsFromDto(diff) {
  if (!diff) return null;
  return {
    added: diff.addedTables?.length || 0,
    removed: diff.removedTables?.length || 0,
    modified: diff.modifiedTables?.length || 0,
  };
}
