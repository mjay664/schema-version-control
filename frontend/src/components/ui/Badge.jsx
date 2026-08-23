import React from 'react';

export function Badge({ tone = 'neutral', dot = false, className = '', children, ...rest }) {
  return (
    <span className={`badge badge-${tone} ${className}`.trim()} {...rest}>
      {dot && <span className="badge-dot" />}
      {children}
    </span>
  );
}

/** Merge-request status → tone + copy. STALE is computed server-side when a
    branch head moves after approval, so it needs first-class treatment. */
export const MR_STATUS = {
  OPEN:     { tone: 'mod',     label: 'Open' },
  APPROVED: { tone: 'indigo',  label: 'Approved' },
  STALE:    { tone: 'del',     label: 'Approval stale' },
  MERGED:   { tone: 'add',     label: 'Merged' },
  CLOSED:   { tone: 'neutral', label: 'Closed' },
};

export function StatusBadge({ status }) {
  const { tone, label } = MR_STATUS[status] ?? { tone: 'neutral', label: status || 'Unknown' };
  return (
    <Badge tone={tone} dot>
      {label}
    </Badge>
  );
}
