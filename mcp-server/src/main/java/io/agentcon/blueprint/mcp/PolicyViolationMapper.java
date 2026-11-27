package io.agentcon.blueprint.mcp;

import io.agentcon.blueprint.guardrails.PolicyViolation;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Presenter routes report a busy workspace without starting a competing operation. */
@Provider
public class PolicyViolationMapper implements ExceptionMapper<PolicyViolation> {
    public Response toResponse(PolicyViolation e) {
        return Response.status(409).entity(new JsonObject()
                .put("verdict", "DENY").put("rule", e.decision().rule())
                .put("reason", e.decision().reason()).encode()).build();
    }
}
