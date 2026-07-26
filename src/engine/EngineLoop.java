package engine;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Functional interface for zero-allocation engine ticks")
@FunctionalInterface
public interface EngineLoop {
    void tick();
}
