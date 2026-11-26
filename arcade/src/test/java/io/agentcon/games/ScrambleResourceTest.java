package io.agentcon.games;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Integration tests for the Word Scramble game endpoint.
 */
@QuarkusTest
class ScrambleResourceTest {

    @Test
    void newRoundShowsScrambledWord() {
        given()
            .when().get("/scramble")
            .then()
            .statusCode(200)
            .body(containsString("Unscramble"));
    }

    @Test
    void correctGuessShowsSuccess() {
        given()
            .queryParam("word",  "quarkus")
            .queryParam("guess", "quarkus")
            .when().get("/scramble")
            .then()
            .statusCode(200)
            .body(containsString("Correct"));
    }

    @Test
    void wrongGuessShowsFailure() {
        given()
            .queryParam("word",  "quarkus")
            .queryParam("guess", "kubernetes")
            .when().get("/scramble")
            .then()
            .statusCode(200)
            .body(containsString("Nope"))
            .body(not(containsString("Correct!")));
    }

    @Test
    void mixedCaseGuessIsRejected() {
        // INTENTIONAL BUG: case-sensitive comparison means "QUARKUS" ≠ "quarkus"
        // This is the guardrail gap — an AI assistant should fix it with equalsIgnoreCase
        given()
            .queryParam("word",  "quarkus")
            .queryParam("guess", "QUARKUS")
            .when().get("/scramble")
            .then()
            .statusCode(200)
            .body(containsString("Nope"));
    }

    @Test
    void uppercaseWordInUrlShowsFormNotFeedback() {
        // ?word=ENDPOINT (no &guess=) must show the guess form, not a "Nope" result
        given()
            .queryParam("word", "ENDPOINT")
            .when().get("/scramble")
            .then()
            .statusCode(200)
            .body(containsString("Your answer"))
            .body(not(containsString("Nope")))
            .body(not(containsString("Correct")));
    }

    @Test
    void exactLowercaseGuessIsAccepted() {
        // Only exact lowercase match succeeds (demonstrating the case-sensitive gap)
        given()
            .queryParam("word",  "quarkus")
            .queryParam("guess", "quarkus")
            .when().get("/scramble")
            .then()
            .statusCode(200)
            .body(containsString("Correct"));
    }
}
