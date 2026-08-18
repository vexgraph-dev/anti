package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Primitive integer-ID model for Vulkan Enums (0 java.lang.String references)")
public record VulkanEnum(
    int nameId,
    long numericValue,
    int commentId,
    boolean isAlias,
    int aliasOfId,
    int extendsEnumId,
    int extNumber,
    int offset,
    boolean isNegative
) {}
