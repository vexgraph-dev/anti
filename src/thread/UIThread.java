package thread;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import annotation.Volatile;
import darling.Canvas;
import darling.Container;
import darling.Panel;
import lang.Vec4;
import nio.ForeignMemory;
import oop.TypeRegister;
import struct.Array;
import struct.Map;

import java.util.concurrent.locks.LockSupport;

/**
 * Data-Oriented Design (DOD) off-heap UI Worker Thread Manager.
 *
 * Owns the UI hierarchy traversal, state management (Hover, Press, Focus),
 * component lifecycle (blinking carets, animations), and event dispatching.
 * Decouples interactive UI logic from OS input pump (EventThread) and GPU command submission (DrawThread).
 */
@Draft
@Intention("Off-heap UI state and component worker thread manager tracking worker handles via dogfooded struct.Map")
@Volatile
public final class UIThread {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_UI_THREAD;
    public static final int TYPE_UI_THREAD = TypeRegister.UI_THREAD_SINGLETON;

    // Event packet types in UI ring buffer
    public static final int EVENT_NONE         = 0;
    public static final int EVENT_MOUSE_MOVE   = 1;
    public static final int EVENT_MOUSE_DOWN   = 2;
    public static final int EVENT_MOUSE_UP     = 3;
    public static final int EVENT_KEY_DOWN     = 4;
    public static final int EVENT_KEY_UP       = 5;
    public static final int EVENT_CHAR_INPUT   = 6;
    public static final int EVENT_RESIZE       = 7;

    // Off-heap manager registry mapping workerPtr -> Thread instance
    private static final long WORKER_MAP_PTR = Map.instant(TypeRegister.ID_LONG, TypeRegister.ID_VARIABLE, 64);

    private static final long MAIN_WORKER_PTR;

    static {
        MAIN_WORKER_PTR = invoke();
    }

    private UIThread() {}

    public static int classId() {
        return CLASS_ID;
    }

    public static long getMainWorker() {
        return MAIN_WORKER_PTR;
    }

    /**
     * Creates and registers a new off-heap UI worker thread handle.
     */
    public static long invoke() {
        long block = ForeignMemory.allocateNative(64);
        long workerPtr = block + 8L;

        // Write bit-packed header ID & length header
        ForeignMemory.setInt(block, TYPE_UI_THREAD);
        ForeignMemory.setInt(block + 4L, 1);

        long workQueuePtr = RingBuffer.instant(TypeRegister.ID_LONG, 2048);

        // Off-heap worker layout (relative to workerPtr):
        // workerPtr + 0:  state (0 = STOPPED, 1 = RUNNING) (int)
        // workerPtr + 4:  poolSize (1 thread) (int)
        // workerPtr + 8:  workQueuePtr (RingBuffer handle) (long)
        // workerPtr + 16: rootUiNode (root Container ptr) (long)
        // workerPtr + 24: windowPtr bound to this worker (long)
        // workerPtr + 32: hoveredNode (long)
        // workerPtr + 40: focusedNode (long)
        // workerPtr + 48: pressedNode (long)
        ForeignMemory.setInt(workerPtr, 0);                 // STOPPED
        ForeignMemory.setInt(workerPtr + 4L, 1);             // 1 thread
        ForeignMemory.setLong(workerPtr + 8L, workQueuePtr);
        ForeignMemory.setLong(workerPtr + 16L, 0L);          // rootUiNode
        ForeignMemory.setLong(workerPtr + 24L, 0L);          // windowPtr
        ForeignMemory.setLong(workerPtr + 32L, 0L);          // hoveredNode
        ForeignMemory.setLong(workerPtr + 40L, 0L);          // focusedNode
        ForeignMemory.setLong(workerPtr + 48L, 0L);          // pressedNode

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

    public static long getRootUi(long workerPtr) {
        if (workerPtr == 0L) return 0L;
        return ForeignMemory.getLong(workerPtr + 16L);
    }

    public static void setRootUi(long workerPtr, long rootUiPtr) {
        if (workerPtr == 0L) return;
        ForeignMemory.setLong(workerPtr + 16L, rootUiPtr);
    }

    public static void bindWindow(long workerPtr, long windowPtr) {
        if (workerPtr == 0L) return;
        ForeignMemory.setLong(workerPtr + 24L, windowPtr);
    }

    public static long getHoveredNode(long workerPtr) {
        if (workerPtr == 0L) return 0L;
        return ForeignMemory.getLong(workerPtr + 32L);
    }

    public static long getFocusedNode(long workerPtr) {
        if (workerPtr == 0L) return 0L;
        return ForeignMemory.getLong(workerPtr + 40L);
    }

    public static void setFocusedNode(long workerPtr, long nodePtr) {
        if (workerPtr == 0L) return;
        ForeignMemory.setLong(workerPtr + 40L, nodePtr);
    }

    /**
     * Starts the main background UI worker thread.
     */
    public static synchronized void start() {
        run(MAIN_WORKER_PTR);
    }

    /**
     * Instantiates and launches the background UI worker thread for the specified handle.
     */
    public static synchronized boolean run(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return false;
        if (isRunning(workerPtr)) return true;

        long queuePtr = getQueue(workerPtr);
        ForeignMemory.setInt(workerPtr, 1); // Set state to RUNNING

        Thread worker = Thread.ofPlatform()
                .name("Anti-UIWorker-0x" + Long.toHexString(workerPtr).toUpperCase())
                .daemon(true)
                .start(() -> {
                    try {
                        processLoop(workerPtr, queuePtr);
                    } catch (Throwable t) {
                        System.out.println("[UIThread] Worker crashed: " + t.getMessage());
                        t.printStackTrace();
                    } finally {
                        ForeignMemory.setInt(workerPtr, 0); // Mark STOPPED
                    }
                });

        Map.putObject(WORKER_MAP_PTR, workerPtr, worker);
        return true;
    }

    /**
     * Stops the UI worker thread handle.
     */
    public static synchronized void stop(long workerPtr) {
        if (workerPtr == 0L || !isRegistered(workerPtr)) return;
        ForeignMemory.setInt(workerPtr, 0); // Signal STOP
    }

    public static synchronized void stopAll() {
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            stop(workerPtr);
        }
        Array.free(keysPtr);
    }

