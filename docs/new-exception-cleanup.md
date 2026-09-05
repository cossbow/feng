# 异常清理栈（Exception Cleanup Stack）方案

> 状态：已实现并验证（2026-09-06）
> 范围：仅 C 后端（`org.cossbow.feng.coder` + `src/main/resources/c/`）

## 背景

异常通过 `setjmp`/`longjmp` 实现。GCC 的 `__attribute__((cleanup))`（`FENG$DEC`）在 **longjmp 时不触发**，导致异常跨栈传播时被跳过的局部强引用无法释放：

```feng
func main() {
    try {
        var i = new(int);      // FENG$DEC(i) —— longjmp 时不会执行
        throw new(MyException); // 泄漏 i
    } catch (e) {}
}
```

`FENG$DEC` 的 guard 只在函数**正常返回**时出栈，异常路径完全跳过。

## 方案：运行时清理栈（方案 A）

### 核心机制

```
Feng$Cleanup { prev, fn, slot }         // 镜像条目，线程局部 LIFO
Feng$cleanup_top                        // 栈顶（_Thread_local）
ExFrame.cleanup_mark                    // setjmp 时捕获的水位
```

1. **登记**：每个需清理的局部变量声明后发射一条 mirror 条目挂到栈顶，条目自身挂
   `FENG$DEC(Feng$cleanup_guard)` —— 正常退出时由 C 编译器自动出栈（guard 技巧），
   异常时由 `Feng$throw` 手动展开。
2. **水位**：每个 `try` 帧在 setjmp 前记录 `cleanup_mark = Feng$cleanup_top`，
   只展开到水位之上（水位本身由正常路径 guard 管理，保留给 catch 之后）。
3. **展开**：`Feng$throw` 先 `Feng$cleanup_unwind(frame->cleanup_mark)` 执行并弹出
   水位之上的镜像（调用 `fn(slot)` 释放局部），再 `longjmp`。
4. **所有权转移**：`throw e`（e 为已镜像局部）先 `Feng$inc(_ex)` 补引用，
   镜像展开释放一次、catch 处理释放一次，两边平衡。

### 关键判定规则

- `thrownLocal` 必须查 `mirroredVars: Set<Variable>`（`declareVar` 实际发射镜像才
  登记），**不能按类型判断**——catch 变量是手动声明的非镜像强引用别名，误 inc 会泄漏。
- 强引用参数：`$name_id_own` 副本镜像 + FuncWriter 把参数 `Variable` 传入
  `declareVar` 登记，否则 `throw p`（参数）会 double-free / UAF。
- 合成函数体（`enterProc == null`：cleanup/copy/destroy）不登记镜像——
  析构/清理路径不应再抛异常（unwind 重入）。

## 改动清单

| 文件 | 内容 |
|------|------|
| `src/main/resources/c/Header.h` | `Feng$Cleanup` 结构、`Feng$cleanup_top` extern、`Feng$cleanup_guard`/`Feng$cleanup_unwind` inline、`ExFrame.cleanup_mark` 字段 |
| `src/main/resources/c/builtin.c` | `_Thread_local Feng$Cleanup* Feng$cleanup_top`；`Feng$throw` 先展开再 longjmp（无帧先全展开再 abort） |
| `src/main/java/.../coder/StmtWriter.java` | `declareVar` 4 参版本（带 `Variable`）；`emitCleanupEntry`；`mirroredVars` 登记；`throw e` 补 inc；try 帧 `cleanup_mark` |
| `src/main/java/.../coder/FuncWriter.java` | `writeParamCleanupDecls` 将参数 `Variable` 传入 `declareVar` 登记 |
| `src/main/java/.../coder/ReleaserWriter.java` | testRunner per-test 帧补 `cleanup_mark` |

## 验证

- 测试：`src/test/resources/coder/cleanup-stack-1.feng`（GeneratorTest 自动遍历，
  memchk + address sanitizer 下 `leaked=0`，7 个场景：局部泄漏、跨函数、多局部、
  抛镜像局部、水位保留、强引用参数、正常路径）
- 回归：error/string/util/strconv/assert/math/buffer/sort/path/time 全零泄漏
- 命令行验证：
  `java -Dsan=address -Dfeng.memchk -jar target/feng-0.0.2.a.jar -p test -t f -b m -D -Lstd=std -i src/test/resources/coder/cleanup-stack-1.feng -o <out>`
  然后 `make` + 运行 `<out>/test.exe`

## 已知问题（既有，非本次引入）

1. **catch 体内 throw 死循环**：try 帧 `Feng$ex_top = _frame.prev` 在 catch/finally
   之后才执行，catch 内直接 `throw e`（或调用任何抛异常函数）会重入同一 setjmp 帧
   → 无限循环。
2. **try 块内 `new([3]int)` 缺 typedef**：test 模式下 SRef 数组局部声明在 try 内时
   `Feng$ArraySRef_Int` typedef 未发射（函数顶部声明则正常）。

## 改进方向（待讨论）

- **可穿透函数集分析（gating）**：基于调用图不动点分析（throw/assert/checkIndex/
  required 为源头），只对可能被 unwind 穿过的函数插入 mirror。当前所有函数都插桩，
  正确但开销偏高（每个需清理局部多一条栈条目 + 一次原子无关的 push/pop）。
