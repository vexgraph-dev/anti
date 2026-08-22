#ifndef TIME_DATETIME_H
#define TIME_DATETIME_H

#include <stdint.h>

// time/datetime.h — UTC date/time breakdown (Legacy: time/DateTime.java).
//
// Breaks epoch milliseconds into UTC calendar fields using Howard Hinnant's
// standard integer algorithm — no division by floating point, no libc
// localtime, allocation-free. dayOfWeek is ISO-8601: 1=Monday .. 7=Sunday.

typedef struct DateTime {
    int64_t epochMillis;
    int32_t year;
    int32_t month;      // 1-12
    int32_t day;        // 1-31
    int32_t hour;       // 0-23
    int32_t minute;     // 0-59
    int32_t second;     // 0-59
    int32_t millisecond;// 0-999
    int32_t dayOfWeek;  // 1=Monday .. 7=Sunday
} DateTime;

// Set from the current system wall clock.
void DateTime_set(DateTime *dt);

// Recompute every calendar field from epochMillis (Hinnant's algorithm).
void setEpochMillis(DateTime *dt, int64_t epochMillis);

int64_t DateTime_epochMillis(const DateTime *dt);
int32_t DateTime_year(const DateTime *dt);
int32_t DateTime_month(const DateTime *dt);
int32_t DateTime_day(const DateTime *dt);
int32_t DateTime_hour(const DateTime *dt);
int32_t DateTime_minute(const DateTime *dt);
int32_t DateTime_second(const DateTime *dt);
int32_t DateTime_millisecond(const DateTime *dt);
int32_t DateTime_dayOfWeek(const DateTime *dt);

#endif
