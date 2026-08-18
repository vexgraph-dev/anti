package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Primitive integer-ID model for Vulkan Types (0 java.lang.String references)")
public record VulkanType(
    int nameId,
    int categoryId,
    int parentId,
    int aliasId,
    boolean isHandle,
    boolean isDispatchable,
    boolean isBasetype,
    boolean isBitmask,
    boolean isStruct,
    boolean isUnion,
    boolean isEnum,
    boolean isFuncpointer
) {}
