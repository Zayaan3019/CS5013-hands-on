# M5 -- AI-assisted testing and documentation

Starter for the tax-calculator + order-API exercises.

Run:

    make deps && make test
    make coverage       # JaCoCo HTML at coverage/index.html
    make mutation       # PIT HTML at build/reports/pitest/index.html

Session 5A: use an LLM to raise `TaxCalculator` line/branch coverage
above the ~45%/~30% baseline the three seed tests give you.
Session 5B: add JavaDoc, a README, and an OpenAPI spec for
`OrderApi` (fake Spring annotations already in place so it compiles
without Spring on the classpath).

The `mutation` target uses PIT 1.17.4 with the JUnit 5 plugin; both
are auto-downloaded into `libs/` on first use.  Baseline mutation
score with the seed test is around 36% (15 of 42 mutants killed) --
Part D of the handout asks students to kill at least one surviving
mutant.
