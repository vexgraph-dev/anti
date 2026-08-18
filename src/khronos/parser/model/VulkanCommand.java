package khronos.parser.model;

import java.util.List;

public record VulkanCommand(
    String name,
    String returnType,
    List<VulkanParam> params,
    String aliasOf,
    String successCodes,
    String errorCodes,
    String queues,
    String renderPassScope,
    String cmdbufferLevel,
    boolean isInstanceLevel,
    boolean isDeviceLevel,
    boolean isGlobalLevel
) {
    public boolean isAlias() {
        return aliasOf != null && !aliasOf.isEmpty();
    }
}
