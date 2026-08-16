package bindings;

import annotation.Draft;
import annotation.HotCode;
import annotation.Intention;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Method directory for the scripting system.
 *
 * <p>Every bound method has ONE uniform shape:
 *
 * <pre>
 *   long fn(long argsPtr, long argCount)
 * </pre>
 *
 * Arguments travel as a raw off-heap pointer into a {@code primitive.Arguments}
 * buffer — no heap arrays, no boxing, zero allocation per call. Callers address
 * methods by <b>slot index</b>, which is the stable ABI / versioning contract:
 * resolve a name to a slot once at bind/parse time ({@link #slot}), then dispatch
 * every runtime call through {@link #invoke}.
 *
 * <p>Dispatch is an O(1) {@code MethodHandle[]} lookup (fully AOT-safe, see
 * preferences 11.6). Per 11.2 the table is deliberately NOT {@code static final}
 * — it is populated at runtime in the static initializer. Classes that call FFM
 * downcalls from a static init must be deferred with {@code --initialize-at-run-time};
 * this registry itself performs no FFM calls, but the AOT script should still
 * defer {@code bindings.Bindings} to build-time-freeze safety.
 *
 * <p>Versioning: slot indices are never reused and never renumbered. When a
 * method changes ABI semantics, register a NEW slot (e.g. {@code math.add}
 * alongside {@code math.add2}); old scripts keep calling their original slot.
 */
@HotCode
@Draft
@Intention("Uniform-ABI method directory: stable slot indices, runtime-built MethodHandle table, name->slot resolution only at bind time.")
public final class Bindings {

    // ---- stable slot indices (THE versioning contract - never renumber) ----
    public static final int SLOT_MATH_ADD     = 0;
    public static final int SLOT_MATH_SUB     = 1;
    public static final int SLOT_MATH_MUL     = 2;
    public static final int SLOT_MATH_DIV     = 3;
    public static final int SLOT_MATH_MAX     = 4;
    public static final int SLOT_MATH_MIN     = 5;
    public static final int SLOT_MATH_ABS     = 6;
    public static final int SLOT_VEC2_LENGTH  = 7;
    public static final int SLOT_VEC4_LENGTH  = 8;
    public static final int SLOT_INT_GET      = 9;
    public static final int SLOT_INT_SET      = 10;
    public static final int SLOT_FILE_EXISTS  = 11;
    public static final int SLOT_FILE_OPEN    = 12;
    public static final int SLOT_FILE_CLOSE   = 13;
    public static final int SLOT_FILE_WRITE   = 14;
    public static final int SLOT_STRING_ALLOC = 15;
    public static final int SLOT_STRING_FREE  = 16;
    public static final int SLOT_STRING_LEN   = 17;
    public static final int METHOD_COUNT      = 18;

    // 11.2: method handles must NOT be static final (native-image may constant-fold
    // them); the table is populated at runtime in the static initializer below.
    private static MethodHandle[] TABLE;
    private static String[] NAMES;

    static {
        TABLE = new MethodHandle[METHOD_COUNT];
        NAMES = new String[METHOD_COUNT];
        register(SLOT_MATH_ADD,     BindingsMath.class, "add",          "math.add");
        register(SLOT_MATH_SUB,     BindingsMath.class, "sub",          "math.sub");
        register(SLOT_MATH_MUL,     BindingsMath.class, "mul",          "math.mul");
        register(SLOT_MATH_DIV,     BindingsMath.class, "div",          "math.div");
        register(SLOT_MATH_MAX,     BindingsMath.class, "max",          "math.max");
        register(SLOT_MATH_MIN,     BindingsMath.class, "min",          "math.min");
        register(SLOT_MATH_ABS,     BindingsMath.class, "abs",          "math.abs");
        register(SLOT_VEC2_LENGTH,  BindingsMath.class, "vec2Length",   "vec2.length");
        register(SLOT_VEC4_LENGTH,  BindingsMath.class, "vec4Length",   "vec4.length");
        register(SLOT_INT_GET,      BindingsMath.class, "intGet",       "int.get");
        register(SLOT_INT_SET,      BindingsMath.class, "intSet",       "int.set");
        register(SLOT_FILE_EXISTS,  BindingsIO.class,   "fileExists",   "file.exists");
        register(SLOT_FILE_OPEN,    BindingsIO.class,   "fileOpen",     "file.open");
        register(SLOT_FILE_CLOSE,   BindingsIO.class,   "fileClose",    "file.close");
        register(SLOT_FILE_WRITE,   BindingsIO.class,   "fileWrite",    "file.write");
        register(SLOT_STRING_ALLOC, BindingsIO.class,   "stringAllocate", "string.allocate");
        register(SLOT_STRING_FREE,  BindingsIO.class,   "stringFree",   "string.free");
        register(SLOT_STRING_LEN,   BindingsIO.class,   "stringLength", "string.length");
    }

    private static void register(int slot, Class<?> owner, String method, String name) {
        if (slot < 0 || slot >= METHOD_COUNT) {
            throw new ExceptionInInitializerError("Slot " + slot + " out of range [0, " + METHOD_COUNT + ")");
        }
        try {
            TABLE[slot] = MethodHandles.lookup().findStatic(
                    owner, method, MethodType.methodType(long.class, long.class, long.class));
            NAMES[slot] = name;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Failed to bind " + owner.getName() + "." + method + ": " + e);
        }
    }

    private Bindings() {}

    /** Slot index for a method name, or -1. Bind-time only — never call this hot. */
    public static int slot(String name) {
        for (int i = 0; i < METHOD_COUNT; i++) {
            if (name.equals(NAMES[i])) return i;
        }
        return -1;
    }

    public static String name(int slot) {
        return NAMES[slot];
    }

    /** O(1) dispatch by stable slot index through the uniform ABI. */
    public static long invoke(int slot, long argsPtr, long argCount) {
        try {
            return (long) TABLE[slot].invokeExact(argsPtr, argCount);
        } catch (Throwable t) {
            throw new RuntimeException("Bindings.invoke failed for slot " + slot + " (" + NAMES[slot] + "): " + t, t);
        }
    }

    /** Convenience: name -> invoke, resolving the slot each call (bind/script paths only). */
    public static long call(String name, long argsPtr, long argCount) {
        int slot = slot(name);
        if (slot < 0) throw new IllegalArgumentException("Unknown binding: " + name);
        return invoke(slot, argsPtr, argCount);
    }
}