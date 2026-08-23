import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { api } from '../lib/api';
import { useToast } from './ToastProvider';

/* ===========================================================================
   Workspace state: the repository / branch / merge-request context that every
   view reads from. Centralised so components stay presentational and the
   refresh fan-out after a commit or merge lives in exactly one place.
   =========================================================================== */

const PAGE_SIZE = 10;

const WorkspaceContext = createContext(null);

/** Engine catalogue fallback if the backend returns nothing for a repository. */
const FALLBACK_CONSTRAINTS = [
  { name: 'PRIMARY KEY', category: 'Key', description: 'Primary key' },
  { name: 'NOT NULL', category: 'Nullability', description: 'Non-null column' },
  { name: 'UNIQUE', category: 'Key', description: 'Unique values constraint' },
  { name: 'SERIAL', category: 'Generator', description: 'PostgreSQL auto-increment integer' },
  { name: 'AUTO_INCREMENT', category: 'Generator', description: 'MySQL/H2 auto-increment' },
  { name: 'FOREIGN KEY', category: 'Relation', description: 'Foreign key reference' },
  { name: 'CHECK', category: 'Validation', description: 'Validation expression' },
  { name: 'DEFAULT', category: 'Value', description: 'Default value expression' },
];

const mergeById = (existing, incoming) => {
  const seen = new Set(existing.map((item) => item.id));
  return [...existing, ...incoming.filter((item) => !seen.has(item.id))];
};

