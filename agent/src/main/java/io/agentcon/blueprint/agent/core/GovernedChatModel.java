package io.agentcon.blueprint.agent.core;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;

/** CDI model boundary: per-run inspection and budgets wrap the configured private model. */
@ApplicationScoped
public class GovernedChatModel implements ChatModel {
    @Override
    public ChatResponse doChat(ChatRequest request) {
        return AgentInvocation.current().chat(request);
    }
}
