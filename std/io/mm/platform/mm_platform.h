// ===== mm.h —— std$io$mm 模块的 C 接口声明 =====
//
// 跨平台内存原语：匿名映射、文件共享映射、释放、页面大小、内存拷贝。
// 符号统一使用 io_ 前缀，实现在 platform/mm_platform.c
// （Linux: mmap/munmap/sysconf；Windows: VirtualAlloc 族）。
//
// 说明：这里刻意不 include <sys/mman.h>，而是直接声明这些函数，
// 避免 C2FengConverter 展开系统头转换出大量无关符号。

#include <stddef.h>

// 匿名私有映射（Linux: MAP_PRIVATE|MAP_ANONYMOUS；Windows: VirtualAlloc）
void *io_mmap_anon(size_t len);

// 文件/设备共享映射 MAP_SHARED（io_uring ring、共享内存等）
void *io_mmap_fd(int fd, size_t len, size_t offset);

// 释放映射（Linux: munmap；Windows: VirtualFree）
int io_munmap(void *addr, size_t len);

// 系统页面大小
size_t io_page_size(void);

// 内存拷贝（返回 dst）
void *io_memcpy(void *dst, const void *src, size_t n);
