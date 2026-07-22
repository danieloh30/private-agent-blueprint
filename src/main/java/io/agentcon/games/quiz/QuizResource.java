package io.agentcon.games.quiz;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * REST resource for the Simple Quiz Game.
 *
 * GET /quiz                    → show question #0 (question index 0)
 * GET /quiz?q=1&answer=A       → submit answer for question 0, show question 1
 * GET /quiz?q=5&answer=C       → submit last answer, show final score
 *
 * GUARDRAIL GAP (intentional for AI demo):
 *   The `score` and `answers` parameters are passed back and forth through
 *   hidden HTML fields, so a crafty player can manipulate them.  An AI
 *   assistant should move game state to the server side.
 *
 * AI IMPROVEMENT IDEAS:
 * - Validate that `answer` is one of A/B/C/D before comparing (missing check!).
 * - Store answers in a server-side session to prevent client-side cheating.
 * - Add a timer per question using a Quarkus scheduler.
 * - Randomise question order each game.
 */
@Path("/quiz")
public class QuizResource {

    @Inject
    Template quiz;

    private static final List<Question> QUESTIONS = QuestionBank.ALL;
    private static final int TOTAL = QUESTIONS.size();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance play(
            @QueryParam("q")      Integer qIndex,
            @QueryParam("answer") String  answer,
            @QueryParam("score")  Integer score,
            @QueryParam("answers") String  prevAnswers) {

        int idx   = (qIndex  == null) ? 0 : qIndex;
        int pts   = (score   == null) ? 0 : score;
        String pa = (prevAnswers == null) ? "" : prevAnswers;

        // Evaluate the submitted answer (only if we have a valid question index)
        if (answer != null && !answer.isBlank() && idx > 0 && idx <= TOTAL) {
            Question prev = QUESTIONS.get(idx - 1);
            if (answer.equalsIgnoreCase(prev.answer())) {
                pts++;
            }
            pa = pa + answer;
        }

        boolean finished = (idx >= TOTAL);

        if (finished) {
            return quiz.data("finished",       true)
                       .data("score",          pts)
                       .data("total",          TOTAL)
                       .data("question",       null)
                       .data("qIndex",         idx)
                       .data("answers",        pa)
                       .data("questionNum",    idx)         // display: already past last question
                       .data("progressPct",    100)
                       .data("isLastQuestion", false)
                       .data("isPerfect",      pts == TOTAL)
                       .data("isGood",         pts >= 3 && pts < TOTAL);
        }

        int nextIdx  = idx + 1;
        int progPct  = (TOTAL == 0) ? 0 : (idx * 100 / TOTAL);
        boolean last = (nextIdx >= TOTAL);
        return quiz.data("finished",       false)
                   .data("score",          pts)
                   .data("total",          TOTAL)
                   .data("question",       QUESTIONS.get(idx))
                   .data("qIndex",         idx)
                   .data("answers",        pa)
                   .data("questionNum",    nextIdx)         // 1-based display
                   .data("progressPct",    progPct)
                   .data("isLastQuestion", last)
                   .data("nextQIndex",     nextIdx)
                   .data("isPerfect",      false)
                   .data("isGood",         false);
    }
}