    public static synchronized void freeAllSystem() {
        stopAll();
        long keysPtr = Map.getKeys(WORKER_MAP_PTR);
        if (keysPtr == 0L) return;
        int count = Array.length(keysPtr);
        for (int i = 0; i < count; i++) {
            long workerPtr = Array.get(keysPtr, i);
            long queuePtr = getQueue(workerPtr);
            if (queuePtr != 0L) RingBuffer.free(queuePtr);
            ForeignMemory.freeNative(workerPtr - 8L);
        }
        Array.free(keysPtr);
        Map.free(WORKER_MAP_PTR);
    }

    private static void processLoop(long workerPtr, long queuePtr) {
        while (ForeignMemory.getInt(workerPtr) == 1 && !Thread.currentThread().isInterrupted()) {
            boolean hasWork = false;

            // 1. Drain pending UI event packets from the RingBuffer
            if (queuePtr != 0L && !RingBuffer.isEmpty(queuePtr)) {
                long packet = RingBuffer.poll(queuePtr);
                if (packet != 0L) {
                    hasWork = true;
                    handleEventPacket(workerPtr, packet);
                }
            }

            // 2. Perform periodic component updates (e.g. caret blinking, animations)
            tickComponents(workerPtr);

            if (!hasWork) {
                // High-cadence adaptive sleep for sub-millisecond latency with 0% CPU burn
                LockSupport.parkNanos(250_000L); // 0.25ms
            }
        }
    }

    private static void handleEventPacket(long workerPtr, long packet) {
        int eventType = (int) (packet >>> 48) & 0xFFFF;
        int param1 = (int) (packet >>> 32) & 0xFFFF;
        int param2 = (int) (packet >>> 16) & 0xFFFF;
        int param3 = (int) packet & 0xFFFF;

        long root = getRootUi(workerPtr);
        if (root == 0L) return;

        switch (eventType) {
            case EVENT_MOUSE_MOVE -> {
                float canvasX = (float) param1;
                float canvasY = (float) param2;
                long hitNode = findHitNode(root, canvasX, canvasY);
                long oldHovered = getHoveredNode(workerPtr);
                if (hitNode != oldHovered) {
                    ForeignMemory.setLong(workerPtr + 32L, hitNode);
                    if (oldHovered != 0L) Container.markDirty(oldHovered);
                    if (hitNode != 0L) Container.markDirty(hitNode);
                }
            }
            case EVENT_MOUSE_DOWN -> {
                int button = param1;
                float canvasX = (float) param2;
                float canvasY = (float) param3;
                long hitNode = findHitNode(root, canvasX, canvasY);
                ForeignMemory.setLong(workerPtr + 48L, hitNode); // pressedNode
                setFocusedNode(workerPtr, hitNode);
                if (hitNode != 0L) Container.markDirty(hitNode);
            }
            case EVENT_MOUSE_UP -> {
                long pressed = ForeignMemory.getLong(workerPtr + 48L);
                if (pressed != 0L) {
                    ForeignMemory.setLong(workerPtr + 48L, 0L);
                    Container.markDirty(pressed);
                }
            }
            case EVENT_RESIZE -> {
                Container.markDirty(root);
            }
            default -> {}
        }
    }

