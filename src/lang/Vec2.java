package lang;

import nio.ForeignMemory;

/**
 * Off-Heap 2D Vector representation (x, y) stored in contiguous 8-byte raw native memory.
 * Designed for zero-GC allocation and pure Java FFM performance.
 */
public final class Vec2
{
    public static final long BYTES = 8L;

    private Vec2() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, 0.0f, 0.0f);
        return ptr;
    }

    public static long allocate(float x, float y)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, x, y);
        return ptr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static float getX(long ptr)
    {
        return ForeignMemory.getFloat(ptr);
    }

    public static void setX(long ptr, float x)
    {
        ForeignMemory.putFloat(ptr, x);
    }

    public static float getY(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 4L);
    }

    public static void setY(long ptr, float y)
    {
        ForeignMemory.putFloat(ptr + 4L, y);
    }

    public static void set(long ptr, float x, float y)
    {
        ForeignMemory.putFloat(ptr, x);
        ForeignMemory.putFloat(ptr + 4L, y);
    }

    public static void copy(long dest, long src)
    {
        ForeignMemory.putFloat(dest, ForeignMemory.getFloat(src));
        ForeignMemory.putFloat(dest + 4L, ForeignMemory.getFloat(src + 4L));
    }

    public static void add(long dest, long a, long b)
    {
        set(dest, getX(a) + getX(b), getY(a) + getY(b));
    }

    public static void sub(long dest, long a, long b)
    {
        set(dest, getX(a) - getX(b), getY(a) - getY(b));
    }

    public static void mul(long dest, long a, float scalar)
    {
        set(dest, getX(a) * scalar, getY(a) * scalar);
    }

    public static void div(long dest, long a, float scalar)
    {
        float inv = 1.0f / scalar;
        set(dest, getX(a) * inv, getY(a) * inv);
    }

    public static float dot(long a, long b)
    {
        return getX(a) * getX(b) + getY(a) * getY(b);
    }

    public static float lengthSquared(long ptr)
    {
        float x = getX(ptr);
        float y = getY(ptr);
        return x * x + y * y;
    }

    public static float length(long ptr)
    {
        return (float) Math.sqrt(lengthSquared(ptr));
    }

    public static void normalize(long dest, long src)
    {
        float len = length(src);
        if (len != 0.0f)
        {
            div(dest, src, len);
        }
        else
        {
            set(dest, 0.0f, 0.0f);
        }
    }

    public static float distance(long a, long b)
    {
        float dx = getX(a) - getX(b);
        float dy = getY(a) - getY(b);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static void lerp(long dest, long a, long b, float t)
    {
        float ax = getX(a);
        float ay = getY(a);
        set(dest, ax + t * (getX(b) - ax), ay + t * (getY(b) - ay));
    }

    public static String toString(long ptr)
    {
        return "(" + getX(ptr) + ", " + getY(ptr) + ")";
    }
}
