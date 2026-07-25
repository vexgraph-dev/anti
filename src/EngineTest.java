import hardware.Window;

void main() throws Throwable
{
    // 1. macOS TRAMPOLINE: Auto-relaunch with -XstartOnFirstThread if missing
    if(System.getProperty("os.name").toLowerCase().contains("mac")
            && !Boolean.getBoolean("mac.firstThread")) {

        IO.println("[Trampoline] Relaunching EngineTest with -XstartOnFirstThread...");

        ProcessBuilder pb = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-XstartOnFirstThread",
                "-Dmac.firstThread=true", // Mark that we've relaunched
                "-cp", System.getProperty("java.class.path"),
                "EngineTest"
        );

        pb.inheritIO();
        Process p = pb.start();
        System.exit(p.waitFor());
        return;
    }

    // 2. ACTUAL ENGINE LOGIC
    System.out.println("Starting Engine Window Test...");
    long windowPtr = Window.allocate();
    System.out.println("Window spawned at: " + windowPtr);

    Window.setSize(windowPtr, 800, 600);
    Window.show(windowPtr);

    long surfacePtr = Window.createSurface(windowPtr);
    System.out.println("Surface created at (CAMetalLayer): " + surfacePtr);

    // 3. START BACKGROUND GAME THREAD
    Thread gameThread = new Thread(() ->
    {
        System.out.println("[Game Thread] Booting up 60 FPS logic loop...");
        while(!Window.shouldClose(windowPtr)) {
            // TODO: Add your Vulkan/Metal rendering and game logic here!
            try {
                Thread.sleep(16); // Simulate 60 FPS tick
            }
            catch(InterruptedException e) {
                break;
            }
        }
        System.out.println("[Game Thread] Shutting down...");
    });
    gameThread.start();

    // 4. MAIN THREAD: DEDICATED EVENT PUMP
    System.out.println("[Main Thread] Pumping macOS events...");
    while(!Window.shouldClose(windowPtr)) {
        Window.pollEvents();

        // We sleep just 2ms. This prevents the while-loop from burning 100% CPU,
        // but is fast enough that the macOS WindowServer (and the Dock) remain buttery smooth!
        try {
            Thread.sleep(2);
        }
        catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    Window.free(windowPtr);
    System.out.println("Test complete.");
}