    /**
     * Hierarchical hit-testing: finds the topmost child container hit by (canvasX, canvasY).
     */
    public static long findHitNode(long rootNode, float canvasX, float canvasY) {
        if (rootNode == 0L || !Container.isVisible(rootNode) || !Container.isEnabled(rootNode)) {
            return 0L;
        }

        long rect = Vec4.allocate();
        try {
            Canvas.resolveRoot(rootNode, Canvas.getVirtualWidth(), Canvas.getVirtualHeight(), rect);
            float rx = Vec4.getX(rect);
            float ry = Vec4.getY(rect);
            float rw = Vec4.getZ(rect);
            float rh = Vec4.getW(rect);

            if (canvasX < rx || canvasX >= rx + rw || canvasY < ry || canvasY >= ry + rh) {
                return 0L;
            }

            // Check children top-to-bottom (last added child is top-most)
            if (oop.TypeRegister.isA(Container.classId(rootNode), Panel.CLASS_ID)) {
                int count = Panel.childCount(rootNode);
                for (int i = count - 1; i >= 0; i--) {
                    long child = Panel.getChild(rootNode, i);
                    long hit = findChildHit(child, rx, ry, rw, rh, canvasX, canvasY);
                    if (hit != 0L) return hit;
                }
            }

            return rootNode;
        } finally {
            Vec4.free(rect);
        }
    }

    private static long findChildHit(long childPtr, float parentX, float parentY, float parentW, float parentH, float px, float py) {
        if (childPtr == 0L || !Container.isVisible(childPtr) || !Container.isEnabled(childPtr)) {
            return 0L;
        }

        long rect = Vec4.allocate();
        try {
            Container.resolve(childPtr, parentX, parentY, parentW, parentH, rect);
            float rx = Vec4.getX(rect);
            float ry = Vec4.getY(rect);
            float rw = Vec4.getZ(rect);
            float rh = Vec4.getW(rect);

            if (px < rx || px >= rx + rw || py < ry || py >= ry + rh) {
                return 0L;
            }

            if (oop.TypeRegister.isA(Container.classId(childPtr), Panel.CLASS_ID)) {
                int count = Panel.childCount(childPtr);
                for (int i = count - 1; i >= 0; i--) {
                    long grandchild = Panel.getChild(childPtr, i);
                    long hit = findChildHit(grandchild, rx, ry, rw, rh, px, py);
                    if (hit != 0L) return hit;
                }
            }

            return childPtr;
        } finally {
            Vec4.free(rect);
        }
    }

    private static void tickComponents(long workerPtr) {
        // Reserved for active UI component ticks (e.g. text cursor blink, scroll inertia)
    }

    /**
     * Post a mouse move event into the UI thread queue.
     */
    public static void postMouseMove(long workerPtr, float canvasX, float canvasY) {
        long queue = getQueue(workerPtr);
        if (queue == 0L) return;
        long packet = ((long) EVENT_MOUSE_MOVE << 48) |
                      (((long) ((int) canvasX & 0xFFFF)) << 32) |
                      (((long) ((int) canvasY & 0xFFFF)) << 16);
        RingBuffer.offer(queue, packet);
    }

    /**
     * Post a mouse button event into the UI thread queue.
     */
    public static void postMouseButton(long workerPtr, int button, boolean isDown, float canvasX, float canvasY) {
        long queue = getQueue(workerPtr);
        if (queue == 0L) return;
        int type = isDown ? EVENT_MOUSE_DOWN : EVENT_MOUSE_UP;
        long packet = ((long) type << 48) |
                      (((long) (button & 0xFFFF)) << 32) |
                      (((long) ((int) canvasX & 0xFFFF)) << 16) |
                      ((long) ((int) canvasY & 0xFFFF));
        RingBuffer.offer(queue, packet);
    }
}
