# Architectural Decisions

## Project Summary

This project is a web application for version-controlling database schemas across branches.

The core workflow is:

1. Create a repository for a target database engine.
2. Create and evolve schema versions on branches.
3. Create a Merge Request to propose changes to a target branch.
4. Review ancestor-aware schema diffs.
5. Require peer approval.
6. Invalidate approval when either branch changes.
7. Perform a three-way merge using the common ancestor.
8. Explicitly resolve conflicts by choosing the source or target definition.
9. Merge into the protected `main` branch only through an approved Merge Request.

### Hard problems intentionally addressed

- Schema-aware three-way merge
- Common ancestor discovery
- Conflict detection and explicit resolution
- Stale approval detection
- Race-safe merge validation
- Approval-bound version integrity
- Engine-specific schema validation

The remaining sections record the significant decisions made while building the system, including alternatives considered, tradeoffs accepted, defects discovered, and changes deliberately excluded from scope.

---

## 1. Authentication

### Decision
Implemented basic authentication using Spring Security and JWT.

### Alternatives considered
- No authentication
- Server-side sessions
- OAuth
- Full role-based authorization

### Reasoning
The system is conceptually a collaborative version-control tool. Associating schema changes with authenticated users makes version history and merge activity attributable to a specific actor.
JWT provides a simple stateless authentication mechanism for a separately deployed React frontend and Spring Boot backend.

### What was deliberately cut
Authorization and access control were excluded. Any authenticated user can access and modify repositories. The project focuses on schema versioning and merging rather than building a full multi-tenant collaboration system.

---

## 2. Layered Package Architecture

### Decision
Organized the Java backend code strictly into layered packages:
- `controller/`: REST API Controllers (exposing service interfaces only)
- `service/`: Service Interfaces
- `service/impl/`: Service Implementations
- `repository/`: Spring Data JPA Repositories (accessed exclusively by service implementations)
- `model/`: JPA Entities
- `dto/`: Request/Response & Inter-layer Data Transfer Objects
- `mapper/`: Entity <-> DTO converters
- `exception/`: `@RestControllerAdvice` Global Exception Handler & Domain Exceptions
- `config/`: Spring Security & Bean Configurations
- `constants/`: Global Enums & Constants

### Reasoning
Enforces clean separation of concerns, decoupling REST controllers from business logic and database persistence models. Concealing service implementations behind service interfaces allows modular extensibility and simplifies mocking in unit tests.

---

## 3. Engine-Aware Backend Data Types & Validation

### Decision
Associating each repository with a database engine (`POSTGRESQL`, `MYSQL`, `SQLITE`, `ORACLE`, `GENERIC`) and serving engine-specific data types via backend REST API (`GET /api/repositories/{id}/datatypes`).
Included strict schema and data type validation on schema commit endpoints.

### Alternatives considered
- Hardcoding static data type dropdowns in the frontend UI
- Querying live database server connections dynamically for every schema change

### Reasoning
Schema Version Control systems manage schema definitions across heterogeneous target database engines. Sourcing data type definitions from an engine-aware backend catalog permits tracking offline schemas while ensuring data types match the repository's target database engine.
If a repository uses an unknown or unconfigured engine, a `GENERIC` ANSI SQL fallback catalog is provided. Additionally, column data types are validated for syntax correctness during schema commits.

---

## 4. Unified Single-Tab Schema Editor with Target Branch Context, Author Attribution & Mandatory Conflict Resolution

### Decision
Eliminate separate top-level navigation tabs (`Schema Editor` / `Diff & 3-Way Merge`) in favor of a single unified **Schema Editor** workspace.
The top toolbar of the Schema Editor explicitly displays the active branch (e.g. `feature/orders`), the target branch selector (defaulting to the branch from which the current branch was spawned, e.g. `main`), and a **Compare & Merge** action.

Branch comparisons and 3-way merges are handled via an interactive overlay. When schema conflicts exist between branch heads:
1. Diffs and conflicts display **Commit Author Attribution** (showing user display names & avatars for both target and source branch heads, e.g. `Target (main) by Alex` vs `Source (feature/orders) by Jay`).
2. The user must explicitly select a resolution for every conflict.
3. The merge action is disabled in both frontend UI and backend API until all conflicts are resolved.

### Alternatives considered
- Maintaining two top-level tabs and relying on manual branch navigation for merging
- Silently auto-resolving conflicts by taking source branch changes (`theirs`) without user confirmation or author attribution

