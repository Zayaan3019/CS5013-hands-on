import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A bounded ReAct loop. Only the driver can append tool observations. */
public class AgentDriver {
    private final LlmClient llm;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path promptTemplate;
    private final int stepBudget;
    private final long tokenBudget;
    private static final Pattern FINAL = Pattern.compile("(?m)^Final Answer:");

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget) {
        this(llm, promptTemplate, stepBudget, Long.MAX_VALUE);
    }

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget, long tokenBudget) {
        if (stepBudget <= 0 || tokenBudget <= 0) {
            throw new IllegalArgumentException("Budgets must be positive");
        }
        this.llm = llm;
        this.promptTemplate = promptTemplate;
        this.stepBudget = stepBudget;
        this.tokenBudget = tokenBudget;
        registerTool(new ListBooksTool());
        registerTool(new SearchBooksTool());
    }

    public void registerTool(Tool tool) { tools.put(tool.name(), tool); }
    public Map<String, Tool> getTools() { return Map.copyOf(tools); }

    public record ToolCall(int iteration, String name, String argument, String observation, boolean executed) {}
    public record RunResult(String transcript, int iterations, long inputTokens,
                            long outputTokens, String stopReason, List<ToolCall> calls) {
        public long tokens() { return inputTokens + outputTokens; }
    }

    public String run(String userQuery) throws IOException {
        return runDetailed(userQuery).transcript();
    }

    /**
     * A step is one model request, with at most one tool execution. Tokens include every
     * input prompt (including repeated history) and generated output. Usage is checked
     * after generation: reaching the cap blocks the proposed tool and any next request.
     * This is a metered stopping budget, not a provider-side hard generation cap.
     */
    public RunResult runDetailed(String userQuery) throws IOException {
        String template = Files.readString(promptTemplate);
        StringBuilder scratch = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        int iterations = 0;
        long inputTokens = 0, outputTokens = 0;
        String reason = "STEP_BUDGET";
        for (int step = 0; step < stepBudget; step++) {
            Matcher slots = Pattern.compile("\\{query\\}|\\{scratchpad\\}").matcher(template);
            StringBuilder prompt = new StringBuilder();
            while (slots.find()) {
                slots.appendReplacement(prompt, Matcher.quoteReplacement(
                        slots.group().equals("{query}") ? userQuery : scratch.toString()));
            }
            slots.appendTail(prompt);
            LlmClient.Reply reply;
            iterations++;
            try {
                reply = llm.completeWithUsage(prompt.toString());
            } catch (RuntimeException e) {
                scratch.append("Driver: model error: ").append(e.getMessage()).append('\n');
                reason = "MODEL_ERROR";
                break;
            }
            inputTokens = Math.addExact(inputTokens, reply.inputTokens());
            outputTokens = Math.addExact(outputTokens, reply.outputTokens());
            String response = reply.text();
            Matcher fabricated = Pattern.compile("(?m)^Observation:").matcher(response);
            if (fabricated.find()) response = response.substring(0, fabricated.start()).stripTrailing();
            scratch.append(response).append('\n');
            scratch.append("Driver usage: iteration=").append(iterations)
                    .append(" input=").append(reply.inputTokens())
                    .append(" output=").append(reply.outputTokens())
                    .append(" cumulative=").append(inputTokens + outputTokens)
                    .append(" accounting=").append(reply.accounting()).append('\n');
            if (inputTokens + outputTokens >= tokenBudget) {
                reason = "TOKEN_BUDGET";
                break;
            }
            if (FINAL.matcher(response).find()) {
                reason = "FINAL_ANSWER";
                break;
            }
            Map.Entry<String, String> action = parseAction(response);
            if (action == null) {
                reason = "INVALID_ACTION";
                break;
            }
            Tool tool = tools.get(action.getKey());
            String observation;
            try {
                observation = tool == null ? "ERROR: unknown tool " + action.getKey()
                        : tool.call(action.getValue());
            } catch (RuntimeException e) {
                observation = "ERROR: " + e.getMessage();
            }
            calls.add(new ToolCall(iterations, action.getKey(), action.getValue(), observation, tool != null));
            scratch.append("Observation: ").append(observation).append('\n');
            // Test the step boundary after the permitted tool, not before it.
            if (shouldStop(response, step)) break;
        }
        scratch.append("Driver stop: ").append(reason).append("; iterations=")
                .append(iterations).append('/').append(stepBudget)
                .append("; tokens=").append(inputTokens + outputTokens).append('/')
                .append(tokenBudget == Long.MAX_VALUE ? "unlimited" : tokenBudget).append('\n');
        return new RunResult(scratch.toString(), iterations, inputTokens, outputTokens,
                reason, List.copyOf(calls));
    }

    /** step is zero-based; the boundary check runs after this step's tool. */
    protected boolean shouldStop(String response, int step) {
        return step >= stepBudget - 1 || (response != null && FINAL.matcher(response).find());
    }

    /** Parse the first action and input; never borrow input from a later action. */
    protected Map.Entry<String, String> parseAction(String response) {
        if (response == null) return null;
        Matcher action = Pattern.compile(
                "(?m)^Action:[ \\t]*([A-Za-z_][A-Za-z0-9_]*)[ \\t]*\\r?$").matcher(response);
        if (!action.find()) return null;
        String name = action.group(1);
        int end = action.end();
        int nextAction = action.find() ? action.start() : response.length();
        Matcher input = Pattern.compile("(?m)^Action Input:[ \\t]*([^\\r\\n]*)")
                .matcher(response.substring(end, nextAction));
        if (!input.find()) return null;
        return Map.entry(name, input.group(1).trim());
    }
}
