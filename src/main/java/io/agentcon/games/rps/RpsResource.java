package io.agentcon.games.rps;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource for the Rock-Paper-Scissors game.
 *
 * GET /rps           → show the game form (no move submitted yet)
 * GET /rps?move=rock → play a round and show the result
 *
 * KNOWN BUG (intentional for AI demo):
 *   An invalid `move` query param (e.g. "banana") is silently treated as a
 *   player loss.  An AI assistant should add input validation here.
 *
 * AI IMPROVEMENT IDEAS:
 * - Use @ServerExceptionMapper to return a friendly 400 for bad `move` values.
 * - Add @RolesAllowed("player") once authentication is wired in.
 * - Make the computer move "smarter" via pattern-tracking.
 */
@Path("/rps")
public class RpsResource {

    @Inject
    Template rps;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance play(@QueryParam("move") String move) {
        if (move == null || move.isBlank()) {
            // No move yet — just show the form
            return rps.data("result", null);
        }

        RpsChoice playerChoice;
        try {
            playerChoice = RpsChoice.valueOf(move.toUpperCase());
        } catch (IllegalArgumentException e) {
            // BUG: returns a confusing "you lost" page for invalid input
            // An AI assistant can improve this by returning a proper error message
            return rps.data("result", new RpsResult(move, "?", "invalid"));
        }

        RpsChoice computer = RpsChoice.computerMove();
        int outcome = playerChoice.versus(computer);
        String outcomeLabel = switch (outcome) {
            case 1  -> "win";
            case 0  -> "draw";
            default -> "loss";
        };

        return rps.data("result",
                new RpsResult(playerChoice.name(), computer.name(), outcomeLabel));
    }
}
