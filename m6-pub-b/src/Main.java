import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Reproducible live experiments. Run from the lab directory. */
public class Main {
    public static final String QUESTION_A = "How many books did Ursula Le Guin write in the 1970s?";
    public static final String HARD_QUESTION = "Compare the catalogue counts for Ursula Le Guin "
            + "in the 1960s and 1970s, and Octavia Butler in the 1970s and 1990s. "
            + "For an auditable comparison, make four separate search_books calls, one for each "
            + "author-decade pair in that order, then report the four counts and which author "
            + "has more books across their two decades. Do not combine pairs in a broad search.";

    public static void main(String[] args) throws Exception {
        String experiment = args.length > 0 ? args[0] : "01-sharp";
        Path prompt = Path.of(args.length > 1 ? args[1] : "prompts/react.txt");
        int steps = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        long tokens = args.length > 3 ? Long.parseLong(args[3]) : Long.MAX_VALUE;
        String question = args.length > 4 && args[4].equals("hard") ? HARD_QUESTION : QUESTION_A;
        String model = args.length > 5 ? args[5] : "llama3.1:8b";
        Path traces = Path.of("traces");
        Files.createDirectories(traces);
        StringBuilder raw = new StringBuilder();
        OllamaLlm live = new OllamaLlm(model);
        LlmClient logged = new LlmClient() {
            int turn;
            public String complete(String p) { return completeWithUsage(p).text(); }
            public Reply completeWithUsage(String p) {
                System.out.println(experiment + ": model request " + (++turn));
                Reply r = live.completeWithUsage(p);
                raw.append("=== Model response ").append(turn).append(" (untrusted) ===\n")
                        .append(r.text()).append("\nInput tokens: ").append(r.inputTokens())
                        .append("; output tokens: ").append(r.outputTokens()).append("\n\n");
                return r;
            }
        };
        AgentDriver agent = new AgentDriver(logged, prompt, steps, tokens);
        AgentDriver.RunResult result = agent.runDetailed(question);
        String report = "Experiment: " + experiment + "\nUTC: " + Instant.now()
                + "\nModel: " + model + "\nOptions: temperature=0, seed=42, num_ctx=8192, num_predict=256"
                + "\nQuestion: " + question + "\nPrompt: " + prompt
                + "\nPrompt SHA-256: " + sha(Files.readAllBytes(prompt))
                + "\nCatalogue SHA-256: " + sha(Files.readAllBytes(Path.of("books.json")))
                + "\nStep budget: " + steps + "\nToken budget: " + tokens
                + "\nIterations: " + result.iterations() + "\nTool executions: "
                + result.calls().stream().filter(AgentDriver.ToolCall::executed).count()
                + "\nInput tokens: " + result.inputTokens() + "\nOutput tokens: " + result.outputTokens()
                + "\nTotal tokens: " + result.tokens() + "\nStop reason: " + result.stopReason() + "\n";
        for (AgentDriver.ToolCall c : result.calls()) {
            report += (c.executed() ? "Tool execution" : "Rejected tool request")
                    + " (iteration " + c.iteration() + "): " + c.name()
                    + "(" + c.argument() + ")\n";
        }
        report += "\n=== Effective prompt template ===\n" + Files.readString(prompt)
                + "\n=== Driver transcript (observations are real tool results) ===\n"
                + result.transcript() + "\n=== Raw model responses ===\n" + raw;
        Files.writeString(traces.resolve(experiment + ".txt"), report.stripTrailing() + "\n");
        System.out.println(report.substring(0, report.indexOf("=== Effective")));
        System.out.println(result.transcript());
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
