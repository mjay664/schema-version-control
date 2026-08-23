package com.schema.versioncontrol;

import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.service.AuditService;
import com.schema.versioncontrol.service.SchemaVersionService;
import com.schema.versioncontrol.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AuditabilityTransactionalTest {

    @Autowired
    private UserService userService;

    @Autowired
    private SchemaVersionService schemaVersionService;

    @Autowired
    private AuditService auditService;

    @Test
    public void testUserAttributionAndTransactionalAuditLog() {
        RegisterRequest regReq = new RegisterRequest("audituser@example.com", "secretPass123", "Audit Tester");
        AuthResponse authRes = userService.registerUser(regReq);
        assertNotNull(authRes.getUser().getId());

        User user = userService.findUserEntityByEmail("audituser@example.com");

        CreateRepoRequest repoReq = new CreateRepoRequest("ecommerce_db", DatabaseEngine.POSTGRESQL);
        RepositoryDto repo = schemaVersionService.createRepository(repoReq, user);
        assertEquals(user.getId(), repo.getCreatedBy().getId());
        assertEquals(DatabaseEngine.POSTGRESQL, repo.getDbEngine());

        CreateBranchRequest branchReq = new CreateBranchRequest("feature/users", "main");
        BranchDto branch = schemaVersionService.createBranch(repo.getId(), branchReq, user);
        assertEquals(user.getId(), branch.getCreatedBy().getId());

        String schema = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\"},{\"name\":\"email\",\"type\":\"VARCHAR(255)\"}]}]}";
        CommitSchemaRequest commitReq = new CommitSchemaRequest("feature/users", schema, "Add users table");
        schemaVersionService.commitSchema(repo.getId(), commitReq, user);

        List<AuditEventDto> auditEvents = auditService.getAuditTrailForRepository(repo.getId());
        assertFalse(auditEvents.isEmpty());

        assertTrue(auditEvents.stream().allMatch(e -> e.getUserDisplayName().equals("Audit Tester")));

        List<String> actionTypes = auditEvents.stream().map(AuditEventDto::getActionType).toList();
        assertTrue(actionTypes.contains("REPOSITORY_CREATED"));
        assertTrue(actionTypes.contains("BRANCH_CREATED"));
        assertTrue(actionTypes.contains("TABLE_CREATED"));
    }
}
