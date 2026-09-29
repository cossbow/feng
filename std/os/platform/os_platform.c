// ===== os_platform.c —— 平台相关 C 函数实现 =====

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

// 标准流访问器：每个流返回一个 dup 副本（fdopen 包新 FILE*）。
// File 的 resource free 因此可以无条件 fclose——关的是副本，
// 原始 C 标准流仍归 CRT 所有，退出期 flush 不受影响
// （否则 globals_cleanup 会 fclose 掉 stdout/stderr，析构器阶段的
//  printf，如 FENG_DEBUG_MEMORY 泄漏检查器，输出将全部丢失）。
// dup/fileno/fdopen 是 POSIX 名；Windows 下 mingw-w64 的 <unistd.h>
// 透传 <io.h>，moldname 机制把它们映射到 UCRT/MSVCRT 的 _dup 等实现。
static FILE* feng_dup_stream(FILE* f, const char* mode) {
    int fd = dup(fileno(f));
    if (fd < 0) abort();
    FILE* fp = fdopen(fd, mode);
    if (!fp) abort();
    return fp;
}

uint64_t feng_stdin(void) {
    return (uint64_t)feng_dup_stream(stdin, "r");
}

uint64_t feng_stdout(void) {
    // 保持 stdout 行缓冲：原始 C stdout 在终端下行缓冲。
    // （Windows UCRT 不支持 _IOLBF，自动降级为全缓冲。）
    FILE* fp = feng_dup_stream(stdout, "w");
    setvbuf(fp, NULL, _IOLBF, 0);
    return (uint64_t)fp;
}

uint64_t feng_stderr(void) {
    // 保持 stderr 无缓冲：错误信息即时可见，崩溃（abort）时不丢失。
    FILE* fp = feng_dup_stream(stderr, "w");
    setvbuf(fp, NULL, _IONBF, 0);
    return (uint64_t)fp;
}
