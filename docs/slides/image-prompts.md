# Image generation prompts

## Slide 9

Edit the reference presentation slide into a final polished replacement for slide 9 of Daniel Oh's Jfokus 2027 deck. Match its existing slide style and font closely: very light cool off-white background, bold dark navy sans-serif Helvetica/Inter-like typography, slate subtitle, cobalt blue and teal accents, fine pale borders, white rounded cards, flat line icons, navy code panel and bottom bar. No photorealism, no glow, no pixel art. Wide 16:9 landscape, target 1920x1080. Crisp readable typography, generous margins. Preserve the reference's two-column layout, with a large Java code card at left and a clear architecture diagram at right. Replace all old content with the following, spell exactly. Do not show @McpToolBox or invented APIs.
Top label: AI CODING / A PRACTICAL DEVELOPER GUIDE
Title: The Java Agent in Quarkus
Subtitle: Declare the agent. Keep the controls explicit.
Left card numbered 1 in blue: DECLARATIVE AGENT
A dark navy code panel, monospaced white code, containing exactly:
@Agent(description =
    "Explore the arcade and propose"
    + " narrow, reviewable fixes")
@SystemMessage(Prompts.SYSTEM)
String chat(
    @MemoryId String sessionId,
    @UserMessage String request);
Under the code panel in readable slate text:
@RegisterAiService wires the governed suppliers.
Model + tools + session memory.
Small caption: Agent method excerpt; full configuration in CodingAgent.java.
Right card numbered 2 in teal: THE GOVERNED AGENT LOOP
Diagram top box with server icon: Private LLM
Below, bidirectional arrow labeled Inference
Middle box with terminal icon: Quarkus @Agent
Inside small line: Generated model/tool conversation
Two small connected side boxes: Session memory / Run + tool budgets
Below middle, bidirectional arrow labeled MCP request / inspected result
Bottom box with server icon: Java MCP Server
Small note inside right card: Policy and approval stay on the server.
Bottom dark navy full-width rounded takeaway bar, split in two with thin separator:
QUARKUS — Generates the agent and drives tool calling.
YOUR CODE — Enforces approval, budgets and inspection.
Footer in small slate: Source: CodingAgent · GovernedChatModel · AgentInvocation
All text must be correct and sharp. Keep the lower takeaway bar and footer within the slide safe area.

## Slide 10

Edit the supplied presentation slide into a final replacement for slide 10 in Daniel Oh's Jfokus 2027 deck. Preserve the exact visual family and font: cool near-white background, bold navy sans-serif headings like Helvetica/Inter, slate subtitle, cobalt blue/teal/purple/orange accents, thin pale borders, rounded white cards, flat line icons, navy code panel and dark bottom bar. No glow, no photorealism. Landscape 16:9 target 1920x1080. Keep generous margins and readable type. Two-column layout: large source code card left, three stacked control cards right. Replace the old illustrative APIs entirely; use only the following content.
Top label: AI CODING / A PRACTICAL DEVELOPER GUIDE
Title: Java MCP Server: Govern Every Tool Call
Subtitle: Java-native tools enforce policy before actions and inspect results before delivery.
Left numbered blue card heading: QUARKUS MCP ANNOTATIONS
Dark navy code panel with white monospace code exactly:
@ToolGuardrails(
    output = LeakageOutputGuardrail.class)
@Tool(name = "read_file",
    description = "Read one permitted text file"
        + " (max 64 KB). Path is relative"
        + " to the repository root.")
ToolResponse readFile(
    @ToolArg(description = "Relative file path")
    String path) { /* ... */ }
Below in small slate readable text: Abbreviated declaration; full checks in CodingTools.java.
Right upper card teal heading: TRUSTED WORKSPACE
Icon of ID card and folder. Text: Authenticate the caller. Resolve the workspace from server state.
Down arrow to middle card.
Middle card purple heading: POLICY BEFORE ACTIONS
Shield icon. Text: Contain paths. Require approval for writes. Run only named verification tasks.
Down arrow to lower card.
Lower card orange heading: INSPECT EVERY RESULT
Filter icon. Text: Bound output. Redact secrets and canaries. Block restricted content.
Bottom full width dark navy bar, two boxes separated by bidirectional arrows:
Left: QUARKUS @AGENT
smaller: Conversation + run budgets
Right: JAVA MCP SERVER
smaller: Tools + policy + output guardrails
Footer slate text: Tool handlers hold no chat history. Workspace, proposals and approvals remain server-side state.
Make no claim that @Agent implements MCP policy or that the server has no state. Preserve clear singular 'Java MCP Server'. Ensure all text stays inside the safe area.