### Reasoning
Decoupling branch diffs into a separate tab isolated users from their active editing context and required tedious context switching. Bringing target branch selection and merge triggers directly into the Schema Editor toolbar creates a fluid Git-like workflow.
Showing commit author attribution on diffs and conflicting changes provides transparency into who introduced each change.
Mandating conflict resolution before enabling merge prevents corrupted schema state from being committed to parent branches and guarantees complete auditability of merge decisions.

---

## 5. Master-Detail Table Editor, In-Page Merge, Dynamic Merge Guard & Infinite Scroll Navigation

### Decision
1. **Master-Detail Table Schema Editor with Search & Scrollable Pagination (20 tables at a time)**:
   - Provide a left-side table master list inside the Schema Editor with a dedicated table search bar.
   - Tables list renders in scrollable chunks of 20 tables at a time.
   - Clicking a table loads its focused column editor on the right side.
2. **In-Page Workspace Tab for Compare & Merge (No Broken Pop-Up Modals)**:
   - Replace pop-up modal dialogs with seamless in-page workspace sub-view tabs: `[ 📄 Table Schema Editor ]` | `[ 🔀 Compare & Merge ]`.
3. **Dynamic Merge State Guard ("Nothing to Merge")**:
   - Compare current branch head against target branch head in real time:
     - Render disabled `✓ Up to date / Nothing to merge` status badge when 0 diffs exist.
     - Render active `⚡ Compare & Merge (X diffs)` trigger when diffs exist.
4. **Explicit Base Branch Creation Context**:
   - Header button explicitly shows the base branch being branched from (`"Create Branch from [currentBranch]"`).
5. **Sidebar Infinite Scroll Pagination & Live Search Filters**:
   - Repositories and Branches in the left sidebar fetch 10 items at a time (`page=0, size=10`), ordered by creation/update timestamp descending.
   - Appends newer items on scroll.
   - Search filter inputs allow filtering repositories and branches by name.
6. **Bottom Expandable Activity & Audit Drawer**:
   - Moved the right-hand Activity Log into a bottom collapsible drawer with expand/collapse toggle and scrollable log stream.

### Alternatives considered
- Opening pop-up dialog overlays for branch merging (caused broken context and restricted viewport)
- Showing an active merge button even when 0 schema diffs existed
- Displaying all tables, repositories, and branches without search or pagination

### Reasoning
Providing table search and chunked pagination (20 tables at a time) keeps large schemas fast and navigable.
Eliminating pop-up dialogs in favor of an in-page sub-view tab maintains workspace continuity and provides full screen space for side-by-side diffs and conflict resolution.
Disabling the merge action when 0 diffs exist prevents redundant merge commits.
Paginating repositories and branches (10 items per chunk, ordered by creation timestamp descending) ensures scalable performance as workspace activity grows.

---

## 6. Approval Flow, Protected Main Branch & Explicit Engine Data Type Selection

### Decision
1. **Protected `main` Branch**:
   - Direct commits (`commitVersion`) on `main` are strictly blocked in backend (`SchemaVersionServiceImpl.commitSchema`) and frontend.
   - `main` branch can only be updated through an approved **Merge Request**.
2. **Lightweight Approval Workflow (`OPEN` ➔ `APPROVED` ➔ `MERGED`)**:
   - Merge Requests capture head version IDs (`source_head_version_id`, `target_head_version_id`) at proposal time.
   - **Self-Approval Prevention**: Merge request creators cannot approve their own MRs (`currentUser.id != mergeRequest.createdBy`).
   - **Dynamic Stale Approval Detection**: Approvals are bound to exact version IDs. If either branch receives new commits, previous approvals automatically become `STALE` and require re-approval.
   - At least 1 valid approval matching current branch heads is required before merging.
3. **Explicit Engine Data Type Dropdown & Customizable Length Parameters**:
   - Data types are sourced dynamically from the backend for the repository's target database engine.
   - Rendered as styled `<select>` dropdown controls in the UI.
   - For parameterized types (e.g. `VARCHAR`), default length parameters (e.g. `255`) are pre-selected, but an explicit custom parameter input allows users to specify larger or custom lengths (e.g., `VARCHAR(500)`, `VARCHAR(65535)`, `DECIMAL(12,4)`).

### Alternatives considered
- Allowing direct commits on `main` without peer review
- Permitting self-approval on merge requests
- Hardcoding static datatypes in frontend UI without custom parameter overrides

