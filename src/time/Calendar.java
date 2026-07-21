package time;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import oop.TypeRegister;

/**
 * Off-Heap Stateless Calendar calculations helper.
 * Provides pure static utilities to perform date additions and queries on DateTime structures.
 */
@Draft
@Intention("Stateless calendar operations for zero-allocation date manipulation and queries on DateTime pointers.")
public final class Calendar {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CALENDAR;

    private Calendar() {}

    public static int classId() {
        return CLASS_ID;
    }

    @Draft
    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    @Draft
    public static int getDaysInMonth(int year, int month) {
        if (month < 1 || month > 12) return 0;
        return switch (month) {
            case 2 -> isLeapYear(year) ? 29 : 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    /**
     * Calculates the day of the week (1=Monday, 7=Sunday) using Zeller's Congruence.
     */
    @Draft
    public static int getDayOfWeek(int year, int month, int day) {
        if (month < 3) {
            month += 12;
            year--;
        }
        int k = year % 100;
        int j = year / 100;
        // Zeller's congruence formula
        int h = (day + 13 * (month + 1) / 5 + k + k / 4 + j / 4 + 5 * j) % 7;
        
        // Convert to ISO-8601 (1=Monday, 7=Sunday)
        // Zeller outputs: 0=Saturday, 1=Sunday, 2=Monday, 3=Tuesday, 4=Wednesday, 5=Thursday, 6=Friday
        return switch (h) {
            case 0 -> 6; // Saturday
            case 1 -> 7; // Sunday
            case 2 -> 1; // Monday
            case 3 -> 2; // Tuesday
            case 4 -> 3; // Wednesday
            case 5 -> 4; // Thursday
            default -> 5; // Friday
        };
    }

    @Draft
    public static void addDays(long dateTimePtr, int days) {
        if (dateTimePtr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        long currentMillis = DateTime.getEpochMillis(dateTimePtr);
        long newMillis = currentMillis + ((long) days * 86_400_000L);
        DateTime.setEpochMillis(dateTimePtr, newMillis);
    }

    @Draft
    public static void addMonths(long dateTimePtr, int months) {
        if (dateTimePtr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        
        // Since month calculation is variable, we extract fields, shift month/year, and recalculate epoch.
        int year = DateTime.getYear(dateTimePtr);
        int month = DateTime.getMonth(dateTimePtr);
        int day = DateTime.getDay(dateTimePtr);
        int hour = DateTime.getHour(dateTimePtr);
        int minute = DateTime.getMinute(dateTimePtr);
        int second = DateTime.getSecond(dateTimePtr);
        int millisecond = DateTime.getMillisecond(dateTimePtr);

        // Adjust month/year
        int totalMonths = (year * 12) + (month - 1) + months;
        int newYear = totalMonths / 12;
        int newMonth = (totalMonths % 12) + 1;

        // Clip day if it exceeds the maximum days in the target month (e.g. Jan 31st + 1 month -> Feb 28th)
        int maxDays = getDaysInMonth(newYear, newMonth);
        int newDay = Math.min(day, maxDays);

        // Reconstitute epoch millis
        long newEpoch = toEpochMillis(newYear, newMonth, newDay, hour, minute, second, millisecond);
        DateTime.setEpochMillis(dateTimePtr, newEpoch);
    }

    @Draft
    public static void addYears(long dateTimePtr, int years) {
        if (dateTimePtr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        
        int year = DateTime.getYear(dateTimePtr);
        int month = DateTime.getMonth(dateTimePtr);
        int day = DateTime.getDay(dateTimePtr);
        int hour = DateTime.getHour(dateTimePtr);
        int minute = DateTime.getMinute(dateTimePtr);
        int second = DateTime.getSecond(dateTimePtr);
        int millisecond = DateTime.getMillisecond(dateTimePtr);

        int newYear = year + years;
        int maxDays = getDaysInMonth(newYear, month);
        int newDay = Math.min(day, maxDays);

        long newEpoch = toEpochMillis(newYear, month, newDay, hour, minute, second, millisecond);
        DateTime.setEpochMillis(dateTimePtr, newEpoch);
    }

    /**
     * Converts calendar fields to UTC epoch milliseconds using the inverse of Hinnant's algorithm.
     */
    private static long toEpochMillis(int year, int month, int day, int hour, int minute, int second, int millisecond) {
        int y = year - (month <= 2 ? 1 : 0);
        int m = month + (month <= 2 ? 9 : -3);
        int era = (y >= 0 ? y : y - 399) / 400;
        int yoe = y - era * 400;
        int doy = (153 * m + 2) / 5 + day - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        long totalDays = era * 146097L + doe - 719468;
        
        long totalSecs = totalDays * 86400L + hour * 3600L + minute * 60L + second;
        return totalSecs * 1000L + millisecond;
    }
}
