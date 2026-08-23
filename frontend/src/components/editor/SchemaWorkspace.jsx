import React, { useState } from 'react';
import {
  ExternalLink,
  GitBranch,
  GitCommit,
  GitPullRequest,
  Lock,
  Undo2,
} from 'lucide-react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { useToast } from '../../state/ToastProvider';
import { Button } from '../ui/Button';
import { StatusBadge } from '../ui/Badge';
import { Banner, EmptyState } from '../ui/Feedback';
import { useSchemaBuffer } from './useSchemaBuffer';
import { TableList } from './TableList';
import { TableDetail } from './TableDetail';
import {
  AddTableModal,
  CommitModal,
  CreateBranchModal,
  SubmitMergeRequestModal,
} from './modals';
import { diffSchemas } from '../../lib/schema';
import { pluralize, shortId } from '../../lib/format';

export function SchemaWorkspace() {
  const {
    currentRepo,
    currentBranch,
    branches,
    versions,
    versionsReady,
    dataTypes,
    constraints,
    users,
    currentUser,
    isProtectedBranch,
    activeMrForBranch,
    commitSchema,
    createBranch,
    createMergeRequest,
    openMergeRequest,
  } = useWorkspace();
  const toast = useToast();

  const [modal, setModal] = useState(null); // 'table' | 'commit' | 'branch' | 'mr'

  const buffer = useSchemaBuffer({
    repoId: currentRepo?.id,
    branch: currentBranch,
    versions,
    versionsReady,
    readOnly: isProtectedBranch,
  });

  if (!currentRepo || !currentBranch) {
    return (
      <EmptyState icon={GitBranch} title="Nothing checked out">
        Pick a repository and a branch in the sidebar to start editing a schema.
      </EmptyState>
    );
  }

  const { tables, committedTables, selectedTable, selectedTableName, setSelectedTableName, isDirty, currentJson, ops } =
    buffer;

  const pending = diffSchemas(committedTables, tables).stats;
  const pendingSummary = [
    pending.added && `${pluralize(pending.added, 'table')} added`,
    pending.removed && `${pluralize(pending.removed, 'table')} dropped`,
    pending.modified && `${pluralize(pending.modified, 'table')} modified`,
  ]
    .filter(Boolean)
    .join(', ');

  const closeModal = () => setModal(null);

  const handleAddTable = (name) =>
    ops.addTable(name, {
      idType: dataTypes.find((d) => d.name.startsWith('UUID'))?.name || 'UUID',
      timeType: dataTypes.find((d) => d.name.startsWith('TIMESTAMP'))?.name || 'TIMESTAMP',
    });

  return (
    <>
      {/* ------------------------------------------------------- toolbar */}
      <div className="toolbar">
        <span className={`branch-chip ${isProtectedBranch ? 'is-protected' : ''}`.trim()}>
          {isProtectedBranch ? <Lock size={12} /> : <GitBranch size={12} />}
          {currentBranch.name}
        </span>

        <Button icon={GitBranch} onClick={() => setModal('branch')}>
          Branch from this
        </Button>

        <span className="spacer" />

        {isDirty && (
          <>
            <span className="row dim" style={{ fontSize: 'var(--fs-xs)' }}>
              <span className="dirty-dot" />
              {pendingSummary || 'Uncommitted edits'}
            </span>
            <Button icon={Undo2} onClick={ops.revert} title="Discard uncommitted edits">
              Revert
            </Button>
          </>
        )}

        {!isProtectedBranch && (
          <>
            <Button
              variant="primary"
              icon={GitCommit}
              disabled={!isDirty}
              title={isDirty ? 'Commit this schema version' : 'No uncommitted edits'}
              onClick={() => setModal('commit')}
            >
              Commit
            </Button>

            {activeMrForBranch ? (
              <Button
                variant="accent"
                icon={GitPullRequest}
                onClick={() => openMergeRequest(activeMrForBranch.id)}
              >
                View MR {shortId(activeMrForBranch.id)}
              </Button>
            ) : (
              <Button
                variant="accent"
                icon={GitPullRequest}
                disabled={isDirty}
                title={isDirty ? 'Commit your edits before opening a merge request' : 'Open a merge request'}
                onClick={() => setModal('mr')}
              >
                Merge request
              </Button>
            )}
          </>
        )}
      </div>

      {/* ------------------------------------------------------- banners */}
      {isProtectedBranch && (
        <Banner tone="warn" icon={Lock}>
          <strong>main</strong> is protected — it only moves through an approved merge request.
          Branch off to make schema edits.
        </Banner>
      )}

      {activeMrForBranch && (
        <Banner
          tone="info"
          icon={GitPullRequest}
          action={
            <Button size="xs" icon={ExternalLink} onClick={() => openMergeRequest(activeMrForBranch.id)}>
              Review
            </Button>
          }
        >
          <span className="row">
            Merge request open: <strong>{activeMrForBranch.sourceBranch?.name}</strong> →{' '}
            <strong>{activeMrForBranch.targetBranch?.name}</strong>
            <StatusBadge status={activeMrForBranch.status} />
          </span>
        </Banner>
      )}

      {/* -------------------------------------------------- master/detail */}
      <div className="editor">
        <TableList
          tables={tables}
          selectedName={selectedTableName}
          onSelect={setSelectedTableName}
          readOnly={isProtectedBranch}
          onAddTable={() => setModal('table')}
        />
        <TableDetail
          table={selectedTable}
          dataTypes={dataTypes}
          constraints={constraints}
          readOnly={isProtectedBranch}
          ops={ops}
          onError={toast.error}
        />
      </div>

      {/* -------------------------------------------------------- modals */}
      {modal === 'table' && <AddTableModal onClose={closeModal} onCreate={handleAddTable} />}

      {modal === 'commit' && (
        <CommitModal
          branchName={currentBranch.name}
          changeSummary={pendingSummary || 'no table-level changes'}
          onClose={closeModal}
          onCommit={(message) => commitSchema(currentJson, message)}
        />
      )}

      {modal === 'branch' && (
        <CreateBranchModal
          baseBranchName={currentBranch.name}
          onClose={closeModal}
          onCreate={(name) => createBranch(name, currentBranch.name)}
        />
      )}

      {modal === 'mr' && (
        <SubmitMergeRequestModal
          sourceBranch={currentBranch}
          branches={branches}
          users={users}
          currentUser={currentUser}
          defaultTarget={currentBranch.sourceBranchName || 'main'}
          onClose={closeModal}
          onSubmit={createMergeRequest}
        />
      )}
    </>
  );
}
