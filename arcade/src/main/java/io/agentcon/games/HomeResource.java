package io.agentcon.games;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Serves the main game-selection home page.
 *
 * AI IMPROVEMENT IDEAS:
 * - Add player session tracking (score history)
 * - Add a leaderboard backed by a database
 * - Guard against missing template (TemplateNotFoundException)
 */
@Path("/")
public class HomeResource {

    @Inject
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        return index.instance();
    }
}
