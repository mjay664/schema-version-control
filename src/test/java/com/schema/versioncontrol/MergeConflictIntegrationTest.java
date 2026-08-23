package com.schema.versioncontrol;

import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.repository.UserRepository;
import com.schema.versioncontrol.service.SchemaVersionService;
import com.schema.versioncontrol.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class MergeConflictIntegrationTest {

    @Autowired
    private SchemaVersionService schemaVersionService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User devUser;
    private RepositoryDto repo;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
        AuthResponse auth = userService.registerUser(new RegisterRequest("dev@example.com", "password123", "Developer"));
        devUser = userService.findUserEntityByEmail("dev@example.com");

        repo = schemaVersionService.createRepository(new CreateRepoRequest("conflict_test_repo", DatabaseEngine.POSTGRESQL), devUser);
    }

    @Test
    public void testTwoBranchesDivergingFromSameBaseDetectConflictOnMerge() {
        // 1. Commit base schema on main (Table users with email VARCHAR(100))
        // Note: Direct commits on main are restricted, so we commit on feature/base and merge to main
        BranchDto baseBranch = schemaVersionService.createBranch(repo.getId(), new CreateBranchRequest("feature/base", "main"), devUser);
        
        String initialSchema = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\",\"primaryKey\":true},{\"name\":\"email\",\"type\":\"VARCHAR(100)\"}]}]}";
        schemaVersionService.commitSchema(repo.getId(), new CommitSchemaRequest("feature/base", initialSchema, "Base schema"), devUser);
        
        // Merge feature/base into main to establish common ancestor state
        MergeResultDto baseMerge = schemaVersionService.merge(repo.getId(), new MergeBranchRequest("feature/base", "main", null), devUser);
        assertTrue(baseMerge.isSuccess());

        // 2. Create feature/branch-a and feature/branch-b from main (both start at same common ancestor)
        schemaVersionService.createBranch(repo.getId(), new CreateBranchRequest("feature/branch-a", "main"), devUser);
        schemaVersionService.createBranch(repo.getId(), new CreateBranchRequest("feature/branch-b", "main"), devUser);

        // 3. Branch A changes email type to VARCHAR(500)
        String schemaA = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\",\"primaryKey\":true},{\"name\":\"email\",\"type\":\"VARCHAR(500)\"}]}]}";
        schemaVersionService.commitSchema(repo.getId(), new CommitSchemaRequest("feature/branch-a", schemaA, "Expand email to 500"), devUser);

        // 4. Branch B changes email type to TEXT
        String schemaB = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\",\"primaryKey\":true},{\"name\":\"email\",\"type\":\"TEXT\"}]}]}";
        schemaVersionService.commitSchema(repo.getId(), new CommitSchemaRequest("feature/branch-b", schemaB, "Change email to TEXT"), devUser);

        // 5. Merge Branch A into main
        MergeResultDto mergeA = schemaVersionService.merge(repo.getId(), new MergeBranchRequest("feature/branch-a", "main", null), devUser);
        assertTrue(mergeA.isSuccess());
        assertFalse(mergeA.isHasConflicts());

        // 6. Attempt to merge Branch B into main -> MUST DETECT CONFLICT!
        MergeResultDto mergeB = schemaVersionService.merge(repo.getId(), new MergeBranchRequest("feature/branch-b", "main", null), devUser);
        
        assertFalse(mergeB.isSuccess(), "Merge should fail due to conflict");
        assertTrue(mergeB.isHasConflicts(), "Merge result must report conflicts");
        assertNotNull(mergeB.getConflicts(), "Conflicts list must not be null");
        assertFalse(mergeB.getConflicts().isEmpty(), "Conflicts list must contain the detected column conflict");
        
        System.out.println("Successfully detected 3-way merge conflict: " + mergeB.getConflicts());

        // 7. Resolve conflict explicitly and merge Branch B into main
        String resolvedSchema = "{\"tables\":[{\"name\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"UUID\",\"primaryKey\":true},{\"name\":\"email\",\"type\":\"TEXT\"}]}]}";
        MergeResultDto resolvedMerge = schemaVersionService.merge(repo.getId(), new MergeBranchRequest("feature/branch-b", "main", resolvedSchema), devUser);
        
        assertTrue(resolvedMerge.isSuccess(), "Merge with resolved schema should succeed");
    }
}
