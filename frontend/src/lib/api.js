/* ===========================================================================
   REST client for the Schema Version Control backend.
   Single fetch wrapper: JSON in, JSON out, bearer auth, normalised errors.
   =========================================================================== */

/**
 * Where the backend lives.
 *
 * Empty in development, so requests stay same-origin and Vite's dev proxy
 * forwards /api to localhost:8080. In a deployment VITE_API_BASE_URL is the
 * backend's origin, baked in at build time.
 *
 * Render's blueprint exposes a service's address as a bare hostname, so a
 * value arriving without a scheme is assumed to be https.
 */
function resolveApiOrigin() {
  const configured = (import.meta.env.VITE_API_BASE_URL ?? '').trim();
  if (!configured) return '';

  const withScheme = /^https?:\/\//i.test(configured) ? configured : `https://${configured}`;
  return withScheme.replace(/\/+$/, '');
}

const API_BASE = `${resolveApiOrigin()}/api`;
const TOKEN_KEY = 'accessToken';

export const getAuthToken = () => localStorage.getItem(TOKEN_KEY);
export const setAuthToken = (token) => localStorage.setItem(TOKEN_KEY, token);
export const removeAuthToken = () => localStorage.removeItem(TOKEN_KEY);

/** Listeners notified when the server rejects our token, so the shell can log out. */
const unauthorizedHandlers = new Set();
export const onUnauthorized = (fn) => {
  unauthorizedHandlers.add(fn);
  return () => unauthorizedHandlers.delete(fn);
};

/** Error carrying the HTTP status, so callers can special-case 409 merge conflicts. */
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request(endpoint, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  const token = getAuthToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
  } catch (_) {
    throw new ApiError('Cannot reach the server. Is the backend running on :8080?', 0);
  }

  if (response.status === 401) {
    removeAuthToken();
    unauthorizedHandlers.forEach((fn) => fn());
  }

  if (!response.ok) {
    throw new ApiError(await readError(response), response.status);
  }

  if (response.status === 204) return null;

  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (_) {
    return text;
  }
}

/** Pull the most useful message out of an ErrorResponse body, falling back to the status. */
async function readError(response) {
  const fallback = `Request failed (${response.status})`;
  let body;
  try {
    body = await response.text();
  } catch (_) {
    return fallback;
  }
  if (!body) return fallback;

  try {
    const json = JSON.parse(body);
    const fieldErrors = json.fieldErrors && Object.values(json.fieldErrors);
    if (fieldErrors && fieldErrors.length) return fieldErrors.join(' · ');
    return json.message || json.error || fallback;
  } catch (_) {
    return body;
  }
}

const qs = (params) =>
  Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join('&');

export const api = {
  /* --- Auth --- */
  register: (email, password, displayName) =>
    request('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, displayName }) }),
  login: (email, password) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  getMe: () => request('/auth/me'),
  getAllUsers: () => request('/auth/users'),

  /* --- Repositories --- */
  getRepositories: (page = 0, size = 10) => request(`/repositories?${qs({ page, size })}`),
  getRepository: (id) => request(`/repositories/${id}`),
  createRepository: (name, dbEngine = 'POSTGRESQL') =>
    request('/repositories', { method: 'POST', body: JSON.stringify({ name, dbEngine }) }),

  /* --- Engine catalogue --- */
  getRepoDataTypes: (repoId) => request(`/repositories/${repoId}/datatypes`),
  getRepoConstraints: (repoId) => request(`/repositories/${repoId}/constraints`),
  getEngineDataTypes: (engine) => request(`/database-types?${qs({ engine })}`),
  getEngineConstraints: (engine) => request(`/database-types/constraints?${qs({ engine })}`),

  /* --- Branches --- */
  getBranches: (repoId, page = 0, size = 10) => request(`/repositories/${repoId}/branches?${qs({ page, size })}`),
  createBranch: (repoId, name, sourceBranch) =>
    request(`/repositories/${repoId}/branches`, { method: 'POST', body: JSON.stringify({ name, sourceBranch }) }),

  /* --- Schema versions --- */
  getVersions: (repoId) => request(`/repositories/${repoId}/versions`),
  commitVersion: (repoId, branchName, schemaData, commitMessage) =>
    request(`/repositories/${repoId}/versions`, {
      method: 'POST',
      body: JSON.stringify({ branchName, schemaData, commitMessage }),
    }),

  /* --- Diff & direct merge --- */
  getDiff: (repoId, sourceBranch, targetBranch) =>
    request(`/repositories/${repoId}/diff?${qs({ sourceBranch, targetBranch })}`),
  mergeBranches: (repoId, sourceBranch, targetBranch, resolvedSchemaData) =>
    request(`/repositories/${repoId}/merge`, {
      method: 'POST',
      body: JSON.stringify({ sourceBranch, targetBranch, resolvedSchemaData }),
    }),

  /* --- Merge requests & approval flow --- */
  getMergeRequests: (repoId) => request(`/repositories/${repoId}/merge-requests`),
  getMergeRequestDetails: (id) => request(`/merge-requests/${id}`),
  createMergeRequest: (repositoryId, sourceBranchId, targetBranchId, requestedApproverId) =>
    request('/merge-requests', {
      method: 'POST',
      body: JSON.stringify({ repositoryId, sourceBranchId, targetBranchId, requestedApproverId }),
    }),
  approveMergeRequest: (id) => request(`/merge-requests/${id}/approve`, { method: 'POST' }),
  /** `resolutions` maps a conflict key ("table" or "table.column") to 'TARGET' | 'SOURCE'. */
  mergeMergeRequest: (id, resolutions) =>
    request(`/merge-requests/${id}/merge`, {
      method: 'POST',
      body: resolutions && Object.keys(resolutions).length ? JSON.stringify({ resolutions }) : undefined,
    }),

  /* --- Audit --- */
  getAuditLog: (repoId) => request(`/repositories/${repoId}/audit`),
  getAllAuditLog: () => request('/repositories/audit/all'),
};
