package io.agentcon.blueprint.agent.core;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One wall-clock deadline shared by model discovery, inference and tool calls. */
final class RunBudget {
    static final class Stopped extends RuntimeException {
        final String reason;
        Stopped(String reason) { super(reason); this.reason = reason; }
    }
    private final long deadline;
    private final AgentSession session;
    int steps;
    int toolCalls;

    RunBudget(Duration duration, AgentSession session) {
        this.deadline = System.nanoTime() + duration.toNanos();
        this.session = session;
    }

    <T> T call(Callable<T> operation) {
        if (session.stopRequested() || Thread.currentThread().isInterrupted()) throw new Stopped("cancelled");
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new Stopped("time-limit");
        FutureTask<T> future = new FutureTask<>(operation);
        Thread worker = Thread.ofVirtual().name("agent-operation").start(future);
        try {
            return future.get(left, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new Stopped("time-limit");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Stopped("cancelled");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } finally {
            if (!future.isDone()) { future.cancel(true); worker.interrupt(); }
        }
    }
}
