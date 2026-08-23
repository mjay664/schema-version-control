import React, { useState } from 'react';
import { Plus, Trash2, Zap } from 'lucide-react';
import { Button, IconButton } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { Checkbox, Field, Input } from '../ui/Field';
import { EmptyState } from '../ui/Feedback';
import { MultiSelect } from '../ui/MultiSelect';

export function IndexesTab({ table, readOnly, ops, onError }) {
  const [name, setName] = useState('');
  const [columns, setColumns] = useState([]);
  const [unique, setUnique] = useState(false);

  const indexes = table.indexes || [];

  const submit = (e) => {
    e.preventDefault();
    const result = ops.addIndex(table.name, name, columns, unique);
    if (result?.ok) {
      setName('');
      setColumns([]);
      setUnique(false);
    } else if (result?.error) {
      onError(result.error);
    }
  };

  return (
    <>
      <div className="detail-scroll" style={{ padding: 'var(--s-3)' }}>
        {indexes.length === 0 ? (
          <EmptyState icon={Zap} title="No indexes">
            Indexes on <code>{table.name}</code> will appear here.
          </EmptyState>
        ) : (
          <div className="col" style={{ gap: 'var(--s-2)' }}>
            {indexes.map((idx) => (
              <div key={idx.name} className="index-row">
                <Zap size={14} />
                <div className="col grow">
                  <span className="index-name">
                    {idx.name}
                    {idx.unique && (
                      <Badge tone="mod" className="nowrap" style={{ marginLeft: 'var(--s-2)' }}>
                        UNIQUE
                      </Badge>
                    )}
                  </span>
                  <span className="index-cols">({(idx.columns || []).join(', ')})</span>
                </div>
                {!readOnly && (
                  <IconButton
                    icon={Trash2}
                    label={`Drop index ${idx.name}`}
                    size="xs"
                    variant="danger"
                    onClick={() => ops.dropIndex(table.name, idx.name)}
                  />
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {!readOnly && (
        <form className="composer" onSubmit={submit} style={{ alignItems: 'flex-end' }}>
          <Field label="New index">
            <Input
              mono
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={`idx_${table.name}_column`}
              required
            />
          </Field>

          <Field label="Columns" className="grow">
            <MultiSelect
              value={columns}
              onChange={setColumns}
              placeholder="Choose columns…"
              options={(table.columns || []).map((c) => ({
                value: c.name,
                label: c.name,
                hint: c.type,
              }))}
            />
          </Field>

          <Checkbox
            label="Unique"
            checked={unique}
            onChange={(e) => setUnique(e.target.checked)}
          />

          <Button type="submit" variant="primary" icon={Plus}>
            Add index
          </Button>
        </form>
      )}
    </>
  );
}
