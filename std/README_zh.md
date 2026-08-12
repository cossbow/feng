# Fēng 标准库参考手册

> 最后更新：2026-08-12  
> 涵盖所有 `std/` 下的模块，按功能域分组。

---

## 编译器内置符号

这些符号由编译器直接提供，无需 import。

| 符号 | 类型 | 说明 |
|------|------|------|
| `Object` | class | 所有（非 final）类的根 |
| `Exception` | class | 异常基类（字段 `fn`、`line`） |
| `NilException` | class | 空指针异常 |
| `OutOfBoundsException` | class | 越界异常 |
| `AssertException` | class | 断言失败异常 |
| `Writer` | interface | `write(data [&!]byte) int` |
| `Reader` | interface | `read(b [&!]byte) int` |
| `Writable` | interface | `write(w &!Writer) int` |
| `format` | func | `format(w &!Writer, fmt, ...)` |
| `assert` | func | `assert(cond bool)` |

---

## 1. 字符串与字节

### `std$string` — 不可变字符串

```feng
import std$string;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `String` | class (Writable) | 不可变 UTF-8 字符串 |
| `String.value` | const field | `[*?#]byte` 底层字节 |
| `String.bytes()` | method | `[*?#]byte` 获取底层字节切片 |
| `String.byteLength()` | method | `int` 字节长度 |
| `String.runeCount()` | method | `int` Unicode 码点数 |
| `String.isEmpty()` | method | `bool` 是否为空 |
| `String.compare(rhs String)` | method | `int` 字典序比较（<0 / 0 / >0） |
| `String.equals(other String)` | method | `bool` |
| `String.concat(other String)` | method | `String` 拼接 |
| `String.indexOf(ch int32)` | method | `int` 按 rune 查找，返回字节偏移，-1 未找到 |
| `String.indexOfStr(sub String)` | method | `int` 子串查找 |
| `String.contains(sub String)` | method | `bool` |
| `String.startsWith(prefix String)` | method | `bool` |
| `String.endsWith(suffix String)` | method | `bool` |
| `String.substring(start, end int)` | method | `String` 字节半开区间 `[start, end)` |
| `String.rune(index int)` | method | `int32` 按字符索引取 rune |
| `String.split(sep String)` | method | `[*]String` 切割 |
| `String.replace(oldStr, newStr String)` | method | `String` 全局替换 |
| `String.trim()` | method | `String` 去除首尾 ASCII 空白 |
| `String.toLower()` | method | `String` ASCII 转小写 |
| `String.toUpper()` | method | `String` ASCII 转大写 |
| `String.write#(w &Writer)` | method | `int` (Writable 实现) |
| `utf8(data [*#]byte)` | func | `String` 构造器 |
| `ascii(data [*#]byte)` | func | `String` 构造器（同 utf8） |
| `join(parts [&#]String, sep String)` | func | `String` 用分隔符拼接字符串数组 |
| `EMPTY` | const | `String` 空串 |

### `std$string` — 可变字符串构建

```feng
import std$string;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `StringBuilder` | class (Writer) | 可变字节缓冲区 |
| `StringBuilder.append(s String)` | method | 追加字符串 |
| `StringBuilder.appendByte(b byte)` | method | 追加单个字节 |
| `StringBuilder.appendRune(r int32)` | method | 追加 UTF-8 编码的 rune |
| `StringBuilder.appendInt(v int)` | method | 追加整数十进制 |
| `StringBuilder.appendFloat(v float)` | method | 追加浮点数 |
| `StringBuilder.appendBool(v bool)` | method | 追加布尔值 |
| `StringBuilder.appendInt64(v int64)` | method | 追加 int64 十进制 |
| `StringBuilder.appendUint(v uint)` | method | 追加无符号整数 |
| `StringBuilder.appendBytes(data [&#]byte)` | method | 追加原始字节 |
| `StringBuilder.clear()` | method | 清空（不释放缓冲区） |
| `StringBuilder.length()` | method | `int` 当前字节数 |
| `StringBuilder.isEmpty()` | method | `bool` |
| `StringBuilder.build()` | method | `String` 构建不可变字符串 |
| `StringBuilder.write(data, offset, length)` | method | `int` (Writer 实现) |
| `newBuilder()` | func | `StringBuilder` 工厂 |
| `newBuilderCapacity(cap int)` | func | `StringBuilder` 指定初始容量 |
| `newBuilderRef()` | func | `*StringBuilder` 堆分配引用 |

### `std$bytes` — 字节缓冲读写

```feng
import std$bytes;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `BufferWriter` | class (Writer) | 写缓冲区 |
| `BufferWriter.write(data, offset, length)` | method | `int` (Writer 实现) |
| `BufferWriter.writeByte(b byte)` | method | 写入单字节 |
| `BufferWriter.get()` | method | `([*#]byte, int)` 获取缓冲区 + 长度 |
| `BufferWriter.length()` | method | `int` |
| `BufferWriter.isEmpty()` | method | `bool` |
| `BufferWriter.clear()` | method | 清空 |
| `BufferWriter.copy()` | method | `[*#]byte` 拷贝当前内容 |
| `BufferWriter.reader()` | method | `BufferReader` 转为只读读取器 |
| `newWriter()` | func | `BufferWriter` 工厂 |
| `BufferReader` | class | 只读缓冲区读取器 |
| `BufferReader.read(b [&]byte)` | method | `int` 读取字节 |
| `BufferReader.seek(pos int)` | method | 跳转到绝对位置 |
| `BufferReader.remaining()` | method | `int` 剩余可读字节 |
| `BufferReader.isEmpty()` | method | `bool` 是否已读完 |

---

## 2. 编码

### `std$encoding$utf8` — UTF-8 编解码

```feng
import std$encoding$utf8;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `runeLen(s [&#]byte)` | func | `int` 计算字节序列中的 rune 数量 |
| `decodeRune(s [&#]byte, offset int)` | func | `(int32, int)` 解码一个 rune，返回 (rune, 字节宽) |
| `encodeRune(buf [&]uint8, offset int, r int32)` | func | `int` 编码 rune，返回写入字节数（最大 4） |
| `valid(s [&#]byte)` | func | `bool` 检查是否为合法 UTF-8 |

### `std$encoding$hex` — 十六进制编解码

```feng
import std$encoding$hex;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `hexEncodeTo(dst [&]byte, src [&#]byte)` | func | `int` 编码到目标缓冲区，返回写入字节数 |
| `hexEncode(src [&#]byte)` | func | `[*]byte` 编码并分配新数组 |
| `hexDecodeTo(dst [&]byte, src [&#]byte)` | func | `(int, bool)` 解码到目标缓冲区 |
| `hexDecode(src [&#]byte)` | func | `([*?]byte, bool)` 解码并分配新数组 |

### `std$encoding$base64` — Base64 编解码 (RFC 4648)

```feng
import std$encoding$base64;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `base64EncodeTo(dst [&]byte, src [&#]byte)` | func | `int` 编码到目标缓冲区 |
| `base64Encode(src [&#]byte)` | func | `[*]byte` 编码并分配新数组 |
| `base64DecodeTo(dst [&]byte, src [&#]byte)` | func | `(int, bool)` 解码到目标缓冲区 |
| `base64Decode(src [&#]byte)` | func | `([*?]byte, bool)` 解码并分配新数组 |

### `std$encoding$binary` — 大/小端二进制读写

```feng
import std$encoding$binary;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `bigUint16(b [&#]byte)` | func | `uint16` 大端读取 |
| `bigUint32(b [&#]byte)` | func | `uint32` |
| `bigUint64(b [&#]byte)` | func | `uint64` |
| `putBigUint16(b [&]byte, v uint16)` | func | 大端写入 |
| `putBigUint32(b [&]byte, v uint32)` | func | |
| `putBigUint64(b [&]byte, v uint64)` | func | |
| `littleUint16(b [&#]byte)` | func | `uint16` 小端读取 |
| `littleUint32(b [&#]byte)` | func | `uint32` |
| `littleUint64(b [&#]byte)` | func | `uint64` |
| `putLittleUint16(b [&]byte, v uint16)` | func | 小端写入 |
| `putLittleUint32(b [&]byte, v uint32)` | func | |
| `putLittleUint64(b [&]byte, v uint64)` | func | |

---

## 3. 数值与转换

### `std$strconv` — 数值 ↔ 字节数组

```feng
import std$strconv;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `parseInt(s [&#]byte)` | func | `(int, bool)` 解析有符号整数 |
| `parseInt64(s [&#]byte)` | func | `(int64, bool)` |
| `parseUint(s [&#]byte)` | func | `(uint, bool)` 解析无符号整数 |
| `parseUint64(s [&#]byte)` | func | `(uint64, bool)` |
| `parseFloat64(s [&#]byte)` | func | `(float, bool)` 解析浮点数 |
| `parseBool(s [&#]byte)` | func | `(bool, bool)` 解析布尔值 |
| `mustParseInt(s [&#]byte)` | func | `int` 解析失败抛出 ParseException |
| `mustParseInt64(s [&#]byte)` | func | `int64` |
| `mustParseUint(s [&#]byte)` | func | `uint` |
| `mustParseUint64(s [&#]byte)` | func | `uint64` |
| `mustParseFloat64(s [&#]byte)` | func | `float` |
| `mustParseBool(s [&#]byte)` | func | `bool` |
| `formatInt(v int)` | func | `[*#]byte` 格式化为十进制字节序列 |
| `formatInt64(v int64)` | func | `[*#]byte` |
| `formatUint(v uint)` | func | `[*#]byte` |
| `formatUint64(v uint64)` | func | `[*#]byte` |
| `formatFloat(v float)` | func | `[*#]byte` |
| `formatBool(v bool)` | func | `[*#]byte` |

---

## 4. 数学

### `std$math` — 数学函数与常量

```feng
import std$math;
```

**常量**

| 符号 | 类型 | 值 |
|------|------|-----|
| `E` | const float | 2.718281828459045 |
| `PI` | const float | 3.141592653589793 |
| `PHI` | const float | 1.618033988749895 |
| `SQRT2` | const float | 1.414213562373095 |

**分类**

| 符号 | 签名 |
|------|------|
| `isNaN(x float)` | `bool` |
| `isInf(x float)` | `bool` |
| `signbit(x float)` | `bool` |

**基本**

| 符号 | 签名 |
|------|------|
| `abs(x float)` | `float` |
| `min(a, b float)` | `float` |
| `max(a, b float)` | `float` |

**舍入**

| 符号 | 签名 |
|------|------|
| `floor(x float)` | `float` |
| `ceil(x float)` | `float` |
| `trunc(x float)` | `float` |
| `round(x float)` | `float` |

**幂/根/对数**

| 符号 | 签名 |
|------|------|
| `sqrt(x float)` | `float` (Newton 法) |
| `exp(x float)` | `float` (Taylor 级数) |
| `log(x float)` | `float` 自然对数 |
| `log10(x float)` | `float` |
| `log2(x float)` | `float` |
| `pow(x, y float)` | `float` |

**三角**

| 符号 | 签名 |
|------|------|
| `sin(x float)` | `float` |
| `cos(x float)` | `float` |
| `tan(x float)` | `float` |
| `asin(x float)` | `float` |
| `acos(x float)` | `float` |
| `atan(x float)` | `float` |
| `atan2(y, x float)` | `float` |

**双曲**

| 符号 | 签名 |
|------|------|
| `sinh(x float)` | `float` |
| `cosh(x float)` | `float` |
| `tanh(x float)` | `float` |

**符号操作**

| 符号 | 签名 |
|------|------|
| `copysignF(x, y float)` | `float` |

---

## 5. 排序与搜索

### `std$sort` — 排序

```feng
import std$sort;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `sort\`T\`(arr [&]T, cmp func(T,T)int)` | func | 快速排序（三数取中，小数组插入排序），`cmp(a,b)<0` 表示 a<b |
| `isSorted\`T\`(arr [&#]T, cmp func(T,T)int)` | func | `bool` 检查是否已排序 |

### `std$sort$search` — 二分查找

```feng
import std$sort$search;
```

> 要求数组已按 `cmp` 排序。

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `binarySearch\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `int` 精确查找，返回索引，-1 未找到 |
| `binarySearchRange\`T\`(arr [&#]T, target T, lo, hi int, cmp func(T,T)int)` | func | `int` 指定范围查找 |
| `lowerBound\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `int` 第一个 >= target 的索引 |
| `upperBound\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `int` 第一个 > target 的索引 |
| `equalRange\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `(int,int)` 返回 `[lower, upper)` 半开区间 |

---

## 6. 数据结构

> 所有容器类在 `std$container` 模块中。

### `Vector\`T\`` — 动态数组

```feng
import std$container;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Vector\`T\`` | class | 自动扩容的动态数组 |
| `.length()` | method | `int` 元素数 |
| `.capacity()` | method | `int` 当前容量 |
| `.isEmpty()` | method | `bool` |
| `.append(e T)` | method | 追加元素 |
| `.appendAll(src [&#]T)` | method | 批量追加 |
| `.insert(index int, e T)` | method | 在指定位置插入 |
| `.get(index int)` | method | `T` 按索引读取 |
| `.set(index int, value T)` | method | 按索引写入 |
| `.removeLast()` | method | `T` 移除并返回末尾元素 |
| `.removeAt(index int)` | method | `T` 移除指定位置元素 |
| `.indexOf(value T)` | method | `int` 首次出现索引，-1 未找到 |
| `.lastIndexOf(value T)` | method | `int` |
| `.contains(value T)` | method | `bool` |
| `.clear()` | method | 清空 |
| `.truncate(newLen int)` | method | 截断 |
| `.reverse()` | method | 原地反转 |
| `.copyTo(dst [&]T)` | method | `int` 复制到目标切片 |
| `newVector\`T\`()` | func | `*Vector\`T\`` 工厂 |
| `newVectorCap\`T\`(cap int)` | func | `*Vector\`T\`` 指定初始容量 |
| `vectorFromArray\`T\`(src [&#]T)` | func | `*Vector\`T\`` 从数组切片构造 |

### `Deque\`T\`` — 双端队列

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Deque\`T\`` | class | 环形缓冲区双端队列 |
| `.pushFront(e T)` | method | |
| `.pushBack(e T)` | method | |
| `.popFront()` | method | `T` |
| `.popBack()` | method | `T` |
| `.peekFront()` | method | `T` |
| `.peekBack()` | method | `T` |
| `.length()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newDeque\`T\`()` | func | `*Deque\`T\`` 工厂 |
| `newDequeCap\`T\`(cap int)` | func | `*Deque\`T\`` 指定初始容量 |

### `Stack\`T\`` — 栈

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Stack\`T\`` | class | LIFO 栈（基于 Vector） |
| `.push(e T)` | method | 入栈 |
| `.pop()` | method | `T` 出栈，空栈抛异常 |
| `.peek()` | method | `T` 查看栈顶 |
| `.size()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newStack\`T\`()` | func | `*Stack\`T\`` 工厂 |

### `BitSet` — 位集

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `BitSet` | class | 32 位 int 数组存储，自动扩容 |
| `.set(index int)` | method | 置位 |
| `.clear(index int)` | method | 清零（超范围无操作） |
| `.toggle(index int)` | method | 翻转 |
| `.get(index int)` | method | `bool` 查询 |
| `.count()` | method | `int` 已置位的数量（popcount） |
| `.cap()` | method | `int` 总容量 |
| `.isEmpty()` | method | `bool` |
| `.clearAll()` | method | 全部清零 |
| `.and(other *?BitSet)` | method | `this = this & other` |
| `.or(other *?BitSet)` | method | `this = this \| other` |
| `.xor(other *?BitSet)` | method | `this = this ~ other` |
| `.andNot(other &BitSet)` | method | `this = this & !other` |
| `newBitSet()` | func | `*BitSet` 工厂 |
| `newBitSetCap(nbits int)` | func | `*BitSet` 指定初始容量 |

### `HashSet\`T\`` — 哈希集合

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Set\`T\`` | interface | 集合接口：`add`, `contains`, `remove`, `size`, `isEmpty`, `clear` |
| `HashSet\`T\`` | class (Set\`T\`) | 基于 HashMap\`T,Void\` 的哈希集合 |
| `.add(key T)` | method | `bool` |
| `.contains(key T)` | method | `bool` |
| `.remove(key T)` | method | `bool` |
| `.size()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `.toList()` | method | `*Vector\`T\`` 导出为列表 |
| `newSet\`T\`(hashing Hashing\`T,Void\`)` | func | `*HashSet\`T\`` 工厂 |
| `Void` | struct | 空结构体（Set 内部使用） |

### `HashMap\`K,V\`` — 哈希表

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Map\`K,V\`` | interface | 映射接口：`set`, `get`, `remove` |
| `Result\`V\`` | final class | 结果包装：`v V`, `ok bool` |
| `Node\`K,V\`` | final class | 链表节点：`key K`, `value V` |
| `Hashing\`K,V\`` | final class | 哈希/等值函数：`hash func(&#Node) int`, `equal func(a,b &#Node) bool` |
| `HashMap\`K,V\`` | class (Map\`K,V\`) | 链地址法哈希表，负载因子 0.75 |
| `.set(key K, value V)` | method | `Result\`V\`` |
| `.get(key K)` | method | `Result\`V\`` |
| `.remove(key K)` | method | `Result\`V\`` |
| `.size()` | method | `int` |
| `.clear()` | method | |
| `.iterator()` | method | `*HashMapIter\`K,V\`` |
| `newHashmap\`K,V\`(hashing Hashing\`K,V\`)` | func | `*Map\`K,V\`` 工厂 |
| `HashMapIter\`K,V\`` | class | 哈希表迭代器 |
| `.hasNext()` | method | `bool` |
| `.next()` | method | 前进 |
| `.key()` | method | `K` 当前键 |
| `.value()` | method | `V` 当前值 |

### `Heap\`T\`` — 二叉堆

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Heap\`T\`` | class | 数组二叉堆，`cmp(a,b)<0` 表示 a 优先级更高（最小堆） |
| `.push(e T)` | method | 入堆 |
| `.pop()` | method | `T` 弹出堆顶，空堆抛异常 |
| `.peek()` | method | `T` 查看堆顶 |
| `.length()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newHeap\`T\`(cmp func(T,T)int)` | func | `*Heap\`T\`` 工厂 |

### `Cache\`K,V\`` — LRU 缓存

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Cache\`K,V\`` | class | LRU 缓存，O(1) get/put/remove，超出容量淘汰最久未用 |
| `.get(key K)` | method | `(V, bool)` 命中会更新 LRU 序 |
| `.put(key K, value V)` | method | 插入/更新 |
| `.remove(key K)` | method | `bool` 删除 |
| `.contains(key K)` | method | `bool` |
| `.length()` | method | `int` |
| `.capacity()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newCache\`K,V\`(cap int, hash func(K)int, equal func(K,K)bool)` | func | `*Cache\`K,V\`` 工厂 |

---

## 7. 时间与随机

### `std$time` — 时间与计时

```feng
import std$time;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Duration` | final class | 纳秒精度的时间间隔 |
| `.nanoseconds()` | method | `int64` |
| `.microseconds()` | method | `int64` |
| `.milliseconds()` | method | `int64` |
| `.seconds()` | method | `float` |
| `.add(other Duration)` | method | `Duration` |
| `.sub(other Duration)` | method | `Duration` |
| `.compare(other Duration)` | method | `int` |
| `nanosecond(n int64)` | func | `Duration` |
| `microsecond(n int64)` | func | `Duration` |
| `millisecond(n int64)` | func | `Duration` |
| `secondF(n float)` | func | `Duration` |
| `minuteF(n float)` | func | `Duration` |
| `hourF(n float)` | func | `Duration` |
| `Datetime` | struct | 日期时间分解（位域）：year(32), month(4), day(6), hour(6), minute(6), second(6), weekday(4) |
| `Date` | final class | 日期包装：`dt Datetime` |
| `Time` | final class | Unix 时间点 |
| `.sec` | const field | `int64` 秒 |
| `.nsec` | const field | `int32` 纳秒 `[0, 999999999]` |
| `.before(other Time)` | method | `bool` |
| `.after(other Time)` | method | `bool` |
| `.equals(other Time)` | method | `bool` |
| `.sub(other Time)` | method | `Duration` |
| `.add(d Duration)` | method | `Time` |
| `.date()` | method | `Date` 分解为年月日 |
| `.year()` | method | `int` |
| `.month()` | method | `int` |
| `.day()` | method | `int` |
| `.hour()` | method | `int` |
| `.minute()` | method | `int` |
| `.second()` | method | `int` |
| `.weekday()` | method | `int` (0=Sun) |
| `now()` | func | `Time` 获取当前时间 |

### `std$rand` — 伪随机数

```feng
import std$rand;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `seed(s uint)` | func | 手动设种子 |
| `autoSeed()` | func | 自动播种 |
| `nextInt32()` | func | `int32` 非负随机整数 |
| `intn(n int)` | func | `int` `[0, n)` 随机整数 |
| `intRange(low, high int)` | func | `int` `[low, high]` 范围内随机整数 |
| `nextFloat()` | func | `float` `[0.0, 1.0)` 随机浮点 |
| `shuffle\`T\`(arr [&]T)` | func | Fisher-Yates 洗牌 |

---

## 8. 哈希与加密

### `std$hash` — Hash 接口

```feng
import std$hash;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Hash` | interface | 通用哈希接口 |
| `.write(data [&#]byte)` | method | `int` 写入数据 |
| `.sum(b [&]byte)` | method | `int` 写出最终哈希值 |
| `.reset()` | method | 复位 |
| `.size()` | method | `int` 输出字节数 |

### `std$hash$fnv` — FNV-1a 64-bit

```feng
import std$hash$fnv;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `FnvHash` | class (Hash) | FNV-1a 64 位哈希 |
| `newFnv()` | func | `*Hash` |

### `std$hash$crc32` — CRC32 (IEEE 802.3)

```feng
import std$hash$crc32;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Crc32` | class (Hash) | CRC32 校验，输出 4 字节 |
| `newCrc32()` | func | `*Hash` |

### `std$hash$md5` — MD5

```feng
import std$hash$md5;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Md5` | class (Hash) | MD5 哈希，输出 16 字节（仅校验用途） |
| `newMd5()` | func | `*Hash` |

### `std$hash$sha256` — SHA-256

```feng
import std$hash$sha256;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Sha256` | class (Hash) | SHA-256 哈希 (FIPS 180-4)，输出 32 字节 |
| `newSha256()` | func | `*Hash` |

---

## 9. 异常

### `std$error` — 标准异常层级

```feng
import std$error;
```

| 符号 | 类型 | 说明 |
|------|------|------|
| `IllegalArgumentException` | class : Exception | 参数不符合前置条件 |
| `IllegalStateException` | class : Exception | 操作在不合法状态下调用 |
| `ParseException` | class : Exception | 解析失败 |
| `UnsupportedException` | class : Exception | 不支持的操作 |

---

## 10. 文件 I/O 与系统

### `std$os` — 文件与控制台

```feng
import std$os;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `OpenMode` | enum | `Read`, `Write`, `Append`, `ReadWrite`, `ReadBinary`, `WriteBinary`, `AppendBinary`, `ReadWriteBinary` |
| `File` | class (Writer, Reader) | 文件句柄，`resource free()` 自动关闭 |
| `.read#(buf [&]byte)` | method | `int` 读取，<=0 表示 EOF/错误 |
| `.write(data, offset, length)` | method | `int` 写入 |
| `.seek(offset int, whence int)` | method | `int` 定位 |
| `.tell()` | method | `int` 当前位置 |
| `.eof()` | method | `bool` 是否 EOF |
| `.error()` | method | `bool` 是否有错误 |
| `open(path [&#]byte, mode OpenMode)` | func | `*File` 打开文件 |
| `stdin` | const | `*File` 标准输入 |
| `stdout` | const | `*File` 标准输出 |
| `stderr` | const | `*File` 标准错误 |
| `printf(fmt [&#]byte, ...)` | func | 格式化输出到 stdout |
| `makeDir(path [&#]byte)` | func | `bool` 创建目录 |
| `removeFile(path [&#]byte)` | func | `bool` 删除文件 |
| `removeDir(path [&#]byte)` | func | `bool` 删除空目录 |
| `exists(path [&#]byte)` | func | `bool` 检查路径是否存在 |
| `isReadable(path [&#]byte)` | func | `bool` 检查可读 |
| `isWritable(path [&#]byte)` | func | `bool` 检查可写 |
| `getEnvVar(name [&#]byte)` | func | `String` 获取环境变量 |
| `setEnvVar(name, value [&#]byte)` | func | `bool` 设置环境变量 |
| `getWorkDir()` | func | `String` 获取当前工作目录 |
| `changeDir(path [&#]byte)` | func | `bool` 切换工作目录 |

---

## 11. 路径操作

### `std$path` — 路径操作（纯 Fēng，`/` 分隔符）

```feng
import std$path;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `SEPARATOR` | const byte | `'/'` |
| `join(a, b String)` | func | `String` 拼接路径 |
| `base(path String)` | func | `String` 路径最后一级 |
| `dir(path String)` | func | `String` 目录部分 |
| `ext(path String)` | func | `String` 扩展名（含 `.`） |
| `split(path String)` | func | `(String, String)` 同时返回 dir + base |
| `isAbs(path String)` | func | `bool` 是否绝对路径 |
| `clean(path String)` | func | `String` 规范化（去除 `.`/`..`/重复 `/`） |

---

## 12. 网络

### `std$net` — TCP/UDP Socket

```feng
import std$net;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `Addr` | final class | 打包 IPv4+端口（uint64） |
| `.packed` | const field | `uint64` 高 32=IP，低 16=端口 |
| `.ip()` | method | `String` 点分十进制 |
| `.port()` | method | `int` |
| `resolve(host [&#]byte, port int)` | func | `Addr` 解析点分十进制 |
| `ip4(a,b,c,d byte, port int)` | func | `Addr` 四段式构造 |
| `Socket` | class (Writer, Reader) | 套接字，`resource free()` RAII 关闭 |
| `.read#(buf [&]byte)` | method | `int` 读取，<=0 表示连接关闭/错误 |
| `.write(data, offset, length)` | method | `int` 写入 |
| `.accept()` | method | `(*Socket, *Addr)` TCP 接受连接 |
| `.sendTo(data [&#]byte, addr Addr)` | method | `int` UDP 发送 |
| `.recvFrom(buf [&]byte)` | method | `(int, *Addr)` UDP 接收 |
| `.close()` | method | 关闭 socket |
| `listenTCP(addr Addr)` | func | `*Socket` TCP 服务端监听 |
| `dial(addr Addr)` | func | `*Socket` TCP 客户端连接 |
| `listenUDP(addr Addr)` | func | `*Socket` UDP 绑定 |

---

## 13. 测试

### `std$testing` — 测试断言

```feng
import std$testing;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `AssertionException` | class : Exception | 断言失败异常（含 `message String`） |
| `assertTrue(condition bool)` | func | 条件为真 |
| `assertFalse(condition bool)` | func | 条件为假 |
| `fail(message String)` | func | 无条件失败 |

---

## 14. 数组工具

### `std$util` — 泛型数组操作

```feng
import std$util;
```

| 符号 | 类型 | 签名 / 说明 |
|------|------|------------|
| `copy\`T\`(src [&#]T, dst [&]T, len int)` | func | 从 src 复制最多 len 个元素到 dst |
| `equal\`T\`(a [&#]T, b [&#]T)` | func | `bool` 比较两个切片是否相等 |
| `fill\`T\`(dst [&]T, value T)` | func | 用 value 填充切片 |
| `reverse\`T\`(arr [&]T)` | func | 原地反转 |
| `indexOf\`T\`(arr [&#]T, value T)` | func | `int` 首次出现索引，-1 未找到 |
| `contains\`T\`(arr [&#]T, value T)` | func | `bool` 是否包含 |

---

## 模块依赖关系

```
std$error          ← 基础异常（无依赖）
std$util           ← 数组工具（无依赖）
std$encoding$utf8  ← UTF-8（无依赖）
std$encoding$binary← 大/小端（无依赖）
std$encoding$hex   ← 十六进制（无依赖）
std$encoding$base64← Base64（无依赖）
std$strconv        ← 数值转换（依赖 std$error）
std$string         ← 字符串（依赖 std$encoding$utf8, std$error）
std$bytes          ← 字节缓冲（依赖 std$util）
std$math           ← 数学（无依赖）
std$sort           ← 排序（无依赖）
std$sort$search    ← 二分查找（无依赖）
std$time           ← 时间（C 互操作）
std$rand           ← 随机（依赖 std$util）
std$container      ← 容器（依赖 std$error）
std$hash           ← Hash 接口（无依赖）
std$hash$fnv       ← FNV（依赖 std$encoding）
std$hash$crc32     ← CRC32（依赖 std$encoding）
std$hash$md5       ← MD5（依赖 std$encoding）
std$hash$sha256    ← SHA-256（依赖 std$encoding）
std$os             ← 文件 I/O（依赖 std$string）
std$path           ← 路径（依赖 std$string）
std$net            ← 网络（依赖 std$string, std$error, std$net$platform）
std$testing        ← 测试（依赖 std$string）
```

---

## import 路径速查

| 模块 | import 路径 |
|------|------------|
| 字符串 | `import std$string;` |
| 字节缓冲 | `import std$bytes;` |
| UTF-8 | `import std$encoding$utf8;` |
| Hex | `import std$encoding$hex;` |
| Base64 | `import std$encoding$base64;` |
| 二进制 | `import std$encoding$binary;` |
| 数值转换 | `import std$strconv;` |
| 数学 | `import std$math;` |
| 排序 | `import std$sort;` |
| 二分查找 | `import std$sort$search;` |
| 时间 | `import std$time;` |
| 随机 | `import std$rand;` |
| 容器 | `import std$container;` |
| Hash 接口 | `import std$hash;` |
| FNV | `import std$hash$fnv;` |
| CRC32 | `import std$hash$crc32;` |
| MD5 | `import std$hash$md5;` |
| SHA-256 | `import std$hash$sha256;` |
| 异常 | `import std$error;` |
| 文件 I/O | `import std$os;` |
| 路径 | `import std$path;` |
| 网络 | `import std$net;` |
| 测试 | `import std$testing;` |
| 数组工具 | `import std$util;` |
