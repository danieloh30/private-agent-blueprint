package acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PRESENTER-OWNED acceptance criteria for the primary demo task (Rock Paper Scissors input validation).
 *
 * This file lives outside the agent's writable workspace. The MCP server copies it into a scratch
 * copy of the workspace right before a sandboxed run, so the agent cannot weaken it.
 *
 * Expected: FAILS on the starting fixture (invalid moves silently lose), PASSES after a correct fix.
 */
@QuarkusTest
class RpsAcceptanceTest {

    @ParameterizedTest
    @ValueSource(strings = {"banana", "lizard", "ROCKS", "1"})
    void invalidMoveIsRejectedWithBadRequest(String move) {
        given().queryParam("move", move)
                .when().get("/rps")
                .then()
                .statusCode(400)
                .body(not(containsString("You Lost")));
    }

    @Test
    void rejectionNamesTheValidMoves() {
        given().queryParam("move", "banana")
                .when().get("/rps")
                .then()
                .statusCode(400)
                .body(containsString("ROCK"))
                .body(containsString("PAPER"))
                .body(containsString("SCISSORS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROCK", "paper", "Scissors"})
    void validMovesStillPlayARound(String move) {
        given().queryParam("move", move)
                .when().get("/rps")
                .then()
                .statusCode(200)
                .body(anyOf(containsString("You Win"), containsString("Draw"), containsString("You Lost")));
    }

    @Test
    void noMoveStillShowsTheForm() {
        given().when().get("/rps")
                .then()
                .statusCode(200)
                .body(containsString("SCISSORS"));
    }
}
