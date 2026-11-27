package io.agentcon.blueprint.agent.core;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.RegisterAiService;
import java.util.function.Function;
import java.util.function.Supplier;

/** Declarative agent; Quarkus generates the implementation. Controls remain in the supplied boundaries. */
@RegisterAiService(
        chatLanguageModelSupplier = CodingAgent.ModelSupplier.class,
        toolProviderSupplier = CodingAgent.ToolsSupplier.class,
        chatMemoryProviderSupplier = CodingAgent.MemorySupplier.class,
        toolHallucinationStrategy = CodingAgent.UnknownTool.class,
        maxToolCallingRoundTrips = 128,
        maxSequentialToolInvocations = 128)
public interface CodingAgent {
    @Agent(description = "Explore the arcade and propose narrow, reviewable fixes")
    @SystemMessage(Prompts.SYSTEM)
    String chat(@MemoryId String sessionId, @UserMessage String request);

    @jakarta.inject.Singleton
    class ModelSupplier implements Supplier<dev.langchain4j.model.chat.ChatModel> {
        @jakarta.inject.Inject GovernedChatModel model;
        public dev.langchain4j.model.chat.ChatModel get() { return model; }
    }

    /** Suppliers resolve state only while the presenter is invoking the agent. */
    @jakarta.inject.Singleton
    class ToolsSupplier implements Supplier<ToolProvider> {
        public ToolProvider get() { return request -> AgentInvocation.current().tools(); }
    }
    @jakarta.inject.Singleton
    class MemorySupplier implements Supplier<ChatMemoryProvider> {
        public ChatMemoryProvider get() { return id -> AgentInvocation.current().memory(); }
    }
    @jakarta.inject.Singleton
    class UnknownTool implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {
        public ToolExecutionResultMessage apply(ToolExecutionRequest request) {
            return ToolExecutionResultMessage.from(request, AgentInvocation.current().execute(request));
        }
    }
}
