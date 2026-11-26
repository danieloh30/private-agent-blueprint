package io.agentcon.blueprint.agent.core;

/**
 * One entry in the presenter-visible trace. Contains tool names, arguments, policy decisions and
 * results (all leakage-inspected). It never contains hidden model reasoning: the agent does not request
 * it, drops any thinking text the endpoint returns, and does not display narration attached to tool calls.
 */
public record TraceEvent(long seq,
                         String time,
                         String kind,
                         String title,
                         String detail,
                         String verdict,
                         String rule,
                         long durationMs) {

    public enum Kind { REQUEST, INSPECTION, MODEL, TOOL, ANSWER, LIMIT, APPROVAL, VERIFICATION, PROBE, ERROR, INFO }
}
