package process;

import darling.Panel;
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
        int bootW = Integer.parseInt(System.getProperty("anti.w", "800"));
        int bootH = Integer.parseInt(System.getProperty("anti.h", "600"));
        long windowPtr = Window.allocate("Engine", bootW, bootH);
        Window.setResolutionType(windowPtr, Window.MACOS_RETINA_RES);
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
        vulkan.Vulkan.initVulkan(surfacePtr, bootW, bootH, bootPresentMode);

        // Flat 2D layout space (darling.Canvas): UI resolves directly in window pixels.
        // Virtual size 0 <= follows window framebuffer extent directly.
        darling.Canvas.setVirtualSize(0f, 0f);
        darling.Canvas.setMode(darling.Canvas.MODE_PIXEL);
        System.out.println("[EngineTest] UI canvas follows window dimensions (mode=pixel)");

        // 3D Scene container: an off-heap Scene3D node positioned in the canvas.
        // The 3D render pipeline renders into this container's bounds (anchored top-left).
        long scene3DPtr = darling.Scene3D.allocate();
        darling.Scene3D.setPos(scene3DPtr, 0f, 0f);
        darling.Scene3D.setSize(scene3DPtr, 800f, 600f);
        darling.Scene3D.setBackgroundColor(scene3DPtr, 0xFF141414);
        vulkan.TriangleRenderer.setScene3D(scene3DPtr);
        vulkan.TriangleRenderer.setWindow(windowPtr);

        // Test UI Panel setup:
        // panel1 (parent): 200x200, location (30,30). The two anchors are modular:
        //   SELF anchor (default TOP_LEFT) = INITIAL POSITION only -> sits at the
        //     top-left corner, 30px margins, always fully visible.
        //   PARENT anchor BOTTOM_RIGHT = RESIZE TRACKING only -> when the window is
        //     resized at the bottom-right, panel1 moves by exactly that delta.
        long panel1 = Panel.allocate();
        Panel.setSize(panel1, 600f, 600f);
        Panel.setLocation(panel1, 30f, 30f);
        Panel.setParentAnchor(panel1, Panel.PARENT_ANCHOR_BOTTOM_RIGHT);
        Panel.setSelfAnchor(panel1, Panel.SELF_ANCHOR_TOP_LEFT);
        Panel.setBackgroundColor(panel1, 30, 41, 59, 200);
        Panel.setClipChildren(panel1, true);

        // panel2 (child): 200x200, location (100, 100), full opaque background (alpha=255)
        long panel2 = Panel.allocate();
        Panel.setSize(panel2, 200f, 200f);
        Panel.setLocation(panel2, 100f, 100f);
        Panel.setBackgroundColor(panel2, 16, 185, 129, 255);

        Panel.add(panel1, panel2);
        vulkan.TriangleRenderer.setRootUi(panel1);

        vulkan.TriangleRenderer.init();

        // @Draft picture demo (pending review): Image asset + darling.Picture node.
        // Width/height AUTO (-1) derive from the image; here width is fixed and
        // height AUTO keeps the sunflower's aspect ratio.
        String picturePath = System.getProperty("anti.picture");
        if (picturePath == null || !java.nio.file.Files.exists(java.nio.file.Path.of(picturePath))) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of("/Users/vexgraph/Downloads/sunflower.png"))) {
                picturePath = "/Users/vexgraph/Downloads/sunflower.png";
            } else if (java.nio.file.Files.exists(java.nio.file.Path.of("clipboard.png"))) {
                picturePath = "clipboard.png";
            } else if (java.nio.file.Files.exists(java.nio.file.Path.of("/Users/vexgraph/IdeaProjects/anti/clipboard.png"))) {
                picturePath = "/Users/vexgraph/IdeaProjects/anti/clipboard.png";
            }
        }
        if (picturePath != null && java.nio.file.Files.exists(java.nio.file.Path.of(picturePath))) {
            try {
                long imagePtr = image.Image.allocate(picturePath, 2048);
                long picturePtr = darling.Picture.allocate();
                darling.Picture.setImage(picturePtr, imagePtr);
                darling.Picture.setWidth(picturePtr, 320f);
                darling.Picture.setHeight(picturePtr, darling.Picture.AUTO);
                darling.Picture.setLocation(picturePtr, 100f, 60f);
                vulkan.TriangleRenderer.setPicture(picturePtr);
            } catch (Throwable t) {
                System.err.println("[EngineTest] Optional demo picture load skipped: " + t.getMessage());
            }
        }

        System.out.println("[Main Thread] Handing over control to the event pump...");

        // Boot the Event Dispatcher Worker: owns Key, Mouse, Touch event dispatching and UI logic
        thread.EventThread.start();

        // Boot the Core Draw Worker: owns pure GPU command buffer recording (produceOnce)
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
