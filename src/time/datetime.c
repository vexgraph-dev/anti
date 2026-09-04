// time/datetime.c — UTC date/time breakdown (Legacy: time/DateTime.java port).
//
// setEpochMillis carries the legacy math verbatim: floor-division chains for
// the h/m/s/ms fields, then Hinnant's era-based civil-from-days conversion.
// All integer, all branches, zero allocation.

#include "time/datetime.h"

#include <time.h>
#include "annotation/overview.h"

;;OVERVIEW
/**
 * ============================================================================
 * MODULE: Datetime (time/datetime.c)
 * ============================================================================
 * UTC date/time breakdown (Legacy: time/DateTime.java).
 *
 * FUNCTION REGISTRY:
 * ----------------------------------------------------------------------------
 * Core Functions:
 *   - DateTime_epochMillis(dt)
 *   - DateTime_year(dt)
 *   - DateTime_month(dt)
 *   - DateTime_day(dt)
 *   - DateTime_hour(dt)
 *   - DateTime_minute(dt)
 *   - DateTime_second(dt)
 *   - DateTime_millisecond(dt)
 *   - DateTime_dayOfWeek(dt)
 *
 * Setters:
 *   - DateTime_set(dt)
 *   - setEpochMillis(dt, epochMillis)
 * ============================================================================
 */


void setEpochMillis(DateTime *dt, int64_t epochMillis) {
    (*dt).epochMillis = epochMillis;

    int64_t totalSeconds = epochMillis / 1000;
    int32_t millisecond = (int32_t)(epochMillis % 1000);
    if (millisecond < 0) {
        millisecond += 1000;
        totalSeconds--;
    }

    int64_t totalMinutes = totalSeconds / 60;
    int32_t second = (int32_t)(totalSeconds % 60);
    if (second < 0) {
        second += 60;
        totalMinutes--;
    }

    int64_t totalHours = totalMinutes / 60;
    int32_t minute = (int32_t)(totalMinutes % 60);
    if (minute < 0) {
        minute += 60;
        totalHours--;
    }

    int64_t totalDays = totalHours / 24;
    int32_t hour = (int32_t)(totalHours % 24);
    if (hour < 0) {
        hour += 24;
        totalDays--;
    }

    // Day of week: 1970-01-01 was Thursday (=4); shift into ISO 1=Mon..7=Sun.
    int32_t dayOfWeek = (int32_t)((totalDays + 3) % 7);
    if (dayOfWeek < 0)
        dayOfWeek += 7;
    dayOfWeek += 1;

    // Howard Hinnant's civil-from-days algorithm.
    int64_t z = totalDays + 719468;
    int64_t era = (z >= 0 ? z : z - 146096) / 146097;
    int32_t doe = (int32_t)(z - era * 146097);
    int32_t yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    int32_t y = yoe + (int32_t)(era * 400);
    int32_t doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    int32_t mp = (5 * doy + 2) / 153;
    int32_t day = doy - (153 * mp + 2) / 5 + 1;
    int32_t month = mp + (mp < 10 ? 3 : -9);
    int32_t year = y + (month <= 2 ? 1 : 0);

    (*dt).year = year;
    (*dt).month = month;
    (*dt).day = day;
    (*dt).hour = hour;
    (*dt).minute = minute;
    (*dt).second = second;
    (*dt).millisecond = millisecond;
    (*dt).dayOfWeek = dayOfWeek;
}

void DateTime_set(DateTime *dt) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    setEpochMillis(dt, (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

int64_t DateTime_epochMillis(const DateTime *dt) {
    return (*dt).epochMillis;
}

int32_t DateTime_year(const DateTime *dt) {
    return (*dt).year;
}

int32_t DateTime_month(const DateTime *dt) {
    return (*dt).month;
}

int32_t DateTime_day(const DateTime *dt) {
    return (*dt).day;
}

int32_t DateTime_hour(const DateTime *dt) {
    return (*dt).hour;
}

int32_t DateTime_minute(const DateTime *dt) {
    return (*dt).minute;
}

int32_t DateTime_second(const DateTime *dt) {
    return (*dt).second;
}

int32_t DateTime_millisecond(const DateTime *dt) {
    return (*dt).millisecond;
}

int32_t DateTime_dayOfWeek(const DateTime *dt) {
    return (*dt).dayOfWeek;
}
