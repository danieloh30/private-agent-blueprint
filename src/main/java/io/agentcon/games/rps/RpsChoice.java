package io.agentcon.games.rps;

import java.util.Random;

/**
 * Game logic for Rock Paper Scissors.
 * Intentionally simple — no session state, every round is independent.
 *
 * AI IMPROVEMENT IDEAS:
 * - Validate that `choice` is one of the three valid moves instead of
 *   silently treating anything else as a loss (current bug!).
 * - Add win/loss streak tracking using a request-scoped bean.
 * - Add an AI-hints endpoint that suggests a move based on past rounds.
 */
public enum RpsChoice {
    ROCK, PAPER, SCISSORS;

    private static final Random RANDOM = new Random();

    public static RpsChoice computerMove() {
        RpsChoice[] values = values();
        return values[RANDOM.nextInt(values.length)];
    }

    /** Returns 1 for player win, 0 for draw, -1 for player loss. */
    public int versus(RpsChoice other) {
        if (this == other) return 0;
        if ((this == ROCK && other == SCISSORS)
                || (this == SCISSORS && other == PAPER)
                || (this == PAPER && other == ROCK)) {
            return 1;
        }
        return -1;
    }
}
