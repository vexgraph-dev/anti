package scene;

import annotation.Draft;
import annotation.Intention;

/**
 * SCENE role recorder. Receives scene tasks drained from the SCENE worker queue
 * each frame and records them into the current merged command buffer. Scenes
 * (gradient rect, sprites, geometry) never touch Vulkan themselves.
 */
@Draft
@Intention("Scene worker role recorder: scene ops enter the frame's single merged command buffer.")
public final class SceneRole {

    private SceneRole() {}

    public static void record(long taskPtr) {
        // TODO: append scene task to the current frame's command buffer. Structure only.
    }
}