# M4 -- Debugging and refactoring with AI

Starter for Sessions 4A (debug) and 4B (refactor).

Run:

    make deps && make test

`PriceEngine.quote` used to carry one planted bug (an off-by-one in the
loyalty-tier boundary check) and a cyclomatic complexity in the low
20s. Both are addressed on this branch now -- see the session notes
below for what was actually done and why.

---

## Session 4A -- AI-assisted debugging

### Part A: explaining the stack trace

`make test` failed on `quotesDiscountForLoyalCustomer`:
`AssertionFailedError: 5-year loyalty customer should get 10% off
(tier 3) ==> expected: <90.0> but was: <95.0>`, plus a JUnit stack
trace under it.

I pasted the trace into the chat panel with the prompt exactly as
given ("Explain this Java stack trace in plain English. What is the
most likely root cause? Do NOT propose a patch yet.") and worked
through it with the source open alongside it. Most of the trace turned
out to be noise: of the nine lines, seven are JUnit's own internal
frames (`AssertionFailureBuilder`, `AssertEquals`, the reflective
`Method.invoke` / `ArrayList.forEach` pair the test runner uses to
call the test method) and carry no information about my code at all.
Only two lines mattered: the assertion message itself (which already
told me 5% was applied instead of 10%) and the one frame naming
`PriceEngineTest.java:92`, which is where the assertion actually sits.

Tracing from there into `PriceEngine.quote`, the loyalty-tier block
had `if (years > 5)` guarding the 10%-off branch, while the comment
two lines above it says "tier 3 (5+ years): 10% off" -- a boundary
mismatch between the stated spec and the `>` used to implement it. A
customer at exactly 5 years falls through into the `years >= 3`
branch and gets 5% instead. There wasn't a real second hypothesis
worth ranking against this one: the test name, the assertion numbers,
the tier comment and the condition all pointed at the same line.

### Part B: patch and verify

Asked for the smallest patch given that root cause. The diff came back
as a single character (`>` to `>=`) with no other lines touched, which
I could verify by inspection wasn't going to affect any of the other
nine tests -- they all use `loyaltyYears` of 0, 1 or 3, none of which
cross the boundary that changed. Applied it, reran `make test`: 10/10
passing. Committed with `git commit -e` so the AI-drafted subject and
body (describing the mechanical change) could be reviewed and a
hand-written "why" added before the commit was finalised.

### Part C: reflection

The explain step was the more useful of the two -- it did the actual
diagnostic work, connecting an opaque `expected: <90.0> but was:
<95.0>` to one specific mismatched line, and once that diagnosis was
right the patch step was close to mechanical (a one-character diff).
If I had to rank them, "explain" earned its keep and "patch" mostly
confirmed what was already obvious by that point. On the stack-trace
question: pasting the *full* trace didn't cost anything here since
it's short, but stepping through the lines afterwards showed that only
the assertion line and one test-code frame were doing any work -- the
rest was JUnit's own plumbing. For a longer trace I'd paste selectively
(the exception line plus the first frame or two that are actually in
my own code) rather than the whole thing by habit, and I'd still paste
the source file alongside it -- without `PriceEngine.java` open, no
amount of trace would have pointed at the exact `>` vs `>=`, because
the trace itself never mentions production code at all.

---

## Session 4B -- reducing cyclomatic complexity

### Measuring the starting complexity

Before refactoring anything I wanted a real number rather than trust
the "roughly 14" this README used to quote. I counted `quote`'s
decision points by hand (every `if`, `else if`, `&&`, `||`, and the
one `for` loop, McCabe's rule of decisions-plus-one) and got **21**.
I also downloaded PMD 7.6.0 directly (it isn't wired into the Makefile
for this module -- the course intentionally leaves that for Modules
5/8 -- so this was a one-off `pmd check` run, not a build target) and
its `CyclomaticComplexity` rule reported **23** for the same method.
The two numbers don't match exactly -- my best guess is that PMD
counts something around the two early guard clauses (the `null`/empty
checks that `throw` immediately) that a plain decision-count doesn't
-- but they agree on the only thing that actually matters here: this
method is well past the ">10 -> refactor" line by either count, not
just the "roughly 14" the header used to claim.

### Part A: ranking refactor targets

`PriceEngine.java` only defines one method (`quote`), so pasting the
whole file and asking for a ranking by refactor priority came back
with a single entry rather than a real list:

1. **`quote(Order, Customer)`** -- current complexity ~21-23 by hand
   and PMD respectively. First move: **extract-method** on the
   loyalty-tier block. It's the most self-contained chunk in the
   method (its own local variables, `years` and `loyaltyRate`, used
   nowhere else), unlike the tax block which reads `running` that
   every earlier step has already mutated. Expected effect: pulling
   out the tier lookup and its apply-if-positive check removes 4
   decision points from `quote` and creates a small (~5) helper --
   this redistributes complexity into two testable-in-isolation
   pieces rather than reducing the total, but no single method stays
   oversized.

### Part B: the extract-method refactor

Extracted the loyalty-tier block into `private static Money
applyLoyaltyDiscount(Money running, Money subtotal, int years)`,
exactly as asked, without touching `quote`'s public signature. One
design choice worth recording: the natural AI-suggested shape was to
pass the whole `Customer` in (`applyLoyaltyDiscount(running, subtotal,
customer)`), which reads slightly cleaner at the call site. I went
with the raw `int years` instead -- the helper doesn't use the
customer's name, id or region, so passing the whole record would
couple it to fields it never touches, and it would mean any unit test
for the helper has to construct a full `Customer` just to exercise a
tier boundary instead of calling
`applyLoyaltyDiscount(running, subtotal, 5)` directly.

While reading the diff I checked the three things the handout calls
out specifically: no parameter or return type changed (still
`Money`/`Money`/`int` in, `Money` out); no early return was silently
lifted out of the conditional (the block never had one -- it just
reassigns `running` and falls through); and on `final` -- the rest of
the file doesn't mark any parameter `final`, so I matched that
existing style rather than introduce it only on the new method.

Ran `make test`: still 10/10. Reran PMD: `quote` dropped from 23 to
**19**; `applyLoyaltyDiscount` didn't even show up in the report,
meaning it's under PMD's reporting threshold -- by hand it comes out
to 5, comfortably in the "easy" band. `quote` is still above 10, which
is expected: this part only asked for one extraction, and the
promo-code block and the tax block are equally good candidates for a
follow-up pass. Committed as one atomic commit containing only the
refactor.

### (Optional) Part C: SpotBugs

Skipped. This module's own scope note (further up this file,
originally) already says PMD/SpotBugs aren't wired in until Modules
5/8, and the section itself is marked optional. I used PMD ad hoc
above purely to get a real number for the reflection questions below,
not as a permanent build target, and didn't chase a SpotBugs setup on
top of it.

### Part D: reflection

**Complexity drop:** `quote` went from CC 23 to 19 by PMD (21 to 17 by
my own hand count) -- a drop of exactly 4 by both measures, which
matches the 4 decision points (the 3-way tier `if`/`else
if`/`else if` chain plus the trailing `if (loyaltyRate > 0.0)`) moved
into the new helper. It's real progress, not a complete fix -- `quote`
is still well above the ">10" line, since this session's Part B
scoped the work to a single extraction.

**SpotBugs:** N/A here since that part was skipped (see above) --
there was no finding to fix, so I can't compare "understood the rule"
against "trusted the AI's explanation" for a real case this time.

**A refactor I rejected:** covered above under Part B -- passing the
whole `Customer` into `applyLoyaltyDiscount` instead of just
`loyaltyYears`. It would have made the call site one argument shorter,
but at the cost of coupling a small, single-purpose helper to a type
it mostly doesn't need, and making it noticeably more annoying to unit
test on its own. I kept the narrower `int years` parameter instead.

---

## Commits on this branch

1. `Add M4 starter: debugging and refactoring with AI` -- the starter
   bundle (Session 4A/4B initial state).
2. `Fix loyalty-tier boundary to include exactly 5 years` -- Session
   4A Part B, the one-character bug fix.
3. `Extract loyalty-tier discount into applyLoyaltyDiscount helper` --
   Session 4B Part B, the extract-method refactor.
