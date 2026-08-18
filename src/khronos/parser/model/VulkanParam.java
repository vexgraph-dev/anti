package khronos.parser.model;

public record VulkanParam(
    String name,
    String type,
    String fullDeclaration,
    boolean isPointer,
    int pointerCount,
    boolean isConst,
    boolean isOptional,
    String len
) {}
