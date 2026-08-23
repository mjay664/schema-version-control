package com.schema.versioncontrol.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ThreeWayMergeEngine {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Resolution choices a reviewer can make for a conflicting path. */
    public static final String TAKE_TARGET = "TARGET";
    public static final String TAKE_SOURCE = "SOURCE";

    /**
     * Stable identifier for a conflicting path: "table" or "table.column".
     * Emitted on every conflict as "key" so callers resolve conflicts by the
     * engine's own naming rather than reconstructing it and drifting.
     */
    public static String conflictKey(String tableName, String columnName) {
        return columnName == null ? tableName : tableName + "." + columnName;
    }

    private static String choiceFor(Map<String, String> resolutions, String key) {
        if (resolutions == null) return null;
        String choice = resolutions.get(key);
        if (choice == null) return null;
        String upper = choice.trim().toUpperCase(Locale.ROOT);
        return (TAKE_TARGET.equals(upper) || TAKE_SOURCE.equals(upper)) ? upper : null;
    }

    @Getter
    @AllArgsConstructor
    public static class MergeCalculation {
        private final boolean hasConflicts;
        private final String mergedSchemaJson;
        private final List<Map<String, Object>> conflicts;

        public boolean hasConflicts() {
            return hasConflicts;
        }
    }

    public static MergeCalculation compute(String ancestorJson, String oursJson, String theirsJson) {
        return compute(ancestorJson, oursJson, theirsJson, Map.of());
    }

    /**
     * Three-way merge. "ours" is the target branch, "theirs" is the source branch.
     *
     * `resolutions` maps a {@link #conflictKey} to {@link #TAKE_TARGET} or
     * {@link #TAKE_SOURCE}. A conflicting path with a resolution is settled that
     * way and is not reported; a path without one is still reported and still
     * blocks the merge. Passing an empty map reproduces the unresolved merge
     * exactly, which is how callers discover what needs deciding.
     */
    public static MergeCalculation compute(String ancestorJson, String oursJson, String theirsJson,
                                           Map<String, String> resolutions) {
        List<Map<String, Object>> conflicts = new ArrayList<>();

        try {
            Map<String, Map<String, Object>> ancestorTables = parseTables(ancestorJson);
            Map<String, Map<String, Object>> oursTables = parseTables(oursJson);
            Map<String, Map<String, Object>> theirsTables = parseTables(theirsJson);

            Set<String> allTableNames = new HashSet<>();
            allTableNames.addAll(ancestorTables.keySet());
            allTableNames.addAll(oursTables.keySet());
            allTableNames.addAll(theirsTables.keySet());

            List<Map<String, Object>> mergedTables = new ArrayList<>();

            for (String tableName : allTableNames) {
                Map<String, Object> ancT = ancestorTables.get(tableName);
                Map<String, Object> oursT = oursTables.get(tableName);
                Map<String, Object> theirsT = theirsTables.get(tableName);

                if (ancT == null) {
                    if (oursT != null && theirsT != null) {
                        Map<String, Object> mergedTable = mergeTableColumns(tableName, null, oursT, theirsT, conflicts, resolutions);
                        mergedTables.add(mergedTable);
                    } else if (oursT != null) {
                        mergedTables.add(oursT);
                    } else {
                        mergedTables.add(theirsT);
                    }
                } else {
                    boolean deletedInOurs = (oursT == null);
                    boolean deletedInTheirs = (theirsT == null);

                    if (deletedInOurs && deletedInTheirs) {
                        // Dropped by both
                    } else if (deletedInOurs && !deletedInTheirs) {
                        if (!theirsT.equals(ancT)) {
                            String choice = choiceFor(resolutions, conflictKey(tableName, null));
                            if (choice == null) {
                                conflicts.add(Map.of(
                                        "key", conflictKey(tableName, null),
                                        "tableName", tableName,
                                        "type", "MODIFY_DELETE_CONFLICT",
                                        "description", "Table '" + tableName + "' was deleted in target branch but modified in source branch"
                                ));
                            } else if (TAKE_SOURCE.equals(choice)) {
                                mergedTables.add(theirsT);
                            }
                            // TAKE_TARGET keeps the deletion: nothing to add.
                        }
                    } else if (!deletedInOurs && deletedInTheirs) {
                        if (!oursT.equals(ancT)) {
                            String choice = choiceFor(resolutions, conflictKey(tableName, null));
                            if (choice == null) {
                                conflicts.add(Map.of(
                                        "key", conflictKey(tableName, null),
                                        "tableName", tableName,
                                        "type", "DELETE_MODIFY_CONFLICT",
                                        "description", "Table '" + tableName + "' was modified in target branch but deleted in source branch"
                                ));
                            } else if (TAKE_TARGET.equals(choice)) {
                                mergedTables.add(oursT);
                            }
                            // TAKE_SOURCE keeps the deletion: nothing to add.
                        }
                    } else {
                        Map<String, Object> mergedTable = mergeTableColumns(tableName, ancT, oursT, theirsT, conflicts, resolutions);
                        mergedTables.add(mergedTable);
                    }
                }
            }

            Map<String, Object> finalSchema = Map.of("tables", mergedTables);
            String mergedJson = objectMapper.writeValueAsString(finalSchema);
            return new MergeCalculation(!conflicts.isEmpty(), mergedJson, conflicts);

        } catch (Exception e) {
            conflicts.add(Map.of("type", "PARSE_ERROR", "description", e.getMessage()));
            return new MergeCalculation(true, oursJson, conflicts);
        }
    }

    private static Map<String, Object> mergeTableColumns(String tableName,
                                                         Map<String, Object> ancT,
                                                         Map<String, Object> oursT,
                                                         Map<String, Object> theirsT,
                                                         List<Map<String, Object>> conflicts,
                                                         Map<String, String> resolutions) {

        Map<String, Map<String, Object>> ancCols = parseColumns(ancT);
        Map<String, Map<String, Object>> oursCols = parseColumns(oursT);
        Map<String, Map<String, Object>> theirsCols = parseColumns(theirsT);

        Set<String> allCols = new HashSet<>();
        allCols.addAll(ancCols.keySet());
        allCols.addAll(oursCols.keySet());
        allCols.addAll(theirsCols.keySet());

        List<Map<String, Object>> mergedCols = new ArrayList<>();

        for (String colName : allCols) {
            Map<String, Object> ancC = ancCols.get(colName);
            Map<String, Object> oursC = oursCols.get(colName);
            Map<String, Object> theirsC = theirsCols.get(colName);

            if (ancC == null) {
                if (oursC != null && theirsC != null) {
                    if (oursC.equals(theirsC)) {
                        mergedCols.add(oursC);
                    } else {
                        String choice = choiceFor(resolutions, conflictKey(tableName, colName));
                        if (choice == null) {
                            conflicts.add(Map.of(
                                    "key", conflictKey(tableName, colName),
                                    "tableName", tableName,
                                    "columnName", colName,
                                    "type", "COLUMN_ADD_CONFLICT",
                                    "description", "Column '" + colName + "' added in both branches with conflicting attributes",
                                    "ours", oursC,
                                    "theirs", theirsC
                            ));
                        }
                        mergedCols.add(TAKE_SOURCE.equals(choice) ? theirsC : oursC);
                    }
                } else if (oursC != null) {
                    mergedCols.add(oursC);
                } else {
                    mergedCols.add(theirsC);
                }
            } else {
                if (oursC == null && theirsC == null) {
                    // Both dropped
                } else if (oursC == null && theirsC != null) {
                    if (!theirsC.equals(ancC)) {
                        String choice = choiceFor(resolutions, conflictKey(tableName, colName));
                        if (choice == null) {
                            conflicts.add(Map.of(
                                    "key", conflictKey(tableName, colName),
                                    "tableName", tableName,
                                    "columnName", colName,
                                    "type", "COLUMN_DELETE_MODIFY_CONFLICT",
                                    "description", "Column '" + colName + "' dropped in target but modified in source"
                            ));
                        } else if (TAKE_SOURCE.equals(choice)) {
                            mergedCols.add(theirsC);
                        }
                        // TAKE_TARGET keeps the drop.
                    }
                } else if (oursC != null && theirsC == null) {
                    if (!oursC.equals(ancC)) {
                        String choice = choiceFor(resolutions, conflictKey(tableName, colName));
                        if (choice == null) {
                            conflicts.add(Map.of(
                                    "key", conflictKey(tableName, colName),
                                    "tableName", tableName,
                                    "columnName", colName,
                                    "type", "COLUMN_MODIFY_DELETE_CONFLICT",
                                    "description", "Column '" + colName + "' modified in target but dropped in source"
                            ));
                        } else if (TAKE_TARGET.equals(choice)) {
                            mergedCols.add(oursC);
                        }
                        // TAKE_SOURCE keeps the drop.
                    }
                } else {
                    String oursType = String.valueOf(oursC.get("type"));
                    String theirsType = String.valueOf(theirsC.get("type"));
                    String ancType = String.valueOf(ancC.get("type"));

                    if (oursType.equals(theirsType)) {
                        mergedCols.add(oursC);
                    } else if (oursType.equals(ancType)) {
                        mergedCols.add(theirsC);
                    } else if (theirsType.equals(ancType)) {
                        mergedCols.add(oursC);
                    } else {
                        String choice = choiceFor(resolutions, conflictKey(tableName, colName));
                        if (choice == null) {
                            conflicts.add(Map.of(
                                    "key", conflictKey(tableName, colName),
                                    "tableName", tableName,
                                    "columnName", colName,
                                    "type", "COLUMN_TYPE_CONFLICT",
                                    "description", "Column '" + colName + "' modified to '" + oursType + "' in target but '" + theirsType + "' in source",
                                    "oursType", oursType,
                                    "theirsType", theirsType,
                                    "ours", oursC,
                                    "theirs", theirsC
                            ));
                        }
                        mergedCols.add(TAKE_SOURCE.equals(choice) ? theirsC : oursC);
                    }
                }
            }
        }

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", tableName);
        table.put("columns", mergedCols);

        if (oursT != null && oursT.containsKey("indexes")) {
            table.put("indexes", oursT.get("indexes"));
        } else if (theirsT != null && theirsT.containsKey("indexes")) {
            table.put("indexes", theirsT.get("indexes"));
        }

        if (oursT != null && oursT.containsKey("constraints")) {
            table.put("constraints", oursT.get("constraints"));
        } else if (theirsT != null && theirsT.containsKey("constraints")) {
            table.put("constraints", theirsT.get("constraints"));
        }
        return table;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parseTables(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object tablesObj = root.get("tables");
            if (tablesObj instanceof List) {
                Map<String, Map<String, Object>> map = new HashMap<>();
                for (Object item : (List<?>) tablesObj) {
                    if (item instanceof Map) {
                        Map<String, Object> t = (Map<String, Object>) item;
                        Object name = t.get("name");
                        if (name != null) {
                            map.put(name.toString(), t);
                        }
                    }
                }
                return map;
            }
        } catch (Exception ignored) {}
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parseColumns(Map<String, Object> table) {
        if (table == null) return Map.of();
        Object colsObj = table.get("columns");
        if (colsObj instanceof List) {
            Map<String, Map<String, Object>> map = new HashMap<>();
            for (Object item : (List<?>) colsObj) {
                if (item instanceof Map) {
                    Map<String, Object> c = (Map<String, Object>) item;
                    Object name = c.get("name");
                    if (name != null) {
                        map.put(name.toString(), c);
                    }
                }
            }
            return map;
        }
        return Map.of();
    }
}
