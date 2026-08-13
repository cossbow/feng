#include <stdint.h>

// Thin wrappers over GCC __atomic_* builtins.
// These are exported to Feng via import std$async$c.

#define M 5  // __ATOMIC_SEQ_CST

int32_t atomicLoad(const volatile void* ptr);
void    atomicStore(volatile void* ptr, int32_t val);
int32_t atomicSwap(volatile void* ptr, int32_t val);
int     atomicCAS(volatile void* ptr, void* expected, int32_t desired);
int32_t atomicFetchAdd(volatile void* ptr, int32_t val);
int32_t atomicFetchSub(volatile void* ptr, int32_t val);

