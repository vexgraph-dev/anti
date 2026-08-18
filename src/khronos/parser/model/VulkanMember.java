package khronos.parser.model;

public record VulkanMember(
    String name,
    String type,
    String fullDeclaration,
    boolean isPointer,
    int pointerCount,
    boolean isConst,
    boolean isArray,
    int arraySize,
    String arraySizeEnum,
    int byteOffset,
    int byteSize,
    int alignment,
    String values,
    String len
) {}
