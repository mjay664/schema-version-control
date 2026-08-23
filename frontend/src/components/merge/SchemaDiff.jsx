import React from 'react';
import { Table2 } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { EmptyState } from '../ui/Feedback';
import { CHANGE, columnFlags, diffSchemas } from '../../lib/schema';

/** Row styling and sigil for one side's change. */
const SIDE_STYLE = {
  [CHANGE.ADDED]: { cls: 'is-add', sigil: '+' },
  [CHANGE.REMOVED]: { cls: 'is-del', sigil: '−' },
  [CHANGE.MODIFIED]: { cls: 'is-mod', sigil: '~' },
  [CHANGE.UNCHANGED]: { cls: 'is-same', sigil: '' },
};

export function DiffLegend() {
  return (
    <div className="legend">
      <span className="legend-item" style={{ color: 'var(--add)' }}>
        <span className="legend-swatch" style={{ background: 'var(--add)' }} /> Added
      </span>
      <span className="legend-item" style={{ color: 'var(--del)' }}>
        <span className="legend-swatch" style={{ background: 'var(--del)' }} /> Removed
      </span>
      <span className="legend-item" style={{ color: 'var(--mod)' }}>
        <span className="legend-swatch" style={{ background: 'var(--mod)' }} /> Modified
      </span>
    </div>
  );
}

/**
 * One column row within a pane, coloured by what *this* side did since the
 * branches diverged — not by the combined difference, which would report a
 * table the target dropped as something the source added.
 */
function DiffLine({ row, side }) {
  const column = side === 'target' ? row.target : row.source;
  const change = side === 'target' ? row.targetChange : row.sourceChange;

  if (!column) {
    return (
      <div className="diff-line is-void">
        {change === CHANGE.REMOVED
          ? `dropped in ${side}`
          : `not in ${side}`}
      </div>
    );
  }

  const { cls, sigil } = SIDE_STYLE[change] ?? SIDE_STYLE[CHANGE.UNCHANGED];

  return (
    <div className={`diff-line ${cls}`}>
      <span className="diff-sigil">{sigil}</span>
      <span className="diff-col">{column.name}</span>
      <span className="diff-flags">
        {columnFlags(column).map((flag) => (
          <span key={flag} className="diff-flag">
            {flag}
          </span>
        ))}
      </span>
      <span className="diff-type">{column.type}</span>
    </div>
  );
}

function Pane({ label, branchName, rows, side, absentNote }) {
  if (absentNote) {
    return <div className="diff-pane is-absent">{absentNote}</div>;
  }
  return (
    <div className="diff-pane">
      <div className="diff-pane-head">
        {label}
        <span className="mono truncate">{branchName}</span>
      </div>
      <div className="diff-pane-body">
        {rows.map((row) => (
          <DiffLine key={`${side}-${row.name}`} row={row} side={side} />
        ))}
      </div>
    </div>
  );
}

/**
 * Side-by-side schema comparison: target branch left, source branch right.
 *
 * When `ancestorTables` is supplied the comparison is three-way, so each pane
 * reports what its own branch changed since the fork. Without it, the source is
 * described relative to the target.
 */
export function SchemaDiff({
  targetTables,
  sourceTables,
  ancestorTables = null,
  targetBranchName,
  sourceBranchName,
  showUnchanged = false,
}) {
  const { tables } = diffSchemas(targetTables, sourceTables, ancestorTables);
  const visible = showUnchanged ? tables : tables.filter((t) => t.status !== 'unchanged');

  if (tables.length === 0) {
    return (
      <EmptyState icon={Table2} title="Both schemas are empty">
        Neither branch defines any tables yet.
      </EmptyState>
    );
  }

  if (visible.length === 0) {
    return (
      <EmptyState icon={Table2} title="No schema differences">
        <code>{sourceBranchName}</code> matches <code>{targetBranchName}</code> table for table.
      </EmptyState>
    );
  }

  return (
    <div className="diff-stack">
      {visible.map((table) => {
        const changedColumns = table.columns.filter((c) => c.status !== 'unchanged').length;

        return (
          <section key={table.name} className="diff-table">
            <header className="diff-table-head">
              <Table2 size={14} className="muted" />
              <span className="diff-table-name">{table.name}</span>
              <Badge tone={table.tone}>{table.label}</Badge>
              {changedColumns > 0 && (
                <span className="dim" style={{ fontSize: 'var(--fs-xs)' }}>
                  {changedColumns} column{changedColumns === 1 ? '' : 's'} changed
                </span>
              )}
            </header>

            <div className="diff-panes">
              <Pane
                label="Target"
                branchName={targetBranchName}
                rows={table.columns}
                side="target"
                absentNote={
                  !table.target
                    ? table.targetChange === CHANGE.REMOVED
                      ? `${table.name} was dropped in ${targetBranchName}`
                      : `${table.name} does not exist in ${targetBranchName}`
                    : null
                }
              />
              <Pane
                label="Source"
                branchName={sourceBranchName}
                rows={table.columns}
                side="source"
                absentNote={
                  !table.source
                    ? table.sourceChange === CHANGE.REMOVED
                      ? `${table.name} was dropped in ${sourceBranchName}`
                      : `${table.name} does not exist in ${sourceBranchName}`
                    : null
                }
              />
            </div>
          </section>
        );
      })}
    </div>
  );
}
