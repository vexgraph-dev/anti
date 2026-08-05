package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

@Draft
@Intention("[definition]")
public class Choice
{
    @Required
    public static final int CLASS_ID = TypeRegister.ID_CHOICE;
    public static final int TYPE_SINGLETON = TypeRegister.FORM_SINGLETON | TypeRegister.WRAP_CHOICE | CLASS_ID;

    // [definition]
    // "if the purpose of choice is to just make a choice variable where it does the
    // deterministic of what value it is, why need to be making a choice when it can
    // just be using an Object[] or even a data structure (anti style)?.
    //
    // good question.
    // * you cannot edit choices than using an array. therefore it is final.
    // * it pairs well with reactive object as another wrapper. when the object was chosen,
    // * the object will fire a script/runnable to that corresponding object, good for storytelling.
    // choices-based actions, while getting an item seamlessly.
    // * comboboxes, you can intentionally return a null, or a desired value, or return a specific
    // method that fires another method.
    // * pairs well with passive object wrapper as well... you can get a method to return an item depending it.

    public static long allocate(long[] objectPtrs, long[] callbackAddrs)
    {
        int len = objectPtrs.length;
        long block = ForeignMemory.allocateNative(8L + len * 16L);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON);
        ForeignMemory.setInt(block + 4L, len);

        for (int i = 0; i < len; i++) {
            long slotBase = userPtr + (i * 16L);
            ForeignMemory.setLong(slotBase, objectPtrs[i]);
            long cb = (callbackAddrs != null && i < callbackAddrs.length) ? callbackAddrs[i] : 0L;
            ForeignMemory.setLong(slotBase + 8L, cb);
        }

        return userPtr;
    }

    public static void free(long ptr)
    {
        if (ptr == 0L) return;
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static int length(long ptr)
    {
        if (ptr == 0L) return 0;
        return ForeignMemory.getInt(ptr - 4L);
    }

    public static long getObject(long ptr, int index)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int len = length(ptr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Choice index " + index + " out of bounds: " + len);
        }
        return ForeignMemory.getLong(ptr + (index * 16L));
    }

    public static long getCallback(long ptr, int index)
    {
        if (ptr == 0L) throw new NullPointerException("Accessing NULL off-heap pointer!");
        int len = length(ptr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Choice index " + index + " out of bounds: " + len);
        }
        return ForeignMemory.getLong(ptr + (index * 16L) + 8L);
    }

    public Choice()
    {

    }
}

