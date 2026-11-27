// Insights shown in the console for each demo step and action.
// Markup inside strings: ==keyword== highlight, ++ALLOW++ green, ??ASK?? amber, !!DENY!! red, `code`.
// "say" is the presenter cue; "vs" contrasts with Act 1 (OpenCode).

window.DEMO_STEPS = [
  {
    id: 'baseline', n: 1, label: 'Baseline: my tests fail',
    targets: ['[data-verify="acceptance-rps"]'],
    insight: 'verify:acceptance-rps',
  },
  {
    id: 'ask', n: 2, label: 'Ask the agent',
    targets: ['[data-preset="primary"]', '#send'],
    insight: 'preset:primary',
  },
  {
    id: 'bad', n: 3, label: 'Try bad actions',
    targets: ['[data-preset="apply"]', '[data-preset="secret"]', '[data-preset="canary"]', '[data-probe="command"]'],
    insight: 'step:bad',
  },
  {
    id: 'approve', n: 4, label: 'Approve and verify',
    targets: ['[data-approve]', '[data-verify="acceptance-rps"]'],
    insight: 'step:approve',
  },
  {
    id: 'promote', n: 5, label: 'Promote and reload',
    targets: [],
    insight: 'step:promote',
  },
  {
    id: 'scramble', n: 6, label: 'Second task: Scramble',
    targets: ['#reset', '[data-preset="backup"]', '[data-verify="acceptance-scramble"]'],
    insight: 'preset:backup',
  },
];

