import React, { useEffect, useState } from 'react';
import { Plus, Table2 } from 'lucide-react';
import { Button } from '../ui/Button';
import { SearchInput } from '../ui/Field';

const CHUNK = 20;

/**
 * Master list of tables. Renders 20 at a time and grows on scroll so large
 * schemas stay responsive.
 */
export function TableList({ tables, selectedName, onSelect, readOnly, onAddTable }) {
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(CHUNK);

  const matches = tables.filter((t) => t.name.toLowerCase().includes(query.toLowerCase()));
  const visible = matches.slice(0, limit);

  useEffect(() => setLimit(CHUNK), [query, tables.length]);

  const handleScroll = (e) => {
    const el = e.currentTarget;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24 && limit < matches.length) {
      setLimit((n) => n + CHUNK);
    }
  };

  return (
    <div className="tablelist">
      <div className="tablelist-head">
        <span className="eyebrow">Tables</span>
        <span className="segmented-count">{tables.length}</span>
        <span className="spacer" />
        {!readOnly && (
          <Button size="xs" variant="primary" icon={Plus} onClick={onAddTable}>
            Table
          </Button>
        )}
      </div>

      <div className="tablelist-tools">
        <SearchInput value={query} onChange={setQuery} placeholder="Filter tables…" />
      </div>

      <div className="tablelist-body" onScroll={handleScroll}>
        {visible.length === 0 ? (
          <p className="list-note">{query ? 'No matching tables' : 'No tables in this schema'}</p>
        ) : (
          visible.map((table) => (
            <button
              key={table.name}
              type="button"
              className="tnode"
              aria-current={table.name === selectedName}
              onClick={() => onSelect(table.name)}
            >
              <Table2 size={13} />
              <span className="tnode-name">{table.name}</span>
              <span className="tnode-count">{table.columns?.length ?? 0}</span>
            </button>
          ))
        )}
        {limit < matches.length && (
          <p className="list-note">
            Showing {visible.length} of {matches.length} — scroll for more
          </p>
        )}
      </div>
    </div>
  );
}
