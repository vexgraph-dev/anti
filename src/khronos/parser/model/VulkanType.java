package khronos.parser.model;

public record VulkanType(
    String name,
    String category,
    String parent,
    String type,
    String alias,
    boolean isHandle,
    boolean isDispatchable,
    boolean isBasetype,
    boolean isBitmask,
    boolean isStruct,
    boolean isUnion,
    boolean isEnum,
    boolean isFuncpointer
) {}
