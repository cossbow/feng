# Redis 服务端 —— 设计与实现计划（Fēng）

> 在 `samples/redis` 数据结构层之上，补齐「服务端上层」：连接管理、事件循环、RESP 命令分发。
> 本文档描述架构、模块职责、关键设计取舍与分阶段实现计划。

---

## 1. 定位与目标

`design.md`（数据结构设计）明确把「网络协议 / 命令解析（RESP）/ 服务端上层」列为非目标。
本文档正是补齐这一层，把已有的三块资产串成一个**可运行的内存 Redis 服务端**：

1. `samples/redis/*` —— 数据结构与 `Keyspace`（已就绪）；
2. `std/redis` —— RESP2 编解码（已就绪）；
3. `std/net`（`net.feng` + `proactor.feng`）—— 阻塞与异步（IOCP）网络（已就绪）。

目标产物：单进程、单线程事件循环、基于 proactor 的异步 Redis 服务端，支持核心命令族。

### 1.1 非目标（本期不做）

持久化（RDB/AOF）、过期/淘汰、事务（MULTI/EXEC）、主从复制、发布订阅、
Stream / Bitmap / HyperLogLog / Geo、TLS、认证/ACL、多线程执行模型。

---

## 2. 现状盘点

### 2.1 已具备（直接复用）

| 层 | 位置 | 能力 |
|----|------|------|
| 数据结构 | `samples/redis/*.feng` | `Keyspace` + `List/Hash/Set/ZSet`，命令级 API + 工厂函数 |
| 类型安全访问 | `keyspace.feng` 的 `Value` | `asString/asList/asHash/asSet/asZSet`、`typeName` |
| RESP 编解码 | `std/redis/redis.feng` | `parse`（`(值, 消费字节数, ok)`）、`encode`、`mustParse`、构造器 |
| 阻塞网络 | `std/net/net.feng` | `listenTCP` / `socketFromFd` / `newSocket` / `setNonBlocking` / `Addr` |
| 异步 I/O | `std/net/proactor.feng` | `newProactor` / `submitRead` / `submitWrite` / `submitAccept` / `submitTimeout` / `poll` |
| 数值解析/格式化 | `std/strconv` | `parseInt` / `parseInt64` / `formatInt` / `formatInt64` |
| 字节缓冲 | `std/bytes` | `BufferWriter`（实现 `Writer` 接口，供 `encode` 使用） |

### 2.2 缺口（本期要实现）

| # | 缺口 | 说明 |
|---|------|------|
| 1 | 事件循环 + `main` | `samples/redis` 现无 `main`，无 accept/read/write 的调度循环 |
| 2 | 连接状态管理 | `fd → 连接状态` 映射：读缓冲、写缓冲、状态位 |
| 3 | 命令分发层 | RESP 数组 → `Keyspace`/各类型操作 → RESP 回复 |
| 4 | 写侧适配 shim | `encode` 产出 `[*#]byte`，需适配 proactor 的 4KB `*Buf` 并分块 |
| 5 | 命令名大小写无关比较 | `bytesEqual` 为严格比较，Redis 命令不区分大小写 |

---

## 3. 总体架构

```
                    ┌─────────────────────────────────────────┐
                    │            server.feng（事件循环）        │
                    │  main → listen → associate → submitAccept │
                    │  loop: poll(completions)                  │
                    └───────┬───────────────────────┬──────────┘
             PRO_ACCEPT/READ │                WRITE │
                    ┌───────▼────────┐   ┌──────────▼─────────┐
                    │  连接状态 Client │   │  写缓冲分块 flush    │
                    │  readBuf 累积    │   │  [*]#byte → Buf      │
                    └───────┬────────┘   └─────────────────────┘
                            │ 完整帧
                    ┌───────▼──────────────────────────────┐
                    │      command.feng（命令分发）          │
                    │  命令名匹配 → Keyspace/类型操作 → 回复  │
                    └───────┬──────────────────────────────┘
                            │
                    ┌───────▼────────┐      ┌───────────────┐
                    │ std$redis(RESP) │      │ redis(Keyspace)│
                    └────────────────┘      └───────────────┘
```

依赖方向（自顶向下，单向）：

```
server.feng ──► command.feng ──► keyspace.feng / list.feng / hash.feng / set.feng / zset.feng
     │                │
     └── std$net ──┐   └── std$redis
                   └──► std$net$platform（C 后端）
```

---