### Reasoning
Protecting `main` guarantees that production schemas can only be altered through reviewed and approved merge requests.
Binding approvals to specific version IDs prevents stealth changes from being slipped into approved merge requests.
Serving engine data types from the backend with explicit custom parameter overrides gives users flexibility to specify custom character lengths or numeric precision while enforcing syntax safety.

---

## 7. Approver Assignment, Side-by-Side Schema Diff Review, Base Branch Auto-Checkout & Branch Audit Scoping

### Decision
1. **Approver Selection & Explicit Assignment**:
   - Creators can select an assigned `requestedApprover` (`User`) when submitting a Merge Request.
2. **Side-by-Side Full Schema & Detailed Diff Highlighting**:
   - Merge Request review views display full side-by-side schemas (Target Schema vs Source Schema) with color-coded diff highlighting (`+ Added`, `- Removed`, `~ Modified`).
   - Peer reviewers inspect exact schema differences before approving.
3. **Explicit Base Branch Selection & Automatic Checkout**:
   - Branch creation forms explicitly require base branch confirmation (`"Base Branch: [ activeBranch v ]"`).
   - Creating a branch automatically switches (checks out) active context to the newly created branch.
4. **Target Branch Selection Restoration**:
   - Workspace toolbar restores explicit target branch selector dropdown (`Merge into: [ targetBranch v ]`).
5. **Submit MR Button Guard**:
   - Disables "Submit Merge Request" button if an open MR already exists for `sourceBranch` ➔ `targetBranch`.
6. **Branch-Filtered Activity & Audit Drawer**:
   - Bottom audit trail stream is dynamically filtered to show events matching the currently checked-out branch.
7. **Collapsible Left Sidebar Layout**:
   - `RepoExplorer` provides a collapse/expand toggle to expand workspace area.

### Alternatives considered
- Allowing MR submission without assigned reviewer
- Showing only high-level table counts without detailed column diffs or full schema context
- Leaving active branch selection unchanged after creating a new branch

### Reasoning
Explicit approver assignment and line-by-line diff highlighting empower peer reviewers to audit exact schema modifications with complete confidence.
Auto-checking out newly created branches eliminates user confusion over active editing context.
Filtering audit logs by current branch gives developers a focused, clutter-free activity history for their active workspace.

---

## 8. Repository-Level Merge Request Hub, Flexible Peer Approvals, Target Branch Decoupling & Dirty-State Controls

### Decision
1. **Repository-Level Merge Requests Hub & Dedicated Diff Page**:
   - Merge Requests are decoupled from single-branch context and accessed via top-level Repository navigation tabs (`[ 📄 Schema Editor ]` | `[ 🔀 Merge Requests (X) ]`).
   - Clicking `Merge Requests` presents a clean, full-page list of all open and closed Merge Requests for the current repository.
   - Clicking any Merge Request opens a dedicated full-page Diff & Review page featuring a `← Back to Merge Requests` button, detailed table/column diff cards, side-by-side schema comparison, conflict resolution controls, peer approvals, and merge execution.
   - Includes a direct navigation trigger (`"Go to Branch [sourceBranch]"`) that switches the active workspace branch to that branch.
   - When editing that branch in Schema Editor, a top banner connects directly back to the active Merge Request.
2. **Flexible Peer Approval Permissions**:
   - Assigning a `requestedApprover` designates a preferred reviewer, but **ANY peer user** (other than the MR creator) can approve the Merge Request.
3. **Decoupled Target Branch in Schema Editor**:
   - The target branch selector is hidden from the main Schema Editor header during standard editing and is only displayed within Merge Request creation and review views.
4. **Strict Auto-Checkout on Branch Creation**:
   - Creating a branch strictly switches active workspace context (`currentBranch`) to the newly created branch without async state resets overriding the selection.
5. **Dirty-State & Interactive Commit Version Modal Dialog**:
   - `Commit Version` button is disabled until unsaved schema changes exist in the editor buffer (`isDirty === true`).
   - Clicking `Commit Version` pops up an interactive **Commit Version Modal Dialog** prompting the user to enter a custom commit message before persisting changes to the branch.
   - `Submit Merge Request` button is disabled until at least 1 commit or schema change exists relative to the base branch, or if an open MR already exists for that branch pair.
