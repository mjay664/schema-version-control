import React, { useState } from 'react';
import { Columns3, Plus, Trash2 } from 'lucide-react';
import { Button, IconButton } from '../ui/Button';
import { Field, Input } from '../ui/Field';
import { EmptyState } from '../ui/Feedback';
import { DataTypePicker } from './DataTypePicker';
import { ConstraintCell } from './ConstraintCell';

export function ColumnsTab({ table, dataTypes, constraints, readOnly, ops, onError }) {
  const [draftName, setDraftName] = useState('');
  const [draftType, setDraftType] = useState('');

  const defaultType = dataTypes[0]?.name || 'VARCHAR(255)';
  const newColumnType = draftType || defaultType;

  const submitColumn = (e) => {
    e.preventDefault();
    const result = ops.addColumn(table.name, draftName, newColumnType);
    if (result?.ok) {
      setDraftName('');
      setDraftType('');
    } else if (result?.error) {
      onError(result.error);
    }
  };

  return (
    <>
      <div className="detail-scroll">
        {(table.columns || []).length === 0 ? (
          <EmptyState icon={Columns3} title="No columns">
            This table has no columns yet. Add one below to describe its shape.
          </EmptyState>
        ) : (
          <table className="dtable">
            <thead>
              <tr>
                <th className="cell-name">Column</th>
                <th className="cell-type">Type</th>
                <th className="cell-cons">Constraints</th>
                {!readOnly && <th className="td-actions">&nbsp;</th>}
              </tr>
            </thead>
            <tbody>
              {table.columns.map((col) => (
                <tr key={col.name}>
                  <td className="cell-name">
                    {readOnly ? (
                      <span className="mono">{col.name}</span>
                    ) : (
                      <Input
                        size="sm"
                        mono
                        value={col.name}
                        aria-label={`Rename column ${col.name}`}
                        onChange={(e) => ops.renameColumn(table.name, col.name, e.target.value)}
                      />
                    )}
                  </td>

                  <td className="cell-type">
                    {readOnly ? (
                      <code className="muted">{col.type}</code>
                    ) : (
                      <DataTypePicker
                        value={col.type}
                        dataTypes={dataTypes}
                        onChange={(type) => ops.retypeColumn(table.name, col.name, type)}
                      />
                    )}
                  </td>

                  <td className="cell-cons">
                    <ConstraintCell
                      column={col}
                      constraints={constraints}
                      readOnly={readOnly}
                      onToggleFlag={(flag) => ops.toggleFlag(table.name, col.name, flag)}
                      onSetField={(field, value) => ops.setColumnField(table.name, col.name, field, value)}
                    />
                  </td>

                  {!readOnly && (
                    <td className="td-actions">
                      <IconButton
                        icon={Trash2}
                        label={`Drop column ${col.name}`}
                        size="xs"
                        variant="danger"
                        onClick={() => ops.dropColumn(table.name, col.name)}
                      />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {!readOnly && (
        <form className="composer" onSubmit={submitColumn}>
          <Field label="New column">
            <Input
              mono
              value={draftName}
              onChange={(e) => setDraftName(e.target.value)}
              placeholder="email"
              required
            />
          </Field>
          <Field label="Type">
            <DataTypePicker value={newColumnType} dataTypes={dataTypes} onChange={setDraftType} />
          </Field>
          <Button type="submit" variant="primary" icon={Plus}>
            Add column
          </Button>
        </form>
      )}
    </>
  );
}
