import { useCallback, useMemo, useState } from 'react';
import { EMPTY_SCHEMA, parseTables, serializeTables, tablesForVersion } from '../../lib/schema';

const emptySnapshot = { key: null, tables: [], baseline: EMPTY_SCHEMA, selected: null };

const pickSelection = (tables, previous) =>
  tables.some((t) => t.name === previous) ? previous : (tables[0]?.name ?? null);

/**
 * Editable schema buffer for the checked-out branch.
 *
 * The buffer reloads whenever the branch head moves — which covers checking out
 * another branch, switching repository, and landing a commit — but a plain
 * refresh of the version list leaves unsaved edits alone.
 *
 * Everything lives in one state object keyed by the head being edited. Deriving
 * it during render (rather than in an effect) avoids painting one frame of the
 * previous branch's schema under the new branch's name, and keeping the key in
 * state rather than a ref keeps that derivation correct under StrictMode's
 * double render.
 *
 * `readOnly` (the protected `main` branch) turns every mutation into a no-op so
 * the guard does not depend on the UI hiding its own buttons.
 */
export function useSchemaBuffer({ repoId, branch, versions, versionsReady, readOnly }) {
  const [snapshot, setSnapshot] = useState(emptySnapshot);

  // The branch list and the version history load in parallel, so a branch head
  // is not resolvable until the history for its repository has arrived.
  const key = repoId && branch && versionsReady ? `${repoId}:${branch.id}:${branch.headVersionId}` : null;

  if (key && snapshot.key !== key) {
    const loaded = tablesForVersion(versions, branch.headVersionId);
    setSnapshot({
      key,
      tables: loaded,
      baseline: serializeTables(loaded),
      selected: pickSelection(loaded, snapshot.selected),
    });
  }

  const { tables, baseline, selected } = snapshot;

  const setSelectedTableName = useCallback(
    (name) => setSnapshot((s) => ({ ...s, selected: name })),
    []
  );

  const setTables = useCallback(
    (updater) =>
      setSnapshot((s) => {
        const next = typeof updater === 'function' ? updater(s.tables) : updater;
        return { ...s, tables: next, selected: pickSelection(next, s.selected) };
      }),
    []
  );

  const currentJson = useMemo(() => serializeTables(tables), [tables]);
  const committedTables = useMemo(() => parseTables(baseline), [baseline]);
  const isDirty = currentJson !== baseline;

  /** Apply a change to one table, leaving the rest untouched. */
  const patchTable = useCallback(
    (tableName, patch) => {
      if (readOnly) return;
      setTables((prev) => prev.map((t) => (t.name === tableName ? { ...t, ...patch(t) } : t)));
    },
    [readOnly, setTables]
  );

  const patchColumn = useCallback(
    (tableName, columnName, patch) =>
      patchTable(tableName, (t) => ({
        columns: t.columns.map((c) => (c.name === columnName ? { ...c, ...patch(c) } : c)),
      })),
    [patchTable]
  );

  const ops = useMemo(
    () => ({
      addTable(rawName, defaults = {}) {
        if (readOnly) return { ok: false, error: 'Branch is protected' };
        const name = rawName.trim().toLowerCase();
        if (!name) return { ok: false, error: 'Table name is required' };
        if (tables.some((t) => t.name === name)) {
          return { ok: false, error: `Table '${name}' already exists` };
        }

        const table = {
          name,
          columns: [
            {
              name: 'id',
              type: defaults.idType || 'UUID',
              primaryKey: true,
              nullable: false,
              unique: true,
            },
            {
              name: 'created_at',
              type: defaults.timeType || 'TIMESTAMP',
              nullable: false,
              defaultValue: 'CURRENT_TIMESTAMP',
            },
          ],
          indexes: [{ name: `idx_${name}_id`, columns: ['id'], unique: true }],
        };

        setSnapshot((s) => ({ ...s, tables: [...s.tables, table], selected: name }));
        return { ok: true };
      },

      dropTable(tableName) {
        if (readOnly) return;
        setTables((prev) => prev.filter((t) => t.name !== tableName));
      },

      addColumn(tableName, rawName, type) {
        if (readOnly) return { ok: false, error: 'Branch is protected' };
        const name = rawName.trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
        if (!name) return { ok: false, error: 'Column name is required' };

        const table = tables.find((t) => t.name === tableName);
        if (table?.columns?.some((c) => c.name === name)) {
          return { ok: false, error: `Column '${name}' already exists on ${tableName}` };
        }

        patchTable(tableName, (t) => ({
          columns: [
            ...t.columns,
            {
              name,
              type,
              nullable: true,
              primaryKey: false,
              unique: false,
              defaultValue: '',
              foreignKey: '',
            },
          ],
        }));
        return { ok: true };
      },

      renameColumn(tableName, columnName, nextName) {
        patchColumn(tableName, columnName, () => ({
          name: nextName.toLowerCase().replace(/[^a-z0-9_]/g, ''),
        }));
      },

      retypeColumn(tableName, columnName, type) {
        patchColumn(tableName, columnName, () => ({ type }));
      },

      /** Toggle a boolean constraint. Making a column a primary key implies NOT NULL + UNIQUE. */
      toggleFlag(tableName, columnName, flag) {
        patchColumn(tableName, columnName, (col) => {
          const next = { [flag]: !col[flag] };
          if (flag === 'primaryKey' && !col.primaryKey) {
            next.nullable = false;
            next.unique = true;
          }
          return next;
        });
      },

      setColumnField(tableName, columnName, field, value) {
        patchColumn(tableName, columnName, () => ({ [field]: value }));
      },

      dropColumn(tableName, columnName) {
        patchTable(tableName, (t) => ({ columns: t.columns.filter((c) => c.name !== columnName) }));
      },

      addIndex(tableName, rawName, columns, unique) {
        if (readOnly) return { ok: false, error: 'Branch is protected' };
        const name = rawName.trim().toLowerCase();
        if (!name) return { ok: false, error: 'Index name is required' };
        if (!columns.length) return { ok: false, error: 'Select at least one column for the index' };

        const table = tables.find((t) => t.name === tableName);
        if ((table?.indexes || []).some((i) => i.name === name)) {
          return { ok: false, error: `Index '${name}' already exists on ${tableName}` };
        }

        patchTable(tableName, (t) => ({ indexes: [...(t.indexes || []), { name, columns, unique }] }));
        return { ok: true };
      },

      dropIndex(tableName, indexName) {
        patchTable(tableName, (t) => ({
          indexes: (t.indexes || []).filter((i) => i.name !== indexName),
        }));
      },

      /** Discard uncommitted edits and return to the branch head. */
      revert() {
        setSnapshot((s) => {
          const restored = parseTables(s.baseline);
          return { ...s, tables: restored, selected: pickSelection(restored, s.selected) };
        });
      },
    }),
    [patchColumn, patchTable, readOnly, setTables, tables]
  );

  return {
    tables,
    committedTables,
    selectedTableName: selected,
    setSelectedTableName,
    selectedTable: tables.find((t) => t.name === selected) ?? null,
    currentJson,
    isDirty,
    ops,
  };
}
