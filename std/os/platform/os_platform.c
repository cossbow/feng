// ===== os_platform.c —— 平台相关 C 函数实现 =====

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

// Cross-platform stdin/stdout/stderr accessors.
// On MSVC, __acrt_iob_func(index) is the standard way to get FILE*
// handles; on Linux/MinGW the global FILE* variables are used directly.

uint64_t feng_stdin(void) {
#ifdef __acrt_iob_func_defined
    return (uint64_t)__acrt_iob_func(0);
#else
    return (uint64_t)stdin;
#endif
}

uint64_t feng_stdout(void) {
#ifdef __acrt_iob_func_defined
    return (uint64_t)__acrt_iob_func(1);
#else
    return (uint64_t)stdout;
#endif
}

uint64_t feng_stderr(void) {
#ifdef __acrt_iob_func_defined
    return (uint64_t)__acrt_iob_func(2);
#else
    return (uint64_t)stderr;
#endif
}
