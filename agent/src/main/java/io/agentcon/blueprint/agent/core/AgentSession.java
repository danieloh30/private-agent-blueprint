package io.agentcon.blueprint.agent.core;

import dev.langchain4j.data.message.ChatMessage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Conversation state for one presenter session. Lives in the agent process only (never in the MCP server). */
public final class AgentSession {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String id;
    private final List<ChatMessage> history = new ArrayList<>();
    private final List<TraceEvent> events = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private volatile Thread worker;

    public boolean stopRequested() { return stopRequested.get(); }
    public void prepareRun() { stopRequested.set(false); }
    public void bindWorker() {
        worker = Thread.currentThread();
        if (stopRequested.get()) worker.interrupt();
    }
    public void releaseWorker() { worker = null; }
    public void requestStop() {
        stopRequested.set(true);
        Thread active = worker;
        if (active != null) active.interrupt();
    }

    public AgentSession(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public List<ChatMessage> history() {
        return history;
    }

    public AtomicBoolean running() {
        return running;
    }

    public TraceEvent emit(TraceEvent.Kind kind, String title, String detail, String verdict, String rule, long durationMs) {
        TraceEvent e = new TraceEvent(seq.incrementAndGet(), LocalTime.now().format(TIME), kind.name(), title,
                detail == null ? "" : detail, verdict, rule, durationMs);
        synchronized (events) {
            events.add(e);
        }
        return e;
    }

    public TraceEvent emit(TraceEvent.Kind kind, String title, String detail) {
        return emit(kind, title, detail, null, null, 0);
    }

    public List<TraceEvent> eventsAfter(long after) {
        synchronized (events) {
            return events.stream().filter(e -> e.seq() > after).toList();
        }
    }
}