## 4. 模块设计

### 4.1 连接状态 `Client`（`server.feng` 内）

```feng
class Client final {
    var sock *Socket;      // 对端 socket
    var readBuf  [*]byte;  // 读缓冲：累积不完整 RESP 帧（增长式）
    var writeBuf [*]byte;  // 写缓冲：待发送字节（含已编码回复）
    var writeOff int;      // 写缓冲已发送偏移
    var buf *Buf;          // 复用 4KB I/O 缓冲（@Sync，见 §6）
    var closing bool;      // 待关闭标记
}
```

**`fd → Client` 映射**：proactor 完成事件 `Completion.fd` 即客户端 fd（accept 事件的 `result`
为新客户端 fd，`fd` 为 listener fd）。用 `fd` 作键即可定位连接，token 保持 opaque 不参与寻址。

实现用 `Dict`int, *Client``（复用 `samples/redis` 的 `Dict`，配 `intHash`/`intEqual`），
或按 `fd` 索引的稀疏表。**初版取 `Dict`**，理由：fd 在 Windows 上无紧凑上限，哈希表最稳。

### 4.2 事件循环 `Server`（`server.feng`）

单线程 proactor 循环，伪代码：

```
func main():
    ensureStartup()
    listener = listenTCP(resolve("127.0.0.1", 6379))
    listener.setNonBlocking()
    p = newProactor()
    p.associate(listener)
    p.submitAccept(listener)
    loop:
        n = p.poll(completions, -1)
        for each ev in completions:
            switch ev.op:
                PRO_ACCEPT:  onAccept(ev)   // result = 新 fd
                PRO_READ:    onRead(ev)     // result = 读入字节数
                PRO_WRITE:   onWrite(ev)    // result = 已写字节数
                PRO_TIMEOUT / 错误: 清理
```

- **onAccept**：`socketFromFd(ev.result)` → `associate` → 建 `Client`（分配 `@Sync Buf`）→
  `submitRead`；随后对 listener 再次 `submitAccept`（保持持续 accept）。
- **onRead**：把 `buf.data[0..result]` 追加进 `client.readBuf`，循环尝试 `redis$parse`，
  凑齐一帧即 `dispatch`，剩余字节继续解下一帧（支持命令流水线）。
- **onWrite**：`writeOff += result`；未发完则 `flush` 继续 `submitWrite` 剩余块。

### 4.3 命令分发 `Command`（`command.feng`）

输入：一个 RESP `Array`（首元素命令名，其余为参数，均为 BulkString）。
输出：RESP 回复。

流程：

1. **命令名归一化**：转大写（或小写）后匹配；Redis 命令大小写不敏感。为此补一个
   `bytesEqualFold` / `bytesToUpper`（`string.feng` 或 command 层本地实现）。
2. **分发**：初版用手写 `if/else` 链按命令名匹配（命令数可控，直接、无函数表开销）。
   函数表（`Dict`[*]byte, 处理函数``）留作命令量增大后的优化，需先确认「函数原型」作泛型
   实参 + 统一签名 `func(cmd, ks) RespValue` 的可行性。
3. **参数校验**：个数不足/过多、整数参数解析失败（`strconv$parseInt`）→ `-ERR`。
4. **类型检查**：命令作用于错误的键类型（如对 String 执行 LPUSH）→ `-WRONGTYPE`。
5. **执行**：调用 `Keyspace` 或经 `Value.asXxx()` 转型后的类型方法。

### 4.4 写侧适配：`[*]#byte → Buf` 分块（`server.feng`）

`std/redis.encode` 返回 `[*#]byte`（不可变），而 `submitWrite` 只收 `@Sync *Buf`（4KB）+ `int32 len`。
适配层：

```
queueWrite(client, bytes [*#]byte):
    把 bytes 追加进 client.writeBuf
    若当前无写在进行 → flush(client)

flush(client):
    remaining = writeBuf.length - writeOff
    n = min(remaining, 4096)
    copy writeBuf[writeOff .. writeOff+n] → buf.data   // [&]byte 视图复制
    submitWrite(sock, buf, int32(n))                    // 返回 token，等 PRO_WRITE 完成
```

大响应（`LRANGE`/`HGETALL`/`SMEMBERS`）天然分块发送，写完成事件驱动下一块，避免 4KB 上限阻塞。

### 4.5 读侧：跨包累积 + 截断判定

