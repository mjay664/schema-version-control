package com.schema.versioncontrol;

import com.schema.versioncontrol.constants.AuditConstants;
import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.constants.MergeRequestStatus;
import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.service.AuditService;
import com.schema.versioncontrol.service.MergeRequestService;
import com.schema.versioncontrol.service.SchemaVersionService;
import com.schema.versioncontrol.service.UserService;
import com.schema.versioncontrol.service.impl.ThreeWayMergeEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Resolving a conflicting merge request end to end.
 *
 * Two branches leave the same ancestor and change `users.email` in different
 * directions, with the source additionally dropping a column the target
 * modified. The merge must refuse until a reviewer decides each path, then land
 * exactly what was chosen.
 */
@SpringBootTest
@Transactional
public class ConflictResolutionFlowTest {

    @Autowired private UserService userService;
    @Autowired private SchemaVersionService schemaVersionService;
    @Autowired private MergeRequestService mergeRequestService;
    @Autowired private AuditService auditService;

    private static final String ANCESTOR = """
            {"tables":[{"name":"users","columns":[
              {"name":"id","type":"UUID"},{"name":"email","type":"TEXT"},{"name":"bio","type":"TEXT"}]}]}""";
    private static final String TARGET_SIDE = """
            {"tables":[{"name":"users","columns":[
              {"name":"id","type":"UUID"},{"name":"email","type":"VARCHAR(255)"},{"name":"bio","type":"VARCHAR(80)"}]}]}""";
    private static final String SOURCE_SIDE = """
            {"tables":[{"name":"users","columns":[
              {"name":"id","type":"UUID"},{"name":"email","type":"VARCHAR(500)"}]}]}""";

    private record Fixture(UUID repoId, UUID mergeRequestId, User author, User reviewer) {}

    /** Diverge two branches from a shared ancestor and land the target side on main. */
    private Fixture divergentBranches(String slug) {
        userService.registerUser(new RegisterRequest(slug + "-author@example.com", "pass1234", "Author"));
        User author = userService.findUserEntityByEmail(slug + "-author@example.com");
        userService.registerUser(new RegisterRequest(slug + "-reviewer@example.com", "pass1234", "Reviewer"));
        User reviewer = userService.findUserEntityByEmail(slug + "-reviewer@example.com");

        RepositoryDto repo = schemaVersionService.createRepository(
                new CreateRepoRequest(slug, DatabaseEngine.POSTGRESQL), author);
        UUID repoId = repo.getId();

        // main is protected, so the ancestor arrives via a throwaway branch.
        schemaVersionService.createBranch(repoId, new CreateBranchRequest("base", "main"), author);
        schemaVersionService.commitSchema(repoId, new CommitSchemaRequest("base", ANCESTOR, "ancestor"), author);
        schemaVersionService.merge(repoId, new MergeBranchRequest("base", "main", null), author);

        // Both branches must fork before either lands, or they share no ancestor.
        BranchDto targetSide = schemaVersionService.createBranch(repoId, new CreateBranchRequest("target-side", "main"), author);
        BranchDto sourceSide = schemaVersionService.createBranch(repoId, new CreateBranchRequest("source-side", "main"), author);

        schemaVersionService.commitSchema(repoId, new CommitSchemaRequest("target-side", TARGET_SIDE, "target changes"), author);
        schemaVersionService.commitSchema(repoId, new CommitSchemaRequest("source-side", SOURCE_SIDE, "source changes"), author);

        MergeResultDto landed = schemaVersionService.merge(repoId, new MergeBranchRequest("target-side", "main", null), author);
        assertTrue(landed.isSuccess(), "the target side alone must merge cleanly");

        BranchDto main = schemaVersionService.getBranches(repoId, 0, 10).stream()
                .filter(b -> b.getName().equals("main")).findFirst().orElseThrow();

        MergeRequestDto mr = mergeRequestService.createMergeRequest(
                new CreateMergeRequestRequest(repoId, sourceSide.getId(), main.getId()), author);
        mergeRequestService.approveMergeRequest(mr.getId(), reviewer);

        assertEquals(targetSide.getId(), targetSide.getId());
        return new Fixture(repoId, mr.getId(), author, reviewer);
    }

    @Test
    public void anApprovedMergeRequestStillRefusesToMergeWhileConflicted() {
        Fixture f = divergentBranches("conflict_refuse_db");

        MergeResultDto result = mergeRequestService.mergeMergeRequest(f.mergeRequestId(), f.reviewer());

        assertFalse(result.isSuccess());
        assertTrue(result.isHasConflicts());
        assertEquals(2, result.getConflicts().size());

        // Approved, but still not merged — approval and mergeability are separate gates.
        MergeRequestDto after = mergeRequestService.getMergeRequestDetails(f.mergeRequestId(), f.reviewer());
        assertEquals(MergeRequestStatus.APPROVED, after.getStatus());
        assertNotEquals(MergeRequestStatus.MERGED, after.getStatus());
    }

