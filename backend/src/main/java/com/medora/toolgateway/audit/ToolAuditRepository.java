package com.medora.toolgateway.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ToolAuditRepository extends JpaRepository<ToolAuditEntity, Long>, JpaSpecificationExecutor<ToolAuditEntity> {
    List<ToolAuditEntity> findTop500ByToolNameOrderByCreatedAtDesc(String toolName);
}
