package io.agentcon.blueprint.guardrails;

/**
 * Result of a policy evaluation. Every tool call produces exactly one decision,
 * which is shown in the presenter UI and written to the audit log.
 *
 * @param verdict ALLOW, ASK (needs a human approval bound to this exact action) or DENY
 * @param rule    short stable rule identifier, e.g. {@code read.protected-path}
 * @param reason  human-readable explanation; never contains secret values
 */
public record Decision(Verdict verdict, String rule, String reason) {

    public enum Verdict { ALLOW, ASK, DENY }

    public static Decision allow(String rule, String reason) {
        return new Decision(Verdict.ALLOW, rule, reason);
    }

    public static Decision ask(String rule, String reason) {
        return new Decision(Verdict.ASK, rule, reason);
    }

    public static Decision deny(String rule, String reason) {
        return new Decision(Verdict.DENY, rule, reason);
    }

    public boolean allowed() {
        return verdict == Verdict.ALLOW;
    }

    @Override
    public String toString() {
        return verdict + " [" + rule + "] " + reason;
    }
}
