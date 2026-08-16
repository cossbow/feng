# Redis 风格数据结构 —— 设计文档（Fēng 实现）

> 系统描述 `samples/redis` 中各组件的架构、算法、接口与设计取舍。

---

## 1. 概述

### 1.1 定位与目标

`samples/redis` 是一个**独立 Fēng 项目**（独立 package，名 `redis`），
实现一组 Redis 风格的**内存数据结构**，作为 Redis 服务端核心存储层的基础。

设计遵循两条主线：

1. **数据结构本体**：只做「可独立验证的内存结构」，每个结构自带随机一致性单测；
2. **内容即身份**：key / field / member 的身份都是「字节内容」而非引用，
   不同实例、相同内容视为同一元素（与 Redis 语义一致）。

### 1.2 非目标（明确不做）

网络协议、命令解析器（RESP）、持久化（RDB/AOF）、过期/淘汰策略、事务、
主从复制、发布订阅，以及 Stream / Bitmap / HyperLogLog / Geo。
这些属于服务端上层，与数据结构本体无关。

### 1.3 类型映射

| Redis 类型 | Fēng 实现 |
|-----------|----------|
| String | 直接 `[*]byte`（可空 `[*?]byte`），无类封装 |
| List | `List` 封装 `std$container` 的 `Deque`[*]byte`` |
| Hash | `Hash` 封装 `Dict`[*]byte, [*]byte`` |
| Set | `Set` 封装 `Dict`[*]byte, bool`` |
| ZSet | `ZSet` = `Dict`(member→score) + `Skiplist`（带 span） |
| 顶层 keyspace | `Keyspace` = `Dict`[*]byte, *Value``（`Value` 为 tagged union） |

---

## 2. 总体架构

### 2.1 文件清单

| 文件 | 职责 |
|------|------|
| `dict.feng` | 泛型 `Dict`K,V``（渐进式 rehash 哈希表） |
| `string.feng` | byte 基础：hash / equal / cmp / length / substr |
| `list.feng` | Redis List（Deque 封装） |
| `skiplist.feng` | 带 span 的顺序统计跳表（ZSet 有序层） |
| `zset.feng` | Redis ZSet（Dict + 跳表双索引） |
| `hash.feng` | Redis Hash（Dict 封装） |
| `set.feng` | Redis Set（Dict 封装） |
| `keyspace.feng` | 顶层 keyspace（统一键值容器） |
| `test_*.feng` | 各组件单元测试（与源码同目录） |

### 2.2 依赖分层（自底向上）

```
string.feng ────────────────────────────────────┐
                                                ├──► hash.feng ─────┐
dict.feng ──────────────────────────────────────┤──► set.feng ──────┤
                                                └──► zset.feng ─────┤──► keyspace.feng
std$rand ──────────► skiplist.feng ─────────────┘                   │
std$container ─────► list.feng ─────────────────────────────────────┘
```

依赖关系要点：

- `dict.feng` 是**自包含**的泛型容器，不依赖任何 Redis 组件（自带 `Result`/`DictEntry`）；
- `string.feng` 提供被多个组件复用的字节工具函数（hash/equal/cmp）；
- `hash.feng` / `set.feng` 直接复用 `Dict`，不引入新容器；
- `zset.feng` 依赖 `Dict`（member→score）与 `Skiplist`（有序层）两套索引；
- `keyspace.feng` 是最终整合层，依赖其余全部类型。

### 2.3 公共约定

- **工厂函数**：每个容器暴露 `newXxx()` 工厂（如 `newDict` / `newList` / `newZSet`），
  封装内部字段初始化，调用方不直接 `new(Xxx)`；
- **可空返回**：越界/不存在统一返回 `nil`（可空引用）或 `(v, ok=false)` 元组，
  不抛业务异常（仅内部不变量违例才 `throw`）；
- **命令映射**：方法名对齐 Redis 命令语义（`lpush`/`hset`/`zadd`…），详见 §8。

---

## 3. 基础层

### 3.1 泛型哈希表 `Dict`K,V``

**职责**：可扩容缩容的哈希表，是 Hash / Set / ZSet / keyspace 的共同底座。

**关键设计**：

