package scene;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import lang.Mat4;
import nio.ForeignMemory;
import primitive.Float;
import primitive.Int;

/**
 * Off-heap Figma-style scene: a flat index space of entities where every entity
 * carries the full metadata set, groups are entities with many-to-many membership,
 * and matrices are per-entity and updated in place at edit time. The renderer reads
 * only (matrix, mesh, object) columns -- it never walks hierarchy.
 *
 * <p>SOA of SOA: each metadata field is its own column; some columns (events,
 * filters, camera) hold pointers to per-entity sub-arrays (columns of columns).
 *
 * <p>Entity center (pivot): solo objects default to bottom-center, groups default
 * to center-middle (estimated from member positions unless explicitly set). A group
 * moving propagates its delta to every member's own matrix, about the group center.
 */
@Draft
@Intention("Scene = flat entity index space + many-to-many group DAG; hierarchy is edit-time, render is flat.")
public final class Scene
{
    // =====================================================================
    // Object types
    // =====================================================================
    @Required public static final int TYPE_OBJECT = 1;
    @Required public static final int TYPE_GROUP  = 2;
    @Required public static final int TYPE_CAMERA = 3;
    @Required public static final int TYPE_LIGHT  = 4;

    // =====================================================================
    // Options bitmask
    // =====================================================================
    public static final int OPT_VISIBLE  = 1;
    public static final int OPT_SELECTED = 2;
    public static final int OPT_LOCKED   = 4;

    // =====================================================================
    // Flags bitmask
    // =====================================================================
    public static final int FLAG_CENTER_SET = 1;

    // =====================================================================
    // Event codes (per-entity event lists)
    // =====================================================================
    public static final int EVENT_BEHAVIOR   = 1;
    public static final int EVENT_VISIBLE    = 2;
    public static final int EVENT_MOVED      = 3;
    public static final int EVENT_DESTROYED  = 4;
    public static final int EVENT_DELETED    = 5;
    public static final int EVENT_MOUSE      = 6;
    public static final int EVENT_FOCUS      = 7;
    public static final int EVENT_HELD       = 8;
    public static final int EVENT_CUSTOM     = 100;

    private static final int MATRICES_PER_ENTITY = 16;
    private static final int FLOATS_PER_CENTER   = 3;
    private static final int LINKS_PER_NODE      = 2;
    private static final int INITIAL_EVENT_CAP   = 4;
    private static final int INITIAL_LINK_POOL   = 1024;

    // ---------------------------------------------------------------------
    // Columns
    // ---------------------------------------------------------------------
    private static long matrixCol;      // Float array: capacity * 16 (one Mat4 per entity)
    private static long centerCol;      // Float array: capacity * 3  (center vector per entity)
    private static long typeCol;        // Int array: capacity
    private static long meshCol;        // Int array: capacity
    private static long optionsCol;     // Int array: capacity (bitmask)
    private static long flagsCol;       // Int array: capacity (bitmask)
    private static long eventHeadCol;   // Int matrix: capacity (pointer -> per-entity event Int array)
    private static long filterHeadCol;  // Int matrix: capacity (pointer -> per-entity filter Int array)
    private static long cameraPtrCol;   // Int matrix: capacity (pointer -> entity.Camera struct for CAMERA entities)

    // Group membership DAG: two linked lists per entity over a shared link pool.
    private static long groupHeadCol;   // Int array: entity -> head node of "groups I belong to"
    private static long memberHeadCol;  // Int array: group  -> head node of "members of this group"
    private static long linkPool;       // Int array: poolSize * 2, node k at [2k]=target [2k+1]=next
    private static int linkPoolSize;
    private static int linkPoolCount;
    private static int linkFreeHead = -1;

    // ---------------------------------------------------------------------
    // Entity id management (free-list recycle, mirrors EntityManager)
    // ---------------------------------------------------------------------
    private static long freeList;       // Int array: capacity, recycled entity ids
    private static int freeCount;

    private static int capacity;
    private static int entityCount;
    private static int activeCamera = -1;

    private Scene() {}

