package io.agentcon.games.scramble;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Word bank and scrambling logic for the Word Scramble game.
 *
 * AI IMPROVEMENT IDEAS:
 * - Load words from a database or external file instead of a hardcoded list.
 * - Add difficulty levels (easy / medium / hard) based on word length.
 * - Fix the scramble algorithm: for very short words (2 letters) it can
 *   return the same word — an AI should detect this edge case.
 */
@ApplicationScoped
public class WordBank {

    private static final List<String> WORDS = List.of(
            "quarkus", "microservice", "container", "reactive", "injection",
            "template", "endpoint", "blueprint", "developer", "framework",
            "kubernetes", "serverless", "streaming", "extension", "registry"
    );

    /** Pick a random word from the bank. */
    public String pickWord() {
        return WORDS.get((int) (Math.random() * WORDS.size()));
    }

    /**
     * Scramble a word so that the result differs from the original.
     *
     * KNOWN ISSUE: For 2-letter words this loop may spin forever.
     * An AI assistant can improve this with a max-attempt guard.
     */
    public String scramble(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        String scrambled;
        do {
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            scrambled = sb.toString();
        } while (scrambled.equals(word));
        return scrambled;
    }
}
