package khronos.parser.model;

import annotation.Draft;
import annotation.Intention;
import java.util.List;

@Draft
@Intention("Primitive integer-ID model for Vulkan Structs and Unions (0 java.lang.String references)")
public record VulkanStruct(
    int nameId,
    boolean isUnion,
    int aliasOfId,
    int structExtendsId,
    List<VulkanMember> members,
    int totalSize,
    int alignment
) {
    public boolean isAlias() {
        return aliasOfId > 0;
    }
}
