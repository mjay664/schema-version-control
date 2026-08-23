import React, { useEffect, useState } from 'react';
import { Columns3, Table2, Trash2, Zap } from 'lucide-react';
import { Button } from '../ui/Button';
import { Segmented } from '../ui/Segmented';
import { EmptyState } from '../ui/Feedback';
import { ColumnsTab } from './ColumnsTab';
import { IndexesTab } from './IndexesTab';

export function TableDetail({ table, dataTypes, constraints, readOnly, ops, onError }) {
  const [tab, setTab] = useState('columns');

  useEffect(() => setTab('columns'), [table?.name]);

  if (!table) {
    return (
      <div className="detail">
        <EmptyState icon={Table2} title="No table selected">
          {readOnly
            ? 'Pick a table from the list to inspect its columns and indexes.'
            : 'Pick a table from the list, or create one to start describing your schema.'}
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="detail">
      <div className="detail-head">
        <span className="detail-title">
          <Table2 size={17} className="muted" />
          {table.name}
        </span>

        <Segmented
          value={tab}
          onChange={setTab}
          items={[
            { value: 'columns', label: 'Columns', icon: Columns3, count: table.columns?.length ?? 0 },
            { value: 'indexes', label: 'Indexes', icon: Zap, count: table.indexes?.length ?? 0 },
          ]}
        />

        <span className="spacer" />

        {!readOnly && (
          <Button variant="danger" icon={Trash2} onClick={() => ops.dropTable(table.name)}>
            Drop table
          </Button>
        )}
      </div>

      <div className="detail-body">
        {tab === 'columns' ? (
          <ColumnsTab
            table={table}
            dataTypes={dataTypes}
            constraints={constraints}
            readOnly={readOnly}
            ops={ops}
            onError={onError}
          />
        ) : (
          <IndexesTab table={table} readOnly={readOnly} ops={ops} onError={onError} />
        )}
      </div>
    </div>
  );
}
