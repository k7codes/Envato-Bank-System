package com.envatobank.api.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.common.util.Hashing;

import tools.jackson.databind.ObjectMapper;

/**
 * Append-only, tamper-evident audit trail.
 * <p>
 * Every entry stores a SHA-256 hash over the previous entry's hash plus the
 * canonical representation of the current record, forming a hash chain. Any
 * tampering with a historic row breaks every subsequent hash and is detectable.
 * <p>
 * Appends are serialized in-process so the chain stays linear.
 */
@Service
public class AuditService {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;
    private final Object appendLock = new Object();

    public AuditService(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, UUID actorId, String actorType,
            String entityType, String entityId, Map<String, Object> details) {

        synchronized (appendLock) {
            String previousHash = repository.findTopByOrderByIdDesc()
                    .map(AuditLog::getEntryHash)
                    .orElse(GENESIS_HASH);

            Instant occurredAt = Instant.now();
            String detailsJson = details == null ? null : objectMapper.writeValueAsString(details);

            String canonical = String.join("|",
                    occurredAt.toString(),
                    actorId == null ? "" : actorId.toString(),
                    actorType == null ? "" : actorType,
                    action,
                    entityType == null ? "" : entityType,
                    entityId == null ? "" : entityId,
                    detailsJson == null ? "" : detailsJson);

            String entryHash = Hashing.sha256Hex(previousHash + "|" + canonical);

            AuditLog log = new AuditLog();
            log.setOccurredAt(occurredAt);
            log.setActorId(actorId);
            log.setActorType(actorType);
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setDetails(detailsJson);
            log.setPreviousHash(previousHash);
            log.setEntryHash(entryHash);
            repository.save(log);
        }
    }

    public String genesisHash() {
        return GENESIS_HASH;
    }
}
