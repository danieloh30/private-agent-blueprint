package io.agentcon.blueprint.mcp;

import io.agentcon.blueprint.guardrails.Decision;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inspects EVERY tool result before it leaves the MCP server (and therefore before it can reach
 * the agent, the model, the presenter trace or a log). Attached to every tool with
 * {@code @ToolGuardrails(output = LeakageOutputGuardrail.class)}; {@code McpToolsTest} asserts that no tool lacks it.
 */
@ApplicationScoped
public class LeakageOutputGuardrail implements ToolOutputGuardrail {

    private final LeakageInspector inspector;
    private final AuditLog audit;
    private final ServerConfig config;

    public LeakageOutputGuardrail(LeakageInspector inspector, AuditLog audit, ServerConfig config) {
        this.inspector = inspector;
        this.audit = audit;
        this.config = config;
    }

    @Override
    public void apply(ToolOutputContext context) {
        ToolResponse response = context.getResponse();
        if (response == null || response.content() == null) {
            return;
        }
        List<Content> inspected = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        boolean blocked = false;
        for (Content c : response.content()) {
            if (c instanceof TextContent text) {
                LeakageInspector.Result r = inspector.inspect(text.text());
                if (!r.clean()) {
                    summaries.add(r.summary());
                    blocked |= r.blocked();
                    String note = "[leakage] " + (r.blocked() ? "BLOCKED" : "REDACTED") + " before delivery: " + r.summary();
                    String body = r.text();
                    if (r.blocked()) {
                        // Keep the policy header (a path and a rule, inspected separately) so the decision stays visible.
                        String first = text.text().lines().findFirst().orElse("");
                        if (first.startsWith("[policy] ") && inspector.inspect(first).clean()) {
                            body = first + "\n" + body;
                        }
                    }
                    inspected.add(new TextContent(note + "\n" + body));
                    continue;
                }
            }
            inspected.add(c);
        }
        if (!summaries.isEmpty()) {
            Decision d = blocked
                    ? Decision.deny("leakage.blocked", "Tool output withheld: " + String.join("; ", summaries))
                    : Decision.allow("leakage.redacted", "Tool output redacted: " + String.join("; ", summaries));
            audit.record(config.security().agentIdentity(), context.getTool().name() + ":output-inspection", d,
                    Map.of("mode", inspector.mode().name()));
            context.setResponse(new ToolResponse(response.isError(), inspected));
        }
    }
}
