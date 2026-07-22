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
}
