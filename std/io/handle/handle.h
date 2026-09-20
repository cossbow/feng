// ===== handle.h —— std$io$handle 的 C 接口声明 =====
//
// 仅声明 libc 符号（真实名），由链接器解析，无需 .c。
// Feng 侧直接调用：socket / pipe / socketpair / open / close。
//
// 数组出参（pipe/socketpair 的 int[2]）在 Feng 侧传数组 .values（u64 指针），
// 生成桥接按声明的签名做指针转换。

// 关闭 fd
int close(int fd);

// 创建 socket（返回 fd，失败 -1）；
// domain=AF_INET(2)/AF_UNIX(1)，type=SOCK_STREAM(1)/SOCK_DGRAM(2)
int socket(int domain, int type, int protocol);

// 创建管道；pipefd 为 int[2] 指针（读端@0 / 写端@1）
int pipe(void *pipefd);

// 创建已连接的 socket 对；sv 为 int[2] 指针
int socketpair(int domain, int type, int protocol, void *sv);

// 打开文件（flags=O_RDONLY(0)/O_WRONLY(1)/O_RDWR(2)，可或 O_CREAT(64)/O_TRUNC(512)）
int open(const char *pathname, int flags);
