// ===== io_platform.h —— std$io$ring$linux C 接口声明 =====
//
// 本目录（ring/linux）提供 Linux io_uring 的 syscall shim（io_platform.c）。
// 函数由 ring_linux.feng 经模块前缀 linux$ 调用；本文件同时作为模块标记，
// 让编译器识别此目录为独立模块并编译其中的 .c 文件。

#include <stddef.h>

// 创建 io_uring 实例，返回 ring fd（Linux: syscall(425)；Windows: ENOSYS）
int io_uring_setup(unsigned entries, void *params);

// 提交/收割 SQE/CQE（Linux: syscall(426)；Windows: ENOSYS）
int io_uring_enter(int fd, unsigned to_submit, unsigned min_complete,
                   unsigned flags, const void *arg, size_t argsz);
