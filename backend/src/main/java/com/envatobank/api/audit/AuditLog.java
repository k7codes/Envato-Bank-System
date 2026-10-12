package com.envatobank.api.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tamper-evident audit row. {@code entryHash = sha256(previousHash | canonical)}.
 * Append-only; blocks updates/deletes via database trigger.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @Column(name = "details", updatable = false, columnDefinition = "text")
    private String details;

    @Column(name = "previous_hash", nullable = false, updatable = false)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, updatable = false)
    private String entryHash;
}
