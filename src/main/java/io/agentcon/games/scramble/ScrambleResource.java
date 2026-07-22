package io.agentcon.games.scramble;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource for the Word Scramble game.
 *
 * GET /scramble              → new scrambled word
 * GET /scramble?word=X&guess=Y → check the player's guess
 *
 * PERMISSION GAP (intentional for AI demo):
 *   The original word is passed as a visible query parameter `word`,
 *   so a player can cheat by reading the URL.  An AI assistant should
 *   move the answer to server-side session state or use HMAC signing.
 *
 * AI IMPROVEMENT IDEAS:
 * - Use @SessionScoped to store the current word without exposing it.
 * - Add a "hint" endpoint that reveals one letter.
 * - Add case-insensitive comparison for guesses (current bug!).
 */
@Path("/scramble")
public class ScrambleResource {

    @Inject
    Template scramble;

    @Inject
    WordBank wordBank;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance play(
            @QueryParam("word")  String word,
            @QueryParam("guess") String guess) {

        // New round or direct URL visit with no guess yet — show the form
        if (word == null || word.isBlank() || guess == null) {
            String chosen = (word == null || word.isBlank())
                    ? wordBank.pickWord()
                    : word.toLowerCase();   // normalise URL-capitalised word for display
            return scramble.data(
                    "scrambled", wordBank.scramble(chosen),
                    "word",      chosen,
                    "guess",     null,
                    "correct",   null);
        }

        // INTENTIONAL GUARDRAIL BUG: case-sensitive comparison.
        // "Endpoint" ≠ "endpoint" — the player must match the exact stored case.
        // An AI assistant should change this to word.equalsIgnoreCase(guess).
        boolean correct = word.toLowerCase().equals(guess.trim());
        return scramble.data(
                "scrambled", wordBank.scramble(word.toLowerCase()),
                "word",      word.toLowerCase(),
                "guess",     guess,
                "correct",   correct);
    }
}
