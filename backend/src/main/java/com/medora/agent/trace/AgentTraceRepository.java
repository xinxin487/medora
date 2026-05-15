package com.medora.agent.trace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentTraceRepository extends JpaRepository<AgentTraceEntity, Long> {

    Optional<AgentTraceEntity> findByTraceId(String traceId);
}
