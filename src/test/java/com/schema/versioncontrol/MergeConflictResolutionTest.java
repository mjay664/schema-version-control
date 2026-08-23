package com.schema.versioncontrol;

import com.schema.versioncontrol.service.impl.ThreeWayMergeEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conflict resolution in the three-way engine.
 *
 * "ours" is the target branch, "theirs" is the source branch. A resolution
 * settles one conflicting path by taking one side; anything left undecided must
 * still be reported so the merge stays blocked.
 */
public class MergeConflictResolutionTest {

    private static final String ANCESTOR = """
            {"tables":[
              {"name":"users","columns":[{"name":"id","type":"UUID"},{"name":"email","type":"TEXT"},{"name":"bio","type":"TEXT"}]},
              {"name":"sessions","columns":[{"name":"id","type":"UUID"},{"name":"token","type":"VARCHAR(64)"}]}
            ]}""";

    /** target: retypes email, narrows bio, keeps sessions untouched-but-present. */
    private static final String TARGET = """
            {"tables":[
              {"name":"users","columns":[{"name":"id","type":"UUID"},{"name":"email","type":"VARCHAR(255)"},{"name":"bio","type":"VARCHAR(80)"}]},
              {"name":"sessions","columns":[{"name":"id","type":"UUID"},{"name":"token","type":"VARCHAR(64)"}]}
            ]}""";

    /** source: retypes email differently, drops bio, and modifies sessions. */
    private static final String SOURCE = """
            {"tables":[
              {"name":"users","columns":[{"name":"id","type":"UUID"},{"name":"email","type":"VARCHAR(500)"}]},
              {"name":"sessions","columns":[{"name":"id","type":"UUID"},{"name":"token","type":"VARCHAR(128)"}]}
            ]}""";

    private static ThreeWayMergeEngine.MergeCalculation merge(Map<String, String> resolutions) {
        return ThreeWayMergeEngine.compute(ANCESTOR, TARGET, SOURCE, resolutions);
    }

    @Test
    public void unresolvedMergeReportsEveryConflictWithAStableKey() {
        ThreeWayMergeEngine.MergeCalculation result = merge(Map.of());

        assertTrue(result.hasConflicts());
        assertEquals(2, result.getConflicts().size(), "email type and bio modify/delete");

        Map<String, String> byKey = new java.util.HashMap<>();
        result.getConflicts().forEach(c -> byKey.put((String) c.get("key"), (String) c.get("type")));

        assertEquals("COLUMN_TYPE_CONFLICT", byKey.get("users.email"));
        assertEquals("COLUMN_MODIFY_DELETE_CONFLICT", byKey.get("users.bio"));
    }

    @Test
    public void conflictsCarryBothSidesSoAReviewerCanChoose() {
        Map<String, Object> emailConflict = merge(Map.of()).getConflicts().stream()
                .filter(c -> "users.email".equals(c.get("key")))
                .findFirst().orElseThrow();

        assertEquals("VARCHAR(255)", emailConflict.get("oursType"));
        assertEquals("VARCHAR(500)", emailConflict.get("theirsType"));
    }

    @Test
    public void takingTargetKeepsTheTargetDefinition() {
        ThreeWayMergeEngine.MergeCalculation result = merge(Map.of(
                "users.email", ThreeWayMergeEngine.TAKE_TARGET,
                "users.bio", ThreeWayMergeEngine.TAKE_TARGET));

        assertFalse(result.hasConflicts());
        assertTrue(result.getMergedSchemaJson().contains("VARCHAR(255)"));
        assertFalse(result.getMergedSchemaJson().contains("VARCHAR(500)"));
        assertTrue(result.getMergedSchemaJson().contains("bio"), "target modified bio, so it survives");
    }

    @Test
    public void takingSourceKeepsTheSourceDefinitionIncludingItsDeletions() {
        ThreeWayMergeEngine.MergeCalculation result = merge(Map.of(
                "users.email", ThreeWayMergeEngine.TAKE_SOURCE,
                "users.bio", ThreeWayMergeEngine.TAKE_SOURCE));

        assertFalse(result.hasConflicts());
        assertTrue(result.getMergedSchemaJson().contains("VARCHAR(500)"));
        assertFalse(result.getMergedSchemaJson().contains("\"bio\""), "source dropped bio, so it stays dropped");
    }

