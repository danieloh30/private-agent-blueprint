package io.agentcon.games;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Integration tests for the home / arcade page.
 */
@QuarkusTest
class HomeResourceTest {

    @Test
    void homePageRendersArcade() {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .contentType("text/html")
            .body(containsString("Quarkus Game Arcade"))
            .body(containsString("Rock Paper Scissors"))
            .body(containsString("Word Scramble"))
            .body(containsString("Trivia Quiz"));
    }
}
