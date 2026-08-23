import React from 'react';
import { Badge } from '../ui/Badge';
import { Select } from '../ui/Field';

/** Boolean constraints that map onto dedicated column fields. */
const FLAG_BY_NAME = {
  'PRIMARY KEY': 'primaryKey',
  'NOT NULL': 'nullable',
  UNIQUE: 'unique',
};

function Chip({ tone, label, onRemove }) {
  return (
    <Badge tone={tone}>
      {label}
      {onRemove && (
        <button type="button" className="chip-x" onClick={onRemove} aria-label={`Remove ${label}`}>
          ×
        </button>
      )}
    </Badge>
  );
}

/**
 * Constraint editor for one column: a dropdown that applies a constraint, and a
 * dismissible chip per active constraint. All constraints come from the
 * repository's engine catalogue.
 */
export function ConstraintCell({ column, constraints, readOnly, onToggleFlag, onSetField }) {
  const apply = (name) => {
    if (!name) return;
    const flag = FLAG_BY_NAME[name];
    if (flag) onToggleFlag(flag);
    else onSetField('engineConstraint', name);
  };

  return (
    <div className="constraint-cell">
      {!readOnly && (
        <Select
          size="sm"
          value=""
          aria-label="Add constraint"
          onChange={(e) => {
            apply(e.target.value);
            e.target.value = '';
          }}
        >
          <option value="">Add constraint…</option>
          {constraints.map((c) => (
            <option key={c.name} value={c.name} title={c.description}>
              {c.name}
              {c.category ? ` · ${c.category}` : ''}
            </option>
          ))}
        </Select>
      )}

      {column.primaryKey && (
        <Chip
          tone="indigo"
          label="PRIMARY KEY"
          onRemove={readOnly ? undefined : () => onToggleFlag('primaryKey')}
        />
      )}
      {column.nullable === false && (
        <Chip
          tone="del"
          label="NOT NULL"
          onRemove={readOnly ? undefined : () => onToggleFlag('nullable')}
        />
      )}
      {column.unique && (
        <Chip tone="add" label="UNIQUE" onRemove={readOnly ? undefined : () => onToggleFlag('unique')} />
      )}
      {column.engineConstraint && (
        <Chip
          tone="mod"
          label={column.engineConstraint}
          onRemove={readOnly ? undefined : () => onSetField('engineConstraint', '')}
        />
      )}
      {column.defaultValue && <Chip tone="neutral" label={`DEFAULT ${column.defaultValue}`} />}
    </div>
  );
}
