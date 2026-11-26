package io.agentcon.blueprint.guardrails;

/** Thrown when an action is denied. Carries the decision so callers can report and audit it. */
public class PolicyViolation extends RuntimeException {

    private final Decision decision;

    public PolicyViolation(Decision decision) {
        super(decision.toString());
        this.decision = decision;
    }

    public static PolicyViolation deny(String rule, String reason) {
        return new PolicyViolation(Decision.deny(rule, reason));
    }

    public Decision decision() {
        return decision;
    }
}
