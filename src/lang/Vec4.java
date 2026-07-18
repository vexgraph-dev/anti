package lang;

import nio.ForeignMemory;

/**
 * Off-Heap 4D Vector representation (x, y, z, w) stored in contiguous 16-byte raw native memory.
 * Designed for zero-GC allocation and pure Java FFM performance.
 */
public final class Vec4
{
    public static final long BYTES = 16L;

    private Vec4() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, 0.0f, 0.0f, 0.0f, 0.0f);
        return ptr;
    }

    public static long allocate(float x, float y, float z, float w)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, x, y, z, w);
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

    public static float getZ(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 8L);
    }

    public static void setZ(long ptr, float z)
    {
        ForeignMemory.putFloat(ptr + 8L, z);
    }

    public static float getW(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 12L);
    }

    public static void setW(long ptr, float w)
    {
        ForeignMemory.putFloat(ptr + 12L, w);
    }

    public static void set(long ptr, float x, float y, float z, float w)
    {
        ForeignMemory.putFloat(ptr, x);
        ForeignMemory.putFloat(ptr + 4L, y);
        ForeignMemory.putFloat(ptr + 8L, z);
        ForeignMemory.putFloat(ptr + 12L, w);
    }

    public static void copy(long dest, long src)
    {
        ForeignMemory.putFloat(dest, ForeignMemory.getFloat(src));
        ForeignMemory.putFloat(dest + 4L, ForeignMemory.getFloat(src + 4L));
        ForeignMemory.putFloat(dest + 8L, ForeignMemory.getFloat(src + 8L));
        ForeignMemory.putFloat(dest + 12L, ForeignMemory.getFloat(src + 12L));
    }

    public static void add(long dest, long a, long b)
    {
        set(dest, getX(a) + getX(b), getY(a) + getY(b), getZ(a) + getZ(b), getW(a) + getW(b));
    }

    public static void sub(long dest, long a, long b)
    {
        set(dest, getX(a) - getX(b), getY(a) - getY(b), getZ(a) - getZ(b), getW(a) - getW(b));
    }

    public static void mul(long dest, long a, float scalar)
    {
        set(dest, getX(a) * scalar, getY(a) * scalar, getZ(a) * scalar, getW(a) * scalar);
    }

    public static void div(long dest, long a, float scalar)
    {
        float inv = 1.0f / scalar;
        set(dest, getX(a) * inv, getY(a) * inv, getZ(a) * inv, getW(a) * inv);
    }

    public static float dot(long a, long b)
    {
        return getX(a) * getX(b) + getY(a) * getY(b) + getZ(a) * getZ(b) + getW(a) * getW(b);
    }

    public static float lengthSquared(long ptr)
    {
        float x = getX(ptr);
        float y = getY(ptr);
        float z = getZ(ptr);
        float w = getW(ptr);
        return x * x + y * y + z * z + w * w;
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
            set(dest, 0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    public static void lerp(long dest, long a, long b, float t)
    {
        float ax = getX(a), ay = getY(a), az = getZ(a), aw = getW(a);
        set(dest, ax + t * (getX(b) - ax), ay + t * (getY(b) - ay), az + t * (getZ(b) - az), aw + t * (getW(b) - aw));
    }

    public static String toString(long ptr)
    {
        return "(" + getX(ptr) + ", " + getY(ptr) + ", " + getZ(ptr) + ", " + getW(ptr) + ")";
    }
}
