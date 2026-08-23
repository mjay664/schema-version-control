import React, { useEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';

/**
 * Checkbox dropdown for picking several options at once.
 * options: [{ value, label, hint? }]
 */
export function MultiSelect({ options, value = [], onChange, placeholder = 'Select…', placement = 'top' }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (e) => {
      if (!rootRef.current?.contains(e.target)) setOpen(false);
    };
    const onKeyDown = (e) => e.key === 'Escape' && setOpen(false);

    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const toggle = (optionValue) =>
    onChange(
      value.includes(optionValue) ? value.filter((v) => v !== optionValue) : [...value, optionValue]
    );

  return (
    <div className="ms" ref={rootRef}>
      <button
        type="button"
        className={`ms-trigger ${value.length ? '' : 'is-empty'}`.trim()}
        aria-expanded={open}
        aria-haspopup="listbox"
        onClick={() => setOpen((o) => !o)}
      >
        <span className="truncate">
          {value.length === 0 ? placeholder : `${value.length} selected · ${value.join(', ')}`}
        </span>
        <ChevronDown size={14} style={{ transform: open ? 'rotate(180deg)' : undefined, transition: 'transform 120ms' }} />
      </button>

      {open && (
        <div className="ms-menu" data-placement={placement} role="listbox">
          {options.length === 0 ? (
            <div className="list-note">Nothing to choose from</div>
          ) : (
            options.map((option) => {
              const checked = value.includes(option.value);
              return (
                <label key={option.value} className="ms-option" data-checked={checked}>
                  <input type="checkbox" checked={checked} onChange={() => toggle(option.value)} />
                  <span className="mono">{option.label}</span>
                  {option.hint && <code>{option.hint}</code>}
                </label>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
