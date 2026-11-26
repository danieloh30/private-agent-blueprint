package io.agentcon.blueprint.agent.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.agentcon.blueprint.agent.AgentConfig;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Real MCP client (LangChain4j {@link DefaultMcpClient}) over Streamable HTTP to the Java MCP server.
 * Authenticates with a bearer token from the environment; the token is never part of model context.
 * Connects lazily so the presenter UI can start (and report the problem) when the server is down.
 */
@ApplicationScoped
public class McpToolGateway implements ToolGateway {

    private final AgentConfig config;
    private volatile McpClient client;

    public McpToolGateway(AgentConfig config) {
        this.config = config;
    }

    private McpClient client() {
        McpClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    StreamableHttpMcpTransport transport = new StreamableHttpMcpTransport.Builder()
                            .url(config.mcp().url())
                            .customHeaders(Map.of("Authorization", "Bearer " + config.mcp().agentToken()))
                            .timeout(config.mcp().toolTimeout())
                            .followRedirects(false)
                            .logRequests(false)
                            .logResponses(false)
                            .build();
                    client = new DefaultMcpClient.Builder()
                            .key("arcade-workspace")
                            .clientName("private-coding-agent")
                            .clientVersion("1.0.0")
                            .transport(transport)
                            .toolExecutionTimeout(config.mcp().toolTimeout())
                            .cacheToolList(true)
                            .build();
                }
                c = client;
            }
        }
        return c;
    }

    @Override
    public List<ToolSpecification> listTools() {
        try {
            return client().listTools();
        } catch (RuntimeException e) {
            reset();
            throw e;
        }
    }

    @Override
    public Outcome execute(String id, String name, String argumentsJson) {
        try {
            ToolExecutionResult r = client().executeTool(ToolExecutionRequest.builder()
                    .id(id).name(name).arguments(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson).build());
            return new Outcome(r.isError(), r.resultText());
        } catch (ToolExecutionException e) {
            // The MCP server flags policy denials as isError results; the client surfaces them as exceptions.
            return new Outcome(true, e.getMessage());
        }
    }

    @Override
    public String describe() {
        URI u = URI.create(config.mcp().url());
        return "MCP Streamable HTTP " + u.getHost() + ":" + u.getPort() + u.getPath();
    }

    private synchronized void reset() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // closing a broken client
            }
            client = null;
        }
    }

    @PreDestroy
    void close() {
        reset();
    }
}
