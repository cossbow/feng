#include <stdint.h>

// Declare GCC builtins — recognized and inlined by GCC/Clang
int32_t __atomic_load_4(const volatile void* ptr, int memorder);
void    __atomic_store_4(volatile void* ptr, int32_t val, int memorder);
int32_t __atomic_exchange_4(volatile void* ptr, int32_t val, int memorder);
int     __atomic_compare_exchange_4(volatile void* ptr, void* expected,
                                    int32_t desired, int weak,
                                    int success_memorder, int failure_memorder);
int32_t __atomic_fetch_add_4(volatile void* ptr, int32_t val, int memorder);
int32_t __atomic_fetch_sub_4(volatile void* ptr, int32_t val, int memorder);

#define M 5  // __ATOMIC_SEQ_CST

int32_t atomicLoad(const volatile void* ptr) {
    return __atomic_load_4(ptr, M);
}

void atomicStore(volatile void* ptr, int32_t val) {
    __atomic_store_4(ptr, val, M);
}

int32_t atomicSwap(volatile void* ptr, int32_t val) {
    return __atomic_exchange_4(ptr, val, M);
}

int atomicCAS(volatile void* ptr, void* expected, int32_t desired) {
    return __atomic_compare_exchange_4(ptr, expected, desired, 0, M, M);
}

int32_t atomicFetchAdd(volatile void* ptr, int32_t val) {
    return __atomic_fetch_add_4(ptr, val, M);
}

int32_t atomicFetchSub(volatile void* ptr, int32_t val) {
    return __atomic_fetch_sub_4(ptr, val, M);
}
