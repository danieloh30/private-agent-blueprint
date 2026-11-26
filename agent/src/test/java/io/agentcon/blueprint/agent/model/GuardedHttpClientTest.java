package io.agentcon.blueprint.agent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Destination pinning and redirect handling of the only HTTP path to the model. */
class GuardedHttpClientTest {

    HttpServer approved;
    HttpServer elsewhere;
    AtomicInteger elsewhereHits = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        elsewhere = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        elsewhere.createContext("/", ex -> {
            elsewhereHits.incrementAndGet();
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        elsewhere.start();
        approved = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        approved.createContext("/v1/redirect", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/v1/chat/completions");
            ex.sendResponseHeaders(307, -1);
            ex.close();
        });
        approved.start();
    }

    @AfterEach
    void stop() {
        approved.stop(0);
        elsewhere.stop(0);
    }

    HttpClient client() {
        return new GuardedHttpClient.Builder(URI.create("http://127.0.0.1:" + approved.getAddress().getPort() + "/v1"), false).build();
    }

    @Test
    void redirectsAreNotFollowed() {
        HttpRequest req = HttpRequest.builder().method(HttpMethod.POST)
                .url("http://127.0.0.1:" + approved.getAddress().getPort() + "/v1/redirect").body("{}").build();
        assertThrows(RuntimeException.class, () -> client().execute(req));
        assertEquals(0, elsewhereHits.get(), "redirect target must never be contacted");
    }

    @Test
    void changedDestinationIsRefusedBeforeConnecting() {
        HttpRequest req = HttpRequest.builder().method(HttpMethod.POST)
                .url("http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/v1/chat/completions").body("{}").build();
        assertThrows(GuardedHttpClient.EgressDeniedException.class, () -> client().execute(req));
        assertEquals(0, elsewhereHits.get());
    }

    @Test
    void pathOutsideTheBaseIsRefused() {
        HttpRequest req = HttpRequest.builder().method(HttpMethod.GET)
                .url("http://127.0.0.1:" + approved.getAddress().getPort() + "/admin").build();
        assertThrows(GuardedHttpClient.EgressDeniedException.class, () -> client().execute(req));
    }
}
