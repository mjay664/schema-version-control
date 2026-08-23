package com.schema.versioncontrol;

import com.schema.versioncontrol.service.impl.ThreeWayMergeEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaVersioningTest {

    @Test
    public void testCleanThreeWayMerge() {
        String ancestor = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"}]}]}";
        String ours = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"email\",\"type\":\"TEXT\"}]}]}";
        String theirs = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"created_at\",\"type\":\"TIMESTAMP\"}]}]}";

        ThreeWayMergeEngine.MergeCalculation result = ThreeWayMergeEngine.compute(ancestor, ours, theirs);

        assertFalse(result.hasConflicts());
        assertTrue(result.getMergedSchemaJson().contains("email"));
        assertTrue(result.getMergedSchemaJson().contains("created_at"));
    }

    @Test
    public void testConflictDetectionOnColumnTypeChange() {
        String ancestor = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"email\",\"type\":\"TEXT\"}]}]}";
        String ours = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"email\",\"type\":\"VARCHAR(255)\"}]}]}";
        String theirs = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"email\",\"type\":\"VARCHAR(100)\"}]}]}";

        ThreeWayMergeEngine.MergeCalculation result = ThreeWayMergeEngine.compute(ancestor, ours, theirs);

        assertTrue(result.hasConflicts());
        assertEquals(1, result.getConflicts().size());
        assertEquals("COLUMN_TYPE_CONFLICT", result.getConflicts().get(0).get("type"));
    }
}
