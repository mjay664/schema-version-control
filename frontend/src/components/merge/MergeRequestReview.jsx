import React, { useMemo, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, ExternalLink, GitPullRequest } from 'lucide-react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { useToast } from '../../state/ToastProvider';
import { Button } from '../ui/Button';
import { StatusBadge } from '../ui/Badge';
import { Checkbox } from '../ui/Field';
import { Avatar, EmptyState, Spinner } from '../ui/Feedback';
import { SchemaDiff, DiffLegend } from './SchemaDiff';
import { ConflictPanel } from './ConflictPanel';
import { ApprovalPanel } from './ApprovalPanel';
import { relativeTime, shortId } from '../../lib/format';
import { tablesForVersion } from '../../lib/schema';

export function MergeRequestReview({ mergeRequestId }) {
  const {
    mergeRequests,
    versions,
    versionsReady,
    currentUser,
    closeMergeRequest,
    goToBranch,
    approveMergeRequest,
    mergeMergeRequest,
  } = useWorkspace();
  const toast = useToast();

  const [busy, setBusy] = useState(null);
  const [showUnchanged, setShowUnchanged] = useState(false);
  const [conflicts, setConflicts] = useState([]);
  const [resolutions, setResolutions] = useState({});

  const mr = mergeRequests.find((m) => m.id === mergeRequestId);

  // Diff the branch heads the merge request actually points at — not whatever
  // branch happens to be checked out in the editor.
  const targetTables = useMemo(
    () => tablesForVersion(versions, mr?.targetHeadVersionId),
    [versions, mr?.targetHeadVersionId]
  );
  const sourceTables = useMemo(
    () => tablesForVersion(versions, mr?.sourceHeadVersionId),
    [versions, mr?.sourceHeadVersionId]
  );
  // The backend reports where the branches forked, which lets the diff say what
  // each side changed rather than only how they differ from each other.
  const ancestorTables = useMemo(
    () => (mr?.diff?.ancestorVersionId ? tablesForVersion(versions, mr.diff.ancestorVersionId) : null),
    [versions, mr?.diff?.ancestorVersionId]
  );

  // Conflicts are computed against one exact pair of heads. Once either moves,
  // the report is describing a merge that no longer exists.
  const headPair = `${mr?.sourceHeadVersionId}:${mr?.targetHeadVersionId}`;
  const reportedFor = useRef(headPair);
  if (reportedFor.current !== headPair) {
    reportedFor.current = headPair;
    if (conflicts.length) setConflicts([]);
    if (Object.keys(resolutions).length) setResolutions({});
  }

  if (!mr) {
    return (
      <div className="page">
        <div className="page-head">
          <Button icon={ArrowLeft} onClick={closeMergeRequest}>
            Back
          </Button>
        </div>
        <EmptyState icon={GitPullRequest} title="Merge request not found">
          It may have been merged or removed. Go back to the list to pick another.
        </EmptyState>
      </div>
    );
  }

  const run = async (kind, action) => {
    setBusy(kind);
    try {
      return await action();
    } catch (err) {
      toast.error(err.message);
      return null;
    } finally {
      setBusy(null);
    }
  };

  const handleMerge = async () => {
    const result = await run('merge', () => mergeMergeRequest(mr.id, resolutions));
    const remaining = result?.hasConflicts ? (result.conflicts ?? []) : [];
    setConflicts(remaining);
    // Drop decisions for paths that are no longer in conflict, so a stale
    // choice can never be replayed into a later merge.
    setResolutions((prev) =>
      Object.fromEntries(Object.entries(prev).filter(([key]) => remaining.some((c) => c.key === key)))
    );
  };

  const handleApprove = async () => {
    await run('approve', () => approveMergeRequest(mr.id));
  };

  return (
    <div className="page">
      <div className="review-head">
        <div className="review-title-row">
          <Button icon={ArrowLeft} onClick={closeMergeRequest}>
            All merge requests
          </Button>

          <span className="review-route">
            {mr.sourceBranch?.name}
            <ArrowRight size={17} />
            {mr.targetBranch?.name}
          </span>
          <StatusBadge status={mr.status} />

          <span className="spacer" />

          <Button
            icon={ExternalLink}
            onClick={() => goToBranch(mr.sourceBranch?.name)}
            title={`Check out ${mr.sourceBranch?.name} in the schema editor`}
          >
            Open {mr.sourceBranch?.name}
          </Button>
        </div>

        <div className="review-facts">
          <span className="row">
            <Avatar name={mr.createdBy?.displayName} size="sm" />
            opened by <strong>{mr.createdBy?.displayName}</strong> {relativeTime(mr.createdAt)}
          </span>
          <span>
            reviewer: <strong>{mr.requestedApprover?.displayName || 'any peer'}</strong>
          </span>
          <span className="mono">#{shortId(mr.id)}</span>
          <span className="mono">
            heads {shortId(mr.sourceHeadVersionId)} → {shortId(mr.targetHeadVersionId)}
          </span>
        </div>
      </div>

      <div className="diff-toolbar">
        <span className="eyebrow">Side-by-side schema diff</span>
        <span className="spacer" />
        <Checkbox
          label="Show unchanged tables"
          checked={showUnchanged}
          onChange={(e) => setShowUnchanged(e.target.checked)}
        />
        <DiffLegend />
      </div>

      <div className="page-scroll">
        <div className="review-cols">
          <div className="col grow" style={{ gap: 0 }}>
            <ConflictPanel
              conflicts={conflicts}
              resolutions={resolutions}
              onResolve={(key, side) => setResolutions((prev) => ({ ...prev, [key]: side }))}
              targetBranchName={mr.targetBranch?.name}
              sourceBranchName={mr.sourceBranch?.name}
            />

            {versionsReady ? (
              <SchemaDiff
                targetTables={targetTables}
                sourceTables={sourceTables}
                ancestorTables={ancestorTables}
                targetBranchName={mr.targetBranch?.name}
                sourceBranchName={mr.sourceBranch?.name}
                showUnchanged={showUnchanged}
              />
            ) : (
              <div className="card card-pad">
                <Spinner label="Loading schema history…" />
              </div>
            )}
          </div>

          <ApprovalPanel
            mr={mr}
            currentUser={currentUser}
            busy={busy}
            hasConflicts={conflicts.length > 0}
            allConflictsResolved={conflicts.length > 0 && conflicts.every((c) => resolutions[c.key])}
            onApprove={handleApprove}
            onMerge={handleMerge}
          />
        </div>
      </div>
    </div>
  );
}
