// tests/class_relational_test.c — verify Class() named constructor + Relational spotlight.
#include <stdio.h>
#include <string.h>

#include "oop/Class.h"
#include "oop/type.h"
#include "oop/struct.h"
#include "relational/variable.h"
#include "relational/relational.h"
#include "primitive/string.h"
#include "nio/mem.h"

static int g_failures = 0;
#define CHECK(cond) do{ if(!(cond)){ printf("FAIL %s:%d %s\n",__FILE__,__LINE__,#cond); g_failures++; } }while(0)

static void testClassNamed(void){
    // Class *player = Class(TYPE_VEC3, "position", TYPE_INT, "health");
    Class *player = Class(ID_VEC3, "position", ID_INT, "health", ID_STRING, "name");
    CHECK(player != nullptr);
    CHECK((*player).count == 3);
    CHECK(strcmp(Class_fieldName((*player).genericId, 0), "position")==0);
    CHECK(strcmp(Class_fieldName((*player).genericId, 1), "health")==0);
    CHECK(Class_fieldIndex((*player).genericId, "name")==2);
    // edge: duplicate name → nullptr
    Class *bad = Class(ID_INT, "dup", ID_FLOAT, "dup");
    CHECK(bad == nullptr);
    // edge: null name → nullptr
    Class *bad2 = Class(ID_INT, nullptr);
    CHECK(bad2 == nullptr);
    // nested: Class* as type
    Class *inner = Class(ID_FLOAT, "red", ID_FLOAT, "green", ID_FLOAT, "blue");
    CHECK(inner != nullptr);
    Class *mat = Class((size_t)inner, "albedoMap", ID_FLOAT, "roughness");
    CHECK(mat != nullptr);
    CHECK((*mat).count == 2);
    CHECK(Class_fieldIndex((*mat).genericId, "albedoMap")==0);
    CHECK((*mat).items[0].isStruct == true);
    // alloc + field access
    void *inst = Struct(player);
    CHECK(inst != nullptr);
    Struct_setInt(inst, 1, 100);
    CHECK(Struct_getInt(inst, 1)==100);
    Struct_free(inst);
}

static void testRelationalSpotlight(void){
    Variable global;
    Variable local;
    CHECK(Variable_init(&global));
    CHECK(Variable_init(&local));
    // user vars — health family
    Relational_setString(&global, "health", "100");
    Relational_setString(&global, "health_ui", "panel");
    Relational_setString(&global, "health_progress_bar", "bar");
    Relational_setString(&local, "hp_text", "42");
    Relational_setString(&local, "mana", "50");

    // exact
    int32_t out[10];
    size_t n = Relational_search(&global, "health", out, 10);
    CHECK(n==3); // health, health_ui, health_progress_bar (ranked exact > prefix > substring)
    // verify first is exact "health"
    char name[32];
    Variable_getName(&global, out[0], name, sizeof(name));
    CHECK(strcmp(name, "health")==0);

    // prefix vs substring
    n = Relational_search(&global, "health_", out, 10);
    CHECK(n==2); // health_ui, health_progress_bar (prefix)

    // searchAll merges global+local, first 100 cap
    int32_t outAll[10];
    n = Relational_searchAll(&global, &local, "health", outAll, 10);
    // global 3 + local 0 (hp_text doesn't contain health) =3
    CHECK(n==3);
    // case-insensitive
    n = Relational_search(&global, "HEALTH", out, 10);
    CHECK(n==3);

    // getValue / getFunction
    void *p = Relational_getValue(&global, "health_ui");
    CHECK(p != nullptr);
    CHECK(strcmp(string_get((uint8_t*) p), "panel")==0);
    // function pointer round-trip
    void *fn = (void*) 0x12345;
    Relational_setFunction(&global, "onUpdate", fn);
    CHECK(Relational_getFunction(&global, "onUpdate")==fn);

    // getName/setName
    CHECK(Relational_setName(&global, "mana", "mana2")==false); // mana in local, not global
    CHECK(Relational_setName(&local, "mana", "mana2")==true);
    CHECK(Relational_getId(&local, "mana2")>=0);

    Variable_shutdown(&global);
    Variable_shutdown(&local);
}

int main(void){
    testClassNamed();
    testRelationalSpotlight();
    if(g_failures==0) printf("class_relational_test: all checks passed\n");
    else printf("class_relational_test: %d FAILURES\n", g_failures);
    return g_failures==0?0:1;
}
