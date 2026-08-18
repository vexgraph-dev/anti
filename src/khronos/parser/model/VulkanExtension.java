package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;
import java.util.List;

@Draft
@Intention("Primitive integer-ID model for Vulkan Extensions (0 java.lang.String references)")
public record VulkanExtension(
    int nameId,
    int number,
    int typeId,
    int authorId,
    int contactId,
    int platformId,
    int requiresId,
    int promotedToId,
    int deprecatedById,
    int[] requiredCommandIds,
    int[] requiredTypeIds,
    List<VulkanEnum> requiredEnums
) {}
