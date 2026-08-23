import React from 'react';

const VARIANTS = {
  default: '',
  primary: 'btn-primary',
  accent: 'btn-accent',
  success: 'btn-success',
  danger: 'btn-danger',
  ghost: 'btn-ghost',
};

const SIZES = { xs: 'btn-xs', sm: '', lg: 'btn-lg' };

export function Button({
  variant = 'default',
  size = 'sm',
  icon: Icon,
  iconSize,
  block = false,
  loading = false,
  className = '',
  children,
  disabled,
  ...rest
}) {
  const glyph = size === 'xs' ? 13 : size === 'lg' ? 17 : 14;
  const classes = ['btn', VARIANTS[variant], SIZES[size], block && 'btn-block', className]
    .filter(Boolean)
    .join(' ');

  return (
    <button className={classes} disabled={disabled || loading} {...rest}>
      {loading ? <span className="spinner" /> : Icon && <Icon size={iconSize ?? glyph} />}
      {children}
    </button>
  );
}

/** Square button holding only an icon. `label` becomes the accessible name and tooltip. */
export function IconButton({ icon: Icon, label, size = 'sm', variant = 'ghost', className = '', ...rest }) {
  const glyph = size === 'xs' ? 13 : size === 'lg' ? 17 : 14;
  const classes = ['btn', 'btn-icon', VARIANTS[variant], SIZES[size], className].filter(Boolean).join(' ');

  return (
    <button className={classes} title={label} aria-label={label} {...rest}>
      <Icon size={glyph} />
    </button>
  );
}
