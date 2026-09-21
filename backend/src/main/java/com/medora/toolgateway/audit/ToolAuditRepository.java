package com.medora.toolgateway.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ToolAuditRepository extends JpaRepository<ToolAuditEntity, Long>, JpaSpecificationExecutor<ToolAuditEntity> {
}
