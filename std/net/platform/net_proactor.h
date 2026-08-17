// ===== net_proactor.h —— 统一 Proactor 后端签名（跨平台）=====
//
// Windows 走 IOCP，Linux 走 io_uring（推迟，见 docs/net-event-plan.md §5.4）。
// Feng 侧通过 import std$net$platform 调用，前缀 platform$。
//
// 完成事件结构 ProactorEvent 与 Feng 侧 struct Completion 布局一致
// （token uint64 / result int32 / op int32 / fd int32），定义见 .c。

#include <stdint.h>

// 操作类型（与 Feng 侧 proactor.feng 中同字面量）
#define PRO_READ    1
#define PRO_WRITE   2
#define PRO_ACCEPT  3
#define PRO_CONNECT 4
#define PRO_TIMEOUT 5

// 创建 Proactor，返回 opaque handle；失败返回 NULL
void* proactorCreate(void);

// 关联 socket 到 Proactor（IOCP：CreateIoCompletionPort；io_uring：记映射，幂等）
// 返回 1=成功，0=失败
int   proactorAssociate(void* p, int fd);

// 提交异步操作，按 op 解释其余参数：
//   READ/WRITE: buf+len 为读写缓冲区；fd 为 socket
//   ACCEPT:     fd 为监听 socket；buf/len 忽略
//   CONNECT:    fd 为 socket；ip/port 为目标地址（host 字节序）
//   TIMEOUT:    len 为超时毫秒数；fd/buf 忽略
// token 由调用方传入，完成时原样回传。返回 1=已提交，0=失败。
int   proactorSubmit(void* p, int fd, void* buf, int len,
                     uint32_t ip, uint16_t port, int op, uint64_t token);

// 取回一批完成事件，填充 outEvents（ProactorEvent 数组，最多 outCap 个）。
// timeoutMs: >0 等待毫秒数，0 立即返回，<0 无限等待。
// 返回就绪事件数；0=超时/无完成，-1=错误。
int   proactorPoll(void* p, void* outEvents, int outCap, int timeoutMs);

// 销毁 Proactor
void  proactorDestroy(void* p);
