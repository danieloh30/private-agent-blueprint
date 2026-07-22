package io.agentcon.games.quiz;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side quiz session store — keyed by a UUID cookie set by QuizResource.
 * Uses ApplicationScoped + ConcurrentHashMap to work on the Vert.x reactive stack
 * without requiring the Undertow/Servlet session context.
 */
@ApplicationScoped
public class QuizSession {

    public static class State {
        int     currentIndex = 0;
        int     score        = 0;
        boolean finished     = false;

        void recordAnswer(String answer, boolean correct) {
            if (correct) score++;
            currentIndex++;
            if (currentIndex >= QuestionBank.ALL.size()) finished = true;
        }

        void reset() {
            currentIndex = 0;
            score        = 0;
            finished     = false;
        }
    }

    private final Map<String, State> sessions = new ConcurrentHashMap<>();

    public String createSession() {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new State());
        return id;
    }

    public State get(String id) {
        if (id == null) return null;
        return sessions.get(id);
    }

    public State getOrCreate(String id) {
        if (id == null || !sessions.containsKey(id)) {
            sessions.put(id == null ? "" : id, new State());
            if (id == null) {
                // caller must use createSession() — return a fresh state but don't store under null
                return new State();
            }
        }
        return sessions.get(id);
    }

    /** Evict a session (called on reset). */
    public void remove(String id) {
        if (id != null) sessions.remove(id);
    }
}
