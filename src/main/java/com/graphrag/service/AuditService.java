package com.graphrag.service;

import com.graphrag.config.GraphRagProperties;
import com.graphrag.entity.AuditTrailEntity;
import com.graphrag.jpa.AuditTrailJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditTrailJpaRepository auditRepo;
    private final GraphRagProperties props;

    public AuditService(AuditTrailJpaRepository auditRepo, GraphRagProperties props) {
        this.auditRepo = auditRepo;
        this.props = props;
    }

    public void log(String action, String details) {
        try {
            AuditTrailEntity entity = new AuditTrailEntity();
            entity.setAction(action);
            entity.setDetails(details);
            entity.setVersion(props.version());
            entity.setTimestamp(Instant.now());
            auditRepo.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save audit trail entry: {}", e.getMessage());
        }
    }

    public List<AuditTrailEntity> getTrails(String version) {
        return auditRepo.findByVersion(version);
    }
}
