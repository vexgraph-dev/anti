// main.c — the demo tying the C11 subsystems together.
//
// Story: 4 producer threads race to push 25 jobs each into a shared MPMC ring.
// The engine loop (a separate fixed-timestep loop) drains the ring and counts
// every job. When it has seen all 100, it stops the loop and the program ends.
//
// This exercises, in one file:
//   - Memory   : every block knows its own type + length
//   - BitPool   : lockless pool, slot gets recycled (same address back)
//   - RingBuffer  : MPMC queue with spinlock coordination
//   - Loop  : the while(running){tick} engine loop
//   - SpinLock  : used inside the ring

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bit/bit.h"
#include "buffers/color_buffer.h"
#include "buffers/depth_buffer.h"
#include "buffers/frame_buffer.h"
#include "buffers/normal_buffer.h"
#include "cli/command.h"
#include "cli/commandparser.h"
#include "cli/commandregistry.h"
#include "cli/console.h"
#include "cli/logcommands.h"
#include "cli/scanner.h"
#include "engine/loop.h"
#include "io/antihome.h"
#include "io/file.h"
#include "io/filewriter.h"
#include "io/log.h"
#include "io/logkind.h"
#include "io/logparser.h"
#include "lang/fastmath.h"
#include "lang/mat4.h"
#include "lang/vec2.h"
#include "lang/vec3.h"
#include "lang/vec4.h"
#include "nio/mem.h"
#include "objects/choice.h"
#include "objects/future.h"
#include "objects/global.h"
#include "objects/local.h"
#include "objects/passive.h"
#include "objects/probable.h"
#include "objects/probable_objects.h"
#include "objects/reactive.h"
#include "oop/stride.h"
#include "oop/struct.h"
#include "oop/type.h"
#include "primitive/string.h"
#include "relational/variable.h"
#include "struct/array.h"
#include "struct/deque.h"
#include "struct/list.h"
#include "struct/map.h"
#include "struct/minheap.h"
#include "struct/queue.h"
#include "struct/set.h"
#include "struct/sparseset.h"
#include "struct/stack.h"
#include "atomic/ring.h"
#include "atomic/spin.h"
#include "util/arrays.h"
#include "util/hash.h"
#include "util/random.h"

#define N_THREADS 4
#define N_PUSH 25
#define RING_CAP 16

typedef struct job {
    unsigned int from;
    unsigned int seq;
} job_t;

typedef struct producer_ctx {
    RingBuffer *ring;
    uint32_t id;
} producer_ctx_t;

typedef struct engine_ctx {
    RingBuffer *ring;
    Loop loop;
    uint32_t received;
    int ticks;
} engine_ctx_t;

static void *producer_main(void *arg) {
    producer_ctx_t *ctx = (producer_ctx_t *)arg;
    for (uint32_t i = 0; i < N_PUSH; i++) {
        job_t job = { .from = (*ctx).id, .seq = i };
        while (!RingBuffer_push((*ctx).ring, &job)) {
            // Ring full: spin. The engine loop drains it on its next tick, so
            // this eventually succeeds — it's a bounded wait, not a deadlock.
        }
    }
    return nullptr;
}

// LogParser record callback: prints each record formatted against the first.
static void log_print_handler(void *userdata, int kind, int64_t ts,
                              int64_t v0, int64_t v1, int64_t v2,
                              int64_t v3, int64_t v4) {
    int64_t *base = (int64_t *)userdata;
    if (*base == 0)
        *base = ts;
    char line[128];
    if (LogParser_formatRecord(line, sizeof(line), kind, ts, *base,
                               LogParser_kindName(kind), v0, v1, v2, v3, v4) >= 0)
        printf("  %s\n", line);
}

// Console session state shared with the registered "quit" command.
static bool console_running = false;

// Registered command target: quits the scripted console session.
static void on_quit_command(Command *command) {
    (void)command;
    console_running = false;
}

static void on_reactive_change(uint64_t oldVal, uint64_t newVal, void *userdata) {
    (void)userdata;
    printf("reactive changed: %llu -> %llu\n", (unsigned long long)oldVal, (unsigned long long)newVal);
}

static uint64_t passive_lazy_calc(void *userdata) {
    int *base = (int *)userdata;
    return (uint64_t)((*base) * 2);
}

