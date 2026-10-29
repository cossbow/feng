// ===== handle_open.c —— open 的平台 shim（仅 Windows） =====
//
// handle.h 声明 open 为 POSIX flags（O_CREAT=64/O_TRUNC=512/O_EXCL=128/O_APPEND=1024），
// 但 Windows CRT 的 open 用 MSVC flags（_O_CREAT=0x100/_O_TRUNC=0x200 等）。本文件定义
// open 做 flags 翻译，让 handle.h 的声明在 Windows 上语义成立。
//
// 为什么单独一个文件：handle_platform.c include <io.h>（需要 _pipe），而 <io.h> 声明了
// CRT 的 open（varargs 别名）→ 与这里的 open 定义同名冲突。本文件不 include <io.h>/
// <fcntl.h>（后者也链 <io.h>），改用 Windows API CreateFileA 打开文件（<winbase.h> 不含
// io.h），再经 _open_osfhandle 转成 CRT fd（该函数也声明于 <io.h>，此处手动声明）。
// 链接时本项目内的 open 调用（桥接头文件不含 <io.h>）全部解析到这里的强符号。
//
// POSIX（Linux/macOS）：libc 原生提供 open，本文件 #ifdef _WIN32 隔离为空编译单元。

#ifdef _WIN32

#include <windows.h>
#include <winbase.h>

int __cdecl _open_osfhandle(intptr_t _OSFHandle, int _Flags);

// POSIX flags → 访问方式：O_RDONLY(0)/O_WRONLY(1)/O_RDWR(2)
static DWORD mapAccess(int flags) {
    switch (flags & 0x3) {
        case 1:  return GENERIC_WRITE;
        case 2:  return GENERIC_READ | GENERIC_WRITE;
        default: return GENERIC_READ;
    }
}

// 恒 _O_BINARY：Feng 侧按字节语义读写，禁止 CRT 的 \n→\r\n 文本转换。
static int openFd(HANDLE h) {
    return _open_osfhandle((intptr_t)h, 0x8000 /* _O_BINARY */);
}

int open(const char *pathname, int flags) {
    DWORD access = mapAccess(flags);
    DWORD disposition = OPEN_EXISTING;
    if (flags & 64)     disposition = OPEN_ALWAYS;    // O_CREAT
    if (flags & 512)    disposition = CREATE_ALWAYS;  // O_TRUNC
    if ((flags & 64) && (flags & 512)) disposition = CREATE_ALWAYS;
    HANDLE h = CreateFileA(pathname, access,
        FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
        disposition, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return -1;
    return openFd(h);
}

#endif // _WIN32
