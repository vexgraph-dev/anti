package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;
import java.util.List;

@Draft
@Intention("Primitive integer-ID model for Vulkan Commands and Function Pointers (0 java.lang.String references)")
public record VulkanCommand(
    int nameId,
    int returnTypeId,
    List<VulkanParam> params,
    int aliasOfId,
    int successCodesId,
    int errorCodesId,
    int queuesId,
    int renderPassScopeId,
    int cmdbufferLevelId,
    boolean isInstanceLevel,
    boolean isDeviceLevel,
    boolean isGlobalLevel
) {
    public boolean isAlias() {
        return aliasOfId > 0;
    }
}
