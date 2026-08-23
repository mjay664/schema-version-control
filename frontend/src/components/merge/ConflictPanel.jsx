import React from 'react';
import { AlertTriangle, Check, CheckCircle2, GitBranch } from 'lucide-react';
import { Badge } from '../ui/Badge';

/**
 * Conflicts reported by the three-way merge engine, with a per-conflict choice.
 *
 * The engine speaks in ours/theirs; "ours" is the target branch and "theirs" is
 * the source branch (see ThreeWayMergeEngine.compute(ancestor, target, source)).
 * Everything is relabelled target/source here, because ours/theirs is ambiguous
 * the moment you are not the person who opened the merge request.
 *
 * A choice is a side, never a hand-written definition, so a merge can only land
 * something that already exists on one of the two branches under review.
 */
const TARGET = 'TARGET';
const SOURCE = 'SOURCE';

const KIND_LABEL = {
  COLUMN_TYPE_CONFLICT: 'Type changed on both sides',
  COLUMN_ADD_CONFLICT: 'Added on both sides with different definitions',
  COLUMN_DELETE_MODIFY_CONFLICT: 'Dropped in target, modified in source',
  COLUMN_MODIFY_DELETE_CONFLICT: 'Modified in target, dropped in source',
  MODIFY_DELETE_CONFLICT: 'Table dropped in target, modified in source',
  DELETE_MODIFY_CONFLICT: 'Table modified in target, dropped in source',
  PARSE_ERROR: 'Schema could not be parsed',
};

/** Compact rendering of a column definition. */
function describeColumn(column) {
  if (!column || typeof column !== 'object') return null;
  const flags = [
    column.primaryKey && 'PK',
    column.nullable === false && 'NOT NULL',
    column.unique && 'UQ',
  ].filter(Boolean);
  return [column.type, ...flags].filter(Boolean).join(' · ');
}

/**
 * What picking `side` actually does, per conflict type. Deletion conflicts need
 * spelling out — "target" meaning "stay dropped" is not guessable from a type.
 */
function describeChoice(conflict, side) {
  const isTarget = side === TARGET;

  switch (conflict.type) {
    case 'COLUMN_TYPE_CONFLICT':
      return { verb: 'Use', value: isTarget ? conflict.oursType : conflict.theirsType };
    case 'COLUMN_ADD_CONFLICT':
      return { verb: 'Use', value: describeColumn(isTarget ? conflict.ours : conflict.theirs) };
    case 'COLUMN_MODIFY_DELETE_CONFLICT':
      return isTarget
        ? { verb: 'Keep the column', value: describeColumn(conflict.ours) }
        : { verb: 'Drop the column', value: null };
    case 'COLUMN_DELETE_MODIFY_CONFLICT':
      return isTarget
        ? { verb: 'Drop the column', value: null }
        : { verb: 'Keep the column', value: describeColumn(conflict.theirs) };
    case 'MODIFY_DELETE_CONFLICT':
      return isTarget
        ? { verb: 'Leave the table dropped', value: null }
        : { verb: 'Restore the table', value: null };
    case 'DELETE_MODIFY_CONFLICT':
      return isTarget
        ? { verb: 'Keep the table', value: null }
        : { verb: 'Drop the table', value: null };
    default:
      return { verb: isTarget ? 'Take target' : 'Take source', value: null };
  }
}

function Choice({ conflict, side, branchName, selected, onSelect }) {
  const { verb, value } = describeChoice(conflict, side);

  return (
    <button
      type="button"
      className="conflict-choice"
      data-side={side.toLowerCase()}
      aria-pressed={selected}
      onClick={() => onSelect(side)}
    >
      <span className="conflict-choice-head">
        <GitBranch size={11} />
        <span className="truncate">
          {side === TARGET ? 'target' : 'source'} · {branchName}
        </span>
        {selected && <Check size={13} className="conflict-choice-tick" />}
      </span>
      <span className="conflict-choice-verb">{verb}</span>
      {value && <code className="conflict-choice-value">{value}</code>}
    </button>
  );
}

export function ConflictPanel({
  conflicts,
  resolutions,
  onResolve,
  targetBranchName,
  sourceBranchName,
}) {
  if (!conflicts?.length) return null;

  const decided = conflicts.filter((c) => resolutions[c.key]).length;
  const allDecided = decided === conflicts.length;

  return (
    <section className="conflicts" data-resolved={allDecided} role="alert">
      <header className="conflicts-head">
        {allDecided ? <CheckCircle2 size={15} /> : <AlertTriangle size={15} />}
        <span className="conflicts-title">
          {allDecided
            ? `All ${conflicts.length} conflict${conflicts.length === 1 ? '' : 's'} decided`
            : conflicts.length === 1
              ? '1 conflict blocks this merge'
              : `${conflicts.length} conflicts block this merge`}
        </span>
        <span className="spacer" />
        <Badge tone={allDecided ? 'add' : 'del'}>
          {decided} of {conflicts.length} decided
        </Badge>
      </header>

      <p className="conflicts-lede">
        {allDecided ? (
          <>
            Merging will apply these choices and record them against the merge in the audit
            trail. Change any of them before merging if you picked wrong.
          </>
        ) : (
          <>
            Both branches changed the same thing in different ways since they diverged, so the
            three-way merge cannot pick a winner. Choose a side for each — you can only take a
            definition that already exists on one of the two branches, and the choice is
            recorded in the audit trail.
          </>
        )}
      </p>

      <ul className="conflict-list">
        {conflicts.map((conflict) => {
          const key = conflict.key;
          const chosen = resolutions[key];
          const path = [conflict.tableName, conflict.columnName].filter(Boolean).join('.');

          return (
            <li key={key} className="conflict" data-decided={Boolean(chosen)}>
              <div className="conflict-top">
                {path && <code className="conflict-path">{path}</code>}
                <Badge tone="mod">{KIND_LABEL[conflict.type] ?? conflict.type}</Badge>
              </div>

              <div className="conflict-choices">
                <Choice
                  conflict={conflict}
                  side={TARGET}
                  branchName={targetBranchName}
                  selected={chosen === TARGET}
                  onSelect={(side) => onResolve(key, side)}
                />
                <Choice
                  conflict={conflict}
                  side={SOURCE}
                  branchName={sourceBranchName}
                  selected={chosen === SOURCE}
                  onSelect={(side) => onResolve(key, side)}
                />
              </div>

              {conflict.description && <p className="conflict-why">{conflict.description}</p>}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