    @Test
    public void sidesCanBeMixedAcrossConflicts() {
        ThreeWayMergeEngine.MergeCalculation result = merge(Map.of(
                "users.email", ThreeWayMergeEngine.TAKE_SOURCE,
                "users.bio", ThreeWayMergeEngine.TAKE_TARGET));

        assertFalse(result.hasConflicts());
        assertTrue(result.getMergedSchemaJson().contains("VARCHAR(500)"));
        assertTrue(result.getMergedSchemaJson().contains("bio"));
    }

    @Test
    public void aPartialResolutionStillBlocksTheMerge() {
        ThreeWayMergeEngine.MergeCalculation result = merge(Map.of("users.email", ThreeWayMergeEngine.TAKE_SOURCE));

        assertTrue(result.hasConflicts(), "bio is still undecided");
        assertEquals(1, result.getConflicts().size());
        assertEquals("users.bio", result.getConflicts().get(0).get("key"));
    }

    @Test
    public void anUnrecognisedChoiceIsIgnoredRatherThanSilentlyPickingASide() {
        ThreeWayMergeEngine.MergeCalculation result = merge(Map.of(
                "users.email", "WHATEVER",
                "users.bio", ThreeWayMergeEngine.TAKE_TARGET));

        assertTrue(result.hasConflicts());
        assertEquals("users.email", result.getConflicts().get(0).get("key"));
    }

    @Test
    public void resolutionsForPathsThatDoNotConflictChangeNothing() {
        // sessions differs between the branches but only the source moved it,
        // so the engine takes the source cleanly and never asks.
        ThreeWayMergeEngine.MergeCalculation clean = merge(Map.of(
                "users.email", ThreeWayMergeEngine.TAKE_TARGET,
                "users.bio", ThreeWayMergeEngine.TAKE_TARGET,
                "sessions", ThreeWayMergeEngine.TAKE_TARGET));

        assertFalse(clean.hasConflicts());
        assertTrue(clean.getMergedSchemaJson().contains("VARCHAR(128)"),
                "a resolution must not override a non-conflicting fast-forward");
    }

    @Test
    public void tableLevelConflictCanBeResolvedEitherWay() {
        String ancestor = """
                {"tables":[{"name":"legacy","columns":[{"name":"id","type":"UUID"}]}]}""";
        String target = """
                {"tables":[{"name":"legacy","columns":[{"name":"id","type":"UUID"},{"name":"note","type":"TEXT"}]}]}""";
        String sourceDropsIt = """
                {"tables":[]}""";

        assertTrue(ThreeWayMergeEngine.compute(ancestor, target, sourceDropsIt, Map.of()).hasConflicts());

        ThreeWayMergeEngine.MergeCalculation keep =
                ThreeWayMergeEngine.compute(ancestor, target, sourceDropsIt, Map.of("legacy", ThreeWayMergeEngine.TAKE_TARGET));
        assertFalse(keep.hasConflicts());
        assertTrue(keep.getMergedSchemaJson().contains("legacy"));

        ThreeWayMergeEngine.MergeCalculation drop =
                ThreeWayMergeEngine.compute(ancestor, target, sourceDropsIt, Map.of("legacy", ThreeWayMergeEngine.TAKE_SOURCE));
        assertFalse(drop.hasConflicts());
        assertFalse(drop.getMergedSchemaJson().contains("legacy"));
    }

    @Test
    public void aCleanMergeIsUnaffectedByStrayResolutions() {
        String ancestor = """
                {"tables":[{"name":"users","columns":[{"name":"id","type":"UUID"}]}]}""";
        String target = """
                {"tables":[{"name":"users","columns":[{"name":"id","type":"UUID"},{"name":"a","type":"TEXT"}]}]}""";
        String source = """
                {"tables":[{"name":"users","columns":[{"name":"id","type":"UUID"},{"name":"b","type":"TEXT"}]}]}""";

        ThreeWayMergeEngine.MergeCalculation result =
                ThreeWayMergeEngine.compute(ancestor, target, source, Map.of("users.id", ThreeWayMergeEngine.TAKE_SOURCE));

        assertFalse(result.hasConflicts());
        assertTrue(result.getMergedSchemaJson().contains("\"a\""));
        assertTrue(result.getMergedSchemaJson().contains("\"b\""));
    }
}