| 特性 | 说明 |
|------|------|
| 哈希/相等函数化 | 字段 `hash func(K) int`、`equal func(a,b K) bool` 直接作用于 key，不要求构造假 Node |
| 渐进式 rehash | `ht0` + `ht1` + `rehashIdx`，每次操作迁移最多 1 个桶，避免 O(n) 长停顿 |
| 扩容/缩容滞回 | 负载 ≥ 0.75 翻倍扩容；< 0.25 减半缩容（下限 16 桶）；滞回带避免阈值附近抖动 |
| 双表查找 | rehash 期间 `find`/`remove` 双表查找，新元素插入 `ht1` |
| 返回值 | 自带 `Result`V``（`{v, ok}`）而非 std 容器，保持自包含 |

**核心 API**：

| 方法 | 语义 | 返回 |
|------|------|------|
| `set(key, value)` | 新增或覆盖 | `{ok=true}`（新增）；`{v=old}`（覆盖） |
| `get(key)` | 查找 | `{ok=true, v}`；不存在 `{}` |
| `remove(key)` | 删除 | `{ok=true, v}`；不存在 `{}` |
| `size()` / `isEmpty()` / `clear()` | 属性 | — |
| `iterator()` | 遍历 ht0 + ht1 两张表 | `*DictIter`K,V`` |

**rehash 细节**：`rehashStep(n)` 从 `ht0[rehashIdx]` 起迁移桶，迁移完则 `ht0=ht1`、`ht1=nil`。
迁移按 `hash(key) & (ht1.length-1)` 重新落位，链表顺序保持。

### 3.2 byte 基础（`string.feng`）

`String` 本体直接用 `[*]byte`，不封装类。本模块提供其依赖的纯函数：

| 函数 | 签名 | 说明 |
|------|------|------|
| `hashBytes` | `([*]byte) int` | FNV-1a 64-bit，截断为 `int` |
| `bytesEqual` | `([*]byte, [*]byte) bool` | 逐字节相等 |
| `bytesCmp` | `([*]byte, [*]byte) int` | 字典序 memcmp，返回 -1/0/1 |
| `length` | `([&#]byte) int` | 字节数 |
| `substr` | `([&#]byte, int, int) [*]byte` | 按字节区间 `[start,end)` 切片，越界钳制 |

**参数类型的刻意区分**（关键设计点）：

- `hashBytes` / `bytesEqual` / `bytesCmp` 用 `[*]byte`（可写强引用）——
  因为 `Dict`[*]byte,V`` 的 `hash`/`equal` 字段要求**参数类型精确匹配**
  （Fēng 函数原型不支持参数逆变），`[&#]byte` 无法赋给该字段；
- `length` / `substr` 用 `[&#]byte`（只读虚引用视图）——
  可接受 `[*]byte`、`[*#]byte`（字符串字面量）、`[&]byte` 等任意字节数组，
  因为字符串字面量是 `[*#]byte`（不可修改），不能转 `[*]byte`。

---

## 4. 数据类型层

### 4.1 String

**无类封装**：直接以 `[*]byte` 表达，可空用 `[*?]byte`（`nil` 表示不存在）。
Redis 二进制安全：字符串即字节序列，不解释字符编码。

### 4.2 List

**结构**：`List` 内部持 `dq *c$Deque`[*]byte``，复用 `std$container` 的环形缓冲区双端队列。

**API**：

| 方法 | 语义 |
|------|------|
| `lpush` / `rpush` | 双端插入 O(1) |
| `lpop` / `rpop` | 双端弹出 O(1)，空表返回 `nil` |
| `index(i)` | 按索引读，支持负索引（-1 = 尾），越界返回 `nil` |
| `set(i, v)` | 按索引写，越界抛 `OutOfBoundsException` |
| `range(start, stop)` | 区间截取（负索引 + 越界钳制），返回新 `*List` |
| `trim(start, stop)` | 原地保留区间，O(n) |
| `length` / `isEmpty` / `clear` | 属性 |

**关键点**：

- 为 `std$container` 的 `Deque` 补充了 `get`/`set` 随机访问（`(head+i)%buf.length`，纯增量、向后兼容）；
- `range` 返回新 `List`；`trim` 用「popFront 去头 + popBack 去尾」原地实现，避免中间临时数组。

### 4.3 Hash

**结构**：`Hash` 封装 `Dict`[*]byte, [*]byte``（field → value，身份均为字节内容）。

