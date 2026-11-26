package io.agentcon.games.quiz;

/**
 * A single quiz question with a correct answer and three distractors.
 *
 * AI IMPROVEMENT IDEAS:
 * - Load questions from a database or a JSON file so non-developers can
 *   maintain the question bank without touching Java code.
 * - Add a category / difficulty field.
 */
public record Question(
        String text,
        String answer,
        String optionA,
        String optionB,
        String optionC,
        String optionD
) {}
