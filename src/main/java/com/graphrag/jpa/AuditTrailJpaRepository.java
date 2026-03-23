package com.graphrag.jpa;

import com.graphrag.entity.AuditTrailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditTrailJpaRepository extends JpaRepository<AuditTrailEntity, Long> {
    List<AuditTrailEntity> findByVersion(String version);
    List<AuditTrailEntity> findByAction(String action);
}
