// ===== mm_platform.c —— std$io$mm 的平台实现 =====
//
// 接口声明见 ../mm.h。Linux 使用 mmap/munmap 系统调用；
// Windows 使用 VirtualAlloc/VirtualFree 族（fd 文件映射暂以 NULL 占位，
// 待需要时用 _get_osfhandle + CreateFileMapping 补齐）。

#include <stdint.h>
#include <stddef.h>
#include <string.h>

#ifdef _WIN32
  #include <windows.h>
#else
  #include <sys/mman.h>
  #include <unistd.h>
#endif

void *io_mmap_anon(size_t len) {
#ifdef _WIN32
    return VirtualAlloc(NULL, len, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
#else
    return mmap(NULL, len, PROT_READ | PROT_WRITE,
                MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
#endif
}

void *io_mmap_fd(int fd, size_t len, size_t offset) {
#ifdef _WIN32
    (void)fd; (void)len; (void)offset;
    return NULL;
#else
    return mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED,
                fd, (off_t)offset);
#endif
}

int io_munmap(void *addr, size_t len) {
#ifdef _WIN32
    (void)len;
    return VirtualFree(addr, 0, MEM_RELEASE) ? 0 : -1;
#else
    return munmap(addr, len);
#endif
}

size_t io_page_size(void) {
#ifdef _WIN32
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    return (size_t)si.dwPageSize;
#else
    return (size_t)sysconf(_SC_PAGESIZE);
#endif
}

void *io_memcpy(void *dst, const void *src, size_t n) {
    return memcpy(dst, src, n);
}