    // =====================================================================
    // Lifecycle
    // =====================================================================
    public static void init(int cap)
    {
        capacity = cap;
        entityCount = 0;
        freeCount = 0;
        activeCamera = -1;

        matrixCol       = Float.allocateArray(cap * MATRICES_PER_ENTITY);
        centerCol       = Float.allocateArray(cap * FLOATS_PER_CENTER);
        typeCol         = Int.allocateArray(cap);
        meshCol         = Int.allocateArray(cap);
        optionsCol      = Int.allocateArray(cap);
        flagsCol        = Int.allocateArray(cap);
        eventHeadCol    = Int.allocateMatrix(cap);
        filterHeadCol   = Int.allocateMatrix(cap);
        cameraPtrCol    = Int.allocateMatrix(cap);
        groupHeadCol    = Int.allocateArray(cap);
        memberHeadCol   = Int.allocateArray(cap);
        freeList        = Int.allocateArray(cap);

        for (int i = 0; i < cap; i++)
        {
            Int.set(groupHeadCol, i, -1);
            Int.set(memberHeadCol, i, -1);
        }
        for (int i = 0; i < cap; i++)
        {
            Mat4.identity(matrixCol + (long) i * MATRICES_PER_ENTITY * 4L);
        }

        initLinkPool(INITIAL_LINK_POOL);
    }

    private static void initLinkPool(int poolCap)
    {
        linkPool = Int.allocateArray(poolCap * LINKS_PER_NODE);
        linkPoolSize = poolCap;
        linkPoolCount = 0;
        linkFreeHead = -1;
        for (int k = 0; k < poolCap - 1; k++)
        {
            Int.set(linkPool, k * LINKS_PER_NODE + 1, k + 1);
        }
        Int.set(linkPool, (poolCap - 1) * LINKS_PER_NODE + 1, -1);
        linkFreeHead = 0;
    }

    private static void growLinkPool()
    {
        int newSize = linkPoolSize * 2;
        long newPool = Int.allocateArray(newSize * LINKS_PER_NODE);
        ForeignMemory.copy(linkPool, newPool, (long) linkPoolSize * LINKS_PER_NODE * 4L);
        Int.free(linkPool);
        linkPool = newPool;
        for (int k = linkPoolSize; k < newSize - 1; k++)
        {
            Int.set(linkPool, k * LINKS_PER_NODE + 1, k + 1);
        }
        Int.set(linkPool, (newSize - 1) * LINKS_PER_NODE + 1, -1);
        linkFreeHead = linkPoolSize;
        linkPoolSize = newSize;
    }

    public static void freeAll()
    {
        Float.free(matrixCol);
        Float.free(centerCol);
        Int.free(typeCol);
        Int.free(meshCol);
        Int.free(optionsCol);
        Int.free(flagsCol);
        Int.free(eventHeadCol);
        Int.free(filterHeadCol);
        Int.free(cameraPtrCol);
        Int.free(groupHeadCol);
        Int.free(memberHeadCol);
        Int.free(linkPool);
        Int.free(freeList);
        capacity = 0;
    }

    // =====================================================================
    // Entity creation
    // =====================================================================
    public static int createEntity(int type)
    {
        int id;
        if (freeCount > 0)
        {
            freeCount--;
            id = Int.get(freeList, freeCount);
        }
        else
        {
            id = entityCount++;
        }

        Int.set(typeCol, id, type);
        Int.set(meshCol, id, 0);
        Int.set(optionsCol, id, OPT_VISIBLE);
        Int.set(flagsCol, id, 0);
        Int.setPointer(eventHeadCol, id, 0L);
        Int.setPointer(filterHeadCol, id, 0L);
        Int.setPointer(cameraPtrCol, id, 0L);
        Int.set(groupHeadCol, id, -1);
        Int.set(memberHeadCol, id, -1);

        Mat4.identity(matrixCol + (long) id * MATRICES_PER_ENTITY * 4L);

        // Pivot default: objects bottom-center (0,0,0); groups estimate center-middle lazily.
        Float.set(centerCol, id * FLOATS_PER_CENTER + 0, 0.0f);
        Float.set(centerCol, id * FLOATS_PER_CENTER + 1, 0.0f);
        Float.set(centerCol, id * FLOATS_PER_CENTER + 2, 0.0f);

        if (type == TYPE_CAMERA) activeCamera = id;
        return id;
    }

