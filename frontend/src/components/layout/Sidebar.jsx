import React, { useEffect, useRef, useState } from 'react';
import {
  FolderGit2,
  GitBranch,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  User,
} from 'lucide-react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { useToast } from '../../state/ToastProvider';
import { Button, IconButton } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { Input, SearchInput, Select } from '../ui/Field';

const ENGINES = [
  ['POSTGRESQL', 'PostgreSQL'],
  ['MYSQL', 'MySQL'],
  ['SQLITE', 'SQLite'],
  ['ORACLE', 'Oracle'],
  ['GENERIC', 'Generic ANSI SQL'],
];

/** Fires `onReach` once the list is scrolled near its end. */
function useInfiniteScroll(onReach) {
  const ref = useRef(null);
  const handleScroll = () => {
    const el = ref.current;
    if (!el) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24) onReach();
  };
  return { ref, onScroll: handleScroll };
}

export function Sidebar() {
  const {
    repos,
    currentRepo,
    selectRepo,
    loadMoreRepos,
    hasMoreRepos,
    loadingRepos,
    createRepository,
    branches,
    currentBranch,
    goToBranch,
    loadMoreBranches,
    hasMoreBranches,
    loadingBranches,
    createBranch,
  } = useWorkspace();
  const toast = useToast();

  const [collapsed, setCollapsed] = useState(false);
  const [repoQuery, setRepoQuery] = useState('');
  const [branchQuery, setBranchQuery] = useState('');
  const [showRepoForm, setShowRepoForm] = useState(false);
  const [showBranchForm, setShowBranchForm] = useState(false);
  const [busy, setBusy] = useState(false);

  const [repoName, setRepoName] = useState('');
  const [engine, setEngine] = useState('POSTGRESQL');
  const [branchName, setBranchName] = useState('');
  const [baseBranch, setBaseBranch] = useState('');

  useEffect(() => {
    if (currentBranch?.name) setBaseBranch(currentBranch.name);
  }, [currentBranch?.name]);

  const repoScroll = useInfiniteScroll(loadMoreRepos);
  const branchScroll = useInfiniteScroll(loadMoreBranches);

  const visibleRepos = repos.filter((r) => r.name.toLowerCase().includes(repoQuery.toLowerCase()));
  const visibleBranches = branches.filter((b) =>
    b.name.toLowerCase().includes(branchQuery.toLowerCase())
  );

  const submitRepo = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await createRepository(repoName.trim(), engine);
      setRepoName('');
      setShowRepoForm(false);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  const submitBranch = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await createBranch(branchName.trim(), baseBranch || currentBranch?.name || 'main');
      setBranchName('');
      setShowBranchForm(false);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  };

  if (collapsed) {
    return (
      <aside className="sidebar is-collapsed">
        <IconButton icon={PanelLeftOpen} label="Expand sidebar" onClick={() => setCollapsed(false)} />
        <span className="sidebar-rail-item" title={`${repos.length} repositories`}>
          <FolderGit2 size={17} />
          {repos.length}
        </span>
        {currentRepo && (
          <span className="sidebar-rail-item" title={`${branches.length} branches`}>
            <GitBranch size={17} />
            {branches.length}
          </span>
        )}
      </aside>
    );
  }

  return (
    <aside className="sidebar">
      {/* ---------------------------------------------------- repositories */}
      <section className="sidebar-section">
        <div className="sidebar-head">
          <span className="eyebrow">
            <FolderGit2 size={13} /> Repositories
          </span>
          <span className="spacer" />
          <IconButton
            icon={Plus}
            label="New repository"
            size="xs"
            onClick={() => setShowRepoForm((v) => !v)}
          />
          <IconButton
            icon={PanelLeftClose}
            label="Collapse sidebar"
            size="xs"
            onClick={() => setCollapsed(true)}
          />
        </div>

        <div className="sidebar-tools">
          <SearchInput value={repoQuery} onChange={setRepoQuery} placeholder="Filter repositories…" />
        </div>

        {showRepoForm && (
          <form className="inline-form" onSubmit={submitRepo}>
            <Input
              size="sm"
              value={repoName}
              onChange={(e) => setRepoName(e.target.value)}
              placeholder="repository-name"
              autoFocus
              required
            />
            <Select size="sm" value={engine} onChange={(e) => setEngine(e.target.value)}>
              {ENGINES.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
            <div className="inline-form-actions">
              <Button type="button" size="xs" onClick={() => setShowRepoForm(false)}>
                Cancel
              </Button>
              <Button type="submit" size="xs" variant="primary" loading={busy}>
                Create
              </Button>
            </div>
          </form>
        )}

        <div className="sidebar-list" ref={repoScroll.ref} onScroll={repoScroll.onScroll}>
          {visibleRepos.length === 0 ? (
            <p className="list-note">{repoQuery ? 'No matches' : 'No repositories yet'}</p>
          ) : (
            visibleRepos.map((repo) => (
              <button
                key={repo.id}
                type="button"
                className="entity"
                aria-current={currentRepo?.id === repo.id}
                onClick={() => selectRepo(repo)}
              >
                <span className="entity-top">
                  <span className="entity-name">{repo.name}</span>
                  <span className="spacer" />
                  <Badge tone="neutral">{repo.dbEngine || 'POSTGRESQL'}</Badge>
                </span>
                <span className="entity-meta">
                  <User size={10} /> {repo.createdBy?.displayName || 'Unknown'}
                </span>
              </button>
            ))
          )}
          {loadingRepos && hasMoreRepos && (
            <span className="list-loading">
              <span className="spinner" /> Loading…
            </span>
          )}
        </div>
      </section>

      {/* --------------------------------------------------------- branches */}
      {currentRepo && (
        <section className="sidebar-section">
          <div className="sidebar-head">
            <span className="eyebrow">
              <GitBranch size={13} /> Branches
            </span>
            <span className="spacer" />
            <IconButton
              icon={Plus}
              label="New branch"
              size="xs"
              onClick={() => setShowBranchForm((v) => !v)}
            />
          </div>

          <div className="sidebar-tools">
            <SearchInput value={branchQuery} onChange={setBranchQuery} placeholder="Filter branches…" />
          </div>

          {showBranchForm && (
            <form className="inline-form" onSubmit={submitBranch}>
              <Input
                size="sm"
                value={branchName}
                onChange={(e) => setBranchName(e.target.value)}
                placeholder="feature/payments"
                autoFocus
                required
              />
              <label className="field-hint">Base branch</label>
              <Select size="sm" value={baseBranch} onChange={(e) => setBaseBranch(e.target.value)}>
                {branches.map((b) => (
                  <option key={b.id} value={b.name}>
                    {b.name}
                  </option>
                ))}
              </Select>
              <div className="inline-form-actions">
                <Button type="button" size="xs" onClick={() => setShowBranchForm(false)}>
                  Cancel
                </Button>
                <Button type="submit" size="xs" variant="primary" loading={busy}>
                  Create &amp; check out
                </Button>
              </div>
            </form>
          )}

          <div className="sidebar-list" ref={branchScroll.ref} onScroll={branchScroll.onScroll}>
            {visibleBranches.length === 0 ? (
              <p className="list-note">{branchQuery ? 'No matches' : 'No branches'}</p>
            ) : (
              visibleBranches.map((branch) => (
                <button
                  key={branch.id}
                  type="button"
                  className="entity"
                  aria-current={currentBranch?.id === branch.id}
                  onClick={() => goToBranch(branch)}
                >
                  <span className="entity-top">
                    <span className="entity-name">{branch.name}</span>
                    {branch.name === 'main' && (
                      <>
                        <span className="spacer" />
                        <Badge tone="del">protected</Badge>
                      </>
                    )}
                  </span>
                  <span className="entity-meta">
                    <User size={10} /> {branch.createdBy?.displayName || 'Unknown'}
                    {branch.sourceBranchName && <span>· from {branch.sourceBranchName}</span>}
                  </span>
                </button>
              ))
            )}
            {loadingBranches && hasMoreBranches && (
              <span className="list-loading">
                <span className="spinner" /> Loading…
              </span>
            )}
          </div>
        </section>
      )}
    </aside>
  );
}
