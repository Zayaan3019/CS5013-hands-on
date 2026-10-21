# Session 6B: Agentic mini-pipeline

Completed CS5013 Module 6B book-catalogue lab. Start with
[6B-reflection.md](6B-reflection.md); the experiment evidence is under
[traces/](traces/README.md). All book counts refer to `books.json`, not a
complete author bibliography.

## Build and test

Requires Java 17+; the recorded runs used Java 25 with `--release 17`.
The offline tests need JUnit 1.10.0 (downloaded into ignored `libs/`). They
exercise the real driver and tools with scripted model responses. They do
not require Ollama or internet access once JUnit is present.

On Windows PowerShell, from this directory:

```powershell
.\run.ps1 test
```

On a POSIX shell with make, curl and a JDK:

```sh
make deps
make test
```

## Live experiments

Ollama must be running on `http://localhost:11434` with `llama3.1:8b`
already installed. The Java adapter uses the local HTTP API directly, so
Maven and langchain4j are not needed. Settings are temperature 0, seed 42,
context 8192 and output limit 256 tokens per response. These settings and
the installed model digest are recorded in `traces/environment.json`.
Fixed settings improve repeatability, but do not guarantee identical text
across runtime or hardware changes.

```powershell
.\run.ps1 experiments
# One experiment only:
.\run.ps1 run -RunArgs @('01-sharp', 'prompts/react.txt', '5')
```

```sh
make experiments
# One experiment only:
make run ARGS='01-sharp prompts/react.txt 5'
```

These commands regenerate the numbered live traces. To preserve submitted
results, use a different experiment name for an individual run. `Main`
accepts: experiment-name, prompt-file, step-budget, token-budget,
`hard` (or `simple`), and model-name, in that order. Omitted values default
to the sharp prompt, five steps, no token cap, question A and llama3.1:8b.

## Tools and loop

- `list_books` reads the complete catalogue; its argument is ignored.
- `search_books` reads and filters it using `author|year_from|year_to`.
  Bounds are inclusive; empty parts are unconstrained. Matching ignores
  case and tolerates omitted middle initials. Quotes around the entire
  argument are accepted; quotes around only one field are treated literally.
  Invalid years/ranges return an error instead of broadening the search.
- `AgentDriver` substitutes the query and running transcript into the
  prompt, parses one action/input pair, invokes the registered tool, and
  appends its real observation. A model-written observation is removed,
  along with anything after it. Raw model output is saved separately.
- A step is a model request and at most one tool call. Three steps permit
  up to three calls, then the driver returns `STEP_BUDGET` without a fourth
  request. A final answer also takes a model turn.
- The token budget sums every response's `prompt_eval_count` and
  `eval_count`, including repeated history. It is checked after generation
  and before tool execution. It can overshoot by the last response; it is
  not a strict provider-side token cap. Offline clients explicitly label
  their fallback character estimate.
- `FINAL_ANSWER` is distinct from `STEP_BUDGET`, `TOKEN_BUDGET`,
  `INVALID_ACTION`, and `MODEL_ERROR`. A stopped partial transcript is
  never converted into a fabricated final answer.

The regular-expression catalogue reader deliberately targets this lab's
flat JSON records. A production version should use a JSON parser and
structured tool schemas. Descriptions are the only difference between
`react.txt` and the three `vague-*.txt` prompts. The sharp prompt remains
the default after the experiments.