6. **Side-by-Side Schema Diff with Operations Highlighting**:
   - Renders a clean **Side-by-Side Schema Diff** comparing target branch vs source branch.
   - Highlights added tables/columns/constraints in **Green** (`+`), removed tables/columns/constraints in **Red** (`-`), and modified columns/datatypes in **Amber** (`⚡`).
   - Removes unnecessary brand labels or extra view mode tabs for maximum clarity focus.
7. **Premium Custom Form Input & Select Styling**:
   - Custom styled text inputs, select dropdowns, and option lists with subtle glassmorphism backgrounds (`rgba(15,23,42,0.9)`), custom SVG chevron indicators, dark option menus, rounded corners (`8px`), and glowing indigo focus rings (`0 0 0 3px rgba(99,102,241,0.25)`).
8. **Active Merge Request Action Guard**:
   - When a Merge Request has already been submitted for the active branch, the `Submit Merge Request` button is automatically replaced with a direct link button (`[ View Merge Request #MR-X ]`).
9. **Unified Constraint Dropdown Selector & Multiselect Dropdown for Index Columns**:
   - Column constraints (`PRIMARY KEY`, `NOT NULL`, `UNIQUE`, `SERIAL / AUTO_INCREMENT`, `FOREIGN KEY`, `CHECK`, `DEFAULT`) are selected exclusively from a clean Engine Constraint Dropdown in every column row without redundant quick-action buttons cluttering the view. Active constraints are displayed as dismissible badge tags with `×` clear triggers.
   - Index creation features a custom **Multiselect Dropdown Menu** allowing users to expand a column selector dropdown and check/uncheck multiple table columns for index creation.
   - **Table Operations**: Create table, Drop table.
   - **Column Operations**: Add column, Drop column, Rename column in-place, Retype data type in-place, Select engine constraints exclusively from dropdown.
   - **Table Indexes**: Dedicated Index Management tab supporting index creation (`+ Add Index`), multiselect dropdown column selection, uniqueness flags, and index dropping.
10. **Eye-Friendly Soothing Slate Design System**:
   - Replaced pitch-black harsh contrast with a soft, soothing Slate palette (`#0f172a` canvas, `#1e293b` glassmorphic cards, `#334155` smooth borders, `#f8fafc` primary text, `#94a3b8` muted secondary text).
   - Carefully balanced contrast ratios for extended coding and schema editing comfort, eliminating eye fatigue.
   - Smooth micro-interactions, glowing indigo focus rings (`0 0 0 3px rgba(99,102,241,0.2)`), subtle hover elevations.

### Alternatives considered
1. Restricting approvals exclusively to the single user listed as `requestedApprover`
2. Showing target branch dropdowns constantly in the primary schema editing toolbar
3. Enabling `Commit Version` and `Submit MR` buttons even when 0 schema modifications exist

### Reasoning
Decoupling Merge Requests into a repository-wide hub gives team members a clear dashboard to track all pending schema reviews across all branches.
Allowing any non-author peer to approve prevents bottlenecking when assigned approvers are unavailable while preserving strict self-approval prevention.
Hiding target branch controls during routine schema editing clutter-free editing focus.
Disabling `Commit Version` and `Submit MR` buttons when no schema edits exist prevents empty/redundant commits.

---

## 9. Frontend Rebuild: Component Decomposition, CSS Design System & Centralised Workspace State

### Decision

Rebuilt the React frontend from six large, inline-styled components into a layered structure. No backend contract changed and no behaviour recorded in decisions 1–8 was dropped.

1. **Component decomposition**:
   - `SchemaEditor.jsx` (1831 lines, six responsibilities: table editing, index management, data type selection, merge request list, merge request review, modals) was split into `components/editor/*` (list, detail, columns tab, indexes tab, type picker, constraint cell, modals) and `components/merge/*` (list, review, diff, approval panel).
   - Shared primitives live in `components/ui/*`: `Button`, `Badge`, `Field`, `Modal`, `Segmented`, `MultiSelect`, `Feedback`.
   - Pure logic moved out of components into `lib/schema.js` (parse, serialise, diff, data type handling) and `lib/format.js`.
   - Deleted `DiffAndMergeView.jsx` and `CompareAndMergeModal.jsx` — 555 lines nothing imported.