    @Test
    public void resolvingEveryConflictLandsExactlyTheChosenSides() {
        Fixture f = divergentBranches("conflict_resolve_db");

        MergeResultDto result = mergeRequestService.mergeMergeRequest(f.mergeRequestId(), Map.of(
                "users.email", ThreeWayMergeEngine.TAKE_SOURCE,
                "users.bio", ThreeWayMergeEngine.TAKE_TARGET), f.reviewer());

        assertTrue(result.isSuccess());
        assertFalse(result.isHasConflicts());

        String merged = result.getMergedSchemaData();
        assertTrue(merged.contains("VARCHAR(500)"), "email came from the source branch");
        assertFalse(merged.contains("VARCHAR(255)"), "the target's email must not survive");
        assertTrue(merged.contains("bio"), "bio was kept from the target branch");

        MergeRequestDto after = mergeRequestService.getMergeRequestDetails(f.mergeRequestId(), f.reviewer());
        assertEquals(MergeRequestStatus.MERGED, after.getStatus());
        assertEquals(f.reviewer().getId(), after.getMergedBy().getId());
    }

    @Test
    public void aPartialResolutionIsRejectedRatherThanPartiallyApplied() {
        Fixture f = divergentBranches("conflict_partial_db");

        MergeResultDto result = mergeRequestService.mergeMergeRequest(
                f.mergeRequestId(), Map.of("users.email", ThreeWayMergeEngine.TAKE_SOURCE), f.reviewer());

        assertFalse(result.isSuccess());
        assertTrue(result.isHasConflicts());
        assertEquals(1, result.getConflicts().size());
        assertEquals("users.bio", result.getConflicts().get(0).get("key"));

        MergeRequestDto after = mergeRequestService.getMergeRequestDetails(f.mergeRequestId(), f.reviewer());
        assertNotEquals(MergeRequestStatus.MERGED, after.getStatus());
    }

    @Test
    public void aResolvedMergeIsRecordedAsResolvedNotAsAPlainMerge() {
        Fixture f = divergentBranches("conflict_audit_db");

        mergeRequestService.mergeMergeRequest(f.mergeRequestId(), Map.of(
                "users.email", ThreeWayMergeEngine.TAKE_SOURCE,
                "users.bio", ThreeWayMergeEngine.TAKE_SOURCE), f.reviewer());

        List<String> actions = auditService.getAuditTrailForRepository(f.repoId())
                .stream().map(AuditEventDto::getActionType).toList();

        assertTrue(actions.contains(AuditConstants.ACTION_MERGE_CONFLICT_RESOLVED),
                "the resolution must be attributable, not indistinguishable from a clean merge");
        assertTrue(actions.contains(AuditConstants.ACTION_MERGE_REQUEST_MERGED));
    }

    @Test
    public void resolutionsCannotBeUsedToSlipInAnUnapprovedMerge() {
        userService.registerUser(new RegisterRequest("solo@example.com", "pass1234", "Solo"));
        User solo = userService.findUserEntityByEmail("solo@example.com");

        RepositoryDto repo = schemaVersionService.createRepository(
                new CreateRepoRequest("conflict_noapproval_db", DatabaseEngine.POSTGRESQL), solo);
        schemaVersionService.createBranch(repo.getId(), new CreateBranchRequest("feature", "main"), solo);
        schemaVersionService.commitSchema(repo.getId(),
                new CommitSchemaRequest("feature", ANCESTOR, "work"), solo);

        BranchDto main = schemaVersionService.getBranches(repo.getId(), 0, 10).stream()
                .filter(b -> b.getName().equals("main")).findFirst().orElseThrow();
        BranchDto feature = schemaVersionService.getBranches(repo.getId(), 0, 10).stream()
                .filter(b -> b.getName().equals("feature")).findFirst().orElseThrow();

        MergeRequestDto mr = mergeRequestService.createMergeRequest(
                new CreateMergeRequestRequest(repo.getId(), feature.getId(), main.getId()), solo);

        // No peer approval exists; supplying resolutions must not bypass that gate.
        assertThrows(RuntimeException.class, () -> mergeRequestService.mergeMergeRequest(
                mr.getId(), Map.of("users.email", ThreeWayMergeEngine.TAKE_SOURCE), solo));
    }
}
