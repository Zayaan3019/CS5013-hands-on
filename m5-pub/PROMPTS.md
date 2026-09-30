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
