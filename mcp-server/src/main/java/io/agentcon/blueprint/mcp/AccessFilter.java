package io.agentcon.blueprint.mcp;

import io.quarkus.vertx.http.runtime.filters.Filters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates callers before any route runs.
 *
 * <ul>
 *   <li>{@code /mcp}   requires {@code Authorization: Bearer <agent token>} (the agent process).</li>
 *   <li>{@code /admin} requires {@code X-Approver-Token} (the presenter UI backend only).</li>
 * </ul>
 * Tokens come from the environment, are never sent to the model and are compared in constant time.
 * The agent identity and its workspace are bound server-side; the model cannot claim another identity.
 */
@ApplicationScoped
public class AccessFilter {

    private final ServerConfig config;

    public AccessFilter(ServerConfig config) {
        this.config = config;
    }

    void register(@Observes Filters filters) {
        filters.register(rc -> {
            String path = rc.normalizedPath();
            if (path.startsWith("/mcp")) {
                String auth = rc.request().getHeader("Authorization");
                if (!matches(auth, "Bearer " + config.security().agentToken())) {
                    rc.response().setStatusCode(401).putHeader("WWW-Authenticate", "Bearer").end("agent token required");
                    return;
                }
            } else if (path.startsWith("/admin")) {
                if (!matches(rc.request().getHeader("X-Approver-Token"), config.security().approverToken())) {
                    rc.response().setStatusCode(401).end("approver token required");
                    return;
                }
            }
            rc.next();
        }, 100);
    }

    static boolean matches(String presented, String expected) {
        if (presented == null || expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
