package com.company.leave.common;

import java.time.DayOfWeek;
import java.time.LocalDate;

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
     */
    public static int countWorkingDays(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }

        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }
}
