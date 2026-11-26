package io.agentcon.blueprint.agent.model;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Validates the configured inference destination BEFORE anything is sent.
 *
 * <ul>
 *   <li><b>local</b>: the host must be a loopback address (127.0.0.0/8, ::1, or a name resolving only to loopback).</li>
 *   <li><b>enterprise</b>: HTTPS only, and the host must be listed exactly in {@code blueprint.model.approved-hosts}.</li>
 *   <li><b>scripted</b>: no endpoint at all; a deterministic fallback that performs no inference.</li>
 * </ul>
 * There is no default endpoint and no fallback to a public service. A missing or invalid value stops startup.
 *
 * <p>Application configuration is not a network boundary. Enforce egress with host firewall rules,
 * network policy or an egress proxy as well; see docs/threat-model.md.
 */
public final class ModelEndpointPolicy {

    public enum Profile { LOCAL, ENTERPRISE, SCRIPTED }

    public record Endpoint(Profile profile, URI uri, String modelName) {
        public String host() {
            return uri == null ? "none" : uri.getHost();
        }

        public int port() {
            if (uri == null) return -1;
            if (uri.getPort() > 0) return uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }

        /** Safe to display: scheme, host and port only; never userinfo, path or query. */
        public String display() {
            return uri == null ? "no endpoint (scripted fallback)" : uri.getScheme() + "://" + host() + ":" + port();
        }
    }

    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Resolver resolver;

    public ModelEndpointPolicy() {
        this(InetAddress::getAllByName);
    }

    public ModelEndpointPolicy(Resolver resolver) {
        this.resolver = resolver;
    }

    public Endpoint validate(String profileName, Optional<String> baseUrl, Optional<String> modelName,
                             Optional<List<String>> approvedHosts) {
        Profile profile = parseProfile(profileName);
        if (profile == Profile.SCRIPTED) {
            return new Endpoint(profile, null, "scripted-fallback (no inference)");
        }
        String url = baseUrl.map(String::trim).filter(s -> !s.isEmpty())
                .orElseThrow(() -> new IllegalStateException(
                        "blueprint.model.base-url is required for profile '" + profileName + "' (no default, no public fallback)"));
        String model = modelName.map(String::trim).filter(s -> !s.isEmpty())
                .orElseThrow(() -> new IllegalStateException(
                        "blueprint.model.name is required (set BLUEPRINT_MODEL_NAME); no model is assumed"));
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("blueprint.model.base-url is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalStateException("blueprint.model.base-url must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException("blueprint.model.base-url has no host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("Credentials must not be embedded in blueprint.model.base-url; use BLUEPRINT_MODEL_API_KEY");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("blueprint.model.base-url must not contain a query or fragment");
        }
        switch (profile) {
            case LOCAL -> requireLoopback(uri.getHost());
            case ENTERPRISE -> {
                if (!scheme.equals("https")) {
                    throw new IllegalStateException("The enterprise profile requires https");
                }
                List<String> approved = approvedHosts.orElse(List.of()).stream()
                        .map(h -> h.trim().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).toList();
                if (approved.isEmpty()) {
                    throw new IllegalStateException("The enterprise profile requires blueprint.model.approved-hosts");
                }
                if (!approved.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("Host '" + uri.getHost() + "' is not in blueprint.model.approved-hosts");
                }
            }
            default -> throw new IllegalStateException("unreachable");
        }
        return new Endpoint(profile, uri, model);
    }

    static Profile parseProfile(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("blueprint.model.profile is required: local, enterprise or scripted");
        }
        try {
            return Profile.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown blueprint.model.profile '" + name + "': use local, enterprise or scripted");
        }
    }

    private void requireLoopback(String host) {
        String h = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(h);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Cannot resolve local model host '" + host + "'");
        }
        if (addresses.length == 0) {
            throw new IllegalStateException("Local model host '" + host + "' resolves to nothing");
        }
        for (InetAddress a : addresses) {
            if (!a.isLoopbackAddress()) {
                throw new IllegalStateException("The local profile requires a loopback endpoint; '" + host
                        + "' resolves to non-loopback " + a.getHostAddress());
            }
        }
    }
}