export function WorkspaceProvider({ currentUser, children }) {
  const toast = useToast();

  /* --- Repositories --- */
  const [repos, setRepos] = useState([]);
  const [repoPage, setRepoPage] = useState(0);
  const [hasMoreRepos, setHasMoreRepos] = useState(true);
  const [loadingRepos, setLoadingRepos] = useState(false);
  const [currentRepo, setCurrentRepo] = useState(null);

  /* --- Branches --- */
  const [branches, setBranches] = useState([]);
  const [branchPage, setBranchPage] = useState(0);
  const [hasMoreBranches, setHasMoreBranches] = useState(true);
  const [loadingBranches, setLoadingBranches] = useState(false);
  const [currentBranch, setCurrentBranch] = useState(null);

  /* --- Repository-scoped data --- */
  const [versions, setVersions] = useState([]);
  // Repository the loaded version history belongs to, so consumers can tell an
  // empty history apart from one that simply has not arrived yet.
  const [versionsRepoId, setVersionsRepoId] = useState(null);
  const [mergeRequests, setMergeRequests] = useState([]);
  const [auditEvents, setAuditEvents] = useState([]);
  const [dataTypes, setDataTypes] = useState([]);
  const [constraints, setConstraints] = useState(FALLBACK_CONSTRAINTS);
  const [users, setUsers] = useState([]);
  const [bootstrapping, setBootstrapping] = useState(true);

  /* --- Navigation --- */
  const [view, setView] = useState('editor'); // 'editor' | 'merge_requests'
  const [selectedMrId, setSelectedMrId] = useState(null);

  /* Track the repo whose scoped data is currently loaded, so late responses
     from a previous repository never overwrite the new one. */
  const repoRef = useRef(null);
  repoRef.current = currentRepo?.id ?? null;
  const isStale = (repoId) => repoRef.current !== repoId;

  /* ---------------------------------------------------------------- repos */

  const loadRepos = useCallback(async () => {
    setLoadingRepos(true);
    try {
      const data = (await api.getRepositories(0, PAGE_SIZE)) || [];
      setRepos(data);
      setRepoPage(0);
      setHasMoreRepos(data.length === PAGE_SIZE);
      setCurrentRepo((prev) => {
        if (prev) {
          const refreshed = data.find((r) => r.id === prev.id);
          if (refreshed) return refreshed;
        }
        return prev ?? data[0] ?? null;
      });
      return data;
    } catch (err) {
      toast.error(err.message);
      return [];
    } finally {
      setLoadingRepos(false);
    }
  }, [toast]);

  const loadMoreRepos = useCallback(async () => {
    if (loadingRepos || !hasMoreRepos) return;
    setLoadingRepos(true);
    try {
      const next = repoPage + 1;
      const data = (await api.getRepositories(next, PAGE_SIZE)) || [];
      if (data.length) {
        setRepos((prev) => mergeById(prev, data));
        setRepoPage(next);
      }
      setHasMoreRepos(data.length === PAGE_SIZE);
    } catch (_) {
      setHasMoreRepos(false);
    } finally {
      setLoadingRepos(false);
    }
  }, [hasMoreRepos, loadingRepos, repoPage]);

  const createRepository = useCallback(
    async (name, dbEngine) => {
      const created = await api.createRepository(name, dbEngine);
      setRepos((prev) => mergeById([created], prev));
      setCurrentRepo(created);
      toast.success(`Repository '${created.name}' created`);
      return created;
    },
    [toast]
  );

  /* ------------------------------------------------- repository-scoped IO */

  const loadVersions = useCallback(async (repoId) => {
    if (!repoId) return [];
    try {
      const data = (await api.getVersions(repoId)) || [];
      if (!isStale(repoId)) {
        setVersions(data);
        setVersionsRepoId(repoId);
      }
      return data;
    } catch (_) {
      return [];
    }
  }, []);

  const loadMergeRequests = useCallback(async (repoId) => {
    if (!repoId) return [];
    try {
      const data = (await api.getMergeRequests(repoId)) || [];
      if (!isStale(repoId)) setMergeRequests(data);
      return data;
    } catch (_) {
      return [];
    }
  }, []);

  const loadAudit = useCallback(async (repoId) => {
    try {
      const data = (repoId ? await api.getAuditLog(repoId) : await api.getAllAuditLog()) || [];
      if (!repoId || !isStale(repoId)) setAuditEvents(data);
      return data;
    } catch (_) {
      return [];
    }
  }, []);

  const loadCatalog = useCallback(async (repoId) => {
    if (!repoId) return;
    const [types, cons] = await Promise.all([
      api.getRepoDataTypes(repoId).catch(() => []),
      api.getRepoConstraints(repoId).catch(() => []),
    ]);
    if (isStale(repoId)) return;
    setDataTypes(types || []);
    setConstraints(cons && cons.length ? cons : FALLBACK_CONSTRAINTS);
  }, []);

  /* ------------------------------------------------------------- branches */

  const loadBranches = useCallback(
    async (repoId, preferBranchId) => {
      if (!repoId) return [];
      setLoadingBranches(true);
      try {
        const data = (await api.getBranches(repoId, 0, PAGE_SIZE)) || [];
        if (isStale(repoId)) return data;

        setBranches(data);
        setBranchPage(0);
        setHasMoreBranches(data.length === PAGE_SIZE);
        setCurrentBranch((prev) => {
          const preferred = preferBranchId && data.find((b) => b.id === preferBranchId);
          if (preferred) return preferred;
          const kept = prev && data.find((b) => b.id === prev.id);
          if (kept) return kept;
          return data.find((b) => b.name === 'main') || data[0] || null;
        });
        return data;
      } catch (err) {
        toast.error(err.message);
        return [];
      } finally {
        setLoadingBranches(false);
      }
    },
    [toast]
  );

  const loadMoreBranches = useCallback(async () => {
    if (!currentRepo || loadingBranches || !hasMoreBranches) return;
    setLoadingBranches(true);
    try {
      const next = branchPage + 1;
      const data = (await api.getBranches(currentRepo.id, next, PAGE_SIZE)) || [];
      if (data.length) {
        setBranches((prev) => mergeById(prev, data));
        setBranchPage(next);
      }
      setHasMoreBranches(data.length === PAGE_SIZE);
    } catch (_) {
      setHasMoreBranches(false);
    } finally {
      setLoadingBranches(false);
    }
  }, [branchPage, currentRepo, hasMoreBranches, loadingBranches]);

  /** Create a branch and check it out — branch creation always switches context. */
  const createBranch = useCallback(
    async (name, sourceBranchName) => {
      const created = await api.createBranch(currentRepo.id, name, sourceBranchName);
      setBranches((prev) => mergeById([created], prev));
      setCurrentBranch(created);
      setView('editor');
      toast.success(`Branch '${created.name}' created from '${sourceBranchName}' and checked out`);
      loadBranches(currentRepo.id, created.id);
      loadAudit(currentRepo.id);
      return created;
    },
    [currentRepo, loadAudit, loadBranches, toast]
  );

  /* ------------------------------------------------------------- effects */

  useEffect(() => {
    let cancelled = false;
    (async () => {
      await Promise.all([
        loadRepos(),
        api
          .getAllUsers()
          .then((u) => !cancelled && setUsers(u || []))
          .catch(() => {}),
      ]);
      if (!cancelled) setBootstrapping(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [loadRepos]);

  useEffect(() => {
    const repoId = currentRepo?.id;
    if (!repoId) {
      setBranches([]);
      setCurrentBranch(null);
      setVersions([]);
      setVersionsRepoId(null);
      setMergeRequests([]);
      setAuditEvents([]);
      return;
    }
    // Drop the previous repository's history immediately; keeping it around
    // lets views resolve branch heads against the wrong repository.
    setVersions([]);
    setVersionsRepoId(null);
    setSelectedMrId(null);
    loadBranches(repoId);
    loadVersions(repoId);
    loadMergeRequests(repoId);
    loadAudit(repoId);
    loadCatalog(repoId);
  }, [currentRepo?.id, loadAudit, loadBranches, loadCatalog, loadMergeRequests, loadVersions]);

  /* ------------------------------------------------------------- actions */

  /** Everything that changes when a branch head moves. */
  const refreshAfterWrite = useCallback(
    async (preferBranchId) => {
      const repoId = currentRepo?.id;
      if (!repoId) return;
      await Promise.all([
        loadBranches(repoId, preferBranchId),
        loadVersions(repoId),
        loadMergeRequests(repoId),
        loadAudit(repoId),
      ]);
    },
    [currentRepo?.id, loadAudit, loadBranches, loadMergeRequests, loadVersions]
  );

  const commitSchema = useCallback(
    async (schemaData, message) => {
      const branchName = currentBranch.name;
      await api.commitVersion(currentRepo.id, branchName, schemaData, message);
      await refreshAfterWrite(currentBranch.id);
      toast.success(`Committed to ${branchName}`);
    },
    [currentBranch, currentRepo, refreshAfterWrite, toast]
  );

  const createMergeRequest = useCallback(
    async (sourceBranchId, targetBranchId, requestedApproverId) => {
      const created = await api.createMergeRequest(
        currentRepo.id,
        sourceBranchId,
        targetBranchId,
        requestedApproverId || null
      );
      await Promise.all([loadMergeRequests(currentRepo.id), loadAudit(currentRepo.id)]);
      setView('merge_requests');
      setSelectedMrId(created.id);
      toast.success(`Merge request opened: ${created.sourceBranch?.name} → ${created.targetBranch?.name}`);
      return created;
    },
    [currentRepo, loadAudit, loadMergeRequests, toast]
  );

  const approveMergeRequest = useCallback(
    async (mrId) => {
      await api.approveMergeRequest(mrId);
      await Promise.all([loadMergeRequests(currentRepo.id), loadAudit(currentRepo.id)]);
      toast.success('Merge request approved');
    },
    [currentRepo, loadAudit, loadMergeRequests, toast]
  );

  const mergeMergeRequest = useCallback(
    async (mrId, resolutions) => {
      const result = await api.mergeMergeRequest(mrId, resolutions);
      await refreshAfterWrite(currentBranch?.id);
      if (result?.success) {
        toast.success('Merged into target branch');
      } else if (result?.hasConflicts) {
        const n = result.conflicts?.length || 0;
        // The caller renders the detail; the toast only says how many.
        toast.error(`Merge blocked by ${n} unresolved conflict${n === 1 ? '' : 's'}`);
      } else {
        toast.error('Merge did not complete');
      }
      return result;
    },
    [currentBranch?.id, refreshAfterWrite, toast]
  );

  /* ---------------------------------------------------------- navigation */

  /**
   * Check out a branch and show its schema. Checking out a branch is always a
   * request to look at that branch's schema, so this is the only way to change
   * the current branch — a setter that silently left you on another view would
   * be the same trap twice.
   */
  const goToBranch = useCallback(
    (branchOrName) => {
      const branch =
        typeof branchOrName === 'string'
          ? branches.find((b) => b.name === branchOrName)
          : branchOrName;
      if (branch) setCurrentBranch(branch);
      setSelectedMrId(null);
      setView('editor');
    },
    [branches]
  );

  const openMergeRequest = useCallback((mrId) => {
    setSelectedMrId(mrId);
    setView('merge_requests');
  }, []);

  /** Top-level navigation. Always lands on a section's index, never on
      whichever merge request happened to be open last. */
  const navigateTo = useCallback((next) => {
    setSelectedMrId(null);
    setView(next);
  }, []);

  /* ------------------------------------------------------------- derived */

  const openMrCount = useMemo(
    () => mergeRequests.filter((mr) => mr.status !== 'MERGED' && mr.status !== 'CLOSED').length,
    [mergeRequests]
  );

  const isProtectedBranch = currentBranch?.name === 'main';

  /** The still-open merge request proposing the checked-out branch, if any. */
  const activeMrForBranch = useMemo(
    () =>
      mergeRequests.find(
        (mr) =>
          mr.sourceBranch?.name === currentBranch?.name &&
          mr.status !== 'MERGED' &&
          mr.status !== 'CLOSED'
      ) || null,
    [currentBranch?.name, mergeRequests]
  );

  const value = useMemo(
    () => ({
      currentUser,
      bootstrapping,

      repos,
      currentRepo,
      hasMoreRepos,
      loadingRepos,
      selectRepo: setCurrentRepo,
      loadMoreRepos,
      createRepository,
      refreshRepos: loadRepos,

      branches,
      currentBranch,
      hasMoreBranches,
      loadingBranches,
      loadMoreBranches,
      createBranch,
      isProtectedBranch,

      versions,
      versionsReady: versionsRepoId !== null && versionsRepoId === currentRepo?.id,
      mergeRequests,
      auditEvents,
      dataTypes,
      constraints,
      users,

      view,
      setView,
      navigateTo,
      selectedMrId,
      openMergeRequest,
      closeMergeRequest: () => setSelectedMrId(null),
      goToBranch,

      commitSchema,
      createMergeRequest,
      approveMergeRequest,
      mergeMergeRequest,
      refreshAudit: () => loadAudit(currentRepo?.id),
      refreshMergeRequests: () => loadMergeRequests(currentRepo?.id),

      openMrCount,
      activeMrForBranch,
    }),
    [
      activeMrForBranch,
      approveMergeRequest,
      auditEvents,
      bootstrapping,
      branches,
      commitSchema,
      constraints,
      createBranch,
      createMergeRequest,
      createRepository,
      currentBranch,
      currentRepo,
      currentUser,
      dataTypes,
      goToBranch,
      hasMoreBranches,
      hasMoreRepos,
      isProtectedBranch,
      loadAudit,
      loadMergeRequests,
      loadMoreBranches,
      loadMoreRepos,
      loadRepos,
      loadingBranches,
      loadingRepos,
      mergeMergeRequest,
      mergeRequests,
      navigateTo,
      openMergeRequest,
      openMrCount,
      repos,
      selectedMrId,
      users,
      versions,
      versionsRepoId,
      view,
    ]
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) throw new Error('useWorkspace must be used inside <WorkspaceProvider>');
  return ctx;
}
