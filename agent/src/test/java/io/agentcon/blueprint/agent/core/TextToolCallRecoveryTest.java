package io.agentcon.blueprint.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class TextToolCallRecoveryTest {

    @Test
    void recoversNameArgumentsJsonInCodeFence() {
        String text = "Here is the proposal:\n```json\n{\n  \"name\": \"propose_patch\",\n  \"arguments\": {\"path\": \"src/A.java\", "
                + "\"find\": \"x = null;\", \"replace\": \"throw new X(\\\"{oops}\\\");\"}\n}\n```";
        ToolExecutionRequest r = AgentLoop.recoverTextToolCall(text);
        assertEquals("propose_patch", r.name());
        assertEquals("throw new X(\"{oops}\");", new JsonObject(r.arguments()).getString("replace"));
    }

    @Test
    void recoversToolNameFollowedByJson() {
        ToolExecutionRequest r = AgentLoop.recoverTextToolCall("I will call propose_patch\n{\"path\":\"src/A.java\",\"find\":\"a\",\"replace\":\"b\"}");
        assertEquals("propose_patch", r.name());
        assertEquals("src/A.java", new JsonObject(r.arguments()).getString("path"));
    }

    @Test
    void ignoresUnknownToolsAndProse() {
        assertNull(AgentLoop.recoverTextToolCall("{\"name\": \"run_shell\", \"arguments\": {\"cmd\": \"id\"}}"));
        assertNull(AgentLoop.recoverTextToolCall("The fix is to throw an exception in the catch block."));
        assertNull(AgentLoop.recoverTextToolCall("use propose_patch with { broken json"));
    }
}
