// ===== net_proactor.c —— Proactor 统一后端（Windows IOCP 优先）=====
//
// Windows: IOCP（I/O Completion Port）
// Linux:   io_uring（推迟，见 docs/net-event-plan.md §5.4）
//
// 设计要点（docs/net-event-plan.md §5.3）：
//   - SOCKET 是 64 位 UINT_PTR，但接口沿用 int fd（与 net.h 一致，句柄值远小于 2^31）
//   - per-op token 走扩展 OVERLAPPED（ProactorOp），completionKey 不用于区分操作
//   - AcceptEx/ConnectEx 是扩展函数，经 WSAIoctl 取指针
//   - timeout 用 GetTickCount64 绝对 deadline + 固定数组轮询
//   - buffer 生命周期由 Feng 侧 in-flight 表保证，C 侧不 inc/dec

// op 常量（与 net_proactor.h 保持一致；此处内联定义，使 .c 自包含，
// 避免依赖构建目录里被重命名的头文件）
#define PRO_READ    1
#define PRO_WRITE   2
#define PRO_ACCEPT  3
#define PRO_CONNECT 4
#define PRO_TIMEOUT 5

#ifdef _WIN32

#include <winsock2.h>
#include <windows.h>
#include <mswsock.h>
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

// ---- 扩展 OVERLAPPED：per-op 携带 token/op/fd ----
typedef struct {
    OVERLAPPED ov;
    uint64_t   token;
    int        op;
    int        fd;
    // accept 专用：预创建的新 socket 与地址缓冲（AcceptEx 要求）
    SOCKET     accSock;
    char       accBuf[(sizeof(SOCKADDR_STORAGE) + 16) * 2];
} ProactorOp;

// ---- 完成事件（与 Feng 侧 struct Completion 布局一致）----
typedef struct {
    uint64_t token;
    int32_t  result;
    int32_t  op;
    int32_t  fd;
} ProactorEvent;

// ---- timeout 固定数组 ----
#define MAX_TIMEOUTS 64
typedef struct {
    uint64_t  token;
    ULONGLONG deadline;   // GetTickCount64() 绝对毫秒
} TimeoutEntry;

// ---- Proactor 句柄 ----
typedef struct {
    HANDLE   iocp;
    LPFN_ACCEPTEX               pAcceptEx;
    LPFN_CONNECTEX              pConnectEx;
    LPFN_GETACCEPTEXSOCKADDRS   pGetAcceptExSockaddrs;
    TimeoutEntry timeouts[MAX_TIMEOUTS];
    int          nTimeouts;
} Proactor;

// 从临时 socket 取扩展函数指针（标准做法）
static int loadExtFuncs(Proactor* p) {
    SOCKET s = WSASocket(AF_INET, SOCK_STREAM, IPPROTO_TCP, NULL, 0, WSA_FLAG_OVERLAPPED);
    if (s == INVALID_SOCKET) return 0;

    GUID guidAcceptEx = WSAID_ACCEPTEX;
    GUID guidConnectEx = WSAID_CONNECTEX;
    GUID guidGetSockaddrs = WSAID_GETACCEPTEXSOCKADDRS;
    DWORD bytes = 0;

    int ok = 1;
    if (WSAIoctl(s, SIO_GET_EXTENSION_FUNCTION_POINTER,
                 &guidAcceptEx, sizeof(guidAcceptEx),
                 &p->pAcceptEx, sizeof(p->pAcceptEx), &bytes, NULL, NULL) != 0) ok = 0;
    if (WSAIoctl(s, SIO_GET_EXTENSION_FUNCTION_POINTER,
                 &guidConnectEx, sizeof(guidConnectEx),
                 &p->pConnectEx, sizeof(p->pConnectEx), &bytes, NULL, NULL) != 0) ok = 0;
    if (WSAIoctl(s, SIO_GET_EXTENSION_FUNCTION_POINTER,
                 &guidGetSockaddrs, sizeof(guidGetSockaddrs),
                 &p->pGetAcceptExSockaddrs, sizeof(p->pGetAcceptExSockaddrs),
                 &bytes, NULL, NULL) != 0) ok = 0;

    closesocket(s);
    return ok;
}

void* proactorCreate(void) {
    Proactor* p = (Proactor*)malloc(sizeof(Proactor));
    if (!p) return NULL;
    memset(p, 0, sizeof(*p));

    p->iocp = CreateIoCompletionPort(INVALID_HANDLE_VALUE, NULL, 0, 0);
    if (p->iocp == NULL) {
        free(p);
        return NULL;
    }
    if (!loadExtFuncs(p)) {
        CloseHandle(p->iocp);
        free(p);
        return NULL;
    }
    return p;
}

