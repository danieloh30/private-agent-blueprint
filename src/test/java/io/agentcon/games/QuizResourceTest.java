package io.agentcon.games;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Integration tests for the Quiz game endpoint.
 */
@QuarkusTest
class QuizResourceTest {

    @Test
    void firstQuestionRendered() {
        given()
            .when().get("/quiz")
            .then()
            .statusCode(200)
            .body(containsString("Question 1 of 5"))
            .body(containsString("Quarkus"));
    }

    @Test
    void navigateToNextQuestion() {
        given()
            .queryParam("q",      1)
            .queryParam("answer", "A")
            .queryParam("score",  0)
            .when().get("/quiz")
            .then()
            .statusCode(200)
            .body(containsString("Question 2 of 5"));
    }

    @Test
    void finishedPageShownAfterLastQuestion() {
        given()
            .queryParam("q",      5)
            .queryParam("answer", "A")
            .queryParam("score",  3)
            .when().get("/quiz")
            .then()
            .statusCode(200)
            .body(containsString("Correct answers"));
    }

    @Test
    void perfectScoreShowsTrophy() {
        given()
            .queryParam("q",      5)
            .queryParam("answer", "A")   // correct answer for last question (MCP = A)
            .queryParam("score",  4)
            .when().get("/quiz")
            .then()
            .statusCode(200)
            .body(containsString("Perfect score"));
    }
}