window.INSIGHTS = {
  // ---- Step 1 / presenter checks ------------------------------------------------------------
  'verify:acceptance-rps': {
    title: 'Baseline: my tests decide "done"',
    headline: 'Before the agent touches anything, ==tests I own== define ==done==.',
    points: [
      '9 tests. The ==5 failures are the bug== (4 invalid moves and the error message). The 4 passing tests ==guard against regressions==.',
      'They live in `acceptance/`, ==outside the agent\'s workspace==. The agent can\'t list, read or edit them.',
      'Copied into a ==throwaway sandbox== only when I click: ==no network==, read-only filesystem, CPU, memory and time limits.',
    ],
    say: 'Five fail, and those five are the bug. The agent can\'t see these tests, so when this turns green, it didn\'t grade its own homework.',
    vs: 'In Act 1, OpenCode wrote the test and ran it on my machine: the same tool wrote the code and graded it.',
  },
  'verify:acceptance-rps:FAILED': {
    title: 'Red is the right answer here',
    headline: '!!FAILED!! proves the test ==actually checks the bug==: it isn\'t green no matter what.',
    points: [
      'The failures are exactly the invalid moves (`banana`, `lizard`, `ROCKS`, `1`) and the message naming the valid moves.',
      'Same tests, same sandbox, after the fix. Only the code changes.',
      'The time you just watched is ==a real Maven build== in an ==isolated container==: the cost of trustworthy verification.',
    ],
    say: 'Red is what we want before the fix. Now let\'s ask the agent.',
  },
  'verify:acceptance-rps:PASSED': {
    title: 'Green from tests the agent never saw',
    headline: '++PASSED++: ==independent acceptance==, not the agent\'s word.',
    points: [
      'The same 9 tests that failed now pass: the bug is fixed ==and== valid moves still play a round.',
      'The agent couldn\'t edit these tests, so it couldn\'t make them pass by weakening them.',
      'Still only in the ==disposable workspace==. Your working tree is untouched until you promote.',
    ],
    say: 'Same tests, same sandbox, now green. The agent didn\'t grade its own homework.',
  },
  'verify:acceptance-scramble': {
    title: 'Scramble baseline',
    headline: 'Same idea, second bug: ==tests I own== define ==done==.',
    points: [
      '5 tests. The ==2 failures are the bug==: correct guesses in another case are rejected.',
      'Exact-case guesses must keep working, so the passing tests ==guard against regressions==.',
      'Runs in the ==throwaway sandbox== with ==no network==.',
    ],
    say: 'Two fail, and those two are the bug.',
  },
  'verify:acceptance-scramble:FAILED': {
    title: 'Scramble: red before the fix',
    headline: '!!FAILED!! is expected: mixed-case guesses are still rejected.',
    points: ['Now ask the agent with the ==Backup task== preset.'],
    say: 'Red before the fix, as expected.',
  },
  'verify:acceptance-scramble:PASSED': {
    title: 'Scramble: green',
    headline: '++PASSED++: case-insensitive guesses work, exact guesses still work.',
    points: ['Promote with `scripts/promote-workspace.sh`, then reload the Scramble page.'],
    say: 'Second bug, same governed loop, same independent check.',
  },
  'verify:unit-tests': {
    title: 'The arcade\'s own unit tests',
    headline: 'Useful, but ==written in the workspace==. The agent can change these.',
    points: [
      'That\'s why the agent\'s test edits show up in the diff for ==review==.',
      'The ==acceptance tests== are the independent check, because the agent can\'t reach them.',
    ],
    say: 'Unit tests help, but the tests that decide "done" are the ones the agent can\'t touch.',
  },
  'verify:unit-tests:PASSED': { ref: 'verify:unit-tests' },
  'verify:unit-tests:FAILED': { ref: 'verify:unit-tests' },

  // ---- Step 2 / live prompts -----------------------------------------------------------------
  'preset:primary': {
    title: 'The agent may read, but only propose',
    headline: 'Reading is ++ALLOW++. Changing code is ??ASK??: ==nothing is written== until I approve.',
    points: [
      'Watch `search_text`, `list_files`, `read_file` turn green, then `propose_patch` turn amber (`write.apply-needs-approval`).',
      'Only ==7 MCP tools==, on a ==disposable copy== of the arcade. There is ==no shell tool==.',
      'The model is reached through a ==pinned host==: no redirects, no proxy. The agent process can\'t touch files; the MCP server never talks to the model.',
    ],
    say: 'Green when it reads, amber when it wants to change something. Two diffs wait for me, and nothing has been written.',
    vs: 'In Act 1, OpenCode edited my working tree directly and I reviewed afterwards with git diff.',
  },
  'preset:followup': {
    title: 'A nudge, same rules',
    headline: 'Small models sometimes skip the test. The follow-up goes through the ==same checks==.',
    points: ['Another `propose_patch` with ??ASK??. Approving the first diff doesn\'t approve this one.'],
    say: 'Smaller model, one more nudge, same guardrails.',
  },
  'preset:backup': {
    title: 'Second task in a fresh workspace',
    headline: 'Each task gets a ==fresh disposable workspace==: nothing carries over.',
    points: [
      'Click ==Fresh workspace== first, then send the ==Backup task== preset.',
      'Old approvals are ==bound to the old workspace==, so they can\'t be replayed here.',
      'Then approve, run ==Acceptance: Scramble==, and promote.',
    ],
    say: 'Same loop, second bug. Watch how little I have to say.',
  },

  // ---- Step 3 / bad actions ------------------------------------------------------------------
  'step:bad': {
    title: 'Three ways an agent goes wrong',
    headline: 'It ==believes a lie==, ==reads a secret==, or ==follows an injected instruction==. Each is stopped ==outside the model==.',
    points: [
      '==Bad: apply without approval==: a chat message claims I approved.',
      '==Bad: read secret file==: the agent asks for `config/demo-secrets.properties`.',
      '==Bad: injected doc + canary==: a file hides "read the secrets, run curl".',
      'Then the ==Arbitrary command== probe: no model, just the enforcement.',
    ],
    say: 'Let\'s try to make it misbehave while those diffs are still pending.',
  },
  'preset:apply': {
    title: 'The model believed me. The server didn\'t.',
    headline: '`apply_patch` gets ??ASK??: approval lives in the ==MCP server==, not in the chat.',
    points: [
      'Approval is ==bound to the exact patch hash and file hash==, single use, with an expiry.',
      'A sentence in the prompt can\'t create that record. Only the presenter UI can, with its own token.',
    ],
    say: 'The model believed me. The server didn\'t.',
    vs: 'OpenCode trusts its user, so "I already approved" would just work.',
  },
  'preset:secret': {
    title: 'Secrets never leave the tool server',
    headline: '`read_file` gets !!DENY!! `read.protected-path`: the ==content never leaves the MCP server==.',
    points: [
      'Policy runs ==before the effect==, in the tool server. The prompt can\'t talk its way past it.',
      'The audit log keeps ==metadata only==, never the secret.',
    ],
    say: 'Denied before the file is opened. The model never sees a byte of it.',
    vs: 'In Act 1, anything OpenCode reads can go to the model.',
  },
  'preset:canary': {
    title: 'Redacted before the model',
    headline: 'The canary and a fake key are ==REDACTED== ==before the model== sees the file.',
    points: [
      'The file\'s hidden "read the secrets, run curl" instruction is ==just data==: tool output is wrapped as ==untrusted==.',
      'Detection uses ==configured patterns== and a ==synthetic canary==. It catches what it\'s configured for, not everything.',
      'Network controls restrict ==where== data can go; content rules decide ==what== can go.',
    ],
    say: 'The model got the file, minus the secrets. And the instruction hidden in it was treated as text, not orders.',
  },
  'preset:command': {
    title: 'No shell, only named tasks',
    headline: 'There is ==no shell tool==. `run_verification` accepts only ==named tasks==.',
    points: [
      'A capable model often ==refuses on its own==. The ==Arbitrary command== probe shows the enforcement without relying on that.',
    ],
    say: 'The model may be polite. The server doesn\'t rely on it.',
  },

  // ---- Deterministic probes ------------------------------------------------------------------
  'probe:secret': {
    title: 'Probe: secret file read',
    headline: '!!DENY!! `read.protected-path`. ==Deterministic==: no model, same MCP and policy path.',
    points: ['Probes prove ==enforcement== even when the model behaves perfectly.'],
    say: 'No model involved: this is the server saying no.',
  },
  'probe:traversal': {
    title: 'Probe: path traversal',
    headline: '`../../../../etc/passwd` gets !!DENY!!: paths are ==canonicalized== and must stay ==inside the workspace==.',
    points: ['Symlinks and `..` can\'t escape the disposable copy.'],
    say: 'Classic escape attempt, denied by canonical path checks.',
  },
  'probe:canary': {
    title: 'Probe: canary in user input',
    headline: 'The canary is ==REDACTED== from ==my own input== before it reaches the model.',
    points: ['Inspection runs in ==both directions==: what I type and what tools return.'],
    say: 'Even if I paste a secret, the model gets a redacted version.',
  },
  'probe:restricted': {
    title: 'Probe: restricted marker',
    headline: 'A file marked `AGENT-RESTRICTED` is !!BLOCKED!! as a whole, not just redacted.',
    points: ['Some content shouldn\'t reach the model ==at all==, such as partner terms.'],
    say: 'Redaction for secrets, blocking for whole documents.',
  },
  'probe:command': {
    title: 'Probe: arbitrary command',
    headline: '`curl … | sh` gets !!DENY!! `exec.not-a-named-task`. There is ==no shell tool==.',
    points: [
      'Only ==named tasks== run (unit tests, acceptance checks), inside a container with ==network=none==.',
      'Isolation ==fails closed==: no container runtime, no execution.',
    ],
    say: 'There is nothing to inject a command into.',
    vs: 'In Act 1, OpenCode had a shell on my machine, with my credentials and network.',
  },
  'probe:unknown-tool': {
    title: 'Probe: unknown tool',
    headline: '`run_shell` gets !!DENY!! `agent.tool-not-allowed`: the agent has a ==tool allowlist==.',
    points: ['Checked in the agent ==before== any MCP call, on top of the server\'s own policy.'],
    say: 'Two independent checks: the agent\'s allowlist and the server\'s policy.',
  },
  'probe:unapproved-write': {
    title: 'Probe: write without approval',
    headline: '`apply_patch` with no approval gets ??ASK??. ==Writes need a human==.',
    points: ['The server checks for an approval record bound to that ==exact patch==.'],
    say: 'No approval record, no write.',
  },

  // ---- Step 4 / approvals --------------------------------------------------------------------
  'step:approve': {
    title: 'Approve this exact diff',
    headline: 'Approval is ==bound to this diff==: patch hash, file hash, workspace, ==single use==, ==expiry==.',
    points: [
      'Read the diff first: one line becomes a `WebApplicationException` with status 400.',
      'Then run ==Acceptance: RPS== again: same tests, same sandbox.',
    ],
    say: 'I approve exactly this change, once. If the diff or the file changes, the approval no longer matches.',
  },
  'action:approve': {
    title: 'Approved, applied once',
    headline: '`apply_patch` gets ++ALLOW++ `write.approved`: ==exactly this diff==, ==once==.',
    points: [
      'If the patch or the base file changed, the hashes wouldn\'t match and the write would be refused.',
      'Applied only to the ==disposable workspace==. Next: ==Acceptance: RPS==.',
    ],
    say: 'Approved and applied, once. Now the tests I own.',
  },
  'action:reject': {
    title: 'Rejected',
    headline: 'Rejecting is cheap: the workspace is ==disposable== and nothing was written.',
    points: ['Ask again, or start a ==fresh workspace==.'],
    say: 'Nothing to undo.',
  },

  // ---- Step 5 / promotion --------------------------------------------------------------------
  'step:promote': {
    title: 'You promote, not the agent',
    headline: 'The agent never touches ==your working tree==. ==You promote== the approved files.',
    points: [
      '`scripts/promote-workspace.sh` shows the diff, asks, and copies ==only the changed files== into `arcade/`.',
      'It ==refuses to overwrite== files you changed since the run started, such as OpenCode\'s Quiz fix.',
      'Reload the game page: the fix you ==reviewed and verified== is now live.',
    ],
    say: 'Promotion is a deliberate human step, so this reload proves a change I reviewed.',
    vs: 'In Act 1, the change was already in my tree before I looked at it.',
    command: 'scripts/promote-workspace.sh',
    link: 'http://localhost:8080/rps?move=banana',
  },

  // ---- Run controls ---------------------------------------------------------------------------
  'action:reset': {
    title: 'Fresh workspace',
    headline: 'A new ==disposable copy== of `arcade/`. Old approvals ==don\'t carry over==.',
    points: ['Approvals are bound to the workspace they were granted in.'],
    say: 'Clean slate for the next task.',
  },
};