2. **CSS design system replacing inline styles**:
   - Styling was ~95% inline `style={{}}` objects. It now lives in `styles/tokens.css` (colour, type, space, radius, elevation, motion), `styles/base.css`, `styles/ui.css` (primitives) and `styles/app.css` (layout and features).
   - The Slate identity from decision 8.10 is kept but tightened: glassmorphism and blur are dropped in favour of five flat surface steps, giving denser, IDE-like information design. Inline styles now appear only for genuinely dynamic values.
   - Breakpoints at 1280px / 1100px / 900px reclaim horizontal room by shrinking chrome rather than reflowing the three-pane workspace.

3. **Centralised workspace state**:
   - `state/WorkspaceProvider.jsx` owns repositories, branches, version history, merge requests, audit events and the engine catalogue, replacing prop drilling through `App` → `SchemaEditor`.
   - The refresh fan-out after a commit or merge lives in one place (`refreshAfterWrite`).
   - Repository-scoped loads are guarded against out-of-order responses, so a slow request for a previous repository cannot overwrite the current one.
   - `state/ToastProvider.jsx` replaces the `successMsg` / `errorMsg` string pairs that were duplicated in every component.

4. **Schema editing buffer**:
   - `useSchemaBuffer` holds the editable schema in a single state object keyed by `repo:branch:headVersion`, and derives a reload during render rather than in an effect, so switching branches never paints the previous schema under the new branch name.

### Defects fixed in the process

- **Sidebar infinite scroll fetched and discarded**: `handleRepoScroll` / `handleBranchScroll` requested the next page and incremented the page counter without ever appending the results, so repositories and branches past the first ten were unreachable.
- **Merge request review diffed the wrong schema**: the review page compared the merge request's target head against the *checked-out branch's editor buffer* rather than the merge request's own source head, so reviewing a merge request for any branch other than the current one showed a fabricated diff. It now resolves both sides from their recorded head version IDs.
- **`STALE` status unhandled**: the backend computes `MergeRequestStatus.STALE` when a branch head moves after approval, but the frontend only knew `OPEN` / `APPROVED` / `MERGED`, so stale merge requests fell through to an amber "open" badge and were invisible to the status filters. Stale is now a first-class status, individual approvals are marked valid or stale against the current heads, and the merge gate shows why a merge is blocked.
- **Parameterised data types mangled**: the type dropdown matched a column's type against the full catalogue name. Since the catalogue bakes a default argument into the name (`VARCHAR(255)`, `NUMERIC(10,2)`), any type carrying a different argument was rewritten to `NAME(255)`, turning `DECIMAL(12,2)` into `DECIMAL(255)`. Matching now runs on the base name, with the argument edited in its own field.

### Alternatives considered

- Reskinning in place, keeping inline styles and the monolith — rejected because the styling duplication was the main obstacle to changing anything, and the 1831-line component was where all four defects above were hiding.
- Adding `react-router` for real URLs, or Tailwind for utility styling — both rejected to keep the dependency set at React plus `lucide-react`, so the build stays a stock Vite React setup with nothing extra to justify.

### Reasoning

The recorded behaviour in decisions 1–8 is detailed and was worth preserving exactly; what needed replacing was the structure carrying it. Extracting a token-driven stylesheet makes the visual language editable in one place instead of across 3,800 lines of style objects, and splitting the monolith puts each screen's logic somewhere a reader can find it. Centralising workspace state was what surfaced the stale-data defects: once every view read the same version history, the places that had been resolving schemas from the wrong source became obvious.

---

## 10. Conflict Resolution by Side Selection & Ancestor-Aware Diff

### Context

Conflicting merge requests were a dead end. `MergeRequestServiceImpl.mergeMergeRequest` passed `resolvedSchemaData = null` unconditionally, so the three-way merge could refuse a merge request but nothing could ever settle it. Decision 4 recorded that "the user must explicitly select a resolution for every conflict", but that only ever existed in `CompareAndMergeModal.jsx`, which nothing imported and which decision 9 deleted. The only remedy available in the product was to reconcile by hand on the source branch and re-request approval.

### Decision

1. **Conflicts are resolved by choosing a side, not by submitting a schema**:
   - `POST /api/merge-requests/{id}/merge` accepts an optional body `{ "resolutions": { "<key>": "TARGET" | "SOURCE" } }`, where a key is `table` or `table.column`.
   - Resolutions are applied *inside* `ThreeWayMergeEngine`, at the points where it would otherwise record a conflict. There remains a single merge path; an empty resolution map reproduces the previous behaviour exactly.
   - Every emitted conflict now carries a `key` field, so a client resolves conflicts using the engine's own naming instead of reconstructing it and drifting out of sync.
   - A path left undecided is still reported and still blocks the merge — a partial resolution is rejected outright rather than partly applied.

