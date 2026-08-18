package objects;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import nio.ForeignMemory;
import oop.TypeRegister;

import nio.StringLookup;
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

    /**
     * Allocates off-heap memory for a Choice array object.
     * 
     * Layout (8 + length * 16 bytes):
     * - [block + 0L] (4 bytes): TYPE_HEADER (TYPE_SINGLETON)
     * - [block + 4L] (4 bytes): len (number of choices available)
     * - [userPtr + (index * 16L)] (16 bytes per slot):
     *     - slotBase + 0L (8 bytes): objectPtr (pointer to choice object)
     *     - slotBase + 8L (8 bytes): callbackAddr (function address pointer fired on choice)
     */
    public static long allocate(long[] objectPtrs, long[] callbackAddrs)
    {
        int len = objectPtrs.length;
        long block = ForeignMemory.allocateNative(8L + len * 16L);
        long userPtr = block + 8L;

        ForeignMemory.setInt(block, TYPE_SINGLETON); // class type header
        ForeignMemory.setInt(block + 4L, len); // len (number of choices)

        for (int i = 0; i < len; i++) {
            long slotBase = userPtr + (i * 16L);
            ForeignMemory.setLong(slotBase, objectPtrs[i]); // objectPtr
            long cb = (callbackAddrs != null && i < callbackAddrs.length) ? callbackAddrs[i] : 0L;
            ForeignMemory.setLong(slotBase + 8L, cb); // callbackAddr
        }

        return userPtr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static int length(long ptr)
    {
        return ForeignMemory.getInt(ptr - 4L);
    }

    public static long getObject(long ptr, int index)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        int len = length(ptr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(361) + index + StringLookup.getJavaString(362) + len);
        }
        return ForeignMemory.getLong(ptr + (index * 16L));
    }

    public static long getCallback(long ptr, int index)
    {
        if (ptr == 0L) throw new NullPointerException(StringLookup.getJavaString(27));
        int len = length(ptr);
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException(StringLookup.getJavaString(361) + index + StringLookup.getJavaString(362) + len);
        }
        return ForeignMemory.getLong(ptr + (index * 16L) + 8L);
    }

    public Choice()
    {

    }
}

