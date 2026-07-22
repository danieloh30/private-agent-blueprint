package io.agentcon.games.rps;

/**
 * Result of a single Rock-Paper-Scissors round, passed to the Qute template.
 *
 * AI IMPROVEMENT IDEAS:
 * - Add a "rounds played" counter stored in a CDI ApplicationScoped bean
 *   so the scoreboard can be shown across multiple HTTP requests.
 */
public record RpsResult(
        String playerChoice,
        String computerChoice,
        String outcome   // "win", "draw", or "loss"
) {}
