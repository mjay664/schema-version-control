package com.schema.versioncontrol.constants;

public enum DatabaseEngine {
    POSTGRESQL("PostgreSQL"),
    MYSQL("MySQL"),
    SQLITE("SQLite"),
    ORACLE("Oracle"),
    GENERIC("Generic ANSI SQL");

    private final String displayName;

    DatabaseEngine(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DatabaseEngine fromString(String text) {
        if (text == null || text.isBlank()) {
            return GENERIC;
        }
        for (DatabaseEngine engine : DatabaseEngine.values()) {
            if (engine.name().equalsIgnoreCase(text) || engine.displayName.equalsIgnoreCase(text)) {
                return engine;
            }
        }
        return GENERIC;
    }
}
