package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Primitive integer-ID model for Vulkan Struct Members (0 java.lang.String references)")
public record VulkanMember(
    int nameId,
    int typeId,
    boolean isPointer,
    int pointerCount,
    boolean isConst,
    boolean isArray,
    int arraySize,
    int arrayEnumId,
    int byteOffset,
    int byteSize,
    int alignment,
    int valuesId,
    int lenId
) {}
