package io.agentcon.blueprint.agent;

import io.agentcon.blueprint.agent.core.AgentService;
import io.agentcon.blueprint.agent.core.AgentSession;
import io.agentcon.blueprint.agent.core.Prompts;
import io.agentcon.blueprint.agent.core.TraceEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** JSON API for the presenter UI (served from META-INF/resources/index.html). Bound to loopback. */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class AgentResource {

    private final AgentService agent;

    public AgentResource(AgentService agent) {
        this.agent = agent;
    }

    @GET
    @Path("/status")
    public String status() {
        return agent.status().encode();
    }

    @GET
    @Path("/presets")
    public String presets() {
        JsonArray a = new JsonArray();
        Prompts.PRESETS.forEach(p -> a.add(new JsonObject().put("id", p.id()).put("label", p.label())
                .put("text", p.text()).put("expectation", p.expectation())));
        return a.encode();
    }

    @GET
    @Path("/session")
    public String session(@QueryParam("after") long after) {
        AgentSession s = agent.current();
        JsonArray events = new JsonArray();
        for (TraceEvent e : s.eventsAfter(after)) {
            events.add(new JsonObject().put("seq", e.seq()).put("time", e.time()).put("kind", e.kind())
                    .put("title", e.title()).put("detail", e.detail()).put("verdict", e.verdict())
                    .put("rule", e.rule()).put("durationMs", e.durationMs()));
        }
        return new JsonObject().put("id", s.id()).put("running", s.running().get()).put("events", events).encode();
    }

    @POST
    @Path("/ask")
    public Response ask(String body) {
        String text = new JsonObject(body).getString("text", "");
        if (!agent.submit(agent.current(), text)) {
            return Response.status(409).entity(new JsonObject().put("error", "agent is still running").encode()).build();
        }
        return Response.accepted(new JsonObject().put("accepted", true).encode()).build();
    }

    @GET
    @Path("/proposals")
    public String proposals() {
        return agent.proposals().encode();
    }

    @POST
    @Path("/proposals/{id}/approve")
    public String approve(@PathParam("id") String id, String body) {
        return agent.approveAndApply(id, new JsonObject(body).getString("patchSha256", "")).encode();
    }

    @POST
    @Path("/proposals/{id}/reject")
    public String reject(@PathParam("id") String id) {
        return agent.reject(id).encode();
    }

    @POST
    @Path("/verify/{task}")
    public String verify(@PathParam("task") String task) {
        return agent.verify(task).encode();
    }

    @POST
    @Path("/probes/{name}")
    public String probe(@PathParam("name") String name) {
        return agent.probe(name).encode();
    }

    @GET
    @Path("/evidence")
    public String evidence() {
        return agent.evidence().encodePrettily();
    }

    @POST
    @Path("/reset")
    public String reset() {
        return agent.resetWorkspace().encode();
    }
}
