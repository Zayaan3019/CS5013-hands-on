import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local Ollama HTTP adapter; no third-party runtime dependencies. */
public class OllamaLlm implements LlmClient {
    private final String model;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public OllamaLlm(String model) { this.model = model; }

    @Override public String complete(String prompt) { return completeWithUsage(prompt).text(); }

    @Override public Reply completeWithUsage(String prompt) {
        String body = "{\"model\":" + quote(model) + ",\"prompt\":" + quote(prompt)
                + ",\"stream\":false,\"options\":{\"temperature\":0,\"seed\":42,"
                + "\"num_ctx\":8192,\"num_predict\":256}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/generate"))
                .timeout(Duration.ofMinutes(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> result = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() != 200) {
                throw new IllegalStateException("Ollama HTTP " + result.statusCode() + ": " + result.body());
            }
            return new Reply(stringField(result.body(), "response"),
                    numberField(result.body(), "prompt_eval_count"),
                    numberField(result.body(), "eval_count"), "Ollama prompt_eval_count + eval_count");
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach local Ollama", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama request interrupted", e);
        }
    }

    static String quote(String input) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    static long numberField(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new IllegalStateException("Missing Ollama usage: " + key);
        return Long.parseLong(m.group(1));
    }

    static String stringField(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) throw new IllegalStateException("Missing JSON string: " + key);
        String encoded = m.group(1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c != '\\') { out.append(c); continue; }
            char escaped = encoded.charAt(++i);
            switch (escaped) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '"', '\\', '/' -> out.append(escaped);
                case 'u' -> {
                    out.append((char)Integer.parseInt(encoded.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalStateException("Invalid JSON escape");
            }
        }
        return out.toString();
    }
}
