# AGENTS.md -- Quarkus Project Instructions

This is a Quarkus application. Follow these rules when working on this project.

## Required Workflow

### Bounded bug fixes using existing dependencies

1. Treat the explicit fix request as approval. Start implementation immediately
   without presenting a plan or pausing for confirmation.
2. Read the named source file and its directly related test first. Avoid broad
   searches unless those files do not contain the behavior.
3. Make the smallest viable change using existing APIs and update the targeted
   test. If a new dependency is required, use the new-feature workflow below.
4. Run only the related test with `./mvnw test -Dtest=<TestClass>`.
5. Do not update documentation unless explicitly requested.
6. Finish with a concise summary.

### New features or dependencies

1. Inspect `pom.xml` for existing Quarkus extensions first.
2. Consult official Quarkus documentation only when the existing project does
   not establish the required pattern.
3. Ask before adding a new extension when multiple alternatives exist.
4. Add tests and update README documentation for new features.

## Rules

- Prefer an existing Quarkus extension over implementing equivalent framework
  functionality by hand.
- Ask before choosing among multiple viable extensions.
- ALWAYS write tests for every feature -- no exceptions.
- ALWAYS keep README.md updated with app description, features, endpoints, and Quarkus guide links.
- ALWAYS summarize after completing work -- when you finish building an app, adding a feature, or completing a task, provide a clear summary of what was done (files created/modified, endpoints added, extensions used, etc.) and suggest logical next steps the user might want to take (e.g. adding security, observability, persistence, testing improvements, deployment).
- Use `@QuarkusTest` for integration tests -- Dev Services auto-starts backing services (databases, messaging, etc.).
- Use `%dev.` and `%test.` profile prefixes for dev/test configuration -- never hardcode connection URLs without a profile prefix.

## Testing

- Run a targeted test with `./mvnw test -Dtest=<TestClass>` for a bounded
  bug fix.
- Run `./mvnw test` when a change spans multiple components or adds a feature.
- After fixing a failure, rerun the affected test to verify the correction.
- Never run `./mvnw clean` while Quarkus dev mode is running.

## Error Handling

When compilation or a test fails, use the command output to identify the first
relevant user-code error, fix it, and rerun only the affected test. Read broader
application logs only when the direct failure output is insufficient.
