# Session 5A: AI-generated tests

## Part A - Baseline coverage

I ran `make coverage` with the three starter tests. The JaCoCo report showed:

- Line coverage: 20/41 lines (48.8%).
- Branch coverage: 15/44 branches (34.1%).
- Below 50% branch coverage: `computeVAT` (6/14, 42.9%), `roundToPaise` (0/2, 0%), and `isEligibleForReturn` (0/10, 0%).

`roundToPaise` and `isEligibleForReturn` tied for the lowest branch coverage. I chose `isEligibleForReturn` because it has more uncovered branches.

## Part B - Test generation

I supplied `TaxCalculator.isEligibleForReturn(BigDecimal grossIncome, int ageYears)` and used this prompt:

```text
Generate JUnit 5 tests for the following method.
Rules:
- Use AAA style with explicit comments.
- One assertion per test.
- Test names of the form
  <method>_<condition>_<expected>.
- Cover: (a) null input, (b) empty input if applicable,
  (c) boundary values, (d) one representative happy path.
- Do NOT use mocks.
- Do NOT include implementation-copying assertions; each
  assertion must state a contract, not restate the code.
```

The generated tests I kept in `TaxCalculatorTest.java` were:

```java
@Test
public void isEligibleForReturn_nullGrossIncome_false() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = null;
    int ageYears = 35;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertFalse(eligible);
}

@Test
public void isEligibleForReturn_negativeAge_false() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("300000");
    int ageYears = -1;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertFalse(eligible);
}

@Test
public void isEligibleForReturn_underSixtyAtThreshold_false() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("250000");
    int ageYears = 35;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertFalse(eligible);
}

@Test
public void isEligibleForReturn_underSixtyAboveThreshold_true() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("250001");
    int ageYears = 59;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertTrue(eligible);
}

@Test
public void isEligibleForReturn_sixtyAtThreshold_false() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("300000");
    int ageYears = 60;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertFalse(eligible);
}

@Test
public void isEligibleForReturn_sixtyAboveThreshold_true() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("300001");
    int ageYears = 60;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertTrue(eligible);
}

@Test
public void isEligibleForReturn_eightyAtThreshold_false() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("500000");
    int ageYears = 80;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertFalse(eligible);
}

@Test
public void isEligibleForReturn_overEightyAboveThreshold_true() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("500001");
    int ageYears = 81;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertTrue(eligible);
}
```

## Part C - Assertion review

I checked each generated assertion against the method's contract. The assertions check whether missing income or an invalid age is rejected, and whether income at or above the applicable filing threshold produces the expected eligibility result. None recomputes the method's implementation as its expected value, so I kept all eight. An empty value does not apply to a `BigDecimal` parameter; `null` is the absent-input case.

## Part D - Mutation testing

Before the targeted test, PIT reported 10 of 11 mutants killed for `isEligibleForReturn` (90.9%). The surviving mutant was at line 70:

- Changed the age check from `ageYears < 0` to `ageYears <= 0` (`CONDITIONALS_BOUNDARY`).

I used this follow-up prompt:

```text
The mutant at line 70 ('ageYears < 0' -> 'ageYears <= 0') survived
the test suite. Write ONE new JUnit test that would kill this
mutant without changing any other test. Explain in one
sentence why this test kills the mutant.
```

The added test was:

```java
@Test
public void isEligibleForReturn_zeroAgeAboveThreshold_true() {
    // Arrange
    TaxCalculator calculator = new TaxCalculator();
    BigDecimal grossIncome = new BigDecimal("250001");
    int ageYears = 0;

    // Act
    boolean eligible = calculator.isEligibleForReturn(grossIncome, ageYears);

    // Assert
    assertTrue(eligible);
}
```

This test kills the mutant because age zero is nonnegative, so income above the under-60 threshold must be eligible; the mutated check incorrectly rejects age zero.

After adding it, PIT reported 11/11 mutants killed for `isEligibleForReturn` (100%). The full-class result changed from 25/42 (59.5%) to 26/42 (61.9%).

## Part E - Reflection

