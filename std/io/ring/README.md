# std$io$ring — io_uring / ioring 平台封装

> 本模块封装内核异步 I/O 提交队列（Linux io_uring / Windows ioring），
> 是 io 库异步能力（Proactor）的底层原语层。
> 公共层（proactor、file、socket）只需 `import std$io$ring`，无需感知平台差异。

## 设计：平台二选一，编译期多态

```
std/io/ring/
├── feng.cfg            # 平台白名单：每平台编译一个实现
├── ring_linux.feng     # [linux]   io_uring 实现（真实）
├── ring_windows.feng   # [windows] ioring 占位（接口对齐，未实现）
└── linux/
    ├── io_platform.h   # C 接口声明（std$io$ring$linux 模块）
    └── io_platform.c   # syscall shim（setup=425 / enter=426）
```

- 两个 .feng 文件导出**同名类 `Uring`、相同方法签名**，由 `feng.cfg` 白名单
  按平台二选一编译 → 平台多态无需接口/抽象类。
- 平台段 `feng = xxx.feng` 是文件白名单：该平台只编译列出的文件，
  未列出的平台专属文件（如 `ring_windows.feng`）不会进入构建。
- C 层（`linux/`）由编译器全量收集，平台区分靠 C 侧 `#ifdef`。

## 导入与前缀

```feng
import std$io$ring;
// 模块函数用 ring$ 前缀；Uring 类直接使用（newUring 返回实例）
```

## 公共接口契约

> 两个平台实现必须保持以下导出完全一致，新增成员须两端同步。

### 类 `Uring`

`Uring` 是 `@resource`：引用计数归零自动关闭，**纯自动关闭、无手动 `close()`**。
fd 由 `std$io$handle` 的 `Handle` 管理（成员 `h *handle$Handle`）：Uring 销毁 → `h`
引用归零 → Handle 自动 `close(fd)`。生命周期与 fd 一致，不会重复关闭、误杀后续 fd。

| 成员 | 签名 | 说明 |
|------|------|------|
| `submit` | `(sqe [64]byte) int` | 提交一个 SQE 到内核（不等待完成）。成功 0，失败 -1 |
| `pollOnce` | `(timeoutMs int) (bool, uint64, int)` | 收割一个完成事件，返回 `(完成?, userData, res)` |

`pollOnce` 的 `timeoutMs` 语义：

| 值 | 行为 |
|----|------|
| `< 0` | 阻塞等待直到有完成事件 |
| `= 0` | 非阻塞，无事件立即返回 `(false, 0, 0)` |
| `> 0` | 带超时（毫秒），超时返回 `(false, 0, 0)` |

`res` 为操作结果：`>= 0` 成功（read/write 为字节数），`< 0` 为负的 errno。

### 模块函数

| 符号 | 签名 | 说明 |
|------|------|------|
| `newUring` | `(n int) *Uring` | 创建 ring，容量约 n 个 SQE（`n <= 0` 时按 8）。失败抛 `error$IllegalStateException` |
| `sqeNop` | `(userData uint64) [64]byte` | 组装 NOP SQE（探活/测试用），`user_data` 写 @32 |
| `sqeRead` | `(fd int, addr uint64, len uint32, off uint64, userData uint64) [64]byte` | 异步读：addr=缓冲区指针，off=文件偏移（socket/pipe 填 0）。完成 `res`=读到的字节数 |
| `sqeWrite` | `(fd int, addr uint64, len uint32, off uint64, userData uint64) [64]byte` | 异步写：addr=缓冲区指针，off=文件偏移（socket/pipe 填 0）。完成 `res`=写入的字节数 |
| `sqeAccept` | `(fd int, addr uint64, addrLen uint64, flags uint32, userData uint64) [64]byte` | 接受连接：addr=sockaddr 缓冲区，addrLen=`socklen_t*` 指针。完成 `res`=新 fd |
| `sqeConnect` | `(fd int, addr uint64, addrLen uint32, userData uint64) [64]byte` | 发起连接：addr=sockaddr 缓冲区，addrLen=长度**值**。完成 `res`=0 表示已连接 |
| `sqeTimeout` | `(tsAddr uint64, userData uint64) [64]byte` | 定时器：tsAddr 指向稳定 timespec（sec@0/nsec@8 小端），`len` 恒 1、`off=0` 为纯超时。到期完成 `res`=-110（`-ETIME`） |

