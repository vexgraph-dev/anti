#include <stdio.h>

static int add(int a, int b) { return a + b; }

typedef struct { int x; int y; } Vec2;
typedef int (*MathFn)(int, int);

static const int x = 900;

int main(void) {
    printf("== 1. basic pointer ==\n");
    int value = 42;
    int *p = &value;
    printf("value=%d (*p)=%d &value=%p\n", value, *p, (void *)&value);


    const int var = 23;
    const int *i = &var;
    printf("%d", *i);

    int var1 = 0;
    int *var2 = &var1;
    int **var3 = &var2;
    int ***var4 = &var3;
    printf("%d", ***var4);
    printf("%d", value);


    printf("\n== 2. pointer to pointer (handle tables) ==\n");
    int **pp = &p;
    printf("**pp=%d\n", **pp);



    printf("\n== 3. const variants ==\n");
    const int *pc = &value;   /* can't write through pc, can repoint */
    int n = 7;
    pc = &n;



    printf("pc now points at n -> *pc=%d\n", *pc);
    int *const cp = &value;   /* can't repoint cp, can write through it */
    *cp = 43;
    printf("wrote through cp -> value=%d\n", value);



    printf("\n== 4. void* ==\n");
    void *mem = &value;
    printf("(void*) holds %p, cast to read: %d\n", mem, *(int *)mem);



    printf("\n== 5. array decay ==\n");
    int arr[4] = {1, 2, 3, 4};
    int *ap = arr;
    printf("arr[2]=%d  ap[2]=%d  *(ap+2)=%d\n", arr[2], ap[2], *(ap + 2));



    printf("\n== 6. function pointer ==\n");
    const MathFn fn = add;
    printf("fn(2,3)=%d\n", fn(2, 3));



    printf("\n== 7. (*p).field vs p->field ==\n");
    Vec2 v = {1, 2};
    Vec2 *vp = &v;
    printf("(*vp).x=%d  vp->y=%d\n", (*vp).x, vp->y);



    printf("\n== 8. same 8 bytes, different meaning ==\n");
    int  *as_int  = &value;
    char *as_byte = (char *)&value;
    printf("int *  reads %d at %p\n", *as_int, (void *)as_int);
    printf("char * reads %d at %p (first byte)\n", *as_byte, (void *)as_byte);

    printf("%d", x);

    return 0;
}
