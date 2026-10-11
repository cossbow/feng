#include <stdint.h>

// Type-generic GCC/Clang __atomic_* builtins, called with typed pointers.
// Do NOT declare the size-suffixed names (__atomic_compare_exchange_4 etc.)
// manually: their prototypes conflict with the compilers' builtin signatures,
// so GCC emits an external call and Clang never inlines them at all — the
// result is an undefined reference to __atomic_compare_exchange_4 at link
// time (no -latomic in the generated Makefile). The type-generic forms are
// recognized and inlined by both compilers.

#define M 5  // __ATOMIC_SEQ_CST

int32_t atomicLoad(const volatile void* ptr) {
    return __atomic_load_n((const int32_t*)ptr, M);
}

void atomicStore(volatile void* ptr, int32_t val) {
    __atomic_store_n((int32_t*)ptr, val, M);
}

int32_t atomicSwap(volatile void* ptr, int32_t val) {
    return __atomic_exchange_n((int32_t*)ptr, val, M);
}

int atomicCAS(volatile void* ptr, void* expected, int32_t desired) {
    return __atomic_compare_exchange_n((int32_t*)ptr, (int32_t*)expected,
                                       desired, 0, M, M);
}

int32_t atomicFetchAdd(volatile void* ptr, int32_t val) {
    return __atomic_fetch_add((int32_t*)ptr, val, M);
}

int32_t atomicFetchSub(volatile void* ptr, int32_t val) {
    return __atomic_fetch_sub((int32_t*)ptr, val, M);
}