int proactorAssociate(void* h, int fd) {
    Proactor* p = (Proactor*)h;
    SOCKET s = (SOCKET)(intptr_t)fd;
    // completionKey 传 0；操作区分靠扩展 OVERLAPPED，不靠 key
    HANDLE r = CreateIoCompletionPort((HANDLE)s, p->iocp, 0, 0);
    return r != NULL ? 1 : 0;
}

static ProactorOp* allocOp(uint64_t token, int op, int fd) {
    ProactorOp* po = (ProactorOp*)malloc(sizeof(ProactorOp));
    if (!po) return NULL;
    memset(po, 0, sizeof(*po));
    po->token = token;
    po->op = op;
    po->fd = fd;
    return po;
}

// 提交后统一处理返回：SOCKET_ERROR 且非 WSA_IO_PENDING 才算失败
static int finishSubmit(ProactorOp* po, int rc) {
    if (rc == SOCKET_ERROR && WSAGetLastError() != WSA_IO_PENDING) {
        if (po->op == PRO_ACCEPT) closesocket(po->accSock);
        free(po);
        return 0;
    }
    return 1;
}

int proactorSubmit(void* h, int fd, void* buf, int len,
                   uint32_t ip, uint16_t port, int op, uint64_t token) {
    Proactor* p = (Proactor*)h;
    SOCKET s = (SOCKET)(intptr_t)fd;
    DWORD flags = 0;
    DWORD n = 0;

    switch (op) {
    case PRO_READ: {
        ProactorOp* po = allocOp(token, op, fd);
        if (!po) return 0;
        WSABUF wb;
        wb.buf = (char*)buf;
        wb.len = (ULONG)len;
        return finishSubmit(po, WSARecv(s, &wb, 1, &n, &flags, &po->ov, NULL));
    }
    case PRO_WRITE: {
        ProactorOp* po = allocOp(token, op, fd);
        if (!po) return 0;
        WSABUF wb;
        wb.buf = (char*)buf;
        wb.len = (ULONG)len;
        return finishSubmit(po, WSASend(s, &wb, 1, &n, 0, &po->ov, NULL));
    }
    case PRO_ACCEPT: {
        ProactorOp* po = allocOp(token, op, fd);
        if (!po) return 0;
        // AcceptEx 要求预先创建新 socket 并预留地址缓冲
        po->accSock = WSASocket(AF_INET, SOCK_STREAM, IPPROTO_TCP,
                                NULL, 0, WSA_FLAG_OVERLAPPED);
        if (po->accSock == INVALID_SOCKET) {
            free(po);
            return 0;
        }
        DWORD got = 0;
        int rc = p->pAcceptEx(s, po->accSock, po->accBuf, 0,
                              sizeof(SOCKADDR_STORAGE) + 16,
                              sizeof(SOCKADDR_STORAGE) + 16,
                              &got, &po->ov);
        return finishSubmit(po, rc ? 0 : SOCKET_ERROR);
    }
    case PRO_CONNECT: {
        ProactorOp* po = allocOp(token, op, fd);
        if (!po) return 0;
        // ConnectEx 要求 socket 先 bind 到 INADDR_ANY:0
        SOCKADDR_IN any;
        memset(&any, 0, sizeof(any));
        any.sin_family = AF_INET;
        any.sin_addr.s_addr = INADDR_ANY;
        any.sin_port = 0;
        if (bind(s, (SOCKADDR*)&any, sizeof(any)) == SOCKET_ERROR) {
            free(po);
            return 0;
        }
        SOCKADDR_IN dst;
        memset(&dst, 0, sizeof(dst));
        dst.sin_family = AF_INET;
        dst.sin_addr.s_addr = ip;           // 传入 host 字节序，sin_addr 按网络序存储
        dst.sin_port = htons(port);
        DWORD sent = 0;
        int rc = p->pConnectEx(s, (SOCKADDR*)&dst, sizeof(dst),
                               NULL, 0, &sent, &po->ov);
        return finishSubmit(po, rc ? 0 : SOCKET_ERROR);
    }
    case PRO_TIMEOUT: {
        if (p->nTimeouts >= MAX_TIMEOUTS) return 0;
        p->timeouts[p->nTimeouts].token = token;
        p->timeouts[p->nTimeouts].deadline = GetTickCount64() + (ULONGLONG)len;
        p->nTimeouts++;
        return 1;
    }
    default:
        return 0;
    }
}

