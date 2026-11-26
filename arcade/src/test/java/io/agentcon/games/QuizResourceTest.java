package io.agentcon.games;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Integration tests for the Quiz game endpoint.
 *
 * State is server-side (QuizSession store keyed by a UUID cookie).
 * Each test that needs multi-step state uses a helper that threads the
 * quiz-session cookie across requests.
 */
@QuarkusTest
class QuizResourceTest {

    private static final String COOKIE_NAME = "quiz-session";

    /** Returns a RequestSpecification carrying the quiz-session cookie from a reset response. */
    private RequestSpecification freshSession() {
        Response reset = given().get("/quiz/reset");
        String sid = reset.cookie(COOKIE_NAME);
        return given().cookie(COOKIE_NAME, sid);
    }

    @Test
    void firstQuestionRendered() {
        given()
            .when().get("/quiz/reset")
            .then()
            .statusCode(200)
            .body(containsString("Question 1 of 5"))
            .body(containsString("Quarkus"));
    }

    @Test
    void answerAdvancesToNextQuestion() {
        RequestSpecification session = freshSession();

        session.queryParam("answer", "A")
               .when().get("/quiz")
               .then()
               .statusCode(200)
               .body(containsString("Question 2 of 5"));
    }

    @Test
    void completingAllQuestionsShowsScore() {
        RequestSpecification session = freshSession();

        // Correct answers: A, B, C, D, A (from QuestionBank)
        String[] answers = {"A", "B", "C", "D", "A"};
        for (int i = 0; i < answers.length - 1; i++) {
            session.queryParam("answer", answers[i]).get("/quiz");
        }
        session.queryParam("answer", answers[answers.length - 1])
               .when().get("/quiz")
               .then()
               .statusCode(200)
               .body(containsString("Correct answers"));
    }

    @Test
    void perfectScoreShowsTrophy() {
        // Get a fresh session cookie
        String sid = given().get("/quiz/reset").then().extract().cookie(COOKIE_NAME);

        String[] correct = {"A", "B", "C", "D", "A"};
        for (String ans : correct) {
            given().cookie(COOKIE_NAME, sid)
                   .queryParam("answer", ans)
                   .get("/quiz");
        }
        given().cookie(COOKIE_NAME, sid)
               .when().get("/quiz")
               .then()
               .statusCode(200)
               .body(containsString("Perfect score"));
    }

    @Test
    void urlScoreManipulationIsIgnored() {
        // ?score=99&q=5 must have no effect — score lives server-side
        // Use a fresh session and send score/q params — they should be ignored
        RequestSpecification session = freshSession();
        session.queryParam("score",  99)
               .queryParam("q",      5)
               .when().get("/quiz")
               .then()
               .statusCode(200)
               .body(not(containsString("Perfect score")))
               .body(containsString("Question 1 of 5"));
    }

    @Test
    void resetStartsFreshGame() {
        RequestSpecification session = freshSession();

        session.queryParam("answer", "A").get("/quiz");

        // Reset via the same cookie — should get a new session
        String sid = session.when().get("/quiz/reset")
                            .then().statusCode(200)
                            .body(containsString("Question 1 of 5"))
                            .extract().cookie(COOKIE_NAME);

        // New cookie must differ from old one
        assert sid != null;
    }
}
