package com.medora.toolgateway.execution;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
public class ToolIsolationRegistry {
    private final int permits; private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();
    public ToolIsolationRegistry() { this(4); }
    public ToolIsolationRegistry(int permits) { this.permits = Math.max(1, permits); }
    public Lease tryAcquire(String qualifiedToolName) {
        Semaphore semaphore = bulkheads.computeIfAbsent(qualifiedToolName, ignored -> new Semaphore(permits));
        return semaphore.tryAcquire() ? new Lease(semaphore) : null;
    }
    public static final class Lease implements AutoCloseable {
        private final Semaphore semaphore; private boolean released;
        private Lease(Semaphore semaphore) { this.semaphore = semaphore; }
        public synchronized void close() { if (!released) { released = true; semaphore.release(); } }
    }
}
