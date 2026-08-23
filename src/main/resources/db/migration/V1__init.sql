-- Baseline schema for schema-version-control.
--
-- Derived from the JPA entity mappings by letting Hibernate build the schema
-- against Postgres 16 and dumping the result, then naming the constraints and
-- adding the indexes Hibernate does not infer. `ddl-auto: validate` checks this
-- file and the entities still agree on every boot.
--
-- Postgres does not index foreign keys automatically, so every column used to
-- look rows up gets one explicitly.

CREATE TABLE users (
    id            uuid                        NOT NULL,
    email         varchar(255)                NOT NULL,
    password_hash varchar(255)                NOT NULL,
    display_name  varchar(255)                NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE repositories (
    id         uuid                        NOT NULL,
    name       varchar(255)                NOT NULL,
    db_engine  varchar(255)                NOT NULL,
    created_by uuid                        NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_repositories PRIMARY KEY (id),
    CONSTRAINT uq_repositories_name UNIQUE (name),
    CONSTRAINT fk_repositories_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_repositories_db_engine CHECK (db_engine IN ('POSTGRESQL', 'MYSQL', 'SQLITE', 'ORACLE', 'GENERIC'))
);

-- Ordered listing for the sidebar's paged repository list.
CREATE INDEX ix_repositories_created_at ON repositories (created_at DESC);

CREATE TABLE schema_versions (
    id                 uuid                        NOT NULL,
    repository_id      uuid                        NOT NULL,
    schema_data        text                        NOT NULL,
    -- Comma-separated parent ids; a merge version has two. Kept as text because
    -- ancestry is walked in the service layer, not in SQL.
    parent_version_ids varchar(255),
    commit_message     varchar(255),
    created_by         uuid                        NOT NULL,
    created_at         timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_schema_versions PRIMARY KEY (id),
    CONSTRAINT fk_schema_versions_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX ix_schema_versions_repository ON schema_versions (repository_id, created_at DESC);

CREATE TABLE branches (
    id                 uuid                        NOT NULL,
    repository_id      uuid                        NOT NULL,
    name               varchar(255)                NOT NULL,
    source_branch_name varchar(255),
    head_version_id    uuid,
    created_by         uuid                        NOT NULL,
    created_at         timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_branches PRIMARY KEY (id),
    CONSTRAINT uq_branches_repository_name UNIQUE (repository_id, name),
    CONSTRAINT fk_branches_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX ix_branches_repository ON branches (repository_id, created_at DESC);

CREATE TABLE merge_requests (
    id                     uuid                        NOT NULL,
    repository_id          uuid                        NOT NULL,
    source_branch_id       uuid                        NOT NULL,
    target_branch_id       uuid                        NOT NULL,
    -- Heads captured when the merge request was opened. Approval validity is
    -- decided against the branches' current heads, not against these.
    source_head_version_id uuid,
    target_head_version_id uuid,
    status                 varchar(255)                NOT NULL,
    created_by             uuid                        NOT NULL,
    requested_approver_id  uuid,
    created_at             timestamp(6) with time zone NOT NULL,
    merged_by              uuid,
    merged_at              timestamp(6) with time zone,
    CONSTRAINT pk_merge_requests PRIMARY KEY (id),
    CONSTRAINT fk_merge_requests_source_branch FOREIGN KEY (source_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_merge_requests_target_branch FOREIGN KEY (target_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_merge_requests_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_merge_requests_requested_approver FOREIGN KEY (requested_approver_id) REFERENCES users (id),
    CONSTRAINT fk_merge_requests_merged_by FOREIGN KEY (merged_by) REFERENCES users (id),
    CONSTRAINT ck_merge_requests_status CHECK (status IN ('OPEN', 'APPROVED', 'STALE', 'MERGED', 'CLOSED'))
);

CREATE INDEX ix_merge_requests_repository ON merge_requests (repository_id, created_at DESC);
-- Backs the "is there already an open MR for this branch pair?" guard.
CREATE INDEX ix_merge_requests_branch_pair ON merge_requests (repository_id, source_branch_id, target_branch_id, status);

CREATE TABLE merge_request_approvals (
    id                     uuid                        NOT NULL,
    merge_request_id       uuid                        NOT NULL,
    user_id                uuid                        NOT NULL,
    -- The exact heads this approval reviewed. If either branch has moved since,
    -- the approval is stale and no longer permits a merge.
    source_head_version_id uuid                        NOT NULL,
    target_head_version_id uuid                        NOT NULL,
    created_at             timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_merge_request_approvals PRIMARY KEY (id),
    -- Stops one reviewer approving the same version pair repeatedly. Stale
    -- approvals are never deleted, so re-approving after a commit is a new row.
    CONSTRAINT uq_merge_request_approvals_version_pair
        UNIQUE (merge_request_id, user_id, source_head_version_id, target_head_version_id),
    CONSTRAINT fk_merge_request_approvals_merge_request FOREIGN KEY (merge_request_id) REFERENCES merge_requests (id),
    CONSTRAINT fk_merge_request_approvals_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_merge_request_approvals_merge_request ON merge_request_approvals (merge_request_id);

CREATE TABLE audit_events (
    id            uuid                        NOT NULL,
    repository_id uuid,
    user_id       uuid                        NOT NULL,
    action_type   varchar(255)                NOT NULL,
    entity_type   varchar(255),
    entity_id     varchar(255),
    metadata      text,
    created_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

-- The audit trail is only ever read newest-first, per repository or globally.
CREATE INDEX ix_audit_events_repository ON audit_events (repository_id, created_at DESC);
CREATE INDEX ix_audit_events_user ON audit_events (user_id, created_at DESC);
CREATE INDEX ix_audit_events_created_at ON audit_events (created_at DESC);