**API**（对应命令）：`set`(HSET) / `get`(HGET) / `del`(HDEL) / `len`(HLEN) /
`exists`(HEXISTS) / `all`(HGETALL，返回 field/value 交错的 `*List`)。

### 4.4 Set

**结构**：`Set` 封装 `Dict`[*]byte, bool``（member → 占位 bool，仅用 key 判存在/去重）。

**API**（对应命令）：`add`(SADD) / `remove`(SREM) / `contains`(SISMEMBER) /
`card`(SCARD) / `members`(SMEMBERS，返回 `*List`)。

### 4.5 Skiplist（ZSet 有序层）

**结构**：`Skiplist`M`` 泛型跳表，排序键为 `(score, member)`，
`cmp func(M, M) int` 负责 member 比较。节点 `SkipNode`M`` 存
`score float64` + `member M`，每条 `forward[i]` 附带 `span[i]`（跨越节点数）。

**关键设计**：

| 特性 | 说明 |
|------|------|
| 顺序统计 | `span` 使 `rank`/`getByRank` O(log n)，无需逐元素计数 |
| 随机层高 | 1/2 概率晋升（`std$rand`），`skiplistMaxLevel = 32` |
| 哨兵 header | 满层哨兵节点，`clear` 重建 header 复用对象 |
| 重复拒绝 | 相同 `(score, member)` 插入返回 `false` |
| 区间定位 | `firstInRange` / `lastInRange` 按 score 闭区间 `[min,max]` 定位 |

**API**：

| 方法 | 语义 |
|------|------|
| `insert(score, member)` | 插入，重复返回 `false` |
| `remove(score, member)` | 删除，不存在返回 `false` |
| `find(score, member)` | 判断存在 |
| `rank(score, member)` | 1-based 升序名次，不存在返回 0 |
| `getByRank(r)` | 返回第 r 个元素 `(member, score, ok)` |
| `firstInRange` / `lastInRange` | score 闭区间首/尾元素 |
| `size` / `isEmpty` / `clear` | 属性 |

**span 维护细节**：插入/删除用 `update[i]`（第 i 层前驱）+ `rank[i]` 记录每层跨度，
通过 span 差量 `(rank[0] - rank[i])` 计算新节点跨度，避免逐元素计数。

### 4.6 ZSet（sorted set）

**结构**：双索引，两者始终一致：

1. `dict`（`Dict`*ZMember, float64``）：member → score，O(1) 查分/判存在/ZINCRBY；
2. `sl`（`Skiplist`*ZMember``）：按 (score, member) 有序 + span，O(log n) 范围/rank/遍历。

**member 身份**：`ZMember` 是包装类（`const data [*]byte`），
dict 的 hash/equal 与跳表的 cmp 均按 `data` 字节内容比较，
不同实例、相同内容的 member 视为同一元素。

**API**（对应命令）：

| 方法 | 命令 |
|------|------|
| `add` | ZADD |
| `remove` | ZREM |
| `score` | ZSCORE |
| `card` | ZCARD |
| `incrBy` | ZINCRBY |
| `rank` / `revRank` | ZRANK / ZREVRANK |
| `rangeByRank` / `revRangeByRank` | ZRANGE / ZREVRANGE（by rank） |
| `rangeByScore` / `revRangeByScore` | ZRANGEBYSCORE（正/逆） |
| `count` | ZCOUNT |

**score 变更一致性**：`incrBy`/`add`（改分）先 `sl.remove(旧分, key)` 再 `sl.insert(新分, key)`
最后 `dict.set(key, 新分)`，保证两索引始终同步。

---

## 5. 顶层 keyspace

### 5.1 需求

Redis 顶层是 `Dict`(key → 值)，值需区分类型（String/List/Hash/Set/ZSet）。

### 5.2 设计：int 类型标记 + tagged union

承载「多种可能类型 + 判别」的正确工具**本就是类**：Fēng 的 `struct`/`union`
属于纯值内存层（字段只能是整数/浮点/结构类型及定长数组，禁止引用），
服务于 `sizeof` / mappable / FFI，本就不承担持有对象引用的职责；
而类的字段可以是引用，天然就是 tagged union 的载体。

类型标记语义上 `enum` 是最佳选择（强类型，`.id`/`.value` 为整数），
但当前编译后端尚未生成枚举的 C 符号（`enum` 类型报 `unknown type name`、
枚举值报 `$STR` undeclared），在「不改编译器」约束下暂不可用，
故用 **`const int` 常量 + tagged union 包装类 `Value`** 表达：

