# Presenter-owned acceptance checks

These tests define correct behaviour for the demo tasks, independently of the arcade's own tests
(which the agent may edit and which, for RPS, still assert the seeded bug).

They live outside the agent-writable workspace. The MCP server copies one test class into a scratch
copy of the workspace immediately before a sandboxed run (`acceptance-rps`, `acceptance-scramble`), and
counts results from the surefire report of that class only. A run with zero executed acceptance tests
is reported as FAILED.

| Task | Test | Starting fixture | After a correct fix |
|------|------|------------------|---------------------|
| Primary: RPS invalid input | `RpsAcceptanceTest` | FAILS (5 of 9) | PASSES (9 of 9) |
| Backup: Scramble case-insensitive | `ScrambleAcceptanceTest` | FAILS | PASSES (5 of 5) |
