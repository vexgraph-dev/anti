#ifndef TIME_CALENDAR_H
#define TIME_CALENDAR_H

#include <stdbool.h>
#include <stdint.h>

#include "time/datetime.h"

// time/calendar.h — stateless date arithmetic (Legacy: time/Calendar.java).
//
// Pure utilities over DateTime: leap years, month lengths, Zeller's
// congruence for weekday queries, and add-days/months/years mutations.

bool Calendar_isLeapYear(int32_t year);

// 0 for an _out-of-range month, else 28/29/30/31.
int32_t Calendar_daysInMonth(int32_t year, int32_t month);

// ISO-8601 weekday (1=Monday .. 7=Sunday) via Zeller's congruence.
int32_t Calendar_dayOfWeek(int32_t year, int32_t month, int32_t day);

void Calendar_addDays(DateTime *dt, int32_t days);
void Calendar_addMonths(DateTime *dt, int32_t months);
void Calendar_addYears(DateTime *dt, int32_t years);

#endif
