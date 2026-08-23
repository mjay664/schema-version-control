import React, { useMemo, useState } from 'react';
import { Activity, ChevronDown, ChevronUp, Clock, RefreshCw } from 'lucide-react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { Badge } from '../ui/Badge';
import { Button, IconButton } from '../ui/Button';
import { Avatar } from '../ui/Feedback';
import { formatTime, humanizeAction, prettyJson, relativeTime } from '../../lib/format';

/** Map an audit action onto a badge tone. */
function toneFor(action = '') {
  if (/MERGED|COMPLETED|APPROVED|REGISTERED/.test(action)) return 'add';
  if (/DROPPED|CONFLICT|INVALIDATED/.test(action)) return 'del';
  if (/MODIFIED|UPDATED|STARTED/.test(action)) return 'mod';
  if (/CREATED/.test(action)) return 'indigo';
  return 'neutral';
}

/**
 * Audit events relevant to the checked-out branch, plus repository-wide events
 * that have no branch of their own.
 */
function scopeToBranch(events, branchName) {
  if (!branchName) return events;
  return events.filter((evt) => {
    const haystack = `${evt.metadata || ''} ${evt.entityId || ''}`;
    return (
      haystack.includes(branchName) ||
      /USER_REGISTERED|REPOSITORY_CREATED/.test(evt.actionType || '')
    );
  });
}

export function ActivityDrawer() {
  const { auditEvents, currentBranch, refreshAudit } = useWorkspace();
  const [open, setOpen] = useState(false);
  const [expandedId, setExpandedId] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  const events = useMemo(
    () => scopeToBranch(auditEvents, currentBranch?.name),
    [auditEvents, currentBranch?.name]
  );
  const latest = events[0];

  const handleRefresh = async (e) => {
    e.stopPropagation();
    setRefreshing(true);
    try {
      await refreshAudit();
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <section className="drawer">
      <div
        className="drawer-bar"
        onClick={() => setOpen((v) => !v)}
        role="button"
        tabIndex={0}
        aria-expanded={open}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), setOpen((v) => !v))}
      >
        <span className="drawer-label">
          <Activity size={14} /> Activity &amp; audit
        </span>
        <Badge tone="neutral">
          {events.length} {currentBranch ? `on ${currentBranch.name}` : 'events'}
        </Badge>

        {latest && !open && (
          <span className="drawer-peek truncate">
            <Badge tone={toneFor(latest.actionType)}>{humanizeAction(latest.actionType)}</Badge>
            <span className="truncate">
              by <strong>{latest.userDisplayName}</strong> · {relativeTime(latest.createdAt)}
            </span>
          </span>
        )}

        <span className="spacer" />
        <Button size="xs" icon={RefreshCw} onClick={handleRefresh} loading={refreshing}>
          Refresh
        </Button>
        <IconButton
          icon={open ? ChevronDown : ChevronUp}
          label={open ? 'Collapse' : 'Expand'}
          size="xs"
          onClick={(e) => {
            e.stopPropagation();
            setOpen((v) => !v);
          }}
        />
      </div>

      {open && (
        <div className="drawer-body">
          {events.length === 0 ? (
            <p className="list-note">
              No audit events recorded for {currentBranch?.name || 'this workspace'} yet.
            </p>
          ) : (
            events.map((evt) => {
              const expanded = expandedId === evt.id;
              const hasMeta = evt.metadata && evt.metadata !== '{}';
              return (
                <article key={evt.id} className="event">
                  <div className="event-top">
                    <Badge tone={toneFor(evt.actionType)}>{humanizeAction(evt.actionType)}</Badge>
                    <span className="event-time">
                      <Clock size={10} /> {formatTime(evt.createdAt)}
                    </span>
                  </div>

                  <div className="event-actor">
                    <Avatar name={evt.userDisplayName} size="sm" />
                    <span className="truncate">{evt.userDisplayName || 'Unknown user'}</span>
                  </div>

                  {evt.entityId && (
                    <div className="event-target" title={evt.entityId}>
                      {evt.entityType}: {evt.entityId}
                    </div>
                  )}

                  {hasMeta && (
                    <>
                      <button
                        type="button"
                        className="event-toggle"
                        onClick={() => setExpandedId(expanded ? null : evt.id)}
                      >
                        {expanded ? 'Hide details' : 'View details'}
                      </button>
                      {expanded && <pre className="event-meta">{prettyJson(evt.metadata)}</pre>}
                    </>
                  )}
                </article>
              );
            })
          )}
        </div>
      )}
    </section>
  );
}
