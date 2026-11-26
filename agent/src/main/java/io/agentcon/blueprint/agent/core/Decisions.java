package io.agentcon.blueprint.agent.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the decision lines the MCP server puts into every tool result. */
public final class Decisions {

    private static final Pattern POLICY = Pattern.compile("\\[policy] (ALLOW|ASK|DENY) ([a-z0-9.-]+)");
    private static final Pattern LEAKAGE = Pattern.compile("\\[leakage] (BLOCKED|REDACTED) before delivery: ([^\\n]*)");

    public record Parsed(String verdict, String rule, String leakage) {
    }

    private Decisions() {
    }

    /** A leakage block wins over the policy verdict: the content never left the server. */
    public static Parsed parse(String text) {
        String verdict = null;
        String rule = null;
        String leakage = null;
        Matcher p = POLICY.matcher(text == null ? "" : text);
        if (p.find()) {
            verdict = p.group(1);
            rule = p.group(2);
        }
        Matcher l = LEAKAGE.matcher(text == null ? "" : text);
        if (l.find()) {
            leakage = l.group(1) + ": " + l.group(2);
            if (l.group(1).equals("BLOCKED")) {
                verdict = "DENY";
                rule = "leakage.blocked";
            }
        }
        return new Parsed(verdict, rule, leakage);
    }
}
