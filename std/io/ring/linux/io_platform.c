// ===== io_platform.c —— std$io 平台实现 =====
//
// 接口声明见 ../io.h。Linux 直接调用 io_uring 系统调用
// （setup=425, enter=426，asm-generic 全架构一致）；
// Windows 无 io_uring，提供 ENOSYS stub（返回 -1）。

#include <stdint.h>
#include <stddef.h>

#ifdef _WIN32

int io_uring_setup(unsigned entries, void *params) {
    (void)entries; (void)params;
    return -1;
}

int io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                   unsigned flags, const void *arg, size_t argsz) {
    (void)fd; (void)to_submit; (void)min_complete;
    (void)flags; (void)arg; (void)argsz;
    return -1;
}

#else

#include <sys/syscall.h>
#include <unistd.h>

int io_uring_setup(unsigned entries, void *params) {
    return (int)syscall(425, entries, params);
}

int io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                   unsigned flags, const void *arg, size_t argsz) {
    return (int)syscall(426, fd, to_submit, min_complete, flags, arg, argsz);
}

#endif
