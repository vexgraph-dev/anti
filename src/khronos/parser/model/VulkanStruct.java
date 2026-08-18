package khronos.parser.model;

import java.util.List;

public record VulkanStruct(
    String name,
    boolean isUnion,
    String aliasOf,
    String structExtends,
    List<VulkanMember> members,
    int totalSize,
    int alignment
) {
    public boolean isAlias() {
        return aliasOf != null && !aliasOf.isEmpty();
    }
}
