package io.agentcon.games;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.anyOf;

/**
 * Integration tests for the Rock Paper Scissors game endpoint.
 */
@QuarkusTest
class RpsResourceTest {

    @Test
    void formShownWhenNoMoveProvided() {
        given()
            .when().get("/rps")
            .then()
            .statusCode(200)
            .body(containsString("ROCK"))
            .body(containsString("PAPER"))
            .body(containsString("SCISSORS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROCK", "PAPER", "SCISSORS"})
    void validMovesProduceResult(String move) {
        given()
            .queryParam("move", move)
            .when().get("/rps")
            .then()
            .statusCode(200)
            .body(anyOf(
                    containsString("You Win"),
                    containsString("Draw"),
                    containsString("You Lost")));
    }

    @Test
    void invalidMoveShowsInvalidBadge() {
        given()
            .queryParam("move", "banana")
            .when().get("/rps")
            .then()
            .statusCode(200)
            .body(containsString("Invalid move"));
    }
}
