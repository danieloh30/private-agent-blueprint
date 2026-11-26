package io.agentcon.blueprint.agent.model;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The only HTTP path from the agent to a model.
 *
 * <ul>
 *   <li>Every request URL must match the validated endpoint's scheme, host, port and path prefix.
 *       A changed destination is refused before any bytes leave the process.</li>
 *   <li>Redirects are never followed (a 3xx surfaces as an error instead of silently changing destination).</li>
 *   <li>No proxy unless explicitly enabled.</li>
 * </ul>
 * Request and response bodies are never logged here.
 */
public final class GuardedHttpClient implements HttpClient {

    public static final class EgressDeniedException extends RuntimeException {
        public EgressDeniedException(String message) {
            super(message);
        }
    }

    private final HttpClient delegate;
    private final URI allowed;
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong denied = new AtomicLong();

    GuardedHttpClient(HttpClient delegate, URI allowed) {
        this.delegate = delegate;
        this.allowed = allowed;
    }

    public long requests() {
        return requests.get();
    }

    public long denied() {
        return denied.get();
    }

    void check(String url) {
        URI target = URI.create(url);
        boolean sameOrigin = eq(target.getScheme(), allowed.getScheme())
                && eq(target.getHost(), allowed.getHost())
                && port(target) == port(allowed);
        String base = allowed.getPath() == null ? "" : allowed.getPath().replaceAll("/+$", "");
        boolean underBase = target.getPath() != null && target.getPath().startsWith(base);
        if (!sameOrigin || !underBase || target.getUserInfo() != null) {
            denied.incrementAndGet();
            throw new EgressDeniedException("Model request to " + target.getScheme() + "://" + target.getHost() + ":" + port(target)
                    + " refused: only " + allowed.getScheme() + "://" + allowed.getHost() + ":" + port(allowed) + " is approved");
        }
        requests.incrementAndGet();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        check(request.url());
        return delegate.execute(request);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        check(request.url());
        delegate.execute(request, parser, listener);
    }

    private static boolean eq(String a, String b) {
        return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private static int port(URI u) {
        if (u.getPort() > 0) return u.getPort();
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    /** Builder plugged into LangChain4j's {@code OpenAiChatModel.builder().httpClientBuilder(...)}. */
    public static final class Builder implements HttpClientBuilder {
        private final URI allowed;
        private final boolean useSystemProxy;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(120);
        private GuardedHttpClient built;

        public Builder(URI allowed, boolean useSystemProxy) {
            this.allowed = allowed;
            this.useSystemProxy = useSystemProxy;
        }

        @Override
        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            if (timeout != null) this.connectTimeout = timeout;
            return this;
        }

        @Override
        public Duration readTimeout() {
            return readTimeout;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            if (timeout != null) this.readTimeout = timeout;
            return this;
        }

        @Override
        public HttpClient build() {
            java.net.http.HttpClient.Builder jdk = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                    .connectTimeout(connectTimeout);
            if (!useSystemProxy) {
                jdk.proxy(java.net.http.HttpClient.Builder.NO_PROXY);
            }
            HttpClient delegate = new JdkHttpClientBuilder()
                    .httpClientBuilder(jdk)
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .build();
            built = new GuardedHttpClient(delegate, allowed);
            return built;
        }

        public GuardedHttpClient built() {
            return built;
        }
    }
}
