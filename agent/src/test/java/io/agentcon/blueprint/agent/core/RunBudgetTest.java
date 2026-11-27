package io.agentcon.blueprint.agent.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunBudgetTest {
    @Test
    void deadlineInterruptsAnInFlightModelCall() throws Exception {
        var interrupted = new CountDownLatch(1);
        var model = new TestSupport.FakeModel(i -> {
            try { Thread.sleep(30_000); }
            catch (InterruptedException e) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return dev.langchain4j.data.message.AiMessage.from("late answer");
        });
        var session = new AgentSession("timeout");
        long start = System.nanoTime();
        var result = TestSupport.loop(model, new TestSupport.StubTools(n -> ""),
                new TestSupport.Limits(10, 12, 8000, Duration.ofMillis(200)), Clock.systemUTC()).run(session, "hello");
        assertEquals("time-limit", result.stopReason());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 3);
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    }

    @Test
    void presenterStopInterruptsAnInFlightOperation() throws Exception {
        var session = new AgentSession("stop");
        var started = new CountDownLatch(1);
        var result = new AtomicReference<AgentLoop.Outcome>();
        var model = new TestSupport.FakeModel(i -> {
            started.countDown();
            try { Thread.sleep(30_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return dev.langchain4j.data.message.AiMessage.from("late answer");
        });
        Thread runner = Thread.ofVirtual().start(() -> {
            session.bindWorker();
            result.set(TestSupport.loop(model, new TestSupport.StubTools(n -> ""),
                    TestSupport.Limits.defaults(), Clock.systemUTC()).run(session, "hello"));
        });
        assertTrue(started.await(3, TimeUnit.SECONDS));
        session.requestStop();
        runner.join(3000);
        assertFalse(runner.isAlive());
        assertEquals("cancelled", result.get().stopReason());
    }
}
