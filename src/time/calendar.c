// time/calendar.c — stateless date arithmetic (Legacy: time/Calendar.java port).
//
// addMonths/addYears shift the month/year fields and clip the day into the
// target month (Jan 31 + 1 month => Feb 28), then reconstitute the epoch via
// the inverse of Hinnant's civil-from-days algorithm — same as legacy.

// should support calendar at 1500s i think? may regurgitate...

#include "time/calendar.h"

bool Calendar_isLeapYear(int32_t year) {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
}

int32_t Calendar_daysInMonth(int32_t year, int32_t month) {
    if (month < 1 || month > 12) return 0;
    if (month == 2) return Calendar_isLeapYear(year) ? 29 : 28;
    if (month == 4 || month == 6 || month == 9 || month == 11) return 30;
    return 31;
}

int32_t Calendar_dayOfWeek(int32_t year, int32_t month, int32_t day) {
    if (month < 3) {
        month += 12;
        year--;
    }
    int32_t k = year % 100;
    int32_t j = year / 100;
    // Zeller's congruence: h = 0 Saturday, 1 Sunday, .. 6 Friday.
    int32_t h = (day + 13 * (month + 1) / 5 + k + k / 4 + j / 4 + 5 * j) % 7;

    // Convert to ISO-8601 (1=Monday .. 7=Sunday).
    switch (h) {
        case 0: return 6; // Saturday
        case 1: return 7; // Sunday
        case 2: return 1; // Monday
        case 3: return 2; // Tuesday
        case 4: return 3; // Wednesday
        case 5: return 4; // Thursday
        default: return 5; // Friday
    }
}

void Calendar_addDays(DateTime *dt, int32_t days) {
    setEpochMillis(dt, DateTime_epochMillis(dt) + (int64_t)days * 86400000LL);
}

// Inverse of Hinnant's algorithm: calendar fields => UTC epoch millis.
static int64_t toEpochMillis(int32_t year, int32_t month, int32_t day,
                             int32_t hour, int32_t minute, int32_t second,
                             int32_t millisecond) {
    int32_t y = year - (month <= 2 ? 1 : 0);
    int32_t m = month + (month <= 2 ? 9 : -3);
    int32_t era = (y >= 0 ? y : y - 399) / 400;
    int32_t yoe = y - era * 400;
    int32_t doy = (153 * m + 2) / 5 + day - 1;
    int32_t doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    int64_t totalDays = (int64_t)era * 146097 + doe - 719468;

    int64_t totalSecs = totalDays * 86400LL + hour * 3600LL + minute * 60LL + second;
    return totalSecs * 1000 + millisecond;
}

void Calendar_addMonths(DateTime *dt, int32_t months) {
    int32_t year = DateTime_year(dt);
    int32_t month = DateTime_month(dt);
    int32_t day = DateTime_day(dt);
    int32_t hour = DateTime_hour(dt);
    int32_t minute = DateTime_minute(dt);
    int32_t second = DateTime_second(dt);
    int32_t millisecond = DateTime_millisecond(dt);

    int32_t totalMonths = (year * 12) + (month - 1) + months;
    int32_t newYear = totalMonths / 12;
    int32_t newMonth = (totalMonths % 12) + 1;

    // Clip day into the target month (Jan 31 + 1 month => Feb 28).
    int32_t maxDays = Calendar_daysInMonth(newYear, newMonth);
    int32_t newDay = day < maxDays ? day : maxDays;

    setEpochMillis(dt, toEpochMillis(newYear, newMonth, newDay,
                                              hour, minute, second, millisecond));
}

void Calendar_addYears(DateTime *dt, int32_t years) {
    int32_t year = DateTime_year(dt);
    int32_t month = DateTime_month(dt);
    int32_t day = DateTime_day(dt);
    int32_t hour = DateTime_hour(dt);
    int32_t minute = DateTime_minute(dt);
    int32_t second = DateTime_second(dt);
    int32_t millisecond = DateTime_millisecond(dt);

    int32_t newYear = year + years;
    int32_t maxDays = Calendar_daysInMonth(newYear, month);
    int32_t newDay = day < maxDays ? day : maxDays;

    setEpochMillis(dt, toEpochMillis(newYear, month, newDay,
                                              hour, minute, second, millisecond));
}
