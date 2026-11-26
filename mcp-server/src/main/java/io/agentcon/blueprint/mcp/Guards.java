package io.agentcon.blueprint.mcp;

import io.agentcon.blueprint.guardrails.ApprovalStore;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import io.agentcon.blueprint.guardrails.PolicyEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.util.Map;

/** CDI producers for the framework-free guardrail components. */
@ApplicationScoped
public class Guards {

    @Produces
    @ApplicationScoped
    PolicyEngine policyEngine(ServerConfig config) {
        return new PolicyEngine(new PolicyEngine.Rules(
                config.policy().readAllow(),
                config.policy().writeAllow(),
                config.policy().protectedPaths(),
                VerificationTasks.NAMES));
    }

    @Produces
    @ApplicationScoped
    LeakageInspector leakageInspector(ServerConfig config) {
        return new LeakageInspector(
                LeakageInspector.Mode.valueOf(config.leakage().mode().toUpperCase()),
                config.leakage().canaries(),
                config.leakage().restrictedMarker(),
                Map.of());
    }

    @Produces
    @ApplicationScoped
    ApprovalStore approvalStore(ServerConfig config) {
        return new ApprovalStore(Clock.systemUTC(), config.approval().proposalTtl(), config.approval().approvalTtl());
    }
}