    public static void destroyEntity(int id)
    {
        // Detach from every group both directions.
        while (Int.get(groupHeadCol, id) != -1)
        {
            removeFromGroup(id, Int.get(linkPool, Int.get(groupHeadCol, id) * LINKS_PER_NODE));
        }
        if (Int.getPointer(eventHeadCol, id) != 0L) Int.free(Int.getPointer(eventHeadCol, id));
        if (Int.getPointer(filterHeadCol, id) != 0L) Int.free(Int.getPointer(filterHeadCol, id));
        if (Int.getPointer(cameraPtrCol, id) != 0L) Int.free(Int.getPointer(cameraPtrCol, id));

        Int.setPointer(eventHeadCol, id, 0L);
        Int.setPointer(filterHeadCol, id, 0L);
        Int.setPointer(cameraPtrCol, id, 0L);

        if (freeCount >= capacity) return;
        Int.set(freeList, freeCount++, id);
        if (activeCamera == id) activeCamera = -1;
    }

    // =====================================================================
    // Metadata accessors
    // =====================================================================
    public static long matrixPtr(int id)
    {
        return matrixCol + (long) id * MATRICES_PER_ENTITY * 4L;
    }

    public static int getType(int id) { return Int.get(typeCol, id); }
    public static void setType(int id, int type) { Int.set(typeCol, id, type); }

    public static int getMeshType(int id) { return Int.get(meshCol, id); }
    public static void setMeshType(int id, int mesh) { Int.set(meshCol, id, mesh); }

    public static int getOptions(int id) { return Int.get(optionsCol, id); }
    public static void setOptions(int id, int opts) { Int.set(optionsCol, id, opts); }
    public static boolean isVisible(int id) { return (Int.get(optionsCol, id) & OPT_VISIBLE) != 0; }
    public static void setVisible(int id, boolean v)
    {
        int o = Int.get(optionsCol, id);
        Int.set(optionsCol, id, v ? (o | OPT_VISIBLE) : (o & ~OPT_VISIBLE));
    }

    // ---------------------------------------------------------------------
    // Center / pivot. Objects default bottom-center; groups center-middle.
    // ---------------------------------------------------------------------
    public static float getCenterX(int id) { return Float.get(centerCol, id * FLOATS_PER_CENTER + 0); }
    public static float getCenterY(int id) { return Float.get(centerCol, id * FLOATS_PER_CENTER + 1); }
    public static float getCenterZ(int id) { return Float.get(centerCol, id * FLOATS_PER_CENTER + 2); }

    public static void setCenter(int id, float x, float y, float z)
    {
        Float.set(centerCol, id * FLOATS_PER_CENTER + 0, x);
        Float.set(centerCol, id * FLOATS_PER_CENTER + 1, y);
        Float.set(centerCol, id * FLOATS_PER_CENTER + 2, z);
        Int.set(flagsCol, id, Int.get(flagsCol, id) | FLAG_CENTER_SET);
    }

    public static boolean isCenterSet(int id) { return (Int.get(flagsCol, id) & FLAG_CENTER_SET) != 0; }

    // =====================================================================
    // Events (SOA of SOA: column of pointers to per-entity event Int arrays)
    // =====================================================================
    public static void addEvent(int id, int eventCode)
    {
        long list = Int.getPointer(eventHeadCol, id);
        if (list == 0L)
        {
            list = Int.allocateArray(INITIAL_EVENT_CAP);
            Int.setPointer(eventHeadCol, id, list);
            Int.set(list, 0, eventCode);
            return;
        }
        int len = Int.length(list);
        for (int i = 0; i < len; i++)
        {
            if (Int.get(list, i) == eventCode) return;
            if (Int.get(list, i) == 0)
            {
                Int.set(list, i, eventCode);
                return;
            }
        }
        list = Int.expandArray(list, len * 2);
        Int.setPointer(eventHeadCol, id, list);
        Int.set(list, len, eventCode);
    }

    public static boolean hasEvent(int id, int eventCode)
    {
        long list = Int.getPointer(eventHeadCol, id);
        if (list == 0L) return false;
        int len = Int.length(list);
        for (int i = 0; i < len; i++)
        {
            if (Int.get(list, i) == eventCode) return true;
        }
        return false;
    }

