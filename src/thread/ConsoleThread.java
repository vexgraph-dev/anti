package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import cli.Console;
import cli.Scanner;
import cli.Command;
import cli.CommandParser;
import cli.CommandRegistry;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.Map;

@Draft
@Intention("Data-Oriented off-heap manager for CLI I/O polling, patterned after NetworkingThread")
@Volatile
public final class ConsoleThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_CONSOLE_THREAD;
    public static final int TYPE_CONSOLE_THREAD = TypeRegister.FORM_SINGLETON | CLASS_ID;

    private static final long WORKER_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 16);

    private static final long CORE_WORKER_PTR;

    static {
        CORE_WORKER_PTR = invoke(true);
    }

    private ConsoleThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static long getCoreWorker() {
        return CORE_WORKER_PTR;
    }

    public static boolean isCore(long workerPtr) {
        if (workerPtr == 0L) return false;
        return ForeignMemory.getInt(workerPtr + 16L) == 1;
    }

    @Draft
    public static long invoke() {
        return invoke(false);
    }

    private static long invoke(boolean isCore) {
        long block = ForeignMemory.allocateNative(56);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.setInt(block, TYPE_CONSOLE_THREAD);
        ForeignMemory.setInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_STRING, 1024);

        // Off-heap worker fields:
        // workerPtr + 0: state (0 = STOPPED, 1 = RUNNING)
        // workerPtr + 4: poolSize (1 thread)
        // workerPtr + 8: workQueuePtr (RingBuffer handle)
        // workerPtr + 16: isCore (1 = CORE, 0 = USER)
        ForeignMemory.setInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.setInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.setLong(workerPtr + 8L, workQueuePtr);
        ForeignMemory.setInt(workerPtr + 16L, isCore ? 1 : 0);

        // Connect Console logging to the worker queue
        Console.setLogQueueHead(workQueuePtr);

        // Register worker handle in central pool manager registry
        Map.put(WORKER_MAP_PTR, workerPtr, 1L);
        return workerPtr;
    }

    public static boolean isRegistered(long workerPtr) {
        if (workerPtr == 0L) return false;
        return Map.containsKey(WORKER_MAP_PTR, workerPtr);
    }

    public static boolean isRunning(long workerPtr) {
        if (workerPtr == 0L) return false;
        return ForeignMemory.getInt(workerPtr) == 1;
    }

    public static long getQueue(long workerPtr) {
        if (workerPtr == 0L) return 0L;
        return ForeignMemory.getLong(workerPtr + 8L);
    }

    @Draft
    public static synchronized boolean run(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
        if (isRunning(workerPtr)) return true; // Already running

        long queuePtr = getQueue(workerPtr);

        Thread worker = Thread.ofPlatform()
                .name("Anti-ConsoleWorker-0x" + Long.toHexString(workerPtr).toUpperCase())
                .daemon(true)
                .start(() -> processLoop(workerPtr, queuePtr));

        Map.putObject(WORKER_MAP_PTR, workerPtr, worker);
        ForeignMemory.setInt(workerPtr, 1); // Set state to RUNNING
        return true;
    }

    @Draft
    public static synchronized void stop(long workerPtr) {
        if (workerPtr == 0L) return;
        if (isCore(workerPtr)) return; // Protected core thread
        stopInternal(workerPtr);
    }

    private static void stopInternal(long workerPtr) {
        ForeignMemory.setInt(workerPtr, 0); // Set state to STOPPED

        Thread worker = (Thread) Map.getObject(WORKER_MAP_PTR, workerPtr);
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Map.put(WORKER_MAP_PTR, workerPtr, 1L);

        // Disconnect Console logging queue
        Console.setLogQueueHead(0L);
    }

    @Draft
    public static synchronized void free(long workerPtr) {
        if (workerPtr == 0L) return;
        if (isCore(workerPtr)) return; // Protected core thread
        freeInternal(workerPtr);
    }

    private static void freeInternal(long workerPtr) {
        stopInternal(workerPtr);

        long queuePtr = getQueue(workerPtr);
        if (queuePtr != 0L) {
            RingBuffer.free(queuePtr);
        }

        Map.remove(WORKER_MAP_PTR, workerPtr);

        long block = workerPtr - 8L;
        ForeignMemory.freeNative(block);
    }

    /**
     * Completely shuts down and frees all resources, including the core worker.
     * Call this only during full system shutdown.
     */
    public static synchronized void freeAllSystem() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = struct.Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = struct.Array.get(keysPtr, i);
            freeInternal(workerPtr);
        }
        struct.Array.free(keysPtr);
    }

    @Draft
    private static void processLoop(long workerPtr, long queuePtr) {
        while (isRunning(workerPtr)) {
            // 1. Poll RingBuffer for string pointers and print/flush them
            long strPtr;
            while ((strPtr = RingBuffer.poll(queuePtr)) != 0L) {
                String msg = primitive.string.get(strPtr);
                if (msg != null) {
                    System.out.println(msg);
                }
                primitive.string.free(strPtr);
            }

            // 2. Non-blocking check for System.in bytes, feed into our off-heap Scanner
            try {
                if (Scanner.hasNextLine()) {
                    long linePtr = Scanner.nextLine();
                    if (linePtr != 0L) {
                        long parsedCommand = CommandParser.parse(linePtr);
                        primitive.string.free(linePtr);

                        if (parsedCommand != 0L) {
                            CommandRegistry.execute(parsedCommand);
                            Command.free(parsedCommand);
                        }
                    }
                }
            } catch (Throwable t) {
                System.err.println("Error in ConsoleThread processLoop: " + t.getMessage());
            }

            // Sleep a tiny bit to avoid pegging CPU when idle
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
