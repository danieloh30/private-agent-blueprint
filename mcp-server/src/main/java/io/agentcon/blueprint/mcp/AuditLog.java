package io.agentcon.blueprint.mcp;

import io.agentcon.blueprint.guardrails.Decision;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.vertx.core.json.JsonObject;
import org.jboss.logging.Logger;

/**
 * Append-only audit of every tool call and admin action.
 *
 * <p>Records hold metadata (tool, decision, path, hashes, sizes, findings), never file contents.
 * Every string value passes through the leakage inspector in redact mode before it is stored or logged.
 */
@ApplicationScoped
public class AuditLog {

    private static final Logger LOG = Logger.getLogger("blueprint.audit");
    private static final int KEEP = 500;

    private final WorkspaceManager workspaces;
    private final LeakageInspector inspector;
    private final List<Map<String, Object>> recent = Collections.synchronizedList(new ArrayList<>());

    public AuditLog(WorkspaceManager workspaces, LeakageInspector inspector) {
        this.workspaces = workspaces;
        this.inspector = inspector;
    }

    public void record(String actor, String action, Decision decision, Map<String, ?> details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("seq", recent.size() + 1);
        entry.put("time", Instant.now().toString());
        entry.put("actor", actor);
        entry.put("action", action);
        if (decision != null) {
            entry.put("verdict", decision.verdict().name());
            entry.put("rule", decision.rule());
            entry.put("reason", inspector.redactForLog(decision.reason()));
        }
        WorkspaceManager.Run run = workspaces.current();
        entry.put("workspace", run.id());
        details.forEach((k, v) -> entry.put(k, v instanceof String s ? inspector.redactForLog(truncate(s)) : v));
        synchronized (recent) {
            recent.add(entry);
            if (recent.size() > KEEP) {
                recent.removeFirst();
            }
        }
        try {
            Files.writeString(run.runDir().resolve("audit.jsonl"), new JsonObject(entry).encode() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warnf("Audit write failed: %s", e.getMessage());
        }
        LOG.infof("%s %s %s", actor, action, decision == null ? "" : decision.verdict() + " " + decision.rule());
    }

    public List<Map<String, Object>> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    public void clear() {
        recent.clear();
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
