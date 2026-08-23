import React from 'react';

/**
 * Segmented control.
 * items: [{ value, label, icon?, count? }]
 */
export function Segmented({ items, value, onChange, className = '' }) {
  return (
    <div className={`segmented ${className}`.trim()} role="tablist">
      {items.map((item) => {
        const Icon = item.icon;
        const selected = item.value === value;
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={selected}
            className="segmented-item"
            onClick={() => onChange(item.value)}
          >
            {Icon && <Icon size={13} />}
            {item.label}
            {item.count !== undefined && <span className="segmented-count">{item.count}</span>}
          </button>
        );
      })}
    </div>
  );
}