缓冲区/结构地址用 `buffer.values` 或 `mm$MMap.address()` 取 u64 指针；
**指向的数据须存活到对应 CQE 被 `pollOnce` 收割**（TIMEOUT 的 timespec 尤其如此）。

`res` 为操作结果：`>= 0` 成功（read/write 为字节数，accept 为新 fd），
`< 0` 为负的 errno（如 TIMEOUT 到期为 `-110` = `-ETIME`，被取消为 `-125` = `-ECANCELED`）。

### OP 常量（跨平台操作码抽象）

值与 Linux io_uring UAPI 对齐，作为两平台统一的操作码：

| 常量 | 值 | 操作 |
|------|----|------|
| `OP_NOP` | 0 | 空操作 |
| `OP_TIMEOUT` | 11 | 超时 |
| `OP_ACCEPT` | 13 | 接受连接 |
| `OP_CONNECT` | 16 | 发起连接 |
| `OP_READ` | 22 | 读 |
| `OP_WRITE` | 23 | 写 |

## 上层模块（proactor / handle）

```
std/io/
├── ring/       # 本模块：内核异步 I/O 原语（io_uring/ioring）
├── handle/     # fd 封装（@resource，RC 归零自动 close）
│               #   Handle + newSocket / newPipe / newSocketpair / openFile / handleFromFd
└── proactor/   # 完成通知模型（纯 Feng，构建于 ring + handle）
```

- `handle` 提供 `Handle` 基类（`const fd int` + `close()`，close 幂等）与
  `FileHandle` / `SocketHandle` 子类（**类型即区分**）及工厂函数；它是
  proactor / file / socket 的公共底层（file / socket 的阻塞读写类后置）。
  - 工厂按 fd 来源返回对应类型：`openFile`→`*?FileHandle`，
    `newSocket`/`newSocketpair`→`*?SocketHandle`，`newPipe` 与 ring fd 保持
    普通 `*Handle`（既非文件也非 socket）；`socketFromFd`/`fileFromFd` 包装
    外部 fd（accept 结果等）。
  - 协变：子类可作 `*Handle` 传给 `submitRead/Write`（任何 fd 通用）；
    `submitAccept/Connect` 只收 `*SocketHandle`——**编译期**杜绝文件/pipe fd
    被拿去 accept/connect（类型错编译即报，无需 errno 兜底）。
  - **只有 Handle 有手动 `close()`**（幂等：手动 close 与 `resource free()`
    共享同一 `_closeOnce` 路径）；ring / proactor 本身无 close。
- `proactor` 封装「提交 + 完成收割」：`submitRead/Write/Accept/Connect/Timeout`
  返回 token（0 表示提交失败），`poll(out [&]Completion, timeoutMs int32)` 批量
  收割完成事件（token/op/fd/result），由事件循环按 token 分发。
  **积木式组装**：引擎由调用方创建后传入，Proactor 只做组合——
  `var ring = ring$newUring(8); var pro = pro$newProactor(ring);`
  Proactor 持有 ring 强引用，纯 RC 生命周期（无 close）：引用归零 → ring 字段
  引用释放 → Uring 销毁 → Handle 归零自动关 fd。
  缓冲区由调用方传入（`byte 数组 .values` 或 `mm$MMap.address()`），
  生命周期由调用方保证；本层只登记 token → (op, fd, scratch) 用于回填与释放。
- 超时用 `IORING_OP_TIMEOUT`（内核调度）：`submitTimeout(ms)` 返回 PRO_TIMEOUT
  完成事件，无需事件循环自建定时器。

## 使用示例

```feng
import std$io$ring;

func main() {
    var ring = ring$newUring(8);

    // 组装 NOP SQE，user_data 关联本次完成事件
    var sqe = ring$sqeNop(0x12345678);

    // 提交内核（不阻塞等待）
    var r = ring.submit(sqe);
    if (r != 0) { /* 提交失败 */ }

    // 阻塞收割完成事件，最多等 1000ms
    var pr = ring.pollOnce(1000);
    if (!pr.0) { /* 超时无事件 */ }
    var userData = pr.1;   // 0x12345678 —— 与提交时一致
    var res = pr.2;        // NOP 恒为 0
}
```

