package io.agentcon.blueprint.agent.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;

/** How the agent loop reaches tools. Production implementation: {@link McpToolGateway} (real MCP client). */
public interface ToolGateway {

    record Outcome(boolean error, String text) {
    }

    List<ToolSpecification> listTools();

    Outcome execute(String id, String name, String argumentsJson);

    String describe();
}
