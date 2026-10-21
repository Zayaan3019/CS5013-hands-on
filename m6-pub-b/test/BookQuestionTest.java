import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class BookQuestionTest {
    @Test
    public void liveArgument_quotesAndOmittedInitialsMatchOnly1974() {
        String result = new SearchBooksTool().call("\"Ursula Le Guin|1970|1979\"");
        assertTrue(result.contains("The Dispossessed"));
        assertFalse(result.contains("The Left Hand of Darkness"));
        assertFalse(result.contains("A Wizard of Earthsea"));
        assertEquals(1, result.chars().filter(c -> c == '{').count());
    }

    @Test
    public void invalidYearDoesNotSilentlyBroadenSearch() {
        assertTrue(new SearchBooksTool().call("Le Guin|1970|oops").startsWith("ERROR:"));
        assertTrue(new SearchBooksTool().call("Le Guin|1979|1970").startsWith("ERROR:"));
    }

    @Test
    public void modelCannotSupplyItsOwnToolObservation() throws IOException {
        AtomicInteger turns = new AtomicInteger();
        LlmClient scripted = prompt -> {
            if (turns.getAndIncrement() == 0) {
                return "Action: search_books\nAction Input: \"Ursula Le Guin|1970|1979\"\n"
                        + "Observation: fabricated two-book result\nFinal Answer: 2";
            }
            assertFalse(prompt.contains("fabricated two-book result"));
            assertFalse(prompt.contains("Final Answer: 2"));
            assertTrue(prompt.contains("Observation: [{\"id\": \"b002\""));
            return "Final Answer: 1";
        };
        String transcript = new AgentDriver(scripted, Path.of("prompts/react.txt"), 5).run("Count Le Guin books in the 1970s");
        assertTrue(transcript.contains("Final Answer: 1"));
        assertEquals(2, turns.get());
    }

    @Test
    public void leGuin1970s_usesSearchAndReturnsOneCatalogueBook() throws IOException {
        String question = "How many books did Ursula Le Guin write in the 1970s?";
        AtomicInteger turns = new AtomicInteger();
        // Script the model replies; the driver and catalogue tool run normally.
        LlmClient scripted = prompt -> {
            assertTrue(prompt.contains(question));
            if (turns.getAndIncrement() == 0) {
                return "Action: search_books\nAction Input: Ursula K. Le Guin|1970|1979";
            }
            assertTrue(prompt.contains("Observation: [{\"id\": \"b002\""));
            assertTrue(prompt.contains("The Dispossessed"));
            assertFalse(prompt.contains("The Left Hand of Darkness"));
            assertFalse(prompt.contains("A Wizard of Earthsea"));
            return "Final Answer: The catalogue contains 1 book by Ursula Le Guin "
                    + "from the 1970s: The Dispossessed (1974).";
        };
        AgentDriver agent = new AgentDriver(scripted, Path.of("prompts/react.txt"), 5);
        String transcript = agent.run(question);
        System.out.println("Question: " + question + "\n" + transcript);
        assertTrue(transcript.contains("Final Answer: The catalogue contains 1 book"));
        assertFalse(transcript.contains("no action parsed"));
        assertFalse(transcript.contains("ERROR:"));
        assertEquals(2, turns.get(), "The driver should stop at the final answer");
    }
}
