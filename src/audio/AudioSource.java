package audio;

import annotation.Unsafe;
import annotation.Volatile;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Off-heap AudioSource component manager conforming to the Anti Architecture.
 * Packs Source properties contiguously into lock-free pooled memory slots.
 */
public final class AudioSource
{
    public static final int CLASS_ID = TypeRegister.ID_AUDIO_SOURCE;
    public static final int TYPE_SINGLETON = TypeRegister.AUDIO_SOURCE_SINGLETON;

    private static final int DEFAULT_CAPACITY = 256;
    private static final long SINGLETON_SLOT_SIZE = 64L; // Header (8B) + Data (56B)

    private static final VarHandle SINGLETON_FREE_HEAD_VH;
    private static final VarHandle SINGLETON_EXPANDING_VH;
    private static volatile int singletonExpanding = 0;

    private static Arena poolArena;
    private static volatile boolean active;
    private static volatile long singletonFreeHead;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            SINGLETON_FREE_HEAD_VH = lookup.findStaticVarHandle(AudioSource.class, "singletonFreeHead", long.class);
            SINGLETON_EXPANDING_VH = lookup.findStaticVarHandle(AudioSource.class, "singletonExpanding", int.class);
        }
        catch(ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        poolArena = Arena.ofShared();
        active = true;

        expandSingletonPool();
    }

    private AudioSource() {}

    private static void checkActive()
    {
        if(!active) throw new IllegalStateException("AudioSource subsystem is not active!");
    }

    public static void freeAll()
    {
        if(active) {
            active = false;
            if(poolArena != null && poolArena.scope().isAlive()) {
                poolArena.close();
            }
        }
    }

    private static void expandSingletonPool()
    {
        long totalBytes = DEFAULT_CAPACITY * SINGLETON_SLOT_SIZE;
        long baseAddress = poolArena.allocate(totalBytes, 8).address();

        for(int i = 0; i < DEFAULT_CAPACITY; i++) {
            long currentBlock = baseAddress + (i * SINGLETON_SLOT_SIZE);
            long userPtr = currentBlock + 8L;

            while(true) {
                long oldTagged = singletonFreeHead;
                long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

                ForeignMemory.putLong(userPtr, oldRawHead);

                long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
                long newTagged = (nextGen << 48) | (userPtr & 0x0000FFFFFFFFFFFFL);

                if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) break;
            }
        }
    }

    public static long allocate()
    {
        checkActive();
        while(true) {
            long oldTagged = singletonFreeHead;
            long rawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            if(rawHead == 0L) {
                if(SINGLETON_EXPANDING_VH.compareAndSet(0, 1)) {
                    expandSingletonPool();
                    SINGLETON_EXPANDING_VH.setVolatile(0);
                }
                else {
                    Thread.onSpinWait();
                }
                continue;
            }

            long nextRawHead = ForeignMemory.getLong(rawHead);
            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (nextRawHead & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) {
                long base = rawHead - 8L;
                ForeignMemory.putInt(base, TYPE_SINGLETON);
                ForeignMemory.putInt(base + 4L, 1);
                
                // Initialize default audio fields
                ForeignMemory.putInt(rawHead, 0);       // AL Source ID
                ForeignMemory.putInt(rawHead + 4L, 0);  // AL Buffer ID
                ForeignMemory.putFloat(rawHead + 8L, 1.0f); // Pitch
                ForeignMemory.putFloat(rawHead + 12L, 1.0f); // Gain
                ForeignMemory.putInt(rawHead + 16L, 0); // Looping (0 = false)
                
                return rawHead;
            }
        }
    }

    public static void free(long pointer)
    {
        checkActive();
        if(pointer == 0L) return;

        long base = pointer - 8L;
        int type = ForeignMemory.getInt(base);
        if(type != TYPE_SINGLETON) {
            throw new IllegalStateException("Invalid AudioSource pointer: 0x" + Long.toHexString(pointer).toUpperCase());
        }

        // Reset header
        ForeignMemory.putInt(base, 0);
        ForeignMemory.putInt(base + 4L, -1);

        while(true) {
            long oldTagged = singletonFreeHead;
            long oldRawHead = oldTagged & 0x0000FFFFFFFFFFFFL;

            ForeignMemory.putLong(pointer, oldRawHead);

            long nextGen = ((oldTagged >>> 48) + 1L) & 0xFFFFL;
            long newTagged = (nextGen << 48) | (pointer & 0x0000FFFFFFFFFFFFL);

            if(SINGLETON_FREE_HEAD_VH.compareAndSet(oldTagged, newTagged)) return;
        }
    }
}
