package event;

@FunctionalInterface
public interface HardwareEvent
{
    long runEvent(Resolve resolve);
}