// Called by the engine loop at a fixed timestep. Drains everything the
// producers pushed and stops the loop once the expected total has arrived.
static void engine_tick(void *userdata) {
    engine_ctx_t *ctx = (engine_ctx_t *)userdata;

    (*ctx).ticks++;

    job_t job;
    while (RingBuffer_pop((*ctx).ring, &job)) {
        printf("tick=%d  job from=%u seq=%u\n", (*ctx).ticks, job.from, job.seq);
        (*ctx).received++;
    }

    if ((*ctx).received >= N_THREADS * N_PUSH) {
        Loop_stop(&(*ctx).loop);
    }
}

int main(void) {
    // Memory: allocate a typed block, prove the header round-trips.
    printf("== anti memory ==\n");
    void *blk = Memory_alloc(TYPE_INT_ARRAY, 4 * sizeof(int32_t));
    printf("type=0x%08X len=%zu\n", Memory_type(blk), Memory_length(blk));
    Memory_free(blk);

    // FastMath: fast 32-bit approximations and bitwise ops.
    printf("== anti lang: FastMath ==\n");
    printf("abs(-3.5)=%.4f absInt(-7)=%d round(2.4)=%.1f round(2.6)=%.1f\n",
           (double)FastMath_abs(-3.5f), FastMath_absInt(-7),
           (double)FastMath_round(2.4f), (double)FastMath_round(2.6f));
    printf("sin32(0)=%.4f cos32(0)=%.4f tan32(0)=%.4f\n",
           (double)FastMath_sin32(0.0f), (double)FastMath_cos32(0.0f),
           (double)FastMath_tan32(0.0f));
    printf("sin32(PI/2)=%.4f cos32(PI/2)=%.4f\n",
           (double)FastMath_sin32(FastMath_HALF_PI),
           (double)FastMath_cos32(FastMath_HALF_PI));
    printf("invSqrt(4)=%.4f pow(2,10)=%.1f\n", (double)FastMath_invSqrt(4.0f),
           (double)FastMath_pow(2.0f, 10.0f));
    printf("toRadians(180)=%.4f toDegrees(PI)=%.4f clamp(5,1,3)=%.1f cosFromSin(0.5,PI/2)=%.4f\n",
           (double)FastMath_toRadians(180.0f), (double)FastMath_toDegrees(FastMath_PI),
           (double)FastMath_clamp(5.0f, 1.0f, 3.0f),
           (double)FastMath_cosFromSin(0.5f, FastMath_HALF_PI));

    // File: off-heap file handle (stdio-backed).
    printf("== anti io: File ==\n");
    const char *demo_path = "/tmp/anti_demo.bin";
    File_delete(demo_path);
    File *f = File_open(demo_path, FILE_MODE_WRITE | FILE_MODE_CREATE | FILE_MODE_TRUNCATE);
    const char greeting[] = "anti zero-alloc";
    File_write(f, greeting, (int64_t)(sizeof(greeting) - 1));
    printf("wrote %lld bytes size=%lld pos=%lld\n",
           (long long)(sizeof(greeting) - 1), (long long)File_size(f),
           (long long)File_pos(f));
    File_close(f);

    f = File_open(demo_path, FILE_MODE_READ);
    char readbuf[64];
    int64_t n = File_read(f, readbuf, (int64_t)(sizeof(readbuf) - 1));
    readbuf[n] = '\0';
    printf("read=%lld text=\"%s\" eof=%d exists=%d\n", (long long)n, readbuf,
           File_eof(f), File_exists(demo_path));
    File_seek(f, 0);
    printf("seek0 pos=%lld\n", (long long)File_pos(f));
    File_close(f);
    File_delete(demo_path);

    // AntiHome: per-user ~/anti layout.
    printf("== anti io: AntiHome ==\n");
    printf("ensure=%d root=%s\n", AntiHome_ensure(), AntiHome_root());
    printf("logs=%s projects=%s\n", AntiHome_logs(), AntiHome_projects());
    printf("defaultLog=%s\n", AntiHome_defaultLogPath());

    // FileWriter: buffered binary writer.
    printf("== anti io: FileWriter ==\n");
    FileWriter w;
    if (FileWriter_open(&w, "/tmp/anti_demo_w.bin")) {
        uint8_t data[3] = { 0x41, 0x4E, 0x54 };
        FileWriter_write(&w, data, 3);
        FileWriter_flush(&w);
        printf("open=%d bytes=%llu\n", w.open,
               (unsigned long long)FileWriter_bytesWritten(&w));
        FileWriter_close(&w);
    } else {
        printf("open=failed\n");
    }
    File_delete("/tmp/anti_demo_w.bin");

    // Log: lockless MPSC ring logger with a writer daemon.
    printf("== anti io: Log ==\n");
    const char *log_path = "/tmp/anti_demo.log";
    File_delete(log_path);
    Log log;
    if (Log_init(&log, log_path, 1 << 8)) {
        Log_append(&log, LOG_KIND_KEY_DOWN, 32, 123456789, 0, 0, 0);
        Log_append(&log, LOG_KIND_MOUSE_MOVE, 0, 320 * 1000, 240 * 1000, 0, 0);
        Log_append(&log, LOG_KIND_RENDER_PRODUCE, 3, 12, 0, 0, 0);
        Log_appendKind(&log, LOG_KIND_TOUCH_UP);
        Log_shutdown(&log);
        printf("enabled=%d path=%s\n", Log_isEnabled(&log), Log_path(&log));
        printf("appended=%llu dropped=%llu written=%llu\n",
               (unsigned long long)Log_appended(&log),
               (unsigned long long)Log_dropped(&log),
               (unsigned long long)Log_written(&log));
        File *lf = File_open(log_path, FILE_MODE_READ);
        if (lf) {
            printf("logfile bytes=%lld (header=%d + 4x52=%d)\n",
                   (long long)File_size(lf), LOG_HEADER_BYTES, 4 * LOG_RECORD_BYTES);
            File_close(lf);
        }
        printf("isLogFile=%d count=%lld\n", LogParser_isLogFile(log_path),
               (long long)LogParser_count(log_path));
        printf("parsed records:\n");
        int64_t base_ts = 0;
        (void)LogParser_parse(log_path, log_print_handler, &base_ts);
        File_delete(log_path);
    } else {
        printf("init failed\n");
    }

    // Console: ring-backed string sink, drained later by the console loop.
    printf("== anti cli: Console ==\n");
    Console_init();
    Console_log("hello from the engine console");
    Console_log("a second queued message");
    Console_drain();
    Console_shutdown();

    // Console session: Scanner + CommandParser + CommandRegistry over a script.
    printf("== anti cli: Console session ==\n");
    const char *cmd_log = "/tmp/anti_cmd.log";
    const char *cmd_script = "/tmp/anti_script.txt";
    File_delete(cmd_log);
    Log clog;
    if (Log_init(&clog, cmd_log, 1 << 6)) {
        Log_append(&clog, LOG_KIND_KEY_DOWN, 65, 1111, 0, 0, 0);
        Log_append(&clog, LOG_KIND_MOUSE_DOWN, 0, 500, 300, 0, 0);
        Log_appendKind(&clog, LOG_KIND_RENDER_PRESENT);
        Log_shutdown(&clog);
    }
    File *csf = File_open(cmd_script, FILE_MODE_WRITE | FILE_MODE_TRUNCATE);
    if (csf) {
        const char *lines =
            "log /tmp/anti_cmd.log\n"
            "cat /tmp/anti_cmd.log\n"
            "bogus 1 2\n"
            "quit\n";
        File_write(csf, lines, (int64_t)strlen(lines));
        File_close(csf);
    }
    FILE *console_script = fopen(cmd_script, "r");
    if (console_script) {
        stdin = console_script;
        console_running = true;
        CommandRegistry_register("quit", on_quit_command);
        while (console_running && Scanner_hasNextLine()) {
            uint8_t *line = Scanner_nextLine();
            if (!line)
                continue;
            Command *cmd = CommandParser_parse(string_get(line));
            if (cmd) {
                uint8_t *name = Command_name(cmd);
                if (string_equals(name, "log") && Command_argumentCount(cmd) >= 1)
                    LogCommands_stat(string_get(Command_argument(cmd, 0)));
                else if (string_equals(name, "cat") && Command_argumentCount(cmd) >= 1) {
                    int limit = Command_argumentCount(cmd) >= 2
                        ? atoi(string_get(Command_argument(cmd, 1)))
                        : -1;
                    LogCommands_cat(string_get(Command_argument(cmd, 0)), limit);
                } else {
                    CommandRegistry_execute(cmd);
                }
                Command_free(cmd);
            }
            string_free(line);
        }
        CommandRegistry_free();
        fclose(console_script);
    }
    File_delete(cmd_script);
    File_delete(cmd_log);

    // Vec2: off-heap 2D vector ops.
    printf("== anti lang: Vec2 ==\n");
    Vec2 *va = Vec2_allocateXY(3.0f, 4.0f);
    Vec2 *vb = Vec2_allocateXY(1.0f, 2.0f);
    Vec2 tmp;
    Vec2_add(va, vb, &tmp);
    printf("add=(%.1f,%.1f) dot=%f len=%.3f\n", (double)tmp.x, (double)tmp.y,
           (double)Vec2_dot(va, vb), (double)Vec2_length(va));
    Vec2_normalize(va, &tmp);
    printf("normalized=(%.4f,%.4f) dist=%.3f\n", (double)tmp.x, (double)tmp.y,
           (double)Vec2_distance(va, vb));
    Vec2_perpendicular(va, &tmp);
    printf("perp=(%.1f,%.1f)\n", (double)tmp.x, (double)tmp.y);
    Vec2_lerp(va, vb, 0.5f, &tmp);
    printf("lerp=(%.1f,%.1f) angle=%.3f\n", (double)tmp.x, (double)tmp.y,
           (double)Vec2_angle(va, vb));
    Vec2_free(va);
    Vec2_free(vb);

    // Vec3: off-heap 3D vector ops (cross, normalize, reflect).
    printf("== anti lang: Vec3 ==\n");
    Vec3 *v3a = Vec3_allocateXYZ(1.0f, 0.0f, 0.0f);
    Vec3 *v3b = Vec3_allocateXYZ(0.0f, 1.0f, 0.0f);
    Vec3 r3;
    Vec3_cross(v3a, v3b, &r3);
    printf("cross=(%.1f,%.1f,%.1f) dot=%f len=%f\n", (double)r3.x, (double)r3.y,
           (double)r3.z, (double)Vec3_dot(v3a, v3b), (double)Vec3_length(v3a));
    Vec3 *v3c = Vec3_allocateXYZ(3.0f, 4.0f, 0.0f);
    Vec3_fastNormalize(v3c, &r3);
    printf("fastNormalized=(%.4f,%.4f,%.4f)\n", (double)r3.x, (double)r3.y,
           (double)r3.z);
    Vec3 *normal = Vec3_allocateXYZ(0.0f, 1.0f, 0.0f);
    Vec3 *incident = Vec3_allocateXYZ(1.0f, -1.0f, 0.0f);
    Vec3_reflect(incident, normal, &r3);
    printf("reflect=(%.1f,%.1f,%.1f)\n", (double)r3.x, (double)r3.y, (double)r3.z);
    Vec3_free(v3a);
    Vec3_free(v3b);
    Vec3_free(v3c);
    Vec3_free(normal);
    Vec3_free(incident);

    // Vec4: off-heap 4D vector ops.
    printf("== anti lang: Vec4 ==\n");
    Vec4 *v4a = Vec4_allocateXYZW(1.0f, 2.0f, 3.0f, 4.0f);
    Vec4 *v4b = Vec4_allocateXYZW(2.0f, 0.0f, 0.0f, 1.0f);
    Vec4 r4;
    Vec4_add(v4a, v4b, &r4);
    printf("add=(%.1f,%.1f,%.1f,%.1f) dot=%f len=%.3f\n", (double)r4.x,
           (double)r4.y, (double)r4.z, (double)r4.w,
           (double)Vec4_dot(v4a, v4b), (double)Vec4_length(v4a));
    Vec4_normalize(v4a, &r4);
    printf("normalized=(%.4f,%.4f,%.4f,%.4f)\n", (double)r4.x, (double)r4.y,
           (double)r4.z, (double)r4.w);
    Vec4_free(v4a);
    Vec4_free(v4b);

    // Mat4: column-major 4x4 transforms.
    printf("== anti lang: Mat4 ==\n");
    Mat4 *m = Mat4_allocate();
    Mat4 *mi = Mat4_allocateIdentity();
    printf("identity m00=%f m11=%f m33=%f m30=%f\n", (double)Mat4_get(m, 0, 0),
           (double)Mat4_get(m, 1, 1), (double)Mat4_get(m, 3, 3),
           (double)Mat4_get(m, 3, 0));
    Mat4_translate(mi, 10.0f, 20.0f, 30.0f, m);
    Vec3 *pt3 = Vec3_allocateXYZ(1.0f, 2.0f, 3.0f);
    Vec3 out;
    Mat4_transformVec3(m, pt3, &out);
    printf("translate(10,20,30) * (1,2,3) = (%.1f,%.1f,%.1f)\n", (double)out.x,
           (double)out.y, (double)out.z);
    Mat4 *view = Mat4_allocate();
    Mat4_createViewMatrix(0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, view);
    printf("view[2][2]=%f view[3][2]=%f\n", (double)Mat4_get(view, 2, 2),
           (double)Mat4_get(view, 3, 2));
    Mat4 *proj = Mat4_allocate();
    Mat4_perspective(FastMath_HALF_PI, 16.0f / 9.0f, 0.1f, 100.0f, proj);
    printf("proj[0][0]=%f proj[3][2]=%f\n", (double)Mat4_get(proj, 0, 0),
           (double)Mat4_get(proj, 3, 2));
    Mat4 *trs = Mat4_allocate();
    Mat4_createTransformationMatrix(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 90.0f, 1.0f, 1.0f, 1.0f, trs);
    printf("rotZ90 m00=%f m10=%f m01=%f m11=%f\n", (double)Mat4_get(trs, 0, 0),
           (double)Mat4_get(trs, 1, 0), (double)Mat4_get(trs, 0, 1),
           (double)Mat4_get(trs, 1, 1));
    Mat4_free(m);
    Mat4_free(mi);
    Mat4_free(view);
    Mat4_free(proj);
    Mat4_free(trs);
    Vec3_free(pt3);

    // BitPool: allocation a, free a, allocate again -> the SAME address comes back.
    printf("== anti bit pool ==\n");
    BitPool pool;
    BitPool_init(&pool, 8, 4);
    void *a = BitPool_alloc(&pool, TYPE_INT_SINGLETON);
    void *b = BitPool_alloc(&pool, TYPE_INT_SINGLETON);
    printf("a=%p b=%p\n", a, b);
    BitPool_free(&pool, a);
    void *c = BitPool_alloc(&pool, TYPE_INT_SINGLETON);
    printf("recycled a -> c=%p (same=%d)\n", c, c == a);
    BitPool_shutdown(&pool);

    // Variable: relational symbol registry — every name maps to a typed pointer.
    printf("== anti relational: Variable ==\n");
    Variable vars;
    Variable_init(&vars);

    void *score = Memory_alloc(TYPE_INT_SINGLETON, sizeof(int32_t));
    *(int32_t *)score = 42;

    int32_t score_id = Variable_instant(&vars, "player_score", TYPE_INT_SINGLETON, (uintptr_t) score);
    int32_t name_id = Variable_instant(&vars, "player_name", 0, (uintptr_t)0x1234);
    printf("score_id=%d name_id=%d active=%zu\n", score_id, name_id,
           Variable_getActiveCount(&vars));

    int32_t resolved = Variable_getId(&vars, "player_score");
    char name_buf[VARIABLE_NAME_SIZE + 1];
    Variable_getName(&vars, resolved, name_buf, sizeof(name_buf));
    printf("resolved=%d class=0x%08X ptr=%p name=\"%s\"\n", resolved,
           Variable_getClassId(&vars, resolved),
           (void *)Variable_getPointer(&vars, resolved), name_buf);

    uintptr_t stored = Variable_getPointer(&vars, score_id);
    printf("stored int=%d\n", *(int32_t *)stored);

    bool renamed = Variable_rename(&vars, "player_score", "score");
    int32_t re_id = Variable_getId(&vars, "score");
    int32_t gone_id = Variable_getId(&vars, "player_score");
    printf("renamed=%d new_id=%d old_id=%d\n", renamed, re_id, gone_id);
    Variable_shutdown(&vars);

    // Stride: byte width per class id.
    printf("== anti stride ==\n");
    printf("int=%zu long=%zu double=%zu variable=%zu list=%zu\n",
           Stride_get(ID_INT), Stride_get(ID_LONG), Stride_get(ID_DOUBLE),
           Stride_get(ID_VARIABLE), Stride_get(ID_LIST));

    // List: dynamic stride-based list.
    printf("== anti struct: List ==\n");
    List *list = List_allocate(ID_INT, 16);
    for (uint64_t i = 0; i < 10; i++)
        List_add(list, i * 10);
    printf("size=%zu get0=%llu get9=%llu stride=%zu\n", List_size(list),
           (unsigned long long)List_get(list, 0),
           (unsigned long long)List_get(list, 9), List_stride(list));
    List_remove(list, 0);
    printf("after remove size=%zu get0=%llu\n", List_size(list),
           (unsigned long long)List_get(list, 0));
    List_free(list);

    // Array: fixed stride-based array.
    printf("== anti struct: Array ==\n");
    Array *arr = Array_allocate(ID_LONG, 5);
    for (size_t i = 0; i < 5; i++)
        Array_set(arr, i, 100 + i);
    printf("len=%zu sum=%llu\n", Array_length(arr),
           (unsigned long long)(Array_get(arr, 0) + Array_get(arr, 1)
                                + Array_get(arr, 2) + Array_get(arr, 3)
                                + Array_get(arr, 4)));
    Array_free(arr);

    // Stack: LIFO.
    printf("== anti struct: Stack ==\n");
    Stack *stack = Stack_allocate(ID_INT, 4);
    Stack_push(stack, 1);
    Stack_push(stack, 2);
    Stack_push(stack, 3);
    printf("peek=%llu pop=%llu pop=%llu\n", (unsigned long long)Stack_peek(stack),
           (unsigned long long)Stack_pop(stack), (unsigned long long)Stack_pop(stack));
    Stack_free(stack);

    // Deque: circular double-ended.
    printf("== anti struct: Deque ==\n");
    Deque *deque = Deque_allocate(ID_INT, 4);
    Deque_addFirst(deque, 1);
    Deque_addLast(deque, 2);
    Deque_addFirst(deque, 3);
    printf("size=%zu first=%llu last=%llu get1=%llu\n", Deque_size(deque),
           (unsigned long long)Deque_peekFirst(deque),
           (unsigned long long)Deque_peekLast(deque),
           (unsigned long long)Deque_get(deque, 1));
    printf("popFirst=%llu popLast=%llu\n", (unsigned long long)Deque_removeFirst(deque),
           (unsigned long long)Deque_removeLast(deque));
    Deque_free(deque);

    // Queue: FIFO.
    printf("== anti struct: Queue ==\n");
    Queue *queue = Queue_allocate(ID_INT, 4);
    Queue_push(queue, 7);
    Queue_push(queue, 8);
    Queue_push(queue, 9);
    printf("peek=%llu pop=%llu pop=%llu\n", (unsigned long long)Queue_peek(queue),
           (unsigned long long)Queue_pop(queue), (unsigned long long)Queue_pop(queue));
    Queue_free(queue);

    // Map: open-addressing int -> int.
    printf("== anti struct: Map ==\n");
    Map *map = Map_allocate(ID_INT, ID_LONG, 8);
    for (uint64_t k = 1; k <= 20; k++)
        Map_put(map, k, k * k);
    printf("size=%zu get7=%llu contains20=%d missing100=%d\n", Map_size(map),
           (unsigned long long)Map_get(map, 7), Map_containsKey(map, 20),
           Map_containsKey(map, 100));
    printf("remove9=%llu size=%zu\n", (unsigned long long)Map_remove(map, 9),
           Map_size(map));
    Array *keys = Map_keys(map);
    printf("keys=%zu first_key=%llu\n", Array_length(keys),
           (unsigned long long)Array_get(keys, 0));
    Array_free(keys);
    Map_free(map);

    // Set: unique elements.
    printf("== anti struct: Set ==\n");
    Set *set = Set_allocate(ID_INT, 8);
    for (int32_t i = 0; i < 12; i++)
        Set_add(set, (uint64_t)(i % 6));
    printf("size=%zu contains5=%d contains9=%d\n", Set_size(set),
           Set_contains(set, 5), Set_contains(set, 9));
    Set_remove(set, 5);
    printf("after remove5 contains5=%d size=%zu\n", Set_contains(set, 5),
           Set_size(set));
    List *sorted = Set_toSortedList(set);
    printf("sorted: ");
    for (size_t i = 0; i < List_size(sorted); i++)
        printf("%llu ", (unsigned long long)List_get(sorted, i));
    printf("\n");
    List_free(sorted);
    Set_free(set);

    // MinHeap: priority queue.
    printf("== anti struct: MinHeap ==\n");
    MinHeap *heap = MinHeap_allocate(8);
    MinHeap_push(heap, 10, 5.0f);
    MinHeap_push(heap, 20, 1.0f);
    MinHeap_push(heap, 30, 3.0f);
    printf("size=%zu pop=%d pop=%d pop=%d\n", MinHeap_size(heap),
           MinHeap_popItem(heap), MinHeap_popItem(heap), MinHeap_popItem(heap));
    MinHeap_free(heap);

    // SparseSet: ECS-style entity -> component.
    printf("== anti struct: SparseSet ==\n");
    SparseSet *ss = SparseSet_allocate(8, 100, (size_t)sizeof(int32_t));
    uint8_t *comp = SparseSet_add(ss, 42);
    *(int32_t *)comp = 4242;
    printf("count=%zu contains42=%d value=%d\n", SparseSet_count(ss),
           SparseSet_contains(ss, 42), *(int32_t *)SparseSet_get(ss, 42));
    SparseSet_remove(ss, 42);
    printf("after remove contains42=%d count=%zu\n", SparseSet_contains(ss, 42),
           SparseSet_count(ss));
    SparseSet_free(ss);

    // Hash: FNV + Murmur3.
    printf("== anti util: Hash ==\n");
    printf("fnv(\"anti\")=%016llX mix32(7)=%08X\n",
           (unsigned long long)Hash_fnv1a64((const uint8_t *)"anti", 4),
           Hash_murmur3Mix32(7));

    // Random: chaotic PRNG + weighted draws.
    printf("== anti util: Random ==\n");
    Random *rng = Random_allocate(12345);
    printf("r0=%016llX f1=%f d1=%f\n", (unsigned long long)Random_nextLong(rng),
           (double)Random_nextFloat(rng), Random_nextDouble(rng));
    printf("weight(25,100) hit count=");
    int hits = 0;
    for (int i = 0; i < 1000; i++)
        hits += Random_getWeight(rng, 25, 100) ? 1 : 0;
    printf("%d\n", hits);

    Probable *p = Probable_allocate((uintptr_t)0xCAFE, 1, 2);
    printf("sample=%llu\n", (unsigned long long)Random_sample(rng, p));
    Probable_free(p);

    Scanner_hasNextLine();


    ProbableObjects *objpool = ProbableObjects_allocate(3);
    ProbableObjects_add(objpool, 0x1111, 200);
    ProbableObjects_add(objpool, 0x2222, 40);
    ProbableObjects_add(objpool, 0x3333, 1);
    printf("pool total=%u draw=%llu\n", ProbableObjects_totalWeight(objpool),
           (unsigned long long)ProbableObjects_get(objpool));
    ProbableObjects_free(objpool);
    Random_free(rng);

    // Arrays: sort + search.
    printf("== anti util: Arrays ==\n");
    int32_t buf[6] = { 5, 2, 9, 1, 7, 3 };
    Arrays_sortInt(buf, 6);
    printf("sorted: %d %d %d %d %d %d\n", buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
    printf("search7=%ld\n", (long)Arrays_binarySearchInt(buf, 6, 7));

    // Struct & Fields: size-based dynamic schema and polymorphic allocator.
    printf("== anti oop: Struct ==\n");
    Fields *pointFields = Fields(sizeof(int32_t), sizeof(int64_t), sizeof(float));
    printf("generic=0x%X stride=%zu\n", (*pointFields).genericId, (*pointFields).stride);
    printf("stride_via_registry=%zu\n", Stride_get((*pointFields).genericId));

    void *pt = Struct(pointFields); // allocates singleton
    Struct_setInt(pt, 0, 5);
    Struct_setLong(pt, 1, 123456789);
    Struct_setFloat(pt, 2, 2.5f);
    printf("x=%d y=%lld z=%f\n", Struct_getInt(pt, 0),
           (long long)Struct_getLong(pt, 1), (double)Struct_getFloat(pt, 2));
    Struct_free(pt);

    void *pts = Struct(pointFields, 3); // allocates array of 3 elements
    for (size_t i = 0; i < 3; i++)
        Struct_setIntElement(pts, i, 0, (int32_t)(i + 1));
    printf("aos: %d %d %d\n", Struct_getIntElement(pts, 0, 0),
           Struct_getIntElement(pts, 1, 0), Struct_getIntElement(pts, 2, 0));
    Struct_free(pts);

    // Buffers: 2D multi-channel raster engine
    printf("== anti buffers: Buffer & ColorBuffer ==\n");
    Buffer *colorBuf = ColorBuffer_allocate(64, 64);
    ColorBuffer_setRGBA(colorBuf, 10, 20, 255, 128, 64, 255);
    uint8_t cr = 0, cg = 0, cb = 0, ca = 0;
    ColorBuffer_getRGBA(colorBuf, 10, 20, &cr, &cg, &cb, &ca);
    printf("color[10,20] = rgba(%u,%u,%u,%u)\n", cr, cg, cb, ca);

    Buffer *depthBuf = DepthBuffer_allocate(64, 64);
    DepthBuffer_set(depthBuf, 10, 20, 0.75f);
    printf("depth[10,20] = %.2f\n", (double)DepthBuffer_get(depthBuf, 10, 20));

    Buffer_free(colorBuf);
    Buffer_free(depthBuf);

    // Objects: Future, Reactive, Passive, Choice, Global, Local
    printf("== anti objects: Future & Reactive & Passive & Global ==\n");
    Future *fut = Future_allocate();
    printf("future isGiven=%d\n", Future_isGiven(fut));
    Future_setDesiredValue(fut, 4242);
    printf("future isGiven=%d val=%llu\n", Future_isGiven(fut), (unsigned long long)Future_get(fut));
    Future_free(fut);

    Reactive *rx = Reactive_allocate(100);
    Reactive_setOnChanged(rx, on_reactive_change, nullptr);
    Reactive_set(rx, 250);
    Reactive_free(rx);

    int factor = 21;
    Passive *pv = Passive_allocate(passive_lazy_calc, nullptr, &factor);
    printf("passive lazy val=%llu\n", (unsigned long long)Passive_get(pv));
    Passive_free(pv);

    Global *g = Global_allocate(999);
    Global_set(g, 1000);
    printf("global val=%llu\n", (unsigned long long)Global_get(g));
    Global_free(g);

    // RingBuffer + Loop: 4 producers, 1 consumer loop, expect 100 jobs.
    printf("== anti ring + spin + loop ==\n");
    RingBuffer ring;
    RingBuffer_init(&ring, sizeof(job_t), RING_CAP);

    producer_ctx_t ctxs[N_THREADS];
    pthread_t threads[N_THREADS];
    for (uint32_t i = 0; i < N_THREADS; i++) {
        ctxs[i].ring = &ring;
        ctxs[i].id = i + 1;
        pthread_create(&threads[i], nullptr, producer_main, &ctxs[i]);
    }

    engine_ctx_t engine = {
        .ring = &ring,
        .loop = { .tick = engine_tick, .userdata = nullptr, .frame_ms = 4, .running = false },
    };
    engine.loop.userdata = &engine;

    Loop_run(&engine.loop);

    for (uint32_t i = 0; i < N_THREADS; i++) {
        pthread_join(threads[i], nullptr);
    }

    printf("received=%u/%u ticks=%d\n", engine.received,
           (uint32_t)(N_THREADS * N_PUSH), engine.ticks);

    RingBuffer_shutdown(&ring);
    return 0;
}
