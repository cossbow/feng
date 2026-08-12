# Fēng Standard Library Reference

> Last updated: 2026-08-12  
> Covers all modules under `std/`, grouped by domain.

---

## Compiler Built-in Symbols

These symbols are provided directly by the compiler and require no import.

| Symbol | Type | Description |
|------|------|-------------|
| `Object` | class | Root of all (non-final) classes |
| `Exception` | class | Base exception (fields `fn`, `line`) |
| `NilException` | class | Null pointer exception |
| `OutOfBoundsException` | class | Out-of-bounds exception |
| `AssertException` | class | Assertion failure exception |
| `Writer` | interface | `write(data [&!]byte) int` |
| `Reader` | interface | `read(b [&!]byte) int` |
| `Writable` | interface | `write(w &!Writer) int` |
| `format` | func | `format(w &!Writer, fmt, ...)` |
| `assert` | func | `assert(cond bool)` |

---

## 1. Strings & Bytes

### `std$string` — Immutable String

```feng
import std$string;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `String` | class (Writable) | Immutable UTF-8 string |
| `String.value` | const field | `[*?#]byte` underlying bytes |
| `String.bytes()` | method | `[*?#]byte` get underlying byte slice |
| `String.byteLength()` | method | `int` byte length |
| `String.runeCount()` | method | `int` Unicode code point count |
| `String.isEmpty()` | method | `bool` |
| `String.compare(rhs String)` | method | `int` lexicographic comparison (<0 / 0 / >0) |
| `String.equals(other String)` | method | `bool` |
| `String.concat(other String)` | method | `String` concatenation |
| `String.indexOf(ch int32)` | method | `int` find by rune, returns byte offset, -1 if not found |
| `String.indexOfStr(sub String)` | method | `int` substring search |
| `String.contains(sub String)` | method | `bool` |
| `String.startsWith(prefix String)` | method | `bool` |
| `String.endsWith(suffix String)` | method | `bool` |
| `String.substring(start, end int)` | method | `String` byte half-open range `[start, end)` |
| `String.rune(index int)` | method | `int32` get rune by character index |
| `String.split(sep String)` | method | `[*]String` split |
| `String.replace(oldStr, newStr String)` | method | `String` global replacement |
| `String.trim()` | method | `String` trim leading/trailing ASCII whitespace |
| `String.toLower()` | method | `String` ASCII lowercase |
| `String.toUpper()` | method | `String` ASCII uppercase |
| `String.write#(w &Writer)` | method | `int` (Writable impl) |
| `utf8(data [*#]byte)` | func | `String` constructor |
| `ascii(data [*#]byte)` | func | `String` constructor (alias for utf8) |
| `join(parts [&#]String, sep String)` | func | `String` join string array with separator |
| `EMPTY` | const | `String` empty string |

### `std$string` — Mutable String Builder

```feng
import std$string;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `StringBuilder` | class (Writer) | Mutable byte buffer |
| `StringBuilder.append(s String)` | method | Append string |
| `StringBuilder.appendByte(b byte)` | method | Append single byte |
| `StringBuilder.appendRune(r int32)` | method | Append UTF-8 encoded rune |
| `StringBuilder.appendInt(v int)` | method | Append int as decimal |
| `StringBuilder.appendFloat(v float)` | method | Append float |
| `StringBuilder.appendBool(v bool)` | method | Append boolean |
| `StringBuilder.appendInt64(v int64)` | method | Append int64 as decimal |
| `StringBuilder.appendUint(v uint)` | method | Append unsigned int |
| `StringBuilder.appendBytes(data [&#]byte)` | method | Append raw bytes |
| `StringBuilder.clear()` | method | Clear (keeps buffer) |
| `StringBuilder.length()` | method | `int` current byte count |
| `StringBuilder.isEmpty()` | method | `bool` |
| `StringBuilder.build()` | method | `String` build immutable string |
| `StringBuilder.write(data, offset, length)` | method | `int` (Writer impl) |
| `newBuilder()` | func | `StringBuilder` factory |
| `newBuilderCapacity(cap int)` | func | `StringBuilder` with initial capacity |
| `newBuilderRef()` | func | `*StringBuilder` heap-allocated reference |

### `std$bytes` — Byte Buffer Read/Write

```feng
import std$bytes;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `BufferWriter` | class (Writer) | Write buffer |
| `BufferWriter.write(data, offset, length)` | method | `int` (Writer impl) |
| `BufferWriter.writeByte(b byte)` | method | Write single byte |
| `BufferWriter.get()` | method | `([*#]byte, int)` get buffer + length |
| `BufferWriter.length()` | method | `int` |
| `BufferWriter.isEmpty()` | method | `bool` |
| `BufferWriter.clear()` | method | Clear |
| `BufferWriter.copy()` | method | `[*#]byte` copy current content |
| `BufferWriter.reader()` | method | `BufferReader` convert to read-only reader |
| `newWriter()` | func | `BufferWriter` factory |
| `BufferReader` | class | Read-only buffer reader |
| `BufferReader.read(b [&]byte)` | method | `int` read bytes |
| `BufferReader.seek(pos int)` | method | Seek to absolute position |
| `BufferReader.remaining()` | method | `int` remaining readable bytes |
| `BufferReader.isEmpty()` | method | `bool` whether fully consumed |

---

## 2. Encoding

### `std$encoding$utf8` — UTF-8 Codec

```feng
import std$encoding$utf8;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `runeLen(s [&#]byte)` | func | `int` count runes in byte sequence |
| `decodeRune(s [&#]byte, offset int)` | func | `(int32, int)` decode one rune, returns (rune, byte width) |
| `encodeRune(buf [&]uint8, offset int, r int32)` | func | `int` encode rune, returns bytes written (max 4) |
| `valid(s [&#]byte)` | func | `bool` check valid UTF-8 |

### `std$encoding$hex` — Hexadecimal Codec

```feng
import std$encoding$hex;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `hexEncodeTo(dst [&]byte, src [&#]byte)` | func | `int` encode into destination buffer |
| `hexEncode(src [&#]byte)` | func | `[*]byte` encode and allocate new array |
| `hexDecodeTo(dst [&]byte, src [&#]byte)` | func | `(int, bool)` decode into destination buffer |
| `hexDecode(src [&#]byte)` | func | `([*?]byte, bool)` decode and allocate new array |

### `std$encoding$base64` — Base64 Codec (RFC 4648)

```feng
import std$encoding$base64;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `base64EncodeTo(dst [&]byte, src [&#]byte)` | func | `int` encode into destination buffer |
| `base64Encode(src [&#]byte)` | func | `[*]byte` encode and allocate new array |
| `base64DecodeTo(dst [&]byte, src [&#]byte)` | func | `(int, bool)` decode into destination buffer |
| `base64Decode(src [&#]byte)` | func | `([*?]byte, bool)` decode and allocate new array |

### `std$encoding$binary` — Big/Little Endian Binary I/O

```feng
import std$encoding$binary;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `bigUint16(b [&#]byte)` | func | `uint16` big-endian read |
| `bigUint32(b [&#]byte)` | func | `uint32` |
| `bigUint64(b [&#]byte)` | func | `uint64` |
| `putBigUint16(b [&]byte, v uint16)` | func | big-endian write |
| `putBigUint32(b [&]byte, v uint32)` | func | |
| `putBigUint64(b [&]byte, v uint64)` | func | |
| `littleUint16(b [&#]byte)` | func | `uint16` little-endian read |
| `littleUint32(b [&#]byte)` | func | `uint32` |
| `littleUint64(b [&#]byte)` | func | `uint64` |
| `putLittleUint16(b [&]byte, v uint16)` | func | little-endian write |
| `putLittleUint32(b [&]byte, v uint32)` | func | |
| `putLittleUint64(b [&]byte, v uint64)` | func | |

---

## 3. Number Conversion

### `std$strconv` — Number ↔ Byte Array

```feng
import std$strconv;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `parseInt(s [&#]byte)` | func | `(int, bool)` parse signed integer |
| `parseInt64(s [&#]byte)` | func | `(int64, bool)` |
| `parseUint(s [&#]byte)` | func | `(uint, bool)` parse unsigned integer |
| `parseUint64(s [&#]byte)` | func | `(uint64, bool)` |
| `parseFloat64(s [&#]byte)` | func | `(float, bool)` parse float |
| `parseBool(s [&#]byte)` | func | `(bool, bool)` parse boolean |
| `mustParseInt(s [&#]byte)` | func | `int` throws ParseException on failure |
| `mustParseInt64(s [&#]byte)` | func | `int64` |
| `mustParseUint(s [&#]byte)` | func | `uint` |
| `mustParseUint64(s [&#]byte)` | func | `uint64` |
| `mustParseFloat64(s [&#]byte)` | func | `float` |
| `mustParseBool(s [&#]byte)` | func | `bool` |
| `formatInt(v int)` | func | `[*#]byte` format as decimal byte sequence |
| `formatInt64(v int64)` | func | `[*#]byte` |
| `formatUint(v uint)` | func | `[*#]byte` |
| `formatUint64(v uint64)` | func | `[*#]byte` |
| `formatFloat(v float)` | func | `[*#]byte` |
| `formatBool(v bool)` | func | `[*#]byte` |

---

## 4. Math

### `std$math` — Math Functions & Constants

```feng
import std$math;
```

**Constants**

| Symbol | Type | Value |
|------|------|-------|
| `E` | const float | 2.718281828459045 |
| `PI` | const float | 3.141592653589793 |
| `PHI` | const float | 1.618033988749895 |
| `SQRT2` | const float | 1.414213562373095 |

**Classification**

| Symbol | Signature |
|------|-----------|
| `isNaN(x float)` | `bool` |
| `isInf(x float)` | `bool` |
| `signbit(x float)` | `bool` |

**Basic**

| Symbol | Signature |
|------|-----------|
| `abs(x float)` | `float` |
| `min(a, b float)` | `float` |
| `max(a, b float)` | `float` |

**Rounding**

| Symbol | Signature |
|------|-----------|
| `floor(x float)` | `float` |
| `ceil(x float)` | `float` |
| `trunc(x float)` | `float` |
| `round(x float)` | `float` |

**Power / Root / Logarithm**

| Symbol | Signature |
|------|-----------|
| `sqrt(x float)` | `float` (Newton's method) |
| `exp(x float)` | `float` (Taylor series) |
| `log(x float)` | `float` natural log |
| `log10(x float)` | `float` |
| `log2(x float)` | `float` |
| `pow(x, y float)` | `float` |

**Trigonometric**

| Symbol | Signature |
|------|-----------|
| `sin(x float)` | `float` |
| `cos(x float)` | `float` |
| `tan(x float)` | `float` |
| `asin(x float)` | `float` |
| `acos(x float)` | `float` |
| `atan(x float)` | `float` |
| `atan2(y, x float)` | `float` |

**Hyperbolic**

| Symbol | Signature |
|------|-----------|
| `sinh(x float)` | `float` |
| `cosh(x float)` | `float` |
| `tanh(x float)` | `float` |

**Sign Manipulation**

| Symbol | Signature |
|------|-----------|
| `copysignF(x, y float)` | `float` |

---

## 5. Sorting & Searching

### `std$sort` — Sorting

```feng
import std$sort;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `sort\`T\`(arr [&]T, cmp func(T,T)int)` | func | Quick sort (median-of-three pivot, insertion sort for small subarrays). `cmp(a,b)<0` means a < b |
| `isSorted\`T\`(arr [&#]T, cmp func(T,T)int)` | func | `bool` check if sorted |

### `std$sort$search` — Binary Search

```feng
import std$sort$search;
```

> Requires array to be sorted by `cmp`.

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `binarySearch\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `int` exact search, returns index, -1 if not found |
| `binarySearchRange\`T\`(arr [&#]T, target T, lo, hi int, cmp func(T,T)int)` | func | `int` search within range |
| `lowerBound\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `int` first index >= target |
| `upperBound\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `int` first index > target |
| `equalRange\`T\`(arr [&#]T, target T, cmp func(T,T)int)` | func | `(int,int)` returns `[lower, upper)` half-open range |

---

## 6. Data Structures

> All container classes are in the `std$container` module.

### `Vector\`T\`` — Dynamic Array

```feng
import std$container;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Vector\`T\`` | class | Auto-growing dynamic array |
| `.length()` | method | `int` element count |
| `.capacity()` | method | `int` current capacity |
| `.isEmpty()` | method | `bool` |
| `.append(e T)` | method | Append element |
| `.appendAll(src [&#]T)` | method | Bulk append |
| `.insert(index int, e T)` | method | Insert at position |
| `.get(index int)` | method | `T` read by index |
| `.set(index int, value T)` | method | write by index |
| `.removeLast()` | method | `T` remove and return last element |
| `.removeAt(index int)` | method | `T` remove element at position |
| `.indexOf(value T)` | method | `int` first occurrence index, -1 if not found |
| `.lastIndexOf(value T)` | method | `int` |
| `.contains(value T)` | method | `bool` |
| `.clear()` | method | Clear |
| `.truncate(newLen int)` | method | Truncate |
| `.reverse()` | method | Reverse in place |
| `.copyTo(dst [&]T)` | method | `int` copy to destination slice |
| `newVector\`T\`()` | func | `*Vector\`T\`` factory |
| `newVectorCap\`T\`(cap int)` | func | `*Vector\`T\`` with initial capacity |
| `vectorFromArray\`T\`(src [&#]T)` | func | `*Vector\`T\`` construct from array slice |

### `Deque\`T\`` — Double-Ended Queue

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Deque\`T\`` | class | Ring buffer deque |
| `.pushFront(e T)` | method | |
| `.pushBack(e T)` | method | |
| `.popFront()` | method | `T` |
| `.popBack()` | method | `T` |
| `.peekFront()` | method | `T` |
| `.peekBack()` | method | `T` |
| `.length()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newDeque\`T\`()` | func | `*Deque\`T\`` factory |
| `newDequeCap\`T\`(cap int)` | func | `*Deque\`T\`` with initial capacity |

### `Stack\`T\`` — Stack

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Stack\`T\`` | class | LIFO stack (backed by Vector) |
| `.push(e T)` | method | Push |
| `.pop()` | method | `T` pop, throws on empty |
| `.peek()` | method | `T` peek top |
| `.size()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newStack\`T\`()` | func | `*Stack\`T\`` factory |

### `BitSet` — Bit Set

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `BitSet` | class | Compact bit set (32 bits per int), auto-grows |
| `.set(index int)` | method | Set bit |
| `.clear(index int)` | method | Clear bit (no-op if out of range) |
| `.toggle(index int)` | method | Toggle bit |
| `.get(index int)` | method | `bool` query |
| `.count()` | method | `int` popcount of set bits |
| `.cap()` | method | `int` total bit capacity |
| `.isEmpty()` | method | `bool` |
| `.clearAll()` | method | Clear all bits |
| `.and(other *?BitSet)` | method | `this = this & other` |
| `.or(other *?BitSet)` | method | `this = this \| other` |
| `.xor(other *?BitSet)` | method | `this = this ~ other` |
| `.andNot(other &BitSet)` | method | `this = this & !other` |
| `newBitSet()` | func | `*BitSet` factory |
| `newBitSetCap(nbits int)` | func | `*BitSet` with initial capacity |

### `HashSet\`T\`` — Hash Set

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Set\`T\`` | interface | Set interface: `add`, `contains`, `remove`, `size`, `isEmpty`, `clear` |
| `HashSet\`T\`` | class (Set\`T\`) | Hash set backed by HashMap\`T,Void\` |
| `.add(key T)` | method | `bool` |
| `.contains(key T)` | method | `bool` |
| `.remove(key T)` | method | `bool` |
| `.size()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `.toList()` | method | `*Vector\`T\`` export as list |
| `newSet\`T\`(hashing Hashing\`T,Void\`)` | func | `*HashSet\`T\`` factory |
| `Void` | struct | Empty struct (used internally by Set) |

### `HashMap\`K,V\`` — Hash Table

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Map\`K,V\`` | interface | Map interface: `set`, `get`, `remove` |
| `Result\`V\`` | final class | Result wrapper: `v V`, `ok bool` |
| `Node\`K,V\`` | final class | Linked list node: `key K`, `value V` |
| `Hashing\`K,V\`` | final class | Hash/equality functions: `hash func(&#Node) int`, `equal func(a,b &#Node) bool` |
| `HashMap\`K,V\`` | class (Map\`K,V\`) | Separate chaining hash table, load factor 0.75 |
| `.set(key K, value V)` | method | `Result\`V\`` |
| `.get(key K)` | method | `Result\`V\`` |
| `.remove(key K)` | method | `Result\`V\`` |
| `.size()` | method | `int` |
| `.clear()` | method | |
| `.iterator()` | method | `*HashMapIter\`K,V\`` |
| `newHashmap\`K,V\`(hashing Hashing\`K,V\`)` | func | `*Map\`K,V\`` factory |
| `HashMapIter\`K,V\`` | class | Hash table iterator |
| `.hasNext()` | method | `bool` |
| `.next()` | method | Advance |
| `.key()` | method | `K` current key |
| `.value()` | method | `V` current value |

### `Heap\`T\`` — Binary Heap

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Heap\`T\`` | class | Array-based binary heap. `cmp(a,b)<0` means a has higher priority (min-heap) |
| `.push(e T)` | method | Push |
| `.pop()` | method | `T` pop top, throws on empty |
| `.peek()` | method | `T` peek top |
| `.length()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newHeap\`T\`(cmp func(T,T)int)` | func | `*Heap\`T\`` factory |

### `Cache\`K,V\`` — LRU Cache

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Cache\`K,V\`` | class | LRU cache, O(1) get/put/remove, evicts least-recently-used when full |
| `.get(key K)` | method | `(V, bool)` hit updates LRU order |
| `.put(key K, value V)` | method | Insert / update |
| `.remove(key K)` | method | `bool` delete |
| `.contains(key K)` | method | `bool` |
| `.length()` | method | `int` |
| `.capacity()` | method | `int` |
| `.isEmpty()` | method | `bool` |
| `.clear()` | method | |
| `newCache\`K,V\`(cap int, hash func(K)int, equal func(K,K)bool)` | func | `*Cache\`K,V\`` factory |

---

## 7. Time & Random

### `std$time` — Time & Duration

```feng
import std$time;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Duration` | final class | Nanosecond-precision time interval |
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
| `Datetime` | struct | Datetime decomposition (bit fields): year(32), month(4), day(6), hour(6), minute(6), second(6), weekday(4) |
| `Date` | final class | Date wrapper: `dt Datetime` |
| `Time` | final class | Unix time point |
| `.sec` | const field | `int64` seconds |
| `.nsec` | const field | `int32` nanoseconds `[0, 999999999]` |
| `.before(other Time)` | method | `bool` |
| `.after(other Time)` | method | `bool` |
| `.equals(other Time)` | method | `bool` |
| `.sub(other Time)` | method | `Duration` |
| `.add(d Duration)` | method | `Time` |
| `.date()` | method | `Date` decompose into year/month/day |
| `.year()` | method | `int` |
| `.month()` | method | `int` |
| `.day()` | method | `int` |
| `.hour()` | method | `int` |
| `.minute()` | method | `int` |
| `.second()` | method | `int` |
| `.weekday()` | method | `int` (0=Sun) |
| `now()` | func | `Time` get current time |

### `std$rand` — Pseudo-Random Numbers

```feng
import std$rand;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `seed(s uint)` | func | Manual seed |
| `autoSeed()` | func | Auto-seed |
| `nextInt32()` | func | `int32` non-negative random integer |
| `intn(n int)` | func | `int` random integer in `[0, n)` |
| `intRange(low, high int)` | func | `int` random integer in `[low, high]` |
| `nextFloat()` | func | `float` random float in `[0.0, 1.0)` |
| `shuffle\`T\`(arr [&]T)` | func | Fisher-Yates shuffle |

---

## 8. Hashing & Crypto

### `std$hash` — Hash Interface

```feng
import std$hash;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Hash` | interface | Common hash interface |
| `.write(data [&#]byte)` | method | `int` feed data |
| `.sum(b [&]byte)` | method | `int` write final hash |
| `.reset()` | method | Reset |
| `.size()` | method | `int` output size in bytes |

### `std$hash$fnv` — FNV-1a 64-bit

```feng
import std$hash$fnv;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `FnvHash` | class (Hash) | FNV-1a 64-bit hash |
| `newFnv()` | func | `*Hash` |

### `std$hash$crc32` — CRC32 (IEEE 802.3)

```feng
import std$hash$crc32;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Crc32` | class (Hash) | CRC32 checksum, 4-byte output |
| `newCrc32()` | func | `*Hash` |

### `std$hash$md5` — MD5

```feng
import std$hash$md5;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Md5` | class (Hash) | MD5 hash, 16-byte output (checksum use only) |
| `newMd5()` | func | `*Hash` |

### `std$hash$sha256` — SHA-256

```feng
import std$hash$sha256;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Sha256` | class (Hash) | SHA-256 hash (FIPS 180-4), 32-byte output |
| `newSha256()` | func | `*Hash` |

---

## 9. Exceptions

### `std$error` — Standard Exception Hierarchy

```feng
import std$error;
```

| Symbol | Type | Description |
|------|------|-------------|
| `IllegalArgumentException` | class : Exception | Argument does not meet preconditions |
| `IllegalStateException` | class : Exception | Operation invoked in illegal state |
| `ParseException` | class : Exception | Parsing failure |
| `UnsupportedException` | class : Exception | Unsupported operation |

---

## 10. File I/O & System

### `std$os` — Files & Console

```feng
import std$os;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `OpenMode` | enum | `Read`, `Write`, `Append`, `ReadWrite`, `ReadBinary`, `WriteBinary`, `AppendBinary`, `ReadWriteBinary` |
| `File` | class (Writer, Reader) | File handle, `resource free()` auto-close |
| `.read#(buf [&]byte)` | method | `int` read, <=0 means EOF/error |
| `.write(data, offset, length)` | method | `int` write |
| `.seek(offset int, whence int)` | method | `int` seek |
| `.tell()` | method | `int` current position |
| `.eof()` | method | `bool` end of file |
| `.error()` | method | `bool` error flag |
| `open(path [&#]byte, mode OpenMode)` | func | `*File` open file |
| `stdin` | const | `*File` standard input |
| `stdout` | const | `*File` standard output |
| `stderr` | const | `*File` standard error |
| `printf(fmt [&#]byte, ...)` | func | Formatted output to stdout |
| `makeDir(path [&#]byte)` | func | `bool` create directory |
| `removeFile(path [&#]byte)` | func | `bool` delete file |
| `removeDir(path [&#]byte)` | func | `bool` delete empty directory |
| `exists(path [&#]byte)` | func | `bool` check path existence |
| `isReadable(path [&#]byte)` | func | `bool` check readability |
| `isWritable(path [&#]byte)` | func | `bool` check writability |
| `getEnvVar(name [&#]byte)` | func | `String` get environment variable |
| `setEnvVar(name, value [&#]byte)` | func | `bool` set environment variable |
| `getWorkDir()` | func | `String` get current working directory |
| `changeDir(path [&#]byte)` | func | `bool` change working directory |

---

## 11. Path Operations

### `std$path` — Path Operations (pure Fēng, `/` separator)

```feng
import std$path;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `SEPARATOR` | const byte | `'/'` |
| `join(a, b String)` | func | `String` join path components |
| `base(path String)` | func | `String` last element of path |
| `dir(path String)` | func | `String` directory portion |
| `ext(path String)` | func | `String` file extension (including `.`) |
| `split(path String)` | func | `(String, String)` dir + base simultaneously |
| `isAbs(path String)` | func | `bool` whether absolute path |
| `clean(path String)` | func | `String` normalize (resolve `.`/`..`/duplicate `/`) |

---

## 12. Networking

### `std$net` — TCP/UDP Sockets

```feng
import std$net;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `Addr` | final class | Packed IPv4+port (uint64) |
| `.packed` | const field | `uint64` high 32=IP, low 16=port |
| `.ip()` | method | `String` dotted-decimal |
| `.port()` | method | `int` |
| `resolve(host [&#]byte, port int)` | func | `Addr` parse dotted-decimal |
| `ip4(a,b,c,d byte, port int)` | func | `Addr` four-octet constructor |
| `Socket` | class (Writer, Reader) | Socket, `resource free()` RAII close |
| `.read#(buf [&]byte)` | method | `int` read, <=0 means connection closed/error |
| `.write(data, offset, length)` | method | `int` write |
| `.accept()` | method | `(*Socket, *Addr)` TCP accept connection |
| `.sendTo(data [&#]byte, addr Addr)` | method | `int` UDP send |
| `.recvFrom(buf [&]byte)` | method | `(int, *Addr)` UDP receive |
| `.close()` | method | Close socket |
| `listenTCP(addr Addr)` | func | `*Socket` TCP server listen |
| `dial(addr Addr)` | func | `*Socket` TCP client connect |
| `listenUDP(addr Addr)` | func | `*Socket` UDP bind |

---

## 13. Testing

### `std$testing` — Test Assertions

```feng
import std$testing;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `AssertionException` | class : Exception | Assertion failure (has `message String`) |
| `assertTrue(condition bool)` | func | Assert condition is true |
| `assertFalse(condition bool)` | func | Assert condition is false |
| `fail(message String)` | func | Unconditional failure |

---

## 14. Array Utilities

### `std$util` — Generic Array Operations

```feng
import std$util;
```

| Symbol | Type | Signature / Description |
|------|------|------------------------|
| `copy\`T\`(src [&#]T, dst [&]T, len int)` | func | Copy up to len elements from src to dst |
| `equal\`T\`(a [&#]T, b [&#]T)` | func | `bool` compare two slices for equality |
| `fill\`T\`(dst [&]T, value T)` | func | Fill slice with value |
| `reverse\`T\`(arr [&]T)` | func | Reverse in place |
| `indexOf\`T\`(arr [&#]T, value T)` | func | `int` first occurrence index, -1 if not found |
| `contains\`T\`(arr [&#]T, value T)` | func | `bool` whether slice contains value |

---

## Module Dependency Graph

```
std$error          ← base exceptions (no deps)
std$util           ← array utils (no deps)
std$encoding$utf8  ← UTF-8 (no deps)
std$encoding$binary← big/little endian (no deps)
std$encoding$hex   ← hex (no deps)
std$encoding$base64← Base64 (no deps)
std$strconv        ← number conversion (depends on std$error)
std$string         ← strings (depends on std$encoding$utf8, std$error)
std$bytes          ← byte buffers (depends on std$util)
std$math           ← math (no deps)
std$sort           ← sorting (no deps)
std$sort$search    ← binary search (no deps)
std$time           ← time (C interop)
std$rand           ← random (depends on std$util)
std$container      ← containers (depends on std$error)
std$hash           ← Hash interface (no deps)
std$hash$fnv       ← FNV (depends on std$encoding)
std$hash$crc32     ← CRC32 (depends on std$encoding)
std$hash$md5       ← MD5 (depends on std$encoding)
std$hash$sha256    ← SHA-256 (depends on std$encoding)
std$os             ← file I/O (depends on std$string)
std$path           ← paths (depends on std$string)
std$net            ← networking (depends on std$string, std$error, std$net$platform)
std$testing        ← testing (depends on std$string)
```

---

## Import Path Quick Reference

| Module | Import Path |
|------|-------------|
| String | `import std$string;` |
| Byte Buffer | `import std$bytes;` |
| UTF-8 | `import std$encoding$utf8;` |
| Hex | `import std$encoding$hex;` |
| Base64 | `import std$encoding$base64;` |
| Binary | `import std$encoding$binary;` |
| Number Conversion | `import std$strconv;` |
| Math | `import std$math;` |
| Sorting | `import std$sort;` |
| Binary Search | `import std$sort$search;` |
| Time | `import std$time;` |
| Random | `import std$rand;` |
| Containers | `import std$container;` |
| Hash Interface | `import std$hash;` |
| FNV | `import std$hash$fnv;` |
| CRC32 | `import std$hash$crc32;` |
| MD5 | `import std$hash$md5;` |
| SHA-256 | `import std$hash$sha256;` |
| Exceptions | `import std$error;` |
| File I/O | `import std$os;` |
| Path | `import std$path;` |
| Networking | `import std$net;` |
| Testing | `import std$testing;` |
| Array Utils | `import std$util;` |
