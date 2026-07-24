package actions;

import annotation.HotCode;

public class MathActions
{
    @HotCode // this is just placeholders
    public static long execute(int opcode, long bufferAddress, long offset)
    {
        switch (opcode) {
            case 0:
                // adding both bytes into the mix
                // TODO: Read primitives from raw memory using FFM API
                // e.g., byte a = MemoryAccess.getByte(bufferAddress + offset + 1);
                return 0L; 
            case 1:
                // printing
                System.out.println();
                return 0L;
            case 2:
            case 3:
            case 4:
            case 5:
                // placeholders
                return 0L;
            default:
                return 0L;
        }
    }
}