- Line coverage after the changes: 28/41 (68.3%), up from 20/41 (48.8%).
- Branch coverage after the changes: 25/44 (56.8%), up from 15/44 (34.1%).
- Mutation score for `isEligibleForReturn`: 10/11 (90.9%) before and 11/11 (100%) after the targeted test.
- Mutation score for the full class: 25/42 (59.5%) before and 26/42 (61.9%) after the targeted test.

Mutation testing was more useful here than coverage alone: all branches were covered, but PIT still exposed an age-zero boundary case that needed its own test.

# Session 5B: Auto-documentation

The PDF asks for a Spring `OrderApi`. The M5 branch had `OrderService` and `OrderDto`, but no `OrderApi.java` or Spring annotation stubs. I added a small controller around the existing service operations, along with local stubs, so the source compiles without adding Spring as a dependency.

## Part A - JavaDoc for one endpoint

I used `POST /orders`, implemented by `OrderApi.createOrder`.

Prompt used:

```text
Generate JavaDoc for the following method. Rules:
- One-line summary in imperative mood.
- Describe the contract, not the implementation.
- @param for each parameter with type and constraints.
- @return with what is returned.
- @throws for each declared or unchecked exception the
  caller should be aware of.
- If any behaviour is not evident from the code, write
  "TODO" and skip.
```

I added the JavaDoc to `src/OrderApi.java`, then checked it against the method body with this prompt:

```text
Given the JavaDoc above and the method body below,
identify any statement in the JavaDoc that is inconsistent
with the code. Do not fix; just list.
```

The first check found no inconsistencies. I then changed `createOrder` to trim whitespace from `customerId` before storing it, updated the `@param` description, and ran the check again. It found no inconsistencies in the final version. Invalid requests (a missing body, blank customer ID, or non-positive amount) receive HTTP 400 with an `ErrorResponse`; valid requests receive HTTP 201 with the created DTO.

## Part B - README draft and review

I supplied the M5 file tree and the top-level files (`Makefile`, `README.md`, `src/`, and `test/`) with this prompt:

```text
Draft a README.md for this repository with sections:
description, build, quick example, contributing, license.
Use MIT license placeholder.
Rules:
- Do NOT invent features not present in the code.
- If a section has no evidence in the code, write "TODO" and
  skip.
- The one-line description must be a factual summary of what
  the code does, not marketing copy.
```

I saved the first draft as `README.raw.md` and made the final edits in `README.md`. I checked the build commands against `Makefile`, the Java version against `javac --release 17`, the storage description against `OrderService`, and the route list against `OrderApi`. The quick example calls the existing `OrderService.create` and `OrderDto.from` methods. I found no contribution instructions in the repository, so that section says `TODO`. The draft did not contain any feature claims that needed to be removed.

## Part C - OpenAPI spec

Prompt used:

```text
Read the following Spring @RestController and generate an
OpenAPI 3.0 YAML spec covering:
- Every endpoint (path, method, summary from JavaDoc).
- Request body schemas for POST/PUT.
- Response schemas for 2xx and 4xx.
- Referenced DTO schemas in the components section.
If any endpoint's behaviour is unclear, add a TODO comment
in the spec at that location.
```

I saved the spec as `openapi.yaml`. `OrderApi` has GET collection, GET by ID, POST create, and DELETE cancel operations; it has no PUT method, so there is no PUT body schema. The spec defines `OrderCreateRequest`, `OrderDto`, and `ErrorResponse` under `components.schemas`. Every operation lists a 2xx and a 4xx response. I left TODO notes for the 406/415 response bodies because `OrderApi` does not define them.

I could not check the visual rendering in Swagger Editor because this session had no available browser. `swagger-cli validate openapi.yaml` accepted the file. I also checked the four paths and methods, the path parameter and POST body, the schema references, and the required 2xx and 4xx responses. The TODO comments mark framework-generated error bodies that `OrderApi` does not define.

## Part D - Reflection

- Both JavaDoc checks found no inconsistencies. I reran the check after changing how `customerId` is stored and updating the comment.
- I did not find any invented feature claims in the README draft.
- The OpenAPI work took the most editing because the branch did not include `OrderApi.java`. I based the controller on the service methods already in the project, then made the response codes and schemas match that controller. The framework-generated error responses still need confirmation in a Spring application.
