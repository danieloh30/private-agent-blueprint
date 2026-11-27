package io.agentcon.blueprint.guardrails;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects text before it reaches a model request, a user-visible trace or an ordinary log.
 *
 * <p>Detects three kinds of content:
 * <ol>
 *   <li>a deterministic synthetic <b>canary</b> string (proves the pipeline works end to end);</li>
 *   <li>representative <b>secret patterns</b> (cloud keys, tokens, private-key headers, password assignments);</li>
 *   <li>a documented <b>restricted-content marker</b> ({@value #DEFAULT_MARKER}); content carrying it is always blocked.</li>
 * </ol>
 *
 * <p>In {@link Mode#REDACT} mode matches are replaced with {@code [REDACTED:rule]}; in {@link Mode#BLOCK}
 * mode the whole text is withheld. Findings name the rule and count, never the matched value.
 *
 * <p><b>Limitation:</b> pattern matching cannot detect every secret or every form of leakage
 * (encoded, split, paraphrased or novel formats). It is one layer, not a guarantee.
 */
public final class LeakageInspector {

    public static final String DEFAULT_CANARY = "DEMO-CANARY-7f3a9c2e-DO-NOT-SHARE";
    public static final String DEFAULT_MARKER = "AGENT-RESTRICTED";

    public enum Mode { REDACT, BLOCK }

    public record Finding(String rule, int count) {
    }

    public record Result(String text, List<Finding> findings, boolean blocked) {
        public boolean clean() {
            return findings.isEmpty();
        }

        public String summary() {
            if (findings.isEmpty()) {
                return "clean";
            }
            StringBuilder sb = new StringBuilder(blocked ? "blocked: " : "redacted: ");
            for (int i = 0; i < findings.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(findings.get(i).rule()).append(" x").append(findings.get(i).count());
            }
            return sb.toString();
        }
    }

    private final Mode mode;
    private final String marker;
    private final Map<String, Pattern> rules;

    public LeakageInspector(Mode mode, List<String> canaries, String marker, Map<String, String> extraPatterns) {
        this.mode = mode;
        this.marker = marker == null || marker.isBlank() ? DEFAULT_MARKER : marker;
        Map<String, Pattern> r = new LinkedHashMap<>();
        for (String canary : canaries) {
            if (canary != null && !canary.isBlank()) {
                r.put("canary", Pattern.compile(Pattern.quote(canary.trim())));
            }
        }
        r.putAll(defaultSecretPatterns());
        extraPatterns.forEach((name, regex) -> r.put(name, Pattern.compile(regex)));
        this.rules = Map.copyOf(r);
    }

    public static LeakageInspector defaults(Mode mode) {
        return new LeakageInspector(mode, List.of(DEFAULT_CANARY), DEFAULT_MARKER, Map.of());
    }

    /** Representative, not exhaustive. */
    public static Map<String, Pattern> defaultSecretPatterns() {
        Map<String, Pattern> p = new LinkedHashMap<>();
        p.put("aws-access-key-id", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"));
        p.put("api-key-sk", Pattern.compile("\\bsk-(?:proj-|ant-|live_|test_)?[A-Za-z0-9_-]{16,}"));
        p.put("github-token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,255}\\b"));
        p.put("private-key", Pattern.compile("-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----[\\s\\S]*?(?:-----END (?:[A-Z]+ )?PRIVATE KEY-----|\\z)"));
        p.put("bearer-token", Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{20,}"));
        p.put("credential-assignment", Pattern.compile(
                "(?i)\\b(?:password|passwd|secret|api[_-]?key|access[_-]?token|client[_-]?secret)\\b\\s*[:=]\\s*[\"']?[^\\s\"'#]{8,}"));
        return p;
    }

    public Mode mode() {
        return mode;
    }

    public String marker() {
        return marker;
    }

    public Result inspect(String text) {
        return inspect(text, mode);
    }

    public Result inspect(String text, Mode effectiveMode) {
        if (text == null || text.isEmpty()) {
            return new Result(text, List.of(), false);
        }
        List<Finding> findings = new ArrayList<>();
        if (text.contains(marker)) {
            findings.add(new Finding("restricted-marker", count(text, marker)));
        }
        String redacted = text;
        for (Map.Entry<String, Pattern> rule : rules.entrySet()) {
            Matcher m = rule.getValue().matcher(redacted);
            int n = 0;
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                n++;
                m.appendReplacement(sb, Matcher.quoteReplacement("[REDACTED:" + rule.getKey() + "]"));
            }
            if (n > 0) {
                m.appendTail(sb);
                redacted = sb.toString();
                findings.add(new Finding(rule.getKey(), n));
            }
        }
        if (findings.isEmpty()) {
            return new Result(text, List.of(), false);
        }
        boolean restricted = findings.stream().anyMatch(f -> f.rule().equals("restricted-marker"));
        if (restricted || effectiveMode == Mode.BLOCK) {
            String reason = restricted
                    ? "[BLOCKED: content carries the restricted-content marker and was not delivered]"
                    : "[BLOCKED: sensitive content detected and withheld]";
            return new Result(reason, List.copyOf(findings), true);
        }
        return new Result(redacted, List.copyOf(findings), false);
    }

    /** Always-redact helper for traces and logs. */
    public String redactForLog(String text) {
        return inspect(text, Mode.REDACT).text();
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
