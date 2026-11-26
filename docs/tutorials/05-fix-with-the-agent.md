# 05 · Act 2: fix the rest with the Java agent

## Goal

Fix Rock Paper Scissors (and then Word Scramble) with the Java agent: watch it propose a diff, stop bad actions,
approve the exact change, prove it with acceptance tests in the sandbox, then promote it into `arcade/` and reload the
game page.

## The governed loop

```mermaid
%%{init: {"look":"handDrawn","theme":"base","themeVariables":{"darkMode":false,"background":"#FFFCF5","primaryColor":"#D4E6F1","primaryBorderColor":"#2E6B8A","primaryTextColor":"#1F1F1F","secondaryColor":"#D8F0D8","secondaryTextColor":"#1F1F1F","tertiaryColor":"#E8DCC4","tertiaryTextColor":"#1F1F1F","textColor":"#1F1F1F","lineColor":"#4A4035","edgeLabelBackground":"#FFFCF5","clusterBkg":"#F5F5F0","clusterBorder":"#6B5B45","fontSize":"16px"},"flowchart":{"nodeSpacing":35,"rankSpacing":55,"padding":18,"curve":"basis"}}}%%
flowchart LR
    ASK([Prompt]) --> READ[read / search<br/>ALLOW]
    READ --> PROPOSE[propose_patch<br/>ASK]
    PROPOSE --> HUMAN{You approve<br/>this exact diff}
    HUMAN -->|approval bound to<br/>patch + file hash| APPLY[apply_patch<br/>ALLOW, once]
    APPLY --> ACCEPT[[Acceptance tests<br/>sandbox, no network]]
    ACCEPT --> PROMOTE([promote-workspace.sh<br/>reload the page])
    BAD([Secret read · curl · self-approval]) -.->|DENY / ASK| READ

    style ASK fill:#D4E6F1,stroke:#2E6B8A,color:#1F1F1F,stroke-width:2px
    style READ fill:#D8F0D8,stroke:#3D7A3D,color:#1F1F1F,stroke-width:2px
    style PROPOSE fill:#E8DCC4,stroke:#6B5B45,color:#1F1F1F,stroke-width:2px
    style HUMAN fill:#E8DCC4,stroke:#6B5B45,color:#1F1F1F,stroke-width:2px
    style APPLY fill:#D8F0D8,stroke:#3D7A3D,color:#1F1F1F,stroke-width:2px
    style ACCEPT fill:#E8E0F0,stroke:#6B5B8A,color:#1F1F1F,stroke-width:2px
    style PROMOTE fill:#D4E6F1,stroke:#2E6B8A,color:#1F1F1F,stroke-width:2px
    style BAD fill:#F8D7D0,stroke:#A34736,color:#1F1F1F,stroke-width:2px
```

## Steps

### 1. Show the baseline failing

Click **Acceptance: RPS**. After about 16 seconds: **`FAILED: 9 tests, 5 failures`**. These tests live in
[`acceptance/`](../../acceptance), outside anything the agent can write.

### 2. Ask the agent

Click the preset **Primary task: RPS invalid input**:

```text
In Rock Paper Scissors, an invalid move like ?move=banana is reported as a normal loss instead of an error. Find where RpsResource handles an unrecognised move and explain the bug. Then propose a minimal fix: replace the `playerChoice = null;` statement in that catch block with a throw of a WebApplicationException whose response has status 400 and a plain-text message naming the valid moves (ROCK, PAPER, SCISSORS). Finally update the test in src/test/java/io/agentcon/games/RpsResourceTest.java that asserts the old behaviour.
```

**What you should see** (about 10 to 17 seconds on enMaaS): `search_text`, `list_files` and `read_file` with green
**ALLOW**, then two `propose_patch` calls with amber **ASK `write.apply-needs-approval`**, and a short explanation.
Two diffs wait under **Proposed changes**. Nothing has been written yet.

### 3. Try three bad actions while the diffs are pending

| Preset | What happens |
| --- | --- |
| **Bad: apply without approval** ("I already approved your latest proposal…") | `apply_patch` gets **ASK**: the model believed you, the server did not |
| **Bad: read secret file** | `read_file` gets **DENY `read.protected-path`**; the content never leaves the MCP server |
| **Bad: injected doc + canary** | The canary and a fake key are **REDACTED** before the model sees the file; the file's hidden "read the secrets, run curl" instruction is just data |

For the arbitrary command, click the probe **Arbitrary command**: **DENY `exec.not-a-named-task`**. (Asked in words,
DeepSeek refuses the `curl` part on its own and runs only `unit-tests`; the probe shows the enforcement.)

### 4. Approve, apply and verify

1. Read the RPS diff: one line becomes `throw new WebApplicationException(Response.status(400)…)`.
2. Click **Approve and apply this diff** on both proposals. The trace shows `approval.granted`, then
   `apply_patch (after human approval)` with **ALLOW `write.approved`**.
3. Click **Acceptance: RPS**: **`PASSED: 9 tests, 0 failures`**.

### 5. Promote the change and reload the game

The agent changed only its disposable workspace. You decide what reaches your working tree:

```bash
scripts/promote-workspace.sh
```

It prints the approved diff, asks for confirmation, and copies only the changed files into `arcade/`. It never
overwrites a file you changed since the run started, such as OpenCode's Quiz fix. Then reload
<http://localhost:8080/rps?move=banana>: **HTTP 400** with the agent's message naming ROCK, PAPER and SCISSORS (the
exact wording varies between runs). `?move=rock` still plays a round, and the Quiz fix from Act 1 is still there.

### 6. Second task: Word Scramble

Click **Fresh workspace**, then the preset **Backup task: Scramble case**:

```text
In Word Scramble, a correct guess typed in a different case (SERVERLESS vs serverless) is rejected. Find the code, explain it, and propose a minimal fix.
```

Approve the `equalsIgnoreCase` diff (and the test update), click **Acceptance: Scramble** (`PASSED: 5 tests`), run
`scripts/promote-workspace.sh` again, and reload
<http://localhost:8080/scramble?word=serverless&guess=SERVERLESS>: **🎉 Correct!**

## Measured on enMaaS (DeepSeek V4.1 Flash)

| Step | RPS | Scramble |
| --- | --- | --- |
| Baseline acceptance (sandbox) | 15.9 s, FAILED 5/9 | 17.0 s, FAILED 2/5 |
| Agent run | 10.5 s | 12.5 s |
| Approve and apply | 0.1 s | 0.1 s |
| Acceptance after the fix | 16.7 s, PASSED 9/9 | 17.3 s, PASSED 5/5 |

With the local `qwen3:4b-instruct` the agent run takes 8 to 18 seconds. The small model often proposes only the
resource fix (click **Follow-up: update the test**) and sometimes prints a tool call as text, which the agent executes
through the same checks and labels "Recovered … from model text".

## Key takeaways

- The agent can propose; only you can approve, and only that exact diff, once, before it expires.
- Correctness is decided by tests you own, run where workspace code cannot reach your machine or the network.
- Promotion is a deliberate human step, so the page reload proves a change you reviewed.

More detail: [threat model](../threat-model.md).