```feng
const valueTypeStr  = 0;   // String
const valueTypeList = 1;   // List
const valueTypeHash = 2;   // Hash
const valueTypeSet  = 3;   // Set
const valueTypeZSet = 4;   // ZSet

class Value final {
    var kind int;
    var str   [*?]byte;    // 仅 kind == valueTypeStr 时非空
    var list  *?List;      // 仅 kind == valueTypeList 时非空
    var hash  *?Hash;      // 仅 kind == valueTypeHash 时非空
    var set   *?Set;       // 仅 kind == valueTypeSet 时非空
    var zset  *?ZSet;      // 仅 kind == valueTypeZSet 时非空
}
```

### 5.3 Keyspace API

| 方法 | 命令 | 说明 |
|------|------|------|
| `set(key, v)` | SET | 新增返回 `true`，覆盖返回 `false`（值已更新） |
| `get(key)` | GET | 返回 `*?Value`，不存在 `nil` |
| `typeOf(key)` | TYPE | 返回 `(kind, ok)`，不存在 `ok=false` |
| `del(key)` | DEL | 删除成功返回 `true` |
| `exists(key)` | EXISTS | 是否存在 |
| `size` / `isEmpty` / `clear` | DBSIZE / FLUSHDB | 属性 |

`Keyspace` 内部是 `Dict`[*]byte, *Value``，key 身份为「字节内容」（复用 `hashBytes`/`bytesEqual`）。

### 5.4 类型安全访问器

`kind` 是裸 `int`，直接比较会丢失强类型。为把「判别 + 强类型转型」收口到一处，
`Value` 提供一组访问器（对齐 enum 的 `.name` 与类型安全向下转型语义）：

| 方法 | 返回 | 说明 |
|------|------|------|
| `typeName()` | `[*#]byte` | 返回 `"string"`/`"list"`/`"hash"`/`"set"`/`"zset"`（对齐 TYPE 命令） |
| `asString()` | `[*?]byte` | `kind == valueTypeStr` 返回 `str`，否则 `nil` |
| `asList()` | `*?List` | `kind == valueTypeList` 返回 `list`，否则 `nil` |
| `asHash()` | `*?Hash` | `kind == valueTypeHash` 返回 `hash`，否则 `nil` |
| `asSet()` | `*?Set` | `kind == valueTypeSet` 返回 `set`，否则 `nil` |
| `asZSet()` | `*?ZSet` | `kind == valueTypeZSet` 返回 `zset`，否则 `nil` |

调用方推荐经 `get` 得到 `*Value` 后，直接调用 `asXxx()` 做类型安全的向下转型，
而非裸比较 `kind` 再手工解包——`int` 标记因此被隔离在 `Value` 内部，
在 enum 后端可用之前，由访问器承担强类型语义。

---

## 6. 复杂度分析

| 组件 | 操作 | 复杂度 |
|------|------|--------|
| `Dict` | set / get / remove | 均摊 O(1)（渐进 rehash，每次迁移 1 桶） |
| `Dict` | 扩容/缩容 | 均摊 O(1)，单次不阻塞 |
| String 纯函数 | hashBytes / bytesEqual / bytesCmp | O(n)（n = 字节数） |
| `List` | lpush / rpush / lpop / rpop / index / set | O(1) |
| `List` | range / trim | O(k) / O(n) |
| `Skiplist` | insert / remove / find / rank / getByRank | O(log n) 期望 |
| `Skiplist` | firstInRange / lastInRange | O(log n) |
| `ZSet` | ZADD / ZREM / ZINCRBY / ZRANK / ZRANGEBYSCORE | O(log n) 期望 |
| `ZSet` | ZSCORE / ZCARD | O(1) |
| `Hash` / `Set` | 全部单元素操作 | 均摊 O(1) |
| `Keyspace` | set / get / typeOf / del / exists | 均摊 O(1) |

空间：`Dict` 负载因子维持在 [0.25, 0.75] 区间；`Skiplist` 节点期望层高 2（每层 span 为 O(1) 增量）。

---

## 7. 设计决策与取舍

