import React from 'react';
import { AlertTriangle, Check, CheckCircle2, Circle, GitMerge, ShieldCheck } from 'lucide-react';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { Avatar } from '../ui/Feedback';
import { relativeTime, shortId } from '../../lib/format';

/**
 * An approval is bound to the exact branch heads it reviewed. If either head has
 * moved since, the approval is stale — kept for audit, but no longer sufficient.
 */
export function isApprovalValid(approval, mr) {
  return (
    approval.sourceHeadVersionId === mr.sourceHeadVersionId &&
    approval.targetHeadVersionId === mr.targetHeadVersionId &&
    approval.user?.id !== mr.createdBy?.id
  );
}

function Gate({ ok, children }) {
  return (
    <div className={`gate ${ok ? 'is-ok' : 'is-blocked'}`}>
      {ok ? <CheckCircle2 size={13} /> : <Circle size={13} />}
      <span>{children}</span>
    </div>
  );
}

export function ApprovalPanel({
  mr,
  currentUser,
  onApprove,
  onMerge,
  busy,
  hasConflicts = false,
  allConflictsResolved = false,
}) {
  const approvals = mr.approvals || [];
  const valid = approvals.filter((a) => isApprovalValid(a, mr));
  const isAuthor = currentUser?.id === mr.createdBy?.id;
  const merged = mr.status === 'MERGED';
  const alreadyApproved = valid.some((a) => a.user?.id === currentUser?.id);

  const approveBlockedReason = merged
    ? 'This merge request is already merged'
    : isAuthor
      ? 'You opened this merge request — a peer has to approve it'
      : alreadyApproved
        ? 'You have already approved the current branch heads'
        : mr.canApprove === false
          ? 'You cannot approve this merge request'
          : null;

  return (
    <div className="card approval-panel">
      <div className="row">
        <ShieldCheck size={15} className="muted" />
        <span className="field-label">Approvals</span>
        <span className="spacer" />
        {!merged && (
          <Badge tone={valid.length ? 'add' : 'neutral'}>{valid.length} valid / 1 required</Badge>
        )}
      </div>

      {approvals.length === 0 ? (
        <p className="field-hint">No approvals yet.</p>
      ) : (
        approvals.map((approval) => {
          const ok = isApprovalValid(approval, mr);
          return (
            <div key={approval.id} className={`approval-row ${ok || merged ? '' : 'is-stale'}`}>
              <Avatar name={approval.user?.displayName} size="sm" />
              <span className="approval-who grow">
                <span className="approval-name truncate">{approval.user?.displayName}</span>
                <span className="approval-when">
                  {relativeTime(approval.createdAt)} · reviewed {shortId(approval.sourceHeadVersionId)} →{' '}
                  {shortId(approval.targetHeadVersionId)}
                </span>
              </span>
              {merged ? null : ok ? (
                <Badge tone="add">
                  <Check size={10} /> Valid
                </Badge>
              ) : (
                <Badge tone="del" title="A branch head moved after this approval">
                  <AlertTriangle size={10} /> Stale
                </Badge>
              )}
            </div>
          );
        })
      )}

      {!merged && (
        <div className="col" style={{ gap: 'var(--s-1)' }}>
          <Gate ok={valid.length > 0}>At least one valid peer approval</Gate>
          <Gate ok={valid.length > 0}>Approval matches the current source and target heads</Gate>
          <Gate ok>Not already merged</Gate>
        </div>
      )}

      {mr.status === 'STALE' && (
        <div className="alert alert-warn">
          <AlertTriangle size={14} />
          <span>
            A branch head moved after approval. Re-review the current diff and approve again before
            merging.
          </span>
        </div>
      )}

      {!merged && (
        <div className="col" style={{ gap: 'var(--s-2)' }}>
          <Button
            variant="accent"
            icon={Check}
            block
            loading={busy === 'approve'}
            disabled={Boolean(approveBlockedReason) || Boolean(busy)}
            title={approveBlockedReason || 'Approve the current source and target heads'}
            onClick={onApprove}
          >
            Approve
          </Button>
          {approveBlockedReason && <span className="field-hint">{approveBlockedReason}</span>}

          <Button
            variant="success"
            icon={GitMerge}
            block
            loading={busy === 'merge'}
            disabled={!mr.canMerge || Boolean(busy) || (hasConflicts && !allConflictsResolved)}
            title={
              !mr.canMerge
                ? 'A valid approval is required first'
                : hasConflicts && !allConflictsResolved
                  ? 'Choose a side for every conflict first'
                  : hasConflicts
                    ? 'Merge, applying your conflict decisions'
                    : 'Run the three-way merge'
            }
            onClick={onMerge}
          >
            {hasConflicts
              ? `Merge with ${allConflictsResolved ? 'these choices' : 'resolutions'}`
              : `Merge into ${mr.targetBranch?.name}`}
          </Button>
          {hasConflicts && !allConflictsResolved && (
            <span className="field-hint">
              Decide every conflict in the report beside the diff to enable this.
            </span>
          )}
        </div>
      )}

      {merged && (
        <div className="alert alert-success">
          <CheckCircle2 size={14} />
          <span>
            Merged by <strong>{mr.mergedBy?.displayName || 'unknown'}</strong> {relativeTime(mr.mergedAt)}.
          </span>
        </div>
      )}
    </div>
  );
}
