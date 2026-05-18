package com.medora.agent.trace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AgentTraceRepository extends JpaRepository<AgentTraceEntity, Long>,
        JpaSpecificationExecutor<AgentTraceEntity> {

    Optional<AgentTraceEntity> findByTraceId(String traceId);
}