## 内部布局参考（扩展新 OP 时用）

Linux 实现按内核 UAPI 字节复刻，`ring_linux.feng` 内硬编码偏移：

### SQE（64 字节，`submit` 的入参）

| 偏移 | 字段 | 说明 |
|------|------|------|
| 0 | opcode | 操作码（用 `OP_*` 常量） |
| 4 | fd | 目标 fd（int32；TIMEOUT 固定 -1） |
| 8 | off | 偏移量（u64，read/write）；**ACCEPT 时为 `socklen_t*` 指针（addr2）** |
| 16 | addr | 缓冲区/地址（u64） |
| 24 | len | 长度（u32）；**ACCEPT 恒 0，CONNECT 为 sockaddr 长度值，TIMEOUT 恒 1（内核强制）** |
| 28 | op_flags | 通用标志；**ACCEPT 为 accept_flags，TIMEOUT 为 timeout_flags** |
| 32 | user_data | 完成事件关联值（u64） |

各 OP 特殊布局约定（`ring_linux.feng` / `ring_windows.feng` 组装函数内硬编码）：

| OP | 关键差异 |
|----|----------|
| READ / WRITE | 标准布局：off@8=文件偏移，addr@16=数据缓冲区，len@24=字节数 |
| ACCEPT | `off@8(addr2)` 放 `socklen_t*` 指针，`len=0`，accept_flags 放 @28；完成 `res`=新 fd |
| CONNECT | `addr@16`=sockaddr，`len@24`=sockaddr 长度**值**（非指针） |
| TIMEOUT | `fd=-1`，`addr@16`=稳定 `struct __kernel_timespec`（sec@0/nsec@8 小端），`len@24` 恒 1，`off=0`（纯超时，到期 `res`=-110/-ETIME），timeout_flags@28=0（相对超时） |

### CQE（16 字节，`pollOnce` 内部解析）

| 偏移 | 字段 | 说明 |
|------|------|------|
| 0 | user_data | u64，与提交 SQE 的 user_data 一致 |
| 8 | res | i32 结果（>=0 成功 / <0 负 errno） |

### ring 内存（`newUring` 内部）

- `io_uring_params` 120 字节：`sq_entries@0`、`cq_entries@4`、`sq_off@40`、`cq_off@80`
- mmap 偏移：`IORING_OFF_SQ_RING=0x0`、`IORING_OFF_CQ_RING=0x8000000`、`IORING_OFF_SQES=0x10000000`
- ring 内存经 `std$io$mm` 的 `mm$mmapFd`（MAP_SHARED）映射，自动 munmap

## 扩展指南：新增一个 OP

1. **两端同步**：在 `ring_linux.feng` 与 `ring_windows.feng` 的 `OP_*` 常量区
   添加同值常量（Windows 侧值必须与 Linux UAPI 一致）。
2. **SQE 组装**：在 ring 模块新增 `sqeXxx(...) [64]byte` 导出函数，
   按上表偏移填充 opcode/fd/off/addr/len/user_data（小端）。缓冲区地址
   用 `buffer.values` / `mm$MMap.address()` 取 u64 指针。
3. **提交与收割**：`submit` / `pollOnce` 通用，无需改动；
   完成后在 `pollOnce` 返回的 `(userData, res)` 中按 opcode 分发结果。
4. **占位同步**：Windows 侧组装函数也须存在（可真实构造字节），
   保证 `ring_windows.feng` 编译通过、接口对齐。

## 平台实现状态

| 平台 | 实现 | 状态 |
|------|------|------|
| Linux | `ring_linux.feng` + `linux/io_platform.c` | 完整：setup→mmap 三区→submit→CQE 收割，syscall 425/426 直调；fd 由 Handle 管理，纯自动关闭 |
| Windows | `ring_windows.feng` | 接口占位：`sqe*` 组装函数与 Linux 同签名（纯字节构造，接口对齐）；`submit` 返回 -1、`pollOnce` 返回 `(false,0,0)`、`newUring` 抛异常；`TODO(windows)` 待接 IoRing / IOCP |

> 注意：openEuler 内核存在 io_uring 禁用补丁（`io_uring_enable=0`），
> 需置 1 后 syscall 才可用（详见项目记录）。