    public static int eventCount(int id)
    {
        long list = Int.getPointer(eventHeadCol, id);
        if (list == 0L) return 0;
        int len = Int.length(list), n = 0;
        for (int i = 0; i < len; i++) if (Int.get(list, i) != 0) n++;
        return n;
    }

    public static int getEvent(int id, int index)
    {
        long list = Int.getPointer(eventHeadCol, id);
        return (list == 0L) ? 0 : Int.get(list, index);
    }

    // =====================================================================
    // Group membership (many-to-many DAG)
    // =====================================================================
    public static void addToGroup(int entityId, int groupId)
    {
        if (entityId == groupId || isMemberOf(entityId, groupId)) return;

        int n = allocLink(groupId);
        Int.set(linkPool, n * LINKS_PER_NODE + 1, Int.get(groupHeadCol, entityId));
        Int.set(groupHeadCol, entityId, n);

        int m = allocLink(entityId);
        Int.set(linkPool, m * LINKS_PER_NODE + 1, Int.get(memberHeadCol, groupId));
        Int.set(memberHeadCol, groupId, m);

        if ((Int.get(flagsCol, groupId) & FLAG_CENTER_SET) == 0)
        {
            estimateGroupCenter(groupId);
        }
    }

    public static boolean isMemberOf(int entityId, int groupId)
    {
        int node = Int.get(groupHeadCol, entityId);
        while (node != -1)
        {
            if (Int.get(linkPool, node * LINKS_PER_NODE) == groupId) return true;
            node = Int.get(linkPool, node * LINKS_PER_NODE + 1);
        }
        return false;
    }

    public static void removeFromGroup(int entityId, int groupId)
    {
        unlink(groupHeadCol, entityId, groupId);
        unlink(memberHeadCol, groupId, entityId);
    }

    private static void unlink(long headCol, int ownerId, int targetId)
    {
        int prev = -1;
        int node = Int.get(headCol, ownerId);
        while (node != -1)
        {
            if (Int.get(linkPool, node * LINKS_PER_NODE) == targetId)
            {
                if (prev == -1) Int.set(headCol, ownerId, Int.get(linkPool, node * LINKS_PER_NODE + 1));
                else Int.set(linkPool, prev * LINKS_PER_NODE + 1, Int.get(linkPool, node * LINKS_PER_NODE + 1));
                freeLink(node);
                return;
            }
            prev = node;
            node = Int.get(linkPool, node * LINKS_PER_NODE + 1);
        }
    }

    public static int groupCount(int entityId)
    {
        int node = Int.get(groupHeadCol, entityId), n = 0;
        while (node != -1) { n++; node = Int.get(linkPool, node * LINKS_PER_NODE + 1); }
        return n;
    }

    public static int getGroup(int entityId, int index)
    {
        int node = Int.get(groupHeadCol, entityId), n = 0;
        while (node != -1)
        {
            if (n == index) return Int.get(linkPool, node * LINKS_PER_NODE);
            n++; node = Int.get(linkPool, node * LINKS_PER_NODE + 1);
        }
        return -1;
    }

    public static int memberCount(int groupId)
    {
        int node = Int.get(memberHeadCol, groupId), n = 0;
        while (node != -1) { n++; node = Int.get(linkPool, node * LINKS_PER_NODE + 1); }
        return n;
    }

    public static int getMember(int groupId, int index)
    {
        int node = Int.get(memberHeadCol, groupId), n = 0;
        while (node != -1)
        {
            if (n == index) return Int.get(linkPool, node * LINKS_PER_NODE);
            n++; node = Int.get(linkPool, node * LINKS_PER_NODE + 1);
        }
        return -1;
    }

    private static int allocLink(int target)
    {
        if (linkFreeHead == -1)
        {
            if (linkPoolCount >= linkPoolSize) growLinkPool();
            int k = linkPoolCount++;
            Int.set(linkPool, k * LINKS_PER_NODE, target);
            Int.set(linkPool, k * LINKS_PER_NODE + 1, -1);
            return k;
        }
        int k = linkFreeHead;
        linkFreeHead = Int.get(linkPool, k * LINKS_PER_NODE + 1);
        Int.set(linkPool, k * LINKS_PER_NODE, target);
        Int.set(linkPool, k * LINKS_PER_NODE + 1, -1);
        return k;
    }