| # | 决策 | 理由 |
|---|------|------|
| 1 | 哈希表用 `Dict`K,V`` 而非旧 `HashMap` | 旧的不缩容、hash 挂在 Node 上；`Dict` 支持渐进 rehash + 缩容 |
| 2 | ZSet 有序层用跳表（带 span）而非红黑树 | rank 需要顺序统计，span 天然 O(log n)；红黑树需额外维护子树大小 |
| 3 | String 直接用 `[*]byte` 不封装类 | 避免多余包装，可空用 `[*?]byte` |
| 4 | Hash / Set 直接复用 `Dict` | 不引入 `HashSet`，`Dict` 已是通用底座 |
| 5 | keyspace 用「int 标记 + tagged union 类」 | 异构引用本就用类表达（union 属纯值层）；类型标记语义上应用 enum，后端暂未实现（见 §5.2） |
| 6 | 成员身份 = 字节内容 | 与 Redis 语义一致，跨实例同内容视为同一元素 |

**已知后端规避（未改编译器）**：

- `Skiplist` 泛型实参不能用 `[*]byte`——编译后端对「泛型零值 `var zero M`」在
  M=`[*]byte`（变长数组，C 侧为结构体）时错误生成 `= NULL`，故 ZSet 的 member
  用包装类 `*ZMember`（指针零值 NULL 合法）规避；
- `enum` 语义上适合做类型标记，但当前后端不生成 C 符号，keyspace 用 `const int` 常量替代。

---

## 8. API / 命令映射速查

### 8.1 工厂函数

| 工厂 | 签名 | 用途 |
|------|------|------|
| `newDict` | `newDict`K,V``(hash, equal) | 泛型哈希表 |
| `newList` | `newList()` | Redis List |
| `newSkiplist` | `newSkiplist`M``(cmp) | 泛型跳表 |
| `newZSet` | `newZSet()` | Redis ZSet |
| `newHash` | `newHash()` | Redis Hash |
| `newSet` | `newSet()` | Redis Set |
| `newKeyspace` | `newKeyspace()` | 顶层 keyspace |
| `newStringValue` / `newListValue` / `newHashValue` / `newSetValue` / `newZSetValue` | 各类型 → `*Value` | keyspace 值包装 |

### 8.2 命令映射

| Redis 命令 | 组件 | Fēng 方法 |
|-----------|------|----------|
| SET / GET / DEL / EXISTS / TYPE | Keyspace | `set` / `get` / `del` / `exists` / `typeOf` |
| LPUSH / RPUSH / LPOP / RPOP | List | `lpush` / `rpush` / `lpop` / `rpop` |
| LINDEX / LSET | List | `index` / `set` |
| LRANGE / LTRIM | List | `range` / `trim` |
| HSET / HGET / HDEL / HLEN / HEXISTS / HGETALL | Hash | `set` / `get` / `del` / `len` / `exists` / `all` |
| SADD / SREM / SISMEMBER / SCARD / SMEMBERS | Set | `add` / `remove` / `contains` / `card` / `members` |
| ZADD / ZREM / ZSCORE / ZCARD | ZSet | `add` / `remove` / `score` / `card` |
| ZINCRBY | ZSet | `incrBy` |
| ZRANK / ZREVRANK | ZSet | `rank` / `revRank` |
| ZRANGE / ZREVRANGE | ZSet | `rangeByRank` / `revRangeByRank` |
| ZRANGEBYSCORE | ZSet | `rangeByScore` / `revRangeByScore` |
| ZCOUNT | ZSet | `count` |

---

## 9. 测试约定

- 测试文件与源码同目录，命名 `test_<模块>.feng`，使用 `std$testing` 的 `@Test`；
- 编译运行：
  ```bash
  java -jar target/feng-0.0.1-dev.jar -Lstd=std -p redis -t p -T -b m -D \
      -i samples/redis -o <outdir>
  ./<outdir>/redis
  ```
- 容器正确性测试尽量加**随机压力用例**（如随机 ZADD/ZREM/ZINCRBY 后校验
  Dict 与跳表镜像一致），并做全量反查；
- 当前共 46 个用例全过（Dict 6 + String 6 + List 8 + Skiplist 6 + ZSet 8
  + Hash 4 + Set 4 + keyspace 4）。

---

## 10. 参考

- `ai/specification.md` — 语言语义唯一权威
- `std/hash/fnv.feng` — FNV-1a（byte hash 参考）
