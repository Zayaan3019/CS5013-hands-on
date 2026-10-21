import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BudgetAndProtocolTest {
    private static final Path PROMPT = Path.of("prompts/react.txt");
    private static final String ACTION = "Thought: look up books\nAction: search_books\n"
            + "Action Input: Ursula Le Guin|1970|1979";

    private LlmClient metered(AtomicInteger requests, long input, long output) {
        return new LlmClient() {
            public String complete(String p) { throw new AssertionError("Use usage-aware method"); }
            public Reply completeWithUsage(String p) {
                requests.incrementAndGet();
                return new Reply(ACTION, input, output, "test fixture, not a live model");
            }
        };
    }

    @Test void stepBudgetAllowsExactlyThreeTurnsAndCalls() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AgentDriver.RunResult r = new AgentDriver(metered(requests, 10, 5), PROMPT, 3)
                .runDetailed(Main.HARD_QUESTION);
        assertEquals("STEP_BUDGET", r.stopReason());
        assertEquals(3, r.iterations());
        assertEquals(3, requests.get());
        assertEquals(3, r.calls().size());
        assertEquals(45, r.tokens());
        assertFalse(r.transcript().contains("Final Answer:"));
    }

    @Test void tokenBudgetAtBoundaryBlocksSecondToolAndThirdRequest() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AgentDriver.RunResult r = new AgentDriver(metered(requests, 80, 20), PROMPT, 5, 200)
                .runDetailed("Count books");
        assertEquals("TOKEN_BUDGET", r.stopReason());
        assertEquals(2, requests.get());
        assertEquals(200, r.tokens());
        assertEquals(1, r.calls().size());
    }

    @Test void firstResponseCanOvershootButCannotExecuteATool() throws IOException {
        AgentDriver.RunResult r = new AgentDriver(metered(new AtomicInteger(), 100, 30), PROMPT, 5, 50)
                .runDetailed("Count books");
        assertEquals("TOKEN_BUDGET", r.stopReason());
        assertEquals(1, r.iterations());
        assertEquals(130, r.tokens());
        assertTrue(r.calls().isEmpty());
    }

    @Test void finalAnswerStopsWithoutCallingAnyTool() throws IOException {
        AgentDriver.RunResult r = new AgentDriver(p -> "Final Answer: done", PROMPT, 3)
                .runDetailed("Already done");
        assertEquals("FINAL_ANSWER", r.stopReason());
        assertEquals(1, r.iterations());
        assertTrue(r.calls().isEmpty());
    }

    @Test void invalidBudgetsFailImmediately() {
        assertThrows(IllegalArgumentException.class, () -> new AgentDriver(p -> "", PROMPT, 0));
        assertThrows(IllegalArgumentException.class, () -> new AgentDriver(p -> "", PROMPT, 3, 0));
    }

    @Test void malformedActionStopsWithoutExecutingTool() throws IOException {
        AgentDriver.RunResult r = new AgentDriver(p -> "Action: list_books", PROMPT, 3)
                .runDetailed("Count books");
        assertEquals("INVALID_ACTION", r.stopReason());
        assertTrue(r.calls().isEmpty());
    }

    @Test void actionInputCannotBeBorrowedFromSecondAction() {
        AgentDriver agent = new AgentDriver(p -> "", PROMPT, 3);
        assertNull(agent.parseAction("Action: list_books\nAction: search_books\nAction Input: Le Guin||"));
        var a = agent.parseAction(ACTION + "\nAction: list_books\nAction Input:\n");
        assertEquals("search_books", a.getKey());
        assertEquals("Ursula Le Guin|1970|1979", a.getValue());
        assertEquals("", agent.parseAction("Action: list_books\r\nAction Input:\r\n").getValue());
    }

    @Test void unknownToolIsAnObservationAndCannotRunForever() throws IOException {
        AgentDriver.RunResult r = new AgentDriver(p -> "Action: mystery\nAction Input: x", PROMPT, 1)
                .runDetailed("Count books");
        assertEquals("STEP_BUDGET", r.stopReason());
        assertTrue(r.transcript().contains("ERROR: unknown tool mystery"));
        assertFalse(r.calls().get(0).executed());
    }

    @Test void failedModelIsReportedSeparatelyFromBudget() throws IOException {
        AgentDriver.RunResult r = new AgentDriver(p -> { throw new IllegalStateException("offline"); }, PROMPT, 3)
                .runDetailed("Count books");
        assertEquals("MODEL_ERROR", r.stopReason());
        assertEquals(1, r.iterations());
        assertTrue(r.calls().isEmpty());
    }

    @Test void yearBoundsAreInclusiveAndEmptyResultIsValid() {
        assertTrue(new SearchBooksTool().call("Le Guin|1974|1974").contains("The Dispossessed"));
        assertEquals("[]", new SearchBooksTool().call("Le Guin|1975|1979"));
        assertTrue(new SearchBooksTool().call("isaac asimov||").contains("Foundation"));
        assertTrue(new SearchBooksTool().call("a|b|c|d").startsWith("ERROR:"));
    }

    @Test void missingCatalogueIsReportedByBothTools() {
        Path missing = Path.of("build/nonexistent-catalogue.json");
        assertTrue(new SearchBooksTool(missing).call("||").startsWith("ERROR:"));
        assertTrue(new ListBooksTool(missing).call("").startsWith("ERROR:"));
    }

    @Test void httpJsonAdapterPreservesEscapesAndReadsUsage() {
        String original = "quotes: \"; slash: \\; newline\n; unicode: \u03bb; tab\t";
        String json = "{\"response\":" + OllamaLlm.quote(original) + ",\"eval_count\":17}";
        assertEquals(original, OllamaLlm.stringField(json, "response"));
        assertEquals(17, OllamaLlm.numberField(json, "eval_count"));
        assertEquals("A", OllamaLlm.stringField("{\"response\":\"\\u0041\"}", "response"));
        assertThrows(IllegalStateException.class, () -> OllamaLlm.numberField("{}", "eval_count"));
    }
}