- TCP 分包/半包 → 读入字节**跨多次 read 累积**进 `client.readBuf`。
- `redis$parse` 在「未到齐」与「协议非法」时都返回 `ok=false`，无法仅凭返回值区分。
  **初版策略**：`ok=false` 即继续读（等下一块数据）；仅当读返回 0（对端关闭）或读错误时关闭连接。
  另设 `readBuf` 上限（如 64KB），超限视为恶意连接，直接关闭。
  （若后续要精确区分非法协议，可在 `std/redis` 增加「错误 vs 截断」的区分返回值，本期不做。）

---

## 5. 关键设计决策与取舍

| # | 决策 | 理由 |
|---|------|------|
| 1 | 单线程事件循环（proactor） | `proactor` 的 token 计数「单线程内递增」，模型即单线程；IOCP 完成通知天然适配 |
| 2 | `fd → Client` 用 `Dict` 而非定长数组 | fd 无紧凑上限，哈希表最稳；token 保持 opaque |
| 3 | 命令分发用手写 if/else 而非函数表 | 命令量可控，直接无额外抽象；函数表需先验证函数原型作泛型实参可行性 |
| 4 | 写侧「写缓冲 + 分块 flush」 | `Buf` 固定 4KB，大响应必须分块；写完成事件驱动下一块 |
| 5 | `ok=false` 一律视为「未到齐」继续读 | 简化协议非法判定，配 64KB 上限防内存放大 |
| 6 | 命令名转大写后匹配 | Redis 语义大小写不敏感，归一化后单点比较 |

---

## 6. 并发模型

- **单线程**：`Keyspace` 与各数据结构单线程访问，**无需加锁，不标类级 `@Sync`**。
- **唯一跨 `@Async` 边界的对象是 `*Buf`**（经 `innerSubmit` 交给内核）：`Buf` 是无引用字段的
  struct，天然可同步，用 `@Sync var buf = new(Buf)` 声明即可。
- **`Client` 不标 `@Sync`**：其含 `[*]byte` 变长数组（不可同步），单线程下普通强引用即可，
  且不应穿过并发边界。
- 复用 `samples/redis` 既有约束：`@Sync var x = factory()` 靠类型推断自动标 sync；跨线程共享
  引用字段才需字段级 `@Sync`（本服务端单线程，不涉及）。

---

## 7. 命令清单与映射

### 7.1 通用

| 命令 | 回复 | 说明 |
|------|------|------|
| PING | `+PONG` / `$msg` | 无参 → SimpleString；带参 → BulkString 回显 |
| ECHO msg | `$msg` | BulkString 回显 |
| QUIT | `+OK` 后关闭 | 标记 closing |
| COMMAND | `+OK`（占位） | 完整命令元数据留后期 |

### 7.2 String / Keyspace

| 命令 | 组件 | 方法 | 回复 |
|------|------|------|------|
| SET k v | Keyspace | `set` → `newStringValue` | `+OK` |
| GET k | Keyspace | `get` → `asString` | `$v` / `$-1` |
| DEL k... | Keyspace | `del` | `:n`（删除数） |
| EXISTS k... | Keyspace | `exists` | `:n`（存在数） |
| TYPE k | Keyspace | `typeOf` | `+string/list/hash/set/zset` / `+none` |
| DBSIZE | Keyspace | `size` | `:n` |
| FLUSHDB | Keyspace | `clear` | `+OK` |

### 7.3 List / Hash / Set / ZSet（复用 `design.md` §8 的 API）

| 命令族 | 组件 | 方法 |
|--------|------|------|
| LPUSH/RPUSH/LPOP/RPOP/LINDEX/LSET/LRANGE/LTRIM/LLEN | List | `lpush`/`rpush`/`lpop`/`rpop`/`index`/`set`/`range`/`trim`/`length` |
| HSET/HGET/HDEL/HLEN/HEXISTS/HGETALL | Hash | `set`/`get`/`del`/`len`/`exists`/`all` |
| SADD/SREM/SISMEMBER/SCARD/SMEMBERS | Set | `add`/`remove`/`contains`/`card`/`members` |
| ZADD/ZREM/ZSCORE/ZCARD/ZINCRBY/ZRANK/ZREVRANK/ZRANGE/ZREVRANGE/ZRANGEBYSCORE/ZCOUNT | ZSet | `add`/`remove`/`score`/`card`/`incrBy`/`rank`/`revRank`/`rangeByRank`/`revRangeByRank`/`rangeByScore`/`count` |

