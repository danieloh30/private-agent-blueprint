package io.agentcon.blueprint.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PatchMatchingTest {

    static final String SRC = """
            class T {
                void a() {
                    given()
                        .statusCode(200)
                        .body(containsString("You Lost"));
                }
                void b() {
                    x = 1;
                }
                void c() {
                    x = 1;
                }
            }
            """;

    @Test
    void matchesIgnoringIndentationAndReturnsOriginalText() {
        String located = PatchMatchingTest.locate(".statusCode(200)\n.body(containsString(\"You Lost\"));");
        assertEquals(".statusCode(200)\n            .body(containsString(\"You Lost\"));", located);
    }

    @Test
    void ambiguousOrMissingMatchesAreRejected() {
        assertNull(locate("x = 1;"));
        assertNull(locate("y = 2;"));
        assertNull(locate("   "));
    }

    static String locate(String find) {
        return CodingTools.locateIgnoringIndentation(SRC, find);
    }
}
