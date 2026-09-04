// ===== os_platform.h —— 平台相关 C 函数声明 =====
//
// std/os/file.feng 通过 import std$os$platform 调用，
// 前缀 platform$。跨平台差异（stdin 获取方式等）在此层封装。

#include <stdint.h>
#include <stddef.h>

// Cross-platform stdin/stdout/stderr accessors (implemented in os_platform.c)
uint64_t feng_stdin(void);
uint64_t feng_stdout(void);
uint64_t feng_stderr(void);

// stdlib (skip #include <stdlib.h> to avoid div_t etc.)
int remove(const char *pathname);
char *getenv(const char *name);
size_t strlen(const char *s);

// POSIX
int mkdir(const char *path);
int rmdir(const char *path);
int access(const char *path, int mode);
char *getcwd(char *buf, int size);
int chdir(const char *path);
void *memcpy(void *dest, const void *src, size_t n);
int putenv(const char *envstring);
