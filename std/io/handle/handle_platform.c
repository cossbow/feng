// ===== handle_platform.c —— std$io$handle 的平台 shim =====
//
// 补足 handle.h 声明的 libc 符号在 Windows 上的缺口：
//   socket     —— ws2_32 提供（feng.cfg [windows] link = ws2_32）
//   pipe       —— Windows 无 pipe()，本文件用 _pipe 模拟
//   socketpair —— Windows 无 socketpair()，本文件用回环监听 socket 模拟
//
// open 的 flags 翻译在 handle_open.c（本文件含 <io.h> 会与 CRT 的 open 声明冲突，
// 故单独放一个不 include <io.h> 的编译单元）。
//
// POSIX（Linux/macOS）：libc 原生提供同名符号，本文件全部 #ifdef _WIN32
// 隔离，Linux 下为空编译单元（不定义任何符号，避免与 libc 冲突）。

#ifdef _WIN32
  #include <winsock2.h>
  #include <ws2tcpip.h>
  #include <io.h>
  #include <fcntl.h>
  #include <string.h>

// 懒初始化 Winsock：socket/connect/accept 前必须先 WSAStartup，否则返回
// INVALID_SOCKET（-1）。进程内首次调用时启动一次（引用计数式，幂等）。
static int wsaInit(void) {
    static int done = 0;
    if (!done) {
        WSADATA wsa;
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return -1;
        done = 1;
    }
    return 0;
}

// 创建管道：pipefd[2] 出参（读端@0 / 写端@1），成功 0，失败 -1
int pipe(void *pipefd) {
    int *fds = (int *)pipefd;
    int fds2[2];
    if (_pipe(fds2, 65536, 0) != 0) return -1;
    fds[0] = fds2[0];
    fds[1] = fds2[1];
    return 0;
}

// 创建已连接的 socket 对：sv[2] 出参，成功 0，失败 -1
int socketpair(int domain, int type, int protocol, void *sv) {
    // Windows 无 AF_UNIX socketpair：用本机回环监听 socket 模拟。
    // 监听 127.0.0.1:0（系统分配端口），connect + accept 得一对已连接 socket。
    (void)domain; (void)type; (void)protocol;
    if (wsaInit() != 0) return -1;
    int *fds = (int *)sv;
    SOCKET listenSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSock == INVALID_SOCKET) return -1;
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;
    if (bind(listenSock, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        closesocket(listenSock);
        return -1;
    }
    if (listen(listenSock, 1) != 0) {
        closesocket(listenSock);
        return -1;
    }
    int len = sizeof(addr);
    if (getsockname(listenSock, (struct sockaddr *)&addr, &len) != 0) {
        closesocket(listenSock);
        return -1;
    }
    SOCKET client = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (client == INVALID_SOCKET) {
        closesocket(listenSock);
        return -1;
    }
    if (connect(client, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        closesocket(listenSock);
        closesocket(client);
        return -1;
    }
    SOCKET server = accept(listenSock, NULL, NULL);
    closesocket(listenSock);
    if (server == INVALID_SOCKET) {
        closesocket(client);
        return -1;
    }
    fds[0] = (int)client;
    fds[1] = (int)server;
    return 0;
}

#endif // _WIN32
