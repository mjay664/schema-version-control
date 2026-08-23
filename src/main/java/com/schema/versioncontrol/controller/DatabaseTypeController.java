package com.schema.versioncontrol.controller;

import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.dto.DataTypeDto;
import com.schema.versioncontrol.service.DatabaseTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DatabaseTypeController {

    private final DatabaseTypeService databaseTypeService;

    @GetMapping("/database-types")
    public ResponseEntity<List<DataTypeDto>> getDataTypesForEngine(@RequestParam(value = "engine", required = false) String engineStr) {
        DatabaseEngine engine = DatabaseEngine.fromString(engineStr);
        return ResponseEntity.ok(databaseTypeService.getDataTypesForEngine(engine));
    }

    @GetMapping("/repositories/{id}/datatypes")
    public ResponseEntity<List<DataTypeDto>> getDataTypesForRepository(@PathVariable("id") UUID repositoryId) {
        return ResponseEntity.ok(databaseTypeService.getDataTypesForRepository(repositoryId));
    }

    @GetMapping("/database-types/constraints")
    public ResponseEntity<List<com.schema.versioncontrol.dto.ConstraintTypeDto>> getConstraintsForEngine(@RequestParam(value = "engine", required = false) String engineStr) {
        DatabaseEngine engine = DatabaseEngine.fromString(engineStr);
        return ResponseEntity.ok(databaseTypeService.getConstraintsForEngine(engine));
    }

    @GetMapping("/repositories/{id}/constraints")
    public ResponseEntity<List<com.schema.versioncontrol.dto.ConstraintTypeDto>> getConstraintsForRepository(@PathVariable("id") UUID repositoryId) {
        return ResponseEntity.ok(databaseTypeService.getConstraintsForRepository(repositoryId));
    }
}