    private static void freeLink(int k)
    {
        Int.set(linkPool, k * LINKS_PER_NODE + 1, linkFreeHead);
        linkFreeHead = k;
    }

    // =====================================================================
    // Group center estimation (center-middle from member world positions)
    // =====================================================================
    @Intention("Groups default pivot = center-middle, estimated from member matrix translations, unless explicitly set.")
    public static void estimateGroupCenter(int groupId)
    {
        int count = 0;
        float sx = 0.0f, sy = 0.0f, sz = 0.0f;
        int node = Int.get(memberHeadCol, groupId);
        while (node != -1)
        {
            int m = Int.get(linkPool, node * LINKS_PER_NODE);
            long mp = matrixPtr(m);
            sx += Mat4.getRaw(mp, 12);
            sy += Mat4.getRaw(mp, 13);
            sz += Mat4.getRaw(mp, 14);
            count++;
            node = Int.get(linkPool, node * LINKS_PER_NODE + 1);
        }
        if (count > 0)
        {
            Float.set(centerCol, groupId * FLOATS_PER_CENTER + 0, sx / count);
            Float.set(centerCol, groupId * FLOATS_PER_CENTER + 1, sy / count);
            Float.set(centerCol, groupId * FLOATS_PER_CENTER + 2, sz / count);
        }
    }

    // =====================================================================
    // Edit-time transform propagation: group moves -> every member's OWN
    // matrix updates in place, about the group center. Chains through nested
    // groups (each entity updated exactly once, same delta + center).
    // =====================================================================
    @Intention("Hierarchy is edit-time. propagateDelta() bakes the group's delta into each member's world matrix "
            + "about the group center. Render never traverses; it reads the matrix column.")
    public static void propagateDelta(int groupId, long deltaMat)
    {
        float cx = Float.get(centerCol, groupId * FLOATS_PER_CENTER + 0);
        float cy = Float.get(centerCol, groupId * FLOATS_PER_CENTER + 1);
        float cz = Float.get(centerCol, groupId * FLOATS_PER_CENTER + 2);
        walkMembers(groupId, deltaMat, cx, cy, cz);
    }

    private static void walkMembers(int groupId, long deltaMat, float cx, float cy, float cz)
    {
        int node = Int.get(memberHeadCol, groupId);
        while (node != -1)
        {
            int m = Int.get(linkPool, node * LINKS_PER_NODE);
            composeDeltaAboutCenter(m, deltaMat, cx, cy, cz);
            if (Int.get(typeCol, m) == TYPE_GROUP)
            {
                walkMembers(m, deltaMat, cx, cy, cz);
            }
            node = Int.get(linkPool, node * LINKS_PER_NODE + 1);
        }
    }

    @Intention("new = T(center) * delta * T(-center) * old. Applied in place to the member's own matrix.")
    private static void composeDeltaAboutCenter(int entityId, long deltaMat, float cx, float cy, float cz)
    {
        long m = matrixPtr(entityId);
        long t1 = Mat4.allocate();
        long t2 = Mat4.allocate();
        Mat4.translate(t1, m, -cx, -cy, -cz);
        Mat4.multiply(t2, deltaMat, t1);
        Mat4.translate(m, t2, cx, cy, cz);
        Mat4.free(t1);
        Mat4.free(t2);
    }

    // =====================================================================
    // Camera (an entity with camera metadata; setCamera selects the active one)
    // =====================================================================
    public static int getActiveCamera() { return activeCamera; }
    public static void setActiveCamera(int id) { activeCamera = id; }

    public static long getCameraStruct(int id) { return Int.getPointer(cameraPtrCol, id); }
    public static void setCameraStruct(int id, long camPtr) { Int.setPointer(cameraPtrCol, id, camPtr); }

    // =====================================================================
    // Counters
    // =====================================================================
    public static int getEntityCount() { return entityCount - freeCount; }
    public static int getCapacity() { return capacity; }
}
