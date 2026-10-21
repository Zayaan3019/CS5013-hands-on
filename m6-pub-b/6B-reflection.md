# Session 6B: Agentic mini-pipeline

**Mohamed Zayaan S | CS5013 - Programming with AI | 21 October 2026**

## Implementation and setup

I completed the two catalogue tools, action parsing, stopping conditions and
budgets. `list_books` reads all of `books.json`; `search_books` filters by
`author|year_from|year_to`, with inclusive year bounds. Empty fields remove a
constraint. Author matching ignores case and allows omitted middle initials.
Invalid years return an error instead of silently widening the search.

The prompt asks for a Thought followed by one Action and Action Input, or a
Final Answer. The driver supplies the observation after executing the tool.
It discards observations invented by the model and records explicit stop
reasons. I moved the step-boundary check after tool execution so a budget of
three permits three turns and up to three tool calls. A final answer needs
its own turn.

The live runs used local Ollama 0.40.6 and `llama3.1:8b` (digest prefix
`46e0c10c039e`), with temperature 0, seed 42, context 8192 and an output limit
of 256 tokens per response. The Java HTTP adapter records Ollama's actual
input and output token counts. The four description runs used the same
question, catalogue, model settings and five-step budget. Only the two tool
descriptions changed; names, argument instructions and the remaining prompt
stayed the same. Full prompts, arguments and results are saved in
[`traces/`](traces/README.md).

## Question A and tool preference

The question was: **"How many books did Ursula Le Guin write in the 1970s?"**

In the sharp-description run, the agent called
`search_books(Ursula Le Guin|1970|1979)`. The tool returned exactly one record:
*The Dispossessed* (1974). It excluded the catalogue's 1968 and 1969 Le Guin
books. The correct answer for this catalogue is therefore **1**; this is not
a count of her complete bibliography.

The agent repeated that identical search five times and hit `STEP_BUDGET`
without producing a final answer. Choosing the right tool did not make the
whole run successful. See [`01-sharp.txt`](traces/01-sharp.txt).

`search_books` was the first choice in all four description runs. My reading
is that the author/year argument format and the tool name already gave the
model a strong clue. The sharp description also explicitly connected a
filterable question with the cheaper tool. Across these four runs, 16 actual
executions used search and three used list. An invented `count` request was
rejected and is excluded from those execution totals.

## Three description replacements

In the table, S means `search_books`, L means `list_books`, and X means a
rejected request for an unavailable tool. Each row links to its full trace.

| Description pair: list / search | Observed sequence | Iterations | Total tokens |
| --- | --- | ---: | ---: |
| [Sharp: whole catalogue, expensive / matching filter, cheap](traces/01-sharp.txt) | S, S, S, S, S | 5 | 3,174 |
| ["List books." / "Search for books."](traces/02-vague-1.txt) | S, S, L, S, S | 5 | 5,318 |
| ["Get book information." / "Find book information."](traces/03-vague-2.txt) | S, S, L, S, L | 5 | 5,139 |
| ["Browse the book collection." / "Explore the book collection."](traces/04-vague-3.txt) | S, S, S, S, X | 5 | 2,825 |

All four ended at the step budget without a final answer. The first vague
pair introduced a whole-catalogue call and used about 68% more tokens than
the sharp run. Including that large observation in subsequent prompts made
the remaining turns more expensive.

The second pair also exposed an argument-format problem: the model sent
`"Ursula Le Guin"|1970|1979`, quoting only the author. The parser treated those
quotes literally, so the searches returned `[]`. The third pair made similar
errors, later dropped the year limits, and finally requested `count(6)`.
There is no registered `count` tool, so no such function executed. Its lower
token total was accompanied by failed searches and an incomplete answer.

These results show sensitivity to wording, but one run per variant cannot
establish a reliable causal effect or success rate. Tool names and argument
documentation remained informative even in the vague versions. The sharp
prompt also failed to prevent repeated searches, despite explicitly asking
the model not to repeat a successful one. I would enforce repetition limits
in code rather than depend on that instruction alone.

The earlier CLI runs from the conversation took two and three iterations.
They are preserved as `00-earlier-*.txt`. They used the earlier driver and
different invocation settings, so I kept them separate from this comparison.
Their incomplete `list_books` requests were never executed.

## Step and token budgets

With the sharp descriptions restored, I asked for a comparison of Le Guin's
1960s/1970s books and Butler's 1970s/1990s books, explicitly requesting four
separate searches. This makes the four-call plan part of the test; the small
catalogue could otherwise be fetched with one `list_books` call.

With `stepBudget = 3`, the executed search arguments were:

1. `"Ursula Le Guin|1960|1969"` - returned two records.
2. `"Octavia Butler|1970|1979"` - returned one record.
3. `"Ursula Le Guin|1970|1979"` - returned one record.

The model changed the requested order, but the driver still stopped cleanly
after exactly three turns and three calls: `STEP_BUDGET`, 1,838 tokens. There
was no fourth request or completed comparison. The independent JSON check
gives the four reference counts as 2, 1, 1, 1, making the totals 3 for Le Guin
and 2 for Butler. Those are verification results, not an answer generated by
the budget-limited agent. See [`05-step-budget.txt`](traces/05-step-budget.txt)
and [`catalogue-check.json`](traces/catalogue-check.json).

I reran the same query with three steps and a **600-token budget**. Turn one
used 402 input plus 67 output tokens, totalling 469. Turn two added 586 input
plus 41 output tokens, bringing the total to **1,096**. The driver returned
`TOKEN_BUDGET` before executing the second proposed search. Only one tool
executed, and no third model request occurred. See
[`06-token-budget.txt`](traces/06-token-budget.txt).

This is a post-response stopping budget. It overshot the threshold by 496
tokens because usage becomes available after generation. It prevents further
work, but is not a strict token ceiling. Repeated history is charged each
time it is sent; tool output enters the count through later input prompts.
For a hard ceiling, I would count the next input with the model's tokenizer,
reserve room for output and limit generation to the remaining allowance.

## What I would add for production

I would use structured tool schemas and a proper JSON parser, validate
arguments before dispatch, cap result sizes and detect repeated calls. The
quoted-author failure is a useful example of why a hand-parsed string is a
fragile interface. Timeouts, cancellation and bounded retries would need to
cover tools as well as model requests. Usage limits should be enforced before
requests, with per-user accounting and logs that distinguish failed attempts,
executed tools and successful answers.

I would also treat retrieved text as untrusted data, restrict each tool's
permissions, and test answer correctness separately from tool selection.
The model's Thought text is only narration: in one run it claimed the results
contained no 1970s books even though *The Dispossessed* was present. Actual
dispatches and observations are the useful evidence.

All **24 offline tests passed**, including the supplied reference tests and
added checks for filtering, fabricated observations, malformed actions,
unknown tools, the three-step boundary, token equality and overshoot. The
scripted tests are labelled separately from live evidence in
[`07-tests.txt`](traces/07-tests.txt). The default `prompts/react.txt` is the
sharp version; each vague variant has its own file.