> 各类型命令需处理「类型不匹配」：`get` 后 `asList/asHash/…` 返回 `nil` 即 `-WRONGTYPE`。
> 参数中整数（LINDEX 的 index、ZADD 的 score、EXISTS 的多 key）用 `strconv$parseInt`/`parseInt64` 解析，
> 失败返回 `-ERR value is not an integer or out of range`。

---

## 8. 实现计划（分阶段）

| 阶段 | 内容 | 产物 | 验证 |
|------|------|------|------|
| **P0 骨架** | `server.feng`：`main` + proactor 事件循环 + `Client` + 读累积/写分块；`command.feng`：分发框架 + PING/ECHO/QUIT | 能启动、`redis-cli ping` 通 | `redis-cli` / 自写客户端 |
| **P1 String/Keyspace** | SET/GET/DEL/EXISTS/TYPE/DBSIZE/FLUSHDB | 字符串与键空间命令 | `redis-cli` 全命令冒烟 |
| **P2 List** | LPUSH/RPUSH/LPOP/RPOP/LINDEX/LSET/LRANGE/LTRIM/LLEN | List 命令族 | 命令冒烟 + 边界（负索引、越界） |
| **P3 Hash/Set** | HSET/HGET/HDEL/HLEN/HEXISTS/HGETALL + SADD/SREM/SISMEMBER/SCARD/SMEMBERS | Hash/Set 命令族 | 命令冒烟 |
| **P4 ZSet** | ZADD/ZREM/ZSCORE/ZCARD/ZINCRBY/ZRANK/ZRANGE/ZRANGEBYSCORE/ZCOUNT | ZSet 命令族 | 命令冒烟 + 一致性抽查 |
| **P5 加固** | 大响应分块压测、断连/半包清理、读缓冲上限、`-WRONGTYPE`/`-ERR` 全覆盖 | 稳定性 | 压力测试 + 随机命令 |

每个阶段独立可编译运行；P0 跑通后逐族增量，不动已就绪的数据结构层。

### 8.1 编译运行（沿用 `design.md` §9 约定）

```bash
java -jar target/feng-0.0.1-dev.jar -Lstd=std -p redis -t p -T -b m -D \
    -i samples/redis -o <outdir>
./<outdir>/redis
```

（`-T` 为测试模式；正式跑服务端用非 `-T` 编译，`-t p` 产可执行程序。）

---

## 9. 测试计划

- **协议层单测**：`std/redis` 已有；补「命令名大小写」相关工具函数（`bytesEqualFold`）单测。
- **命令层单测**：对每个命令，构造 RESP 输入 → `dispatch` → 断言回复字节（独立于网络，可直接单测）。
- **端到端**：起服务端 + `redis-cli` 或自写 RESP 客户端脚本冒烟各命令族。
- **压力/边界**：大 `LRANGE`（>4KB 回复分块）、命令流水线、半包/粘包、非法协议、并发多连接。

---

## 10. 风险与开放问题

| 风险/问题 | 影响 | 应对 |
|-----------|------|------|
| `parse` 无法区分「截断 vs 非法」 | 非法协议连接会多读一轮才关闭 | 初版可接受；后期在 `std/redis` 加区分返回值 |
| `[*]byte` 切片（`buf[off..]`）语法待确认 | 读缓冲前进的实现方式 | 实现时确认；必要时用 `substr` 或重新分配 |
| 函数原型作 `Dict` 泛型实参（函数表分发） | 命令量大时 if/else 变长 | 先手写；验证后再演进 |
| Windows IOCP 语义（accept 的 result 为新 fd、需 SO_UPDATE_ACCEPT_CONTEXT） | 已在 C 后端 `fillEvent` 处理 | 无需额外处理，`socketFromFd` 包装即可 |
| 单线程阻塞风险（某命令 O(n)） | 大集合操作可能卡事件循环 | 数据结构已均摊/对数复杂度；大值读写不深拷，本期可接受 |

---

## 11. 参考

- `design.md` — 数据结构设计（API / 命令映射 / 测试约定）
- `ai/specification.md` — 语言语义唯一权威
- `std/redis/redis.feng` — RESP2 编解码
- `std/net/proactor.feng` + `std/net/platform/net_proactor.c` — IOCP 后端
- `reference_zh.md` — 语言参考（`@Async`/`@Sync` 并发模型见「并发检查机制」）
