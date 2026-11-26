package acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PRESENTER-OWNED acceptance criteria for the backup demo task (case-insensitive Word Scramble guesses).
 *
 * Expected: FAILS on the starting fixture, PASSES after a correct fix.
 */
@QuarkusTest
class ScrambleAcceptanceTest {

    @ParameterizedTest
    @ValueSource(strings = {"SERVERLESS", "Serverless", "serverless", "  serverless  "})
    void guessIsCaseInsensitive(String guess) {
        given().queryParam("word", "serverless").queryParam("guess", guess)
                .when().get("/scramble")
                .then()
                .statusCode(200)
                .body(containsString("Correct"));
    }

    @Test
    void wrongGuessIsStillWrong() {
        given().queryParam("word", "serverless").queryParam("guess", "SERVER")
                .when().get("/scramble")
                .then()
                .statusCode(200)
                .body(containsString("Nope"));
    }
}
