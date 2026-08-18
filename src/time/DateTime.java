package time;

import annotation.Draft;
import annotation.Required;
import annotation.Intention;
import nio.ForeignMemory;
import oop.TypeRegister;

import nio.StringLookup;
/**
 * Off-Heap Unix DateTime Subsystem.
 * Breaks down epoch milliseconds to UTC calendar fields using Howard Hinnant's standard algorithm.
 */
@Draft
@Intention("Zero-allocation off-heap Unix DateTime translating epoch millis to calendar components via zero-allocation integer math.")
public final class DateTime {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_DATETIME;

    private DateTime() {}

    public static int classId() {
        return CLASS_ID;
    }

    /**
     * Allocates a new DateTime instance initialized to the current system epoch time.
     * Memory Layout:
     *   [userPtr - 8]: Type ID (DATETIME_SINGLETON)
     *   [userPtr - 4]: Length (1)
     *   [userPtr + 0]: 64-bit long epochMillis
     *   [userPtr + 8]: 32-bit int year
     *   [userPtr + 12]: 32-bit int month (1-12)
     *   [userPtr + 16]: 32-bit int day (1-31)
     *   [userPtr + 20]: 32-bit int hour (0-23)
     *   [userPtr + 24]: 32-bit int minute (0-59)
     *   [userPtr + 28]: 32-bit int second (0-59)
     *   [userPtr + 32]: 32-bit int millisecond (0-999)
     *   [userPtr + 36]: 32-bit int dayOfWeek (1=Monday, 7=Sunday)
     */
    @Draft
    public static long allocate() {
        return allocate(System.currentTimeMillis());
    }

    @Draft
    public static long allocate(long epochMillis) {
        long block = ForeignMemory.allocateNative(48);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TypeRegister.DATETIME_SINGLETON);
        ForeignMemory.setInt(block + 4L, 1);

        setEpochMillis(userPtr, epochMillis);
        return userPtr;
    }

    @Draft
    public static void free(long ptr) {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    @Draft
    public static void setEpochMillis(long ptr, long epochMillis) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        
        ForeignMemory.setLong(ptr, epochMillis);

        long totalSeconds = epochMillis / 1000;
        int millisecond = (int) (epochMillis % 1000);
        if (millisecond < 0) {
            millisecond += 1000;
            totalSeconds--;
        }

        long totalMinutes = totalSeconds / 60;
        int second = (int) (totalSeconds % 60);
        if (second < 0) {
            second += 60;
            totalMinutes--;
        }

        long totalHours = totalMinutes / 60;
        int minute = (int) (totalMinutes % 60);
        if (minute < 0) {
            minute += 60;
            totalHours--;
        }

        long totalDays = totalHours / 24;
        int hour = (int) (totalHours % 24);
        if (hour < 0) {
            hour += 24;
            totalDays--;
        }

        // Day of week calculation (1970-01-01 was Thursday = 4)
        int dayOfWeek = (int) ((totalDays + 3) % 7);
        if (dayOfWeek < 0) {
            dayOfWeek += 7;
        }
        dayOfWeek += 1; // 1 = Monday, 7 = Sunday

        // Howard Hinnant's epoch-to-date conversion algorithm
        long z = totalDays + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        int doe = (int) (z - era * 146097);
        int yoe = (doe - doe/1460 + doe/36524 - doe/146096) / 365;
        int y = yoe + (int)(era * 400);
        int doy = doe - (365*yoe + yoe/4 - yoe/100);
        int mp = (5*doy + 2)/153;
        int day = doy - (153*mp + 2)/5 + 1;
        int month = mp + (mp < 10 ? 3 : -9);
        int year = y + (month <= 2 ? 1 : 0);

        // Commit fields to off-heap memory
        ForeignMemory.setInt(ptr + 8L, year);
        ForeignMemory.setInt(ptr + 12L, month);
        ForeignMemory.setInt(ptr + 16L, day);
        ForeignMemory.setInt(ptr + 20L, hour);
        ForeignMemory.setInt(ptr + 24L, minute);
        ForeignMemory.setInt(ptr + 28L, second);
        ForeignMemory.setInt(ptr + 32L, millisecond);
        ForeignMemory.setInt(ptr + 36L, dayOfWeek);
    }

    @Draft
    public static long getEpochMillis(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getLong(ptr);
    }

    @Draft
    public static int getYear(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 8L);
    }

    @Draft
    public static int getMonth(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 12L);
    }

    @Draft
    public static int getDay(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 16L);
    }

    @Draft
    public static int getHour(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 20L);
    }

    @Draft
    public static int getMinute(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 24L);
    }

    @Draft
    public static int getSecond(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 28L);
    }

    @Draft
    public static int getMillisecond(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 32L);
    }

    @Draft
    public static int getDayOfWeek(long ptr) {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        return ForeignMemory.getInt(ptr + 36L);
    }
}
