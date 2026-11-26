package io.agentcon.games.quiz;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for the Simple Quiz Game.
 *
 * GET /quiz          → show current question (server-side session via cookie)
 * GET /quiz?answer=A → submit answer, advance to next question
 * GET /quiz/reset    → start a fresh game
 *
 * Score lives in QuizSession (server-side ConcurrentHashMap keyed by a UUID cookie).
 * URL params ?score=, ?q=, ?answers= are completely ignored — URL manipulation
 * of the score is no longer possible.
 *
 * AI IMPROVEMENT IDEAS:
 * - Validate that `answer` is one of A/B/C/D before comparing.
 * - Add a timer per question using a Quarkus scheduler.
 * - Randomise question order each game.
 * - Expire stale sessions after a configurable TTL.
 */
@Path("/quiz")
public class QuizResource {

    static final String COOKIE = "quiz-session";

    @Inject Template    quiz;
    @Inject QuizSession store;

    private static final int TOTAL = QuestionBank.ALL.size();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response play(@jakarta.ws.rs.CookieParam(COOKIE) Cookie sessionCookie,
                         @QueryParam("answer") String answer) {

        // Resolve or create session
        String sid   = (sessionCookie != null) ? sessionCookie.getValue() : null;
        boolean isNew = (sid == null || store.get(sid) == null);
        if (isNew) sid = store.createSession();
        QuizSession.State state = store.get(sid);

        // Process submitted answer
        if (answer != null && !answer.isBlank() && !state.finished) {
            Question q       = QuestionBank.ALL.get(state.currentIndex);
            boolean  correct = answer.equalsIgnoreCase(q.answer());
            state.recordAnswer(answer, correct);
        }

        TemplateInstance ti = buildTemplate(state);
        NewCookie cookie    = new NewCookie.Builder(COOKIE).value(sid).path("/quiz").httpOnly(true).build();
        return Response.ok(ti).cookie(isNew ? cookie : null).build();
    }

    @GET
    @Path("/reset")
    @Produces(MediaType.TEXT_HTML)
    public Response reset(@jakarta.ws.rs.CookieParam(COOKIE) Cookie sessionCookie) {
        String sid = (sessionCookie != null) ? sessionCookie.getValue() : null;
        store.remove(sid);
        String newSid   = store.createSession();
        NewCookie cookie = new NewCookie.Builder(COOKIE).value(newSid).path("/quiz").httpOnly(true).build();
        return Response.ok(buildTemplate(store.get(newSid))).cookie(cookie).build();
    }

    private TemplateInstance buildTemplate(QuizSession.State state) {
        if (state.finished) {
            int pts = state.score;
            return quiz.data("finished",       true)
                       .data("score",          pts)
                       .data("total",          TOTAL)
                       .data("question",       null)
                       .data("questionNum",    TOTAL)
                       .data("progressPct",    100)
                       .data("isLastQuestion", false)
                       .data("isPerfect",      pts == TOTAL)
                       .data("isGood",         pts >= 3 && pts < TOTAL);
        }

        int     idx      = state.currentIndex;
        int     pts      = state.score;
        int     nextIdx  = idx + 1;
        int     progPct  = (TOTAL == 0) ? 0 : (idx * 100 / TOTAL);
        boolean last     = (nextIdx >= TOTAL);
        return quiz.data("finished",       false)
                   .data("score",          pts)
                   .data("total",          TOTAL)
                   .data("question",       QuestionBank.ALL.get(idx))
                   .data("questionNum",    nextIdx)
                   .data("progressPct",    progPct)
                   .data("isLastQuestion", last)
                   .data("nextQIndex",     nextIdx)
                   .data("isPerfect",      false)
                   .data("isGood",         false);
    }
}
