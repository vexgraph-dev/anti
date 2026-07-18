package nio;

import nio.NativeSubsystem;
import java.util.concurrent.CopyOnWriteArrayList;

public class MemoryRegistry {
    private static final CopyOnWriteArrayList<NativeSubsystem> subsystems = new CopyOnWriteArrayList<>();

    public static void register(NativeSubsystem subsystem) {
        subsystems.add(subsystem);
    }

    public static void shutdownAll() {
        for (NativeSubsystem sub : subsystems) {
            sub.freeAll();
        }
        subsystems.clear();
    }
}