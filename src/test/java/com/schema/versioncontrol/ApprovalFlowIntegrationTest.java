package com.schema.versioncontrol;

import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.constants.MergeRequestStatus;
import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.exception.InvalidSchemaException;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.service.MergeRequestService;
import com.schema.versioncontrol.service.SchemaVersionService;
import com.schema.versioncontrol.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ApprovalFlowIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private SchemaVersionService schemaVersionService;

    @Autowired
    private MergeRequestService mergeRequestService;

    @Test
    public void testProtectedMainBranchAndApprovalFlow() {
        // 1. Register User A (Creator) & User B (Reviewer)
        AuthResponse resA = userService.registerUser(new RegisterRequest("userA@example.com", "pass1234", "Jay Creator"));
        User userA = userService.findUserEntityByEmail("userA@example.com");

        AuthResponse resB = userService.registerUser(new RegisterRequest("userB@example.com", "pass1234", "Alice Reviewer"));
        User userB = userService.findUserEntityByEmail("userB@example.com");

        // 2. Create Repository
        RepositoryDto repo = schemaVersionService.createRepository(new CreateRepoRequest("approval_db", DatabaseEngine.POSTGRESQL), userA);

        // 3. Test Protected Main Branch: Direct commit on 'main' must throw InvalidSchemaException
        String schema = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"}]}]}";
        CommitSchemaRequest mainCommit = new CommitSchemaRequest("main", schema, "Direct edit on main");
        assertThrows(InvalidSchemaException.class, () -> schemaVersionService.commitSchema(repo.getId(), mainCommit, userA));

        // 4. Create feature branch off main
        BranchDto featureBranch = schemaVersionService.createBranch(repo.getId(), new CreateBranchRequest("feature/orders", "main"), userA);
        BranchDto mainBranch = schemaVersionService.getBranches(repo.getId(), 0, 10).stream().filter(b -> b.getName().equals("main")).findFirst().get();

        // 5. Commit schema on feature branch
        schemaVersionService.commitSchema(repo.getId(), new CommitSchemaRequest("feature/orders", schema, "Add users table"), userA);

        // 6. User A creates Merge Request (feature/orders -> main)
        CreateMergeRequestRequest createMrReq = new CreateMergeRequestRequest(repo.getId(), featureBranch.getId(), mainBranch.getId());
        MergeRequestDto mr = mergeRequestService.createMergeRequest(createMrReq, userA);
        assertEquals(MergeRequestStatus.OPEN, mr.getStatus());

        // 7. Self-Approval Guard: User A cannot approve their own MR
        assertThrows(AccessDeniedException.class, () -> mergeRequestService.approveMergeRequest(mr.getId(), userA));

        // 8. User B approves MR -> Status becomes APPROVED
        MergeRequestDto approvedMr = mergeRequestService.approveMergeRequest(mr.getId(), userB);
        assertEquals(MergeRequestStatus.APPROVED, approvedMr.getStatus());
        assertTrue(approvedMr.isCanMerge());

        // 9. Stale Approval Guard: User A commits a new schema update on feature/orders
        String updatedSchema = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"email\",\"type\":\"VARCHAR(500)\"}]}]}";
        schemaVersionService.commitSchema(repo.getId(), new CommitSchemaRequest("feature/orders", updatedSchema, "Add email column VARCHAR(500)"), userA);

        // Dynamic status must evaluate to STALE
        MergeRequestDto staleMr = mergeRequestService.getMergeRequestDetails(mr.getId(), userB);
        assertEquals(MergeRequestStatus.STALE, staleMr.getStatus());
        assertFalse(staleMr.isCanMerge());

        // 10. User B re-approves updated MR -> Status becomes APPROVED again
        MergeRequestDto reapprovedMr = mergeRequestService.approveMergeRequest(mr.getId(), userB);
        assertEquals(MergeRequestStatus.APPROVED, reapprovedMr.getStatus());

        // 11. Execute Merge -> Status becomes MERGED
        MergeResultDto mergeRes = mergeRequestService.mergeMergeRequest(mr.getId(), userB);
        assertTrue(mergeRes.isSuccess());

        MergeRequestDto finalMr = mergeRequestService.getMergeRequestDetails(mr.getId(), userB);
        assertEquals(MergeRequestStatus.MERGED, finalMr.getStatus());
    }
}
