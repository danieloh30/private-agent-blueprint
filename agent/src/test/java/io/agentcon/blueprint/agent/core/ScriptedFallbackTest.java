package io.agentcon.blueprint.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentcon.blueprint.agent.core.TestSupport.Limits;
import io.agentcon.blueprint.agent.core.TestSupport.StubTools;
import io.agentcon.blueprint.agent.model.ScriptedChatModel;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ScriptedFallbackTest {

    @Test
    void fallbackIsAlwaysLabelledAndPlaysTheFixedPlan() {
        AgentSession session = new AgentSession("s");
        StubTools tools = new StubTools(n -> n.equals("propose_patch")
                ? "[policy] ASK write.apply-needs-approval: x\nPROPOSAL p-12345678 for f" : "[policy] ALLOW read.permitted-source: x");
        var o = TestSupport.loop(new ScriptedChatModel(), tools, Limits.defaults(), Clock.systemUTC())
                .run(session, Prompts.PRESETS.getFirst().text());
        assertTrue(o.answer().startsWith(ScriptedChatModel.LABEL), o.answer());
        assertEquals(java.util.List.of("search_text", "read_file", "propose_patch", "propose_patch"), tools.executed);
    }
}
