package com.schema.versioncontrol.model;

import com.schema.versioncontrol.constants.DatabaseEngine;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_engine", nullable = false)
    private DatabaseEngine dbEngine;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RepositoryEntity(String name, DatabaseEngine dbEngine, User createdBy) {
        this.name = name;
        this.dbEngine = dbEngine != null ? dbEngine : DatabaseEngine.POSTGRESQL;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (dbEngine == null) {
            dbEngine = DatabaseEngine.POSTGRESQL;
        }
    }
}
