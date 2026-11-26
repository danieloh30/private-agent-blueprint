package io.agentcon.blueprint.mcp;

import io.agentcon.blueprint.guardrails.ApprovalStore;
import io.agentcon.blueprint.guardrails.Decision;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import io.agentcon.blueprint.guardrails.PolicyEngine;
import io.agentcon.blueprint.guardrails.PolicyViolation;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presenter-only endpoints (guarded by the approver token in {@link AccessFilter}).
 * These are NOT MCP tools: the model cannot approve its own proposals or run presenter checks.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class AdminResource {

    private final WorkspaceManager workspaces;
    private final ApprovalStore approvals;
    private final PolicyEngine policy;
    private final LeakageInspector inspector;
    private final SandboxRunner sandbox;
    private final AuditLog audit;
    private final CodingTools tools;

    public AdminResource(WorkspaceManager workspaces, ApprovalStore approvals, PolicyEngine policy,
                         LeakageInspector inspector, SandboxRunner sandbox, AuditLog audit, CodingTools tools) {
        this.workspaces = workspaces;
        this.approvals = approvals;
        this.policy = policy;
        this.inspector = inspector;
        this.sandbox = sandbox;
        this.audit = audit;
        this.tools = tools;
    }

    @GET
    @Path("/status")
    public String status() {
        WorkspaceManager.Run run = workspaces.current();
        SandboxRunner.Availability availability = sandbox.availability();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("workspaceId", run.id());
        m.put("workspace", run.workspace().toString());
        m.put("isolationAvailable", availability.available());
        m.put("isolationDetail", availability.detail());
        m.put("isolationControls", sandbox.controls());
        m.put("tasks", VerificationTasks.TASKS.values().stream()
                .map(t -> Map.of("name", t.name(), "description", t.description())).toList());
        m.put("readAllow", policy.rules().readAllow());
        m.put("writeAllow", policy.rules().writeAllow());
        m.put("protectedPaths", policy.rules().protectedPaths());
        m.put("leakageMode", inspector.mode().name());
        return new JsonObject(m).encode();
    }

    @GET
    @Path("/proposals")
    public String proposals() {
        return new JsonArray(approvals.list().stream().map(this::view).map(JsonObject::new).toList()).encode();
    }

    @POST
    @Path("/proposals/{id}/approve")
    public Response approve(@PathParam("id") String id, String body) {
        JsonObject request = body == null || body.isBlank() ? null : new JsonObject(body);
        String approver = request == null ? "presenter" : request.getString("approver", "presenter");
        String presented = request == null ? null : request.getString("patchSha256");
        try {
            ApprovalStore.Proposal p = approvals.approve(id, presented, approver);
            audit.record(approver, "approve", Decision.allow("approval.granted",
                    "Approved " + id + " for " + p.path() + " (expires " + p.expiresAt() + ")"),
                    Map.of("proposal", id, "patchSha256", p.patchSha256()));
            return Response.ok(new JsonObject(view(p)).encode()).build();
        } catch (PolicyViolation v) {
            audit.record(approver, "approve", v.decision(), Map.of("proposal", id));
            return Response.status(409).entity(new JsonObject(Map.of("verdict", v.decision().verdict().name(),
                    "rule", v.decision().rule(), "reason", v.decision().reason())).encode()).build();
        }
    }

    @POST
    @Path("/proposals/{id}/reject")
    public Response reject(@PathParam("id") String id) {
        try {
            ApprovalStore.Proposal p = approvals.reject(id, "presenter");
            audit.record("presenter", "reject", Decision.deny("approval.rejected", "Rejected " + id), Map.of("proposal", id));
            return Response.ok(new JsonObject(view(p)).encode()).build();
        } catch (PolicyViolation v) {
            return Response.status(409).entity(new JsonObject(Map.of("rule", v.decision().rule(), "reason", v.decision().reason())).encode()).build();
        }
    }

    @GET
    @Path("/audit")
    public String audit() {
        return new JsonArray(audit.recent().stream().map(JsonObject::new).toList()).encode();
    }

    @GET
    @Path("/diff")
    @Produces(MediaType.TEXT_PLAIN)
    public String diff() throws Exception {
        return inspector.redactForLog(tools.workspaceDiff());
    }

    /** Presenter-owned checks (e.g. acceptance-rps), run in the same sandbox but outside the agent's control. */
    @POST
    @Path("/verify/{task}")
    public Response verify(@PathParam("task") String task) {
        Decision d = policy.decideTask(task);
        if (!d.allowed()) {
            audit.record("presenter", "verify", d, Map.of("task", task));
            return Response.status(403).entity(new JsonObject(Map.of("rule", d.rule(), "reason", d.reason())).encode()).build();
        }
        SandboxRunner.Result r = sandbox.run(task, workspaces.current());
        audit.record("presenter", "verify:" + task, d, Map.of("status", r.status().name(), "tests", r.testsRun(),
                "failures", r.failures() + r.errors(), "durationMs", r.durationMs()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task", r.task());
        m.put("status", r.status().name());
        m.put("headline", r.headline());
        m.put("tests", r.testsRun());
        m.put("failures", r.failures());
        m.put("errors", r.errors());
        m.put("durationMs", r.durationMs());
        m.put("output", inspector.redactForLog(r.outputTail()));
        m.put("controls", r.controls());
        return Response.ok(new JsonObject(m).encode()).build();
    }

    /** Creates a fresh disposable workspace. Touches only .demo/runs; never the fixture or the developer's tree. */
    @POST
    @Path("/workspace/reset")
    public String reset() {
        approvals.clear();
        audit.clear();
        WorkspaceManager.Run run = workspaces.createRun();
        audit.record("presenter", "workspace-reset", Decision.allow("workspace.fresh", "New disposable workspace " + run.id()), Map.of());
        return new JsonObject().put("workspaceId", run.id()).encode();
    }

    private Map<String, Object> view(ApprovalStore.Proposal p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("path", p.path());
        m.put("state", p.state().name());
        m.put("diff", inspector.redactForLog(p.diff()));
        m.put("patchSha256", p.patchSha256());
        m.put("baseSha256", p.baseSha256());
        m.put("workspaceId", p.workspaceId());
        m.put("createdAt", p.createdAt().toString());
        m.put("expiresAt", p.expiresAt().toString());
        m.put("approvedBy", p.approvedBy());
        return m;
    }
}
