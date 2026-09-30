import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/**
 * Three seed tests. Coverage is intentionally light:
 *  - computeIncomeTax: only the middle slab branch is hit
 *  - computeVAT: only the 18% rate is hit
 *  - applyExemption: only the "under cap" branch is hit
 *  - roundToPaise: not exercised
 *  - isEligibleForReturn: not exercised at all
 */
public class TaxCalculatorTest {

    @Test
    public void incomeTax_middleSlab() {
        TaxCalculator c = new TaxCalculator();
        BigDecimal tax = c.computeIncomeTax(new BigDecimal("700000"));
        // 12500 (5% on 250k-500k) + 40000 (20% on 500k-700k) = 52500
        assertEquals(0, tax.compareTo(new BigDecimal("52500.00")));
    }

    @Test
    public void vat_eighteenPercent() {
        TaxCalculator c = new TaxCalculator();
        BigDecimal gst = c.computeVAT(new BigDecimal("1000"), 18);
        assertEquals(0, gst.compareTo(new BigDecimal("180.00")));
    }

    @Test
    public void exemption_underCap() {
        TaxCalculator c = new TaxCalculator();
        BigDecimal net = c.applyExemption(
                new BigDecimal("600000"), new BigDecimal("120000"));
        assertEquals(0, net.compareTo(new BigDecimal("480000")));
    }

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
}
