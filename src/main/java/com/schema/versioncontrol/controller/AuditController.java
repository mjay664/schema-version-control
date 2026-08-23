package com.schema.versioncontrol.controller;

import com.schema.versioncontrol.dto.AuditEventDto;
import com.schema.versioncontrol.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditEventDto>> getRepositoryAuditTrail(@PathVariable("id") UUID repositoryId) {
        log.debug("GET /api/repositories/{}/audit", repositoryId);
        return ResponseEntity.ok(auditService.getAuditTrailForRepository(repositoryId));
    }

    @GetMapping("/audit/all")
    public ResponseEntity<List<AuditEventDto>> getAllAuditTrail() {
        return ResponseEntity.ok(auditService.getAllAuditEvents());
    }
}
