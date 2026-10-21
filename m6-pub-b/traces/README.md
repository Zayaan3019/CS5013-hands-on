# Session 6B experiment traces

The numbered live runs use the same completed driver and local `llama3.1:8b` model.
Each trace contains the query, budgets, exact prompt, prompt/catalogue hashes,
per-request token usage, actual tool executions and observations, stop reason,
and raw model responses. Model responses are untrusted text; they are not proof
that a tool ran. Use the `Tool execution` summary and driver observations.
Unavailable tools appear as `Rejected tool request`, not as executions.

| File | Purpose |
| --- | --- |
| `01-sharp.txt` | Task 3: original question, informative descriptions |
| `02-vague-1.txt` | Task 4: "List books." / "Search for books." |
| `03-vague-2.txt` | Task 4: "Get book information." / "Find book information." |
| `04-vague-3.txt` | Task 4: "Browse the book collection." / "Explore the book collection." |
| `05-step-budget.txt` | Task 5: restored descriptions, harder query, three steps |
| `06-token-budget.txt` | Task 6: same harder query, three steps, 600 tokens |
| `07-tests.txt` | Offline JUnit tests; includes scripted responses, not live model evidence |
| `environment.json` | Local model digest and runtime/settings |
| `catalogue-check.json` | Independent JSON check of the reference book counts |

The two `00-earlier-*.txt` files preserve the earlier conversation's runs.
They used the CLI adapter and earlier driver, with no recorded fixed seed or
token accounting. They are historical evidence, not part of the controlled
four-way description comparison. The earlier attempted `list_books` calls had
no `Action Input:` line and therefore were never executed.

In the completed driver, a step is one model request and at most one tool
execution. A successful one-search answer normally needs two steps: search,
then final answer. `STEP_BUDGET`, `TOKEN_BUDGET`, `INVALID_ACTION`, and
`MODEL_ERROR` are incomplete outcomes, not successful answers. At the token
boundary, the model request is metered but its proposed tool is not executed.
Tool output is charged when included in a later model input; it is not billed
as a separate generation. Live usage comes from Ollama; offline fallback usage
is explicitly marked as a character-based estimate.

The source prompt `prompts/react.txt` remains the sharp version. Variant
prompts are separate files so rerunning them cannot leave the default vague.
One run per variant is an observation, not a statistical estimate of success.
