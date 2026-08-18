package khronos.parser.model;

import java.util.List;

public record VulkanExtension(
    String name,
    String number,
    String type,
    String author,
    String contact,
    String platform,
    String requires,
    String promotedTo,
    String deprecatedBy,
    List<String> requiredCommands,
    List<String> requiredTypes,
    List<VulkanEnum> requiredEnums
) {}
