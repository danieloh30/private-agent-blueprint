package io.agentcon.blueprint.agent.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.agentcon.blueprint.agent.AgentConfig;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Builds the single {@link ChatModel} the agent uses, after validating the destination.
 * Fails startup on a missing or unapproved endpoint; there is no default and no public fallback.
 *
 * <p>Uses LangChain4j's OpenAI-compatible provider (works with Ollama, vLLM, LiteLLM/LiteMaaS,
 * llama.cpp server and most private gateways) with a guarded HTTP client and logging disabled.
 */
@ApplicationScoped
@Startup
public class ModelFactory {

    private static final Logger LOG = Logger.getLogger(ModelFactory.class);

    private final AgentConfig config;
    private ModelEndpointPolicy.Endpoint endpoint;
    private ChatModel model;
    private GuardedHttpClient.Builder httpBuilder;

    public ModelFactory(AgentConfig config) {
        this.config = config;
    }

    @PostConstruct
    void init() {
        AgentConfig.Model m = config.model();
        endpoint = new ModelEndpointPolicy().validate(m.profile(), m.baseUrl(), m.name(), m.approvedHosts());
        if (endpoint.profile() == ModelEndpointPolicy.Profile.SCRIPTED) {
            model = new ScriptedChatModel();
            LOG.warn("Model profile SCRIPTED: deterministic fallback, NO inference. Label every result accordingly.");
            return;
        }
        Built built = openAi(endpoint, m.apiKey(), m.temperature(), m.timeout(), m.useSystemProxy());
        httpBuilder = built.http();
        model = built.model();
        LOG.infof("Model profile %s -> %s, model '%s' (credentials not logged)",
                endpoint.profile(), endpoint.display(), endpoint.modelName());
    }

    public record Built(ChatModel model, GuardedHttpClient.Builder http) {
    }

    /** OpenAI-compatible chat model with the guarded HTTP client and all request/response logging off. */
    public static Built openAi(ModelEndpointPolicy.Endpoint endpoint, java.util.Optional<String> apiKey, double temperature,
                               java.time.Duration timeout, boolean useSystemProxy) {
        GuardedHttpClient.Builder http = new GuardedHttpClient.Builder(endpoint.uri(), useSystemProxy);
        ChatModel chat = OpenAiChatModel.builder()
                .baseUrl(endpoint.uri().toString())
                .apiKey(apiKey.filter(k -> !k.isBlank()).orElse("not-used-by-local-endpoints"))
                .modelName(endpoint.modelName())
                .temperature(temperature)
                .timeout(timeout)
                .maxRetries(0)
                .returnThinking(false)
                .logRequests(false)
                .logResponses(false)
                .httpClientBuilder(http)
                .build();
        return new Built(chat, http);
    }

    public ChatModel model() {
        return model;
    }

    public ModelEndpointPolicy.Endpoint endpoint() {
        return endpoint;
    }

    public boolean scripted() {
        return endpoint.profile() == ModelEndpointPolicy.Profile.SCRIPTED;
    }

    public long deniedEgressAttempts() {
        return httpBuilder == null || httpBuilder.built() == null ? 0 : httpBuilder.built().denied();
    }

    /** For tests only: swap in a deterministic fake model. */
    public void overrideModelForTests(ChatModel fake) {
        this.model = fake;
    }
}