2. **Why sides rather than a schema payload**: the direct merge endpoint has always accepted an arbitrary `resolvedSchemaData`, which means whoever performs a merge could introduce a definition that appears in neither branch and that no reviewer ever saw. Restricting merge-request resolution to a choice between the two reviewed definitions keeps the approval meaningful — a merge can only land something that already exists on one of the two branches. `resolvedSchemaData` is left in place on the direct endpoint for compatibility.

3. **Resolutions are attributable**: a resolved merge records `MERGE_CONFLICT_RESOLVED` rather than `MERGE_COMPLETED`, with the full resolution map in the audit metadata, so a reviewer can see afterwards which side was chosen for each path and by whom. Approval remains a separate gate: supplying resolutions does not bypass the requirement for a valid peer approval matching the current heads.

4. **The diff is ancestor-aware**: `DiffResultDto.ancestorVersionId` existed but was always passed `null`. `computeDiff` now populates it, and the frontend labels each pane by what *that* branch changed since the fork rather than by how the two heads differ. Previously a table the target had dropped was labelled "Added table" in green, because from the target's perspective it simply was not there — which directly contradicted the conflict report calling the same table "dropped in target, modified in source". Table and column rows now carry `targetChange` and `sourceChange` and read, for example, "Dropped in target · Modified in source".

### Alternatives considered

- **Accepting a full resolved schema on the merge-request endpoint** — simplest, and symmetric with the direct merge endpoint, but it lets the merger land arbitrary unreviewed schema through an approved merge request. Rejected: it would quietly undermine the protected-`main` guarantee that decision 6 exists to provide.
- **Resolving on the source branch and re-requesting approval** — already possible and fully working, and arguably the more honest model because the reviewer then sees the resolution as an ordinary diff. Kept as a valid path, but it is a poor default: it forces a round trip through the schema editor for a decision the reviewer is already looking at.
- **Leaving the diff two-way** — it answers "what does merging change in target?", which is a reasonable reviewer question. Rejected because the two framings sat side by side on the same screen and appeared to contradict each other.

### Tests

`MergeConflictResolutionTest` (10 engine tests): every conflict type resolvable in both directions, stable keys, mixed sides across conflicts, partial resolutions still blocking, unrecognised choices ignored rather than defaulting to a side, and resolutions unable to override a non-conflicting fast-forward.

`ConflictResolutionFlowTest` (5 integration tests): an approved merge request still refusing while conflicted, resolution landing exactly the chosen sides, partial resolution rejected, the resolved merge recorded distinctly in the audit trail, and resolutions unable to bypass the approval gate.

Suite went from 6 tests to 21.

---

## 11. Checking Out a Branch Always Opens Its Schema

### Context

Selecting a branch in the sidebar changed the checked-out branch but left the workspace on whatever view was already open. From the Merge Requests hub — or worse, from a merge request's review page — clicking a branch silently rebound the whole workspace underneath an unrelated screen: the breadcrumb, the schema buffer, the target-branch default and the audit drawer's branch filter all moved, with nothing on screen reflecting it.

The cause was two ways to change branch coexisting in `WorkspaceProvider`:

- `selectBranch` — a bare `setCurrentBranch`, which the sidebar used
- `goToBranch` — sets the branch *and* switches to the editor, used by the merge request page's `Open [branch]` action

### Decision

1. **Checking out a branch is always a request to see that branch's schema.** The sidebar routes through `goToBranch`, which sets the branch, clears any open merge request, and switches to the Schema view. This matches the auto-checkout behaviour already recorded in decisions 7.3 and 8.4: creating a branch checks it out and shows it, and selecting one now does the same.

2. **`selectBranch` was deleted rather than left in place.** A setter that silently skips navigation is the trap that produced this defect, and it was reachable from anywhere with the context. `goToBranch` is now the only way to change the checked-out branch from a component; `createBranch` continues to set it directly inside the provider, where the navigation is already handled.

### Alternatives considered

- **Keeping both and fixing only the call site** — leaves the same footgun for the next caller, with nothing in the API to indicate which one is correct.
- **Leaving the view alone and marking the change some other way** (a toast, a highlighted breadcrumb) — treats the symptom. Selecting a branch has no meaning other than wanting to work on it; there is no case where staying on a merge request list while the underlying branch changes is what was intended.

