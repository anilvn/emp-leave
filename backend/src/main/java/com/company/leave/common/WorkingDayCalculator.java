package com.company.leave.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Stateless utility that counts Mon-Fri working days in a date range (inclusive).
 * Holidays are ignored (AD-3). Not a Spring bean.
 */
public final class WorkingDayCalculator {

    private WorkingDayCalculator() {
        // utility class
    }

    /**
     * Returns the number of Mon-Fri days between {@code start} and {@code end}, inclusive.
     * Returns 0 when the range spans only weekend days.
     *
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     * @throws ArithmeticException if the working-day count exceeds {@link Integer#MAX_VALUE}
     */
    public static int countWorkingDays(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }

        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long completeWeeks = totalDays / 7;
        long count = completeWeeks * 5;
        int remainder = (int) (totalDays % 7);
        int dayOfWeek = start.getDayOfWeek().getValue();
        for (int i = 0; i < remainder; i++) {
            if (dayOfWeek <= DayOfWeek.FRIDAY.getValue()) {
                count++;
            }
            dayOfWeek = dayOfWeek == DayOfWeek.SUNDAY.getValue() ? DayOfWeek.MONDAY.getValue() : dayOfWeek + 1;
        }
        return Math.toIntExact(count);
    }
}