// 填一个完成事件；accept 需额外做 SO_UPDATE_ACCEPT_CONTEXT
static void fillEvent(Proactor* p, ProactorOp* po, int64_t result,
                      ProactorEvent* ev) {
    ev->token = po->token;
    ev->op = po->op;
    ev->fd = po->fd;
    if (po->op == PRO_ACCEPT) {
        // 让新 socket 继承监听 socket 的上下文
        setsockopt(po->accSock, SOL_SOCKET, SO_UPDATE_ACCEPT_CONTEXT,
                   (char*)&(po->fd), sizeof(po->fd));
        ev->result = (int32_t)(intptr_t)po->accSock;  // 新 fd
    } else {
        ev->result = (int32_t)result;
    }
}

// 清出所有到期的 timeout，返回产出的事件数
static int drainTimeouts(Proactor* p, ProactorEvent* evs, int cap) {
    int count = 0;
    ULONGLONG now = GetTickCount64();
    int i = 0;
    while (i < p->nTimeouts) {
        if (p->timeouts[i].deadline <= now && count < cap) {
            evs[count].token = p->timeouts[i].token;
            evs[count].op = PRO_TIMEOUT;
            evs[count].result = 0;
            evs[count].fd = -1;
            count++;
            // 用最后一个元素填补，避免搬移
            p->timeouts[i] = p->timeouts[p->nTimeouts - 1];
            p->nTimeouts--;
        } else {
            i++;
        }
    }
    return count;
}

// 计算最近的 deadline 距 now 的毫秒差；无 timeout 返回 -1
static int nearestTimeoutMs(Proactor* p) {
    if (p->nTimeouts == 0) return -1;
    ULONGLONG now = GetTickCount64();
    ULONGLONG nearest = p->timeouts[0].deadline;
    for (int i = 1; i < p->nTimeouts; i++) {
        if (p->timeouts[i].deadline < nearest) nearest = p->timeouts[i].deadline;
    }
    if (nearest <= now) return 0;
    ULONGLONG diff = nearest - now;
    return diff > (ULONGLONG)INT32_MAX ? INT32_MAX : (int)diff;
}

int proactorPoll(void* h, void* outEvents, int outCap, int timeoutMs) {
    Proactor* p = (Proactor*)h;
    ProactorEvent* evs = (ProactorEvent*)outEvents;
    int count = 0;

    // 先产出已到期的 timeout
    count += drainTimeouts(p, evs, outCap);

    // 计算本轮等待时间：取「最近 deadline」与「调用方 timeout」的较小值
    int waitMs = timeoutMs;
    int tmo = nearestTimeoutMs(p);
    if (tmo >= 0) {
        if (timeoutMs < 0) waitMs = tmo;          // 调用方无限等待 → 受 deadline 约束
        else if (tmo < timeoutMs) waitMs = tmo;   // deadline 更近
    }

    DWORD dw = (waitMs < 0) ? INFINITE : (DWORD)waitMs;

    while (count < outCap) {
        DWORD n = 0;
        ULONG_PTR key = 0;
        OVERLAPPED* ov = NULL;
        BOOL ok = GetQueuedCompletionStatus(p->iocp, &n, &key, &ov, dw);
        dw = 0;  // 首个事件后不再阻塞

        if (ov == NULL) {
            // WAIT_TIMEOUT 或端口关闭（错误）
            break;
        }
        ProactorOp* po = (ProactorOp*)((char*)ov - offsetof(ProactorOp, ov));
        int64_t result = n;
        if (!ok) result = -1;  // 操作失败，负值表错误
        fillEvent(p, po, result, &evs[count]);
        count++;
        free(po);
    }

    // 再清一次到期 timeout（等待期间可能新到期）
    count += drainTimeouts(p, evs + count, outCap - count);

    return count;
}

void proactorDestroy(void* h) {
    Proactor* p = (Proactor*)h;
    if (p->iocp) CloseHandle(p->iocp);
    free(p);
}

#else

// ===== Linux：io_uring 推迟（docs/net-event-plan.md §5.4）=====
// 暂以 select/poll 占位以保证非 Windows 平台能链接通过；
// 正式 io_uring 后端待有 Linux 环境后实现。

#include <stdint.h>

void* proactorCreate(void) { return NULL; }
int   proactorAssociate(void* p, int fd) { (void)p; (void)fd; return 0; }
int   proactorSubmit(void* p, int fd, void* buf, int len,
                     uint32_t ip, uint16_t port, int op, uint64_t token) {
    (void)p; (void)fd; (void)buf; (void)len; (void)ip; (void)port; (void)op; (void)token;
    return 0;
}
int   proactorPoll(void* p, void* outEvents, int outCap, int timeoutMs) {
    (void)p; (void)outEvents; (void)outCap; (void)timeoutMs;
    return 0;
}
void  proactorDestroy(void* p) { (void)p; }

#endif