### Reasoning

Repository selection deliberately does not do this. Picking another repository keeps the current view, so moving between repositories on the Merge Requests hub keeps showing merge requests. Branch is the narrower, editor-scoped selection; repository is the broader context in which any view still makes sense.

---

## 12. PostgreSQL, Docker & Render Deployment

### Decision

1. **PostgreSQL replaces H2 as the application database.** H2 was in-memory (`jdbc:h2:mem`), so every restart discarded all repositories, branches and users. The Postgres driver was already declared; it was simply never used. H2 is now scoped to `test` and stays out of the production image.

2. **Flyway owns the schema; Hibernate validates against it.** `ddl-auto` moves from `update` to `validate`. `V1__init.sql` was produced by letting Hibernate build the schema against Postgres 16 and dumping the result, then naming the constraints and adding indexes Hibernate does not infer — Postgres does not index foreign keys automatically, and every lookup in the repositories goes through one.

   `ddl-auto: update` was doing real work before and would have kept working, but it never drops or renames, diverges silently between environments, and offers no record of what changed when. Depending on it in a product whose entire purpose is reviewable schema change was untenable.

3. **No seed migration.** Nothing is required for the application to function: `main` and its initial version are created at runtime by `createRepository`, and the engine type catalogues are served from `DatabaseTypeServiceImpl` rather than tables. A Flyway migration is also the wrong vehicle for demo data — it runs in every environment including production, cannot be conditionally skipped, and is immutable once applied, so demo accounts with known passwords would be permanent. Demo data lives in `scripts/seed_demo.py`, which is opt-in, idempotent, and refuses to target a non-local host without an explicit password.

4. **Configuration is entirely environment-driven.** Datasource, JWT secret, CORS origins, pool size and log levels all read from environment variables with local-friendly defaults, so the same image runs locally and on Render with only the environment differing.

5. **Deployment hardening**:
   - `/api/auth/**` was `permitAll`, which left `/api/auth/users` enumerating every registered account anonymously. Only `/api/auth/login` and `/api/auth/register` are public now.
   - `/actuator/health` is public because Render polls it before any token exists; nothing else under `/actuator` is exposed.
   - CORS moves from a hardcoded `*` to a configurable origin list.
   - `DeploymentSanityCheck` refuses to start under the `prod` profile while the committed development JWT secret is still in place, and warns when CORS is left open. A misconfigured deploy fails loudly rather than running quietly insecure.

6. **Render topology**: a `render.yaml` blueprint provisioning a Postgres instance, the API as a Docker web service, and the frontend as a static site. Database credentials are wired with `fromDatabase` rather than a connection string, because Spring needs a `jdbc:` URL and the blueprint cannot rewrite one. `VITE_API_BASE_URL` is wired with `fromService`, which yields a bare hostname, so the API client treats a scheme-less value as https.

### Alternatives considered

- **Serving the built frontend from Spring Boot as one service** — one deploy, no CORS, no build-time API URL, and half the hosting. Rejected in favour of a static site: the SPA gets a CDN and stays up while a free backend instance is asleep, and the two deploy independently.
- **Frontend as a Docker/nginx service proxying `/api`** — avoids CORS entirely, but adds a container to maintain and consumes a second web service for something a static host does better.
- **`alpine` JRE base image** — smaller, but published for amd64 only, so it builds on Render and fails on an arm64 laptop. `eclipse-temurin:17-jre-jammy` is multi-arch and keeps local and deployed images identical.
- **Keeping H2 with file persistence** — would survive restarts, but Render's free instances have ephemeral disks, so it would not survive a deploy.

### Notes

- Compose publishes Postgres on **55432**, not 5432. A native Postgres on the standard port is common enough that defaulting to it makes `docker compose up` fail confusingly: the application connects to the wrong server rather than to nothing. Inside the compose network it is still `db:5432`.
- Tests run on H2 with Flyway disabled, so the suite needs no Docker. The Postgres-specific migration is therefore not exercised by `mvn test`; it is verified by booting against a real Postgres with `ddl-auto: validate`, which is what proves the migration and the entity mappings agree.

### Free tier caveats

Render's free Postgres is deleted roughly 30 days after creation, and free web services sleep after 15 minutes idle with a ~50s JVM cold start. Because the frontend is a static site it stays up, so the symptom is a page that loads and then cannot reach its API.
