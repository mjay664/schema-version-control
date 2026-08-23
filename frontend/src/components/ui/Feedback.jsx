import React from 'react';
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react';
import { initials } from '../../lib/format';

const ALERT_ICONS = { success: CheckCircle2, error: AlertTriangle, warn: AlertTriangle, info: Info };

export function Alert({ tone = 'info', children, className = '' }) {
  const Icon = ALERT_ICONS[tone] ?? Info;
  return (
    <div className={`alert alert-${tone} ${className}`.trim()} role={tone === 'error' ? 'alert' : undefined}>
      <Icon size={14} />
      <span>{children}</span>
    </div>
  );
}

export function Banner({ tone = 'info', icon: Icon, children, action }) {
  return (
    <div className={`banner banner-${tone}`}>
      {Icon && <Icon size={14} />}
      <span className="grow">{children}</span>
      {action}
    </div>
  );
}

export function EmptyState({ icon: Icon, title, children, action }) {
  return (
    <div className="empty">
      {Icon && (
        <span className="empty-icon">
          <Icon size={20} />
        </span>
      )}
      {title && <div className="empty-title">{title}</div>}
      {children && <p className="empty-body">{children}</p>}
      {action}
    </div>
  );
}

export function Spinner({ label }) {
  return (
    <span className="row">
      <span className="spinner" />
      {label && <span className="dim">{label}</span>}
    </span>
  );
}

export function Avatar({ name, size = 'md', title }) {
  const cls = size === 'sm' ? 'avatar avatar-sm' : size === 'lg' ? 'avatar avatar-lg' : 'avatar';
  return (
    <span className={cls} title={title ?? name}>
      {initials(name)}
    </span>
  );
}
