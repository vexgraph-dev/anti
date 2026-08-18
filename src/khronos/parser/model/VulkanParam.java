package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Primitive integer-ID model for Vulkan Command Parameters (0 java.lang.String references)")
public record VulkanParam(
    int nameId,
    int typeId,
    boolean isPointer,
    int pointerCount,
    boolean isConst,
    boolean isOptional,
    int lenId
) {}
