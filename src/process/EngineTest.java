package process;

import nio.ForeignMemory;
import window.Window;

import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;

public class EngineTest {
    public static void main(String[] args) throws Throwable
    {
        AntiRuntime.init(null);
        // ACTUAL ENGINE LOGIC

        io.LogKind.registerNames();
        System.out.println("Starting Engine Window Test...");
        long windowPtr = Window.allocate("Engine", 800, 600);
        System.out.println("Window spawned at: " + windowPtr);
        Window.setTargetFps(60);
        Window.setUndecorated(windowPtr, Window.DECORATED);
        Window.show(windowPtr);

        // Listen-only system-wide key telemetry (macOS Input Monitoring permission).
        // Captures key presses from every application while this app is open; the
        // off-heap log is dumped to stdout at teardown and never leaves the machine.
        Window.setKeyTelemetry(true);

        // FIFO: the Core Draw Worker presents at the WindowServer's refresh (60/120Hz).
        // Override via -Danti.present=fifo|mailbox|immediate|-1 (auto) for headless testing.
        int bootPresentMode;
        String bootMode = System.getProperty("anti.present", "fifo").toLowerCase();
        switch (bootMode) {
            case "mailbox" -> bootPresentMode = org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
            case "immediate" -> bootPresentMode = org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
            case "auto" -> bootPresentMode = -1;
            default -> bootPresentMode = VK_PRESENT_MODE_FIFO_KHR;
        }
        Window.setFpsMode(windowPtr, bootPresentMode);

        System.out.println("Metal device available to this process: " + Window.isMetalDeviceAvailable());
        long surfacePtr = Window.createSurface(windowPtr);
        System.out.println("Surface created at (CAMetalLayer): " + surfacePtr);



        // Setup vulkan (which inherently spawns the DrawThread and binds to the surface)
        vulkan.Vulkan.initVulkan(surfacePtr, 800, 600, bootPresentMode);
        vulkan.TriangleRenderer.init();

        // @Draft texture-on-Panel demo (pending review): place a picture panel and
        // load sunflower.png into it so texturing can be assessed in one run.
        long picturePanel = darling.Panel.allocate();
        darling.Container.setX(picturePanel, 100f);
        darling.Container.setY(picturePanel, 60f);
        darling.Panel.setSize(picturePanel, 320f, 211f);
        vulkan.TriangleRenderer.loadPicture(
                System.getProperty("anti.picture", "/Users/vexgraph/Downloads/sunflower.png"),
                picturePanel, 2048);

        System.out.println("[Main Thread] Handing over control to the event pump...");

        // Boot the Core Draw Worker: it drains the input RingBuffers and owns the
        // render loop (produceOnce + presentOnce), paced by the FIFO swapchain.
        long coreDrawWorker = thread.DrawThread.getCoreWorker();
        thread.DrawThread.bindWindow(coreDrawWorker, windowPtr);
        thread.DrawThread.run(coreDrawWorker);

        // Trap Thread 0 in the OS event pump. It spins freely, feeding the input
        // RingBuffers, and never sleeps / never touches Vulkan.
        Window.run(windowPtr, () -> {
            // Spin freely!
        });

        // Window closed: start background teardown to prevent main-thread freeze.
        // AppKit requires the main thread to remain unblocked to play fullscreen exit animations.
        System.out.println("Test complete. Tearing down Vulkan in background...");
        java.util.concurrent.atomic.AtomicBoolean teardownComplete = new java.util.concurrent.atomic.AtomicBoolean(false);
        new Thread(() -> {
            telemetry.KeyLog.dumpRecent(200);
            Window.setKeyTelemetry(false);
            thread.DrawThread.freeAllSystem();
            vulkan.TriangleRenderer.destroy();
            Window.free(windowPtr);
            nio.ForeignMemory.freeAllClasses();
            teardownComplete.set(true);
        }).start();

        // Keep pumping events on main thread so the fullscreen close animation plays smoothly
        while (!teardownComplete.get()) {
            window.Window.pollEvents();
            try { Thread.sleep(16); } catch (Exception ignored) {}
        }
        System.exit(0);
    }
}
