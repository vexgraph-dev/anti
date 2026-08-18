package bindings;

import annotation.Draft;
import annotation.HotCode;
import annotation.Intention;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import nio.StringLookup;
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
        register(SLOT_MATH_ADD,     BindingsMath.class, StringLookup.getJavaString(1031),          StringLookup.getJavaString(1032));
        register(SLOT_MATH_SUB,     BindingsMath.class, StringLookup.getJavaString(1033),          StringLookup.getJavaString(1034));
        register(SLOT_MATH_MUL,     BindingsMath.class, StringLookup.getJavaString(1035),          StringLookup.getJavaString(1036));
        register(SLOT_MATH_DIV,     BindingsMath.class, StringLookup.getJavaString(1037),          StringLookup.getJavaString(1038));
        register(SLOT_MATH_MAX,     BindingsMath.class, StringLookup.getJavaString(1039),          StringLookup.getJavaString(1040));
        register(SLOT_MATH_MIN,     BindingsMath.class, StringLookup.getJavaString(1041),          StringLookup.getJavaString(1042));
        register(SLOT_MATH_ABS,     BindingsMath.class, StringLookup.getJavaString(1043),          StringLookup.getJavaString(1044));
        register(SLOT_VEC2_LENGTH,  BindingsMath.class, StringLookup.getJavaString(1045),   StringLookup.getJavaString(1046));
        register(SLOT_VEC4_LENGTH,  BindingsMath.class, StringLookup.getJavaString(1047),   StringLookup.getJavaString(1048));
        register(SLOT_INT_GET,      BindingsMath.class, StringLookup.getJavaString(1049),       StringLookup.getJavaString(1050));
        register(SLOT_INT_SET,      BindingsMath.class, StringLookup.getJavaString(1051),       StringLookup.getJavaString(1052));
        register(SLOT_FILE_EXISTS,  BindingsIO.class,   StringLookup.getJavaString(1053),   StringLookup.getJavaString(1054));
        register(SLOT_FILE_OPEN,    BindingsIO.class,   StringLookup.getJavaString(1055),     StringLookup.getJavaString(1056));
        register(SLOT_FILE_CLOSE,   BindingsIO.class,   StringLookup.getJavaString(1057),    StringLookup.getJavaString(1058));
        register(SLOT_FILE_WRITE,   BindingsIO.class,   StringLookup.getJavaString(1059),    StringLookup.getJavaString(1060));
        register(SLOT_STRING_ALLOC, BindingsIO.class,   StringLookup.getJavaString(1061), StringLookup.getJavaString(1062));
        register(SLOT_STRING_FREE,  BindingsIO.class,   StringLookup.getJavaString(1063),   StringLookup.getJavaString(1064));
        register(SLOT_STRING_LEN,   BindingsIO.class,   StringLookup.getJavaString(1065), StringLookup.getJavaString(1066));
    }

    private static void register(int slot, Class<?> owner, String method, String name) {
        if (slot < 0 || slot >= METHOD_COUNT) {
            throw new ExceptionInInitializerError(StringLookup.getJavaString(1067) + slot + StringLookup.getJavaString(1068) + METHOD_COUNT + StringLookup.getJavaString(18));
        }
        try {
            TABLE[slot] = MethodHandles.lookup().findStatic(
                    owner, method, MethodType.methodType(long.class, long.class, long.class));
            NAMES[slot] = name;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(StringLookup.getJavaString(1069) + owner.getName() + StringLookup.getJavaString(311) + method + StringLookup.getJavaString(846) + e);
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
            throw new RuntimeException(StringLookup.getJavaString(1070) + slot + StringLookup.getJavaString(370) + NAMES[slot] + StringLookup.getJavaString(1071) + t, t);
        }
    }

    /** Convenience: name -> invoke, resolving the slot each call (bind/script paths only). */
    public static long call(String name, long argsPtr, long argCount) {
        int slot = slot(name);
        if (slot < 0) throw new IllegalArgumentException(StringLookup.getJavaString(1072) + name);
        return invoke(slot, argsPtr, argCount);
    }
}