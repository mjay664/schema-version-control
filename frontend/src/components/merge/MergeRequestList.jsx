import React, { useMemo, useState } from 'react';
import { ArrowRight, ChevronRight, GitPullRequest } from 'lucide-react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { StatusBadge } from '../ui/Badge';
import { SearchInput } from '../ui/Field';
import { EmptyState } from '../ui/Feedback';
import { Segmented } from '../ui/Segmented';
import { Avatar } from '../ui/Feedback';
import { relativeTime, shortId } from '../../lib/format';
import { diffStatsFromDto } from '../../lib/schema';

const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'open', label: 'Open', statuses: ['OPEN'] },
  { value: 'approved', label: 'Approved', statuses: ['APPROVED'] },
  { value: 'stale', label: 'Stale', statuses: ['STALE'] },
  { value: 'merged', label: 'Merged', statuses: ['MERGED'] },
];

function DiffStat({ diff }) {
  const stats = diffStatsFromDto(diff);
  if (!stats) return null;
  const { added, removed, modified } = stats;
  if (!added && !removed && !modified) return <span className="dim">no table changes</span>;

  return (
    <span className="diffstat">
      {added > 0 && <span className="add">+{added}</span>}
      {removed > 0 && <span className="del">−{removed}</span>}
      {modified > 0 && <span className="mod">~{modified}</span>}
    </span>
  );
}

export function MergeRequestList() {
  const { mergeRequests, openMergeRequest } = useWorkspace();
  const [filter, setFilter] = useState('all');
  const [query, setQuery] = useState('');

  const counts = useMemo(() => {
    const by = (statuses) =>
      statuses ? mergeRequests.filter((mr) => statuses.includes(mr.status)).length : mergeRequests.length;
    return Object.fromEntries(FILTERS.map((f) => [f.value, by(f.statuses)]));
  }, [mergeRequests]);

  const visible = useMemo(() => {
    const statuses = FILTERS.find((f) => f.value === filter)?.statuses;
    const needle = query.trim().toLowerCase();

    return mergeRequests.filter((mr) => {
      if (statuses && !statuses.includes(mr.status)) return false;
      if (!needle) return true;
      return [
        mr.sourceBranch?.name,
        mr.targetBranch?.name,
        mr.createdBy?.displayName,
        mr.requestedApprover?.displayName,
      ]
        .filter(Boolean)
        .some((field) => field.toLowerCase().includes(needle));
    });
  }, [filter, mergeRequests, query]);

  return (
    <div className="page">
      <div className="page-head">
        <span className="page-title">
          <GitPullRequest size={17} className="muted" />
          Merge requests
        </span>
        <span className="spacer" />
        <SearchInput
          value={query}
          onChange={setQuery}
          placeholder="Filter by branch or author…"
          className="grow"
        />
        <Segmented
          value={filter}
          onChange={setFilter}
          items={FILTERS.map((f) => ({ value: f.value, label: f.label, count: counts[f.value] }))}
        />
      </div>

      <div className="page-scroll">
        {visible.length === 0 ? (
          <EmptyState icon={GitPullRequest} title="No merge requests here">
            {mergeRequests.length === 0
              ? 'Commit on a feature branch, then open a merge request to get it reviewed into main.'
              : 'Nothing matches the current filter.'}
          </EmptyState>
        ) : (
          <div className="mr-list">
            {visible.map((mr) => (
              <button
                key={mr.id}
                type="button"
                className="mr-card"
                onClick={() => openMergeRequest(mr.id)}
              >
                <span className="mr-glyph" data-status={mr.status}>
                  <GitPullRequest size={16} />
                </span>

                <span className="mr-main">
                  <span className="mr-route">
                    {mr.sourceBranch?.name}
                    <ArrowRight size={13} />
                    {mr.targetBranch?.name}
                    <StatusBadge status={mr.status} />
                  </span>
                  <span className="mr-sub">
                    <span className="row">
                      <Avatar name={mr.createdBy?.displayName} size="sm" />
                      <strong>{mr.createdBy?.displayName}</strong>
                    </span>
                    <span>
                      reviewer: <strong>{mr.requestedApprover?.displayName || 'any peer'}</strong>
                    </span>
                    <DiffStat diff={mr.diff} />
                    <span>#{shortId(mr.id)}</span>
                    <span>{relativeTime(mr.createdAt)}</span>
                  </span>
                </span>

                <ChevronRight size={16} className="dim" />
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
