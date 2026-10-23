package com.company.leave.common;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkingDayCalculatorTest {

    @Test
    void countsShortRangeEndingAtMaximumDateWithoutOverflow() {
        assertThat(WorkingDayCalculator.countWorkingDays(LocalDate.MAX.minusDays(6), LocalDate.MAX)).isEqualTo(5);
    }

    @Test
    void rejectsRangesWhoseWorkingDayCountCannotFitInRequestField() {
        assertThatThrownBy(() -> WorkingDayCalculator.countWorkingDays(LocalDate.MIN, LocalDate.MAX))
                .isInstanceOf(ArithmeticException.class);
    }
}
