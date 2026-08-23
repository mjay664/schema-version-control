package com.schema.versioncontrol.constants;

public final class AuditConstants {

    private AuditConstants() {}

    // Actions
    public static final String ACTION_USER_REGISTERED = "USER_REGISTERED";
    public static final String ACTION_REPOSITORY_CREATED = "REPOSITORY_CREATED";
    public static final String ACTION_BRANCH_CREATED = "BRANCH_CREATED";
    public static final String ACTION_TABLE_CREATED = "TABLE_CREATED";
    public static final String ACTION_TABLE_DROPPED = "TABLE_DROPPED";
    public static final String ACTION_COLUMN_CREATED = "COLUMN_CREATED";
    public static final String ACTION_COLUMN_DROPPED = "COLUMN_DROPPED";
    public static final String ACTION_COLUMN_MODIFIED = "COLUMN_MODIFIED";
    public static final String ACTION_MERGE_STARTED = "MERGE_STARTED";
    public static final String ACTION_MERGE_COMPLETED = "MERGE_COMPLETED";
    public static final String ACTION_MERGE_CONFLICT_DETECTED = "MERGE_CONFLICT_DETECTED";
    public static final String ACTION_MERGE_CONFLICT_RESOLVED = "MERGE_CONFLICT_RESOLVED";
    public static final String ACTION_SCHEMA_UPDATED = "SCHEMA_UPDATED";

    // Merge Request Actions
    public static final String ACTION_MERGE_REQUEST_CREATED = "MERGE_REQUEST_CREATED";
    public static final String ACTION_MERGE_REQUEST_APPROVED = "MERGE_REQUEST_APPROVED";
    public static final String ACTION_MERGE_REQUEST_APPROVAL_INVALIDATED = "MERGE_REQUEST_APPROVAL_INVALIDATED";
    public static final String ACTION_MERGE_REQUEST_MERGED = "MERGE_REQUEST_MERGED";

    // Entity Types
    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_REPOSITORY = "REPOSITORY";
    public static final String ENTITY_BRANCH = "BRANCH";
    public static final String ENTITY_TABLE = "TABLE";
    public static final String ENTITY_COLUMN = "COLUMN";
    public static final String ENTITY_MERGE = "MERGE";
    public static final String ENTITY_SCHEMA = "SCHEMA";
    public static final String ENTITY_MERGE_REQUEST = "MERGE_REQUEST";
}
