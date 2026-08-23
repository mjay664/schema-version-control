import React, { useId } from 'react';
import { Search, X } from 'lucide-react';

export function Field({ label, hint, error, children, className = '' }) {
  const id = useId();
  const control = React.isValidElement(children)
    ? React.cloneElement(children, { id: children.props.id ?? id })
    : children;

  return (
    <div className={`field ${className}`.trim()}>
      {label && (
        <label className="field-label" htmlFor={control?.props?.id ?? id}>
          {label}
        </label>
      )}
      {control}
      {error ? <span className="field-error">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </div>
  );
}

export const Input = React.forwardRef(function Input({ size, mono, className = '', ...rest }, ref) {
  const classes = ['input', size === 'sm' && 'input-sm', mono && 'input-mono', className]
    .filter(Boolean)
    .join(' ');
  return <input ref={ref} className={classes} {...rest} />;
});

export const Select = React.forwardRef(function Select({ size, className = '', children, ...rest }, ref) {
  const classes = ['select', size === 'sm' && 'select-sm', className].filter(Boolean).join(' ');
  return (
    <select ref={ref} className={classes} {...rest}>
      {children}
    </select>
  );
});

export const Textarea = React.forwardRef(function Textarea({ className = '', ...rest }, ref) {
  return <textarea ref={ref} className={`textarea ${className}`.trim()} {...rest} />;
});

export function Checkbox({ label, ...rest }) {
  return (
    <label className="checkbox-label">
      <input type="checkbox" {...rest} />
      {label}
    </label>
  );
}

export function SearchInput({ value, onChange, placeholder = 'Search…', size = 'sm', className = '' }) {
  return (
    <div className={`search ${className}`.trim()}>
      <Search size={13} />
      <Input
        type="search"
        size={size}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
      {value && (
        <button type="button" className="search-clear" onClick={() => onChange('')} aria-label="Clear search">
          <X size={12} />
        </button>
      )}
    </div>
  );
}
