# @Sync/@Async 泛型支持的理论分析与设计

> 状态：理论分析（尚未实现）
> 范围：让 `@Sync` 支持标注泛型类、泛型字段；让 `@Async` 支持泛型函数和方法。
> 关联代码：`SemanticAnalyzer.analyzeSync`、`TypeDeclarer.sync/markSync/syncable`、`TypeDefinition.syncable`。
> 关联文档：`reference_zh.md` 第 3293-3664 行（并发检查）。

## 1. 背景与目标

当前 `@Sync` / `@Async` 标注不支持泛型（`reference_zh.md` 3562-3572 行明确记录）：

```feng
// @Sync    // ✖：泛型类不能标注
class Box`T` {
    // @Sync    // ✖：泛型类中都不能标注
    var t T;
}
// @Async    // ✖：泛型函数不能标注
func run`R`() {}
```

目标：

- `@Sync class Box`T` { ... }` —— 标注泛型类。
- `class Box`T` { @Sync var t *T; }` —— 标注泛型字段（类型变量）。
- `@Async func run`T`(...)` / `@Async method m`T`(...)` —— 泛型函数与方法。

语法层（`Feng.g4`）已经允许这些写法（`classDefinition`/`functionDefinition`/`declaredNames` 都带 `typeParameters?`），**限制纯在语义分析，不需要改 grammar**。

## 2. 现状梳理

### 2.1 类型侧的同步判定

`sync()`（`TypeDeclarer.java:140-142`）：

```java
public boolean sync() {
    return (markSync || maybeRefer().none()) && syncable();
}
```

其中 `markSync` 是"声明是否被 `@Sync` 标注"（变量/字段/参数级，`TypeDeclarer` 已有），`syncable()` 是"实例类型是否可同步"。

| 类 | `syncable()` 现状 | 问题 |
|---|---|---|
| `TypeDeclarer`（基类） | 恒 `true` | 类型变量继承了这个 `true` |
| `GenericTypeDeclarer`（类型变量 `T`） | **未重写**，继承 `true` | 把 `T` 误判成"恒可同步" |
| `DerivedTypeDeclarer` | `def().syncable()`（`DerivedTypeDeclarer.java:77-79`） | 只委托给定义，**忽略泛型实参** |
| `ArrayTypeDeclarer` / `TupleTypeDeclarer` | 递归元素 | 递归穿透到元素 |
| `ClassDefinition` | 由 `analyzeSync` 算出的**缓存布尔**（`ClassDefinition.java:255-261,181`） | 无法表达条件可同步 |
| `InterfaceDefinition` | `modifier().sync().has()`（`InterfaceDefinition.java:102-104`） | 泛型接口无支持 |

### 2.2 分析侧的同步检查

- `analyzeSync(ClassDefinition cd)`（`SemanticAnalyzer.java:1079-1112`）：遍历 `allFields()`，**提前算出一个布尔**写进 `cd.syncable`。
- `analyzeSync(TypeDeclarer td)`（`SemanticAnalyzer.java:1046-1068`）：按 `DerivedTypeDeclarer`/`ArrayTypeDeclarer`/`TupleTypeDeclarer` 分发，**其余（含 `GenericTypeDeclarer`）直接 `return true`**。
- `assignableSync(l, r, ...)`（`SemanticAnalyzer.java:1485-1500`）：核心是 `l.sync() == r.sync()`。
- `markSync(...)`（`SemanticAnalyzer.java:2255-2274`）：标记后要求 `t.sync()` 成立。
- `checkEnterAsync(...)`（`SemanticAnalyzer.java:4366-4374`）：@Async 方法调用点要求接收者 `t.sync()`。
- `@Async` 声明：`declareFunc`/`declareMethod` 置 `enterAsync`；`analyse(FixedParameter)`（1472-1477）强制 `markSync(fp.type())`；`analyse(Prototype)`（1443-1447）禁止返回值。

### 2.3 关键事实：函数体只分析一次

`SemanticAnalyzer.java:1421-1427`：

```java
enterProc = proc;
context.enterScope();
analyse(proc.prototype(), true);
analyse(proc.body());     // ← 声明时分析一次，类型变量仍在
...
```

`GenericMap.instantiate(...)` 只在**调用点实例化签名（prototype）**，用于检查调用本身，**不会重跑函数体**。

**结论：泛型函数体的语义检查（含 sync）在模板层、带类型变量的情况下一次性完成；没有"替换后再重分析"这一步。**

## 3. 核心问题

### 3.1 布尔值表达不了"条件同步"

`syncable()` 对 `@Sync class Box`T` { var t *T; }` 的结果是"当 `T` 可同步时才成立"——这不是 `true`/`false` 能表达的。现有 `ClassDefinition.syncable` 是编译期一次性算好的单个布尔，无法承载这个依赖。

### 3.2 两个未知量：`kind` 与 `syncable`

把 `sync()` 展开：

```
sync = (markSync || isValue) && syncable
```

对裸类型变量 `T`（`GenericTypeDeclarer` 无 `*`），存在**两个**未知量：

1. **`isValue(T)`（值/引用二义性）**：`T` 可能被实例化成值类型（`int`）或引用类型（`*A`）。
2. **`syncable(T)`（可同步性二义性）**：`T` 的实例是否可同步，取决于实参。

这两个未知量的性质**完全不同**：

- **`syncable(T)` 会抵消**：`assignableSync` 只比较可赋值（同类型）的两侧，所以两侧是同一个 `T`、同一个 `If({T})`，自动相等，不构成判定障碍。

- **`isValue(T)` 不抵消**：当两侧 `markSync` 不同（如 async 形参被自动标 `@Sync`，普通 `var` 未标）时，一侧签名含 `isValue`、另一侧含 `true`，两者只在 `isValue` 取特定值时相等。而函数体只分析一次，给不出"有时相等有时不等"的结论。

### 3.3 单次判定的不可判定性

例：

```feng
@Async func run(a T) { var v T = a; }
```

- `a`（async 形参）被自动 `markSync(true)`，`v`（普通 `var`）`markSync=false`。
- 当 `T` 实例化为**引用** `*A`：`v`（非同步）别名到 `a`（同步）→ 违规（对应文档 3633 `var s *ThArg = t` 反例）。
- 当 `T` 实例化为**值** `int`：`v = a` 是拷贝，无别名 → 合法。

由于函数体只分析一次、`assignableSync` 只能给**一个最终判定**，这个判定对"值"或"引用"的某一侧必然错误：

- 判"通过" → 漏掉引用侧的违规（漏报）；
- 判"不通过" → 误伤值侧合法程序（误报）。

**"返回 `false`" 也不是解**：`assignableSync` 是相等比较，两侧都返回 `false` 会变成 `false == false` 通过，是漏报方向。正确的保守方向是"markSync 不同且 kind 未知"直接报错，而非两边都 false。

## 4. 结论

### 4.1 两条结论

1. **单靠符号化 `sync()` 走不通**：符号化（`SyncStatus = Sync / NonSync / If(Set<TypeParameter>)`）能解决 `syncable` 半，但解决不了 `kind` 半——`kind` 未知 + `markSync` 不同时，单次判定必错。

2. **必须先钉死 `kind`**：`kind`（值/引用）二义性必须消除。消除它**不需要发明新约束**，已有的 `DomainTypeConstraint` 就能表达：

   ```
   T : struct   → 值类型 → 自动同步（文档 3460）
   T : class    → 引用   → markSync && syncable
   ```

   而且这套语法、AST、parser 全部就位（`Feng.g4:367-378`、`TypeParameter.constraint()`、`SourceParseVisitor`），唯一缺的是**语义校验**——`SemanticAnalyzer.analyse(TypeParameters)`（589-591）目前只有 `// TODO: check generic constants`，实参是否满足约束根本没检查。

### 4.2 最终方案：约束（钉 kind）+ 符号化（载 syncable）

两者缺一不可：

1. **域约束钉 kind**：补上 `analyse(TypeParameters)` 的实参校验，让 `T : class` / `T : struct` 真正生效。之后 `isValue` 确定，`sync()` 对每个具体实参给出确定结论。

2. **符号化载 syncable**：`syncable()` 返回符号值 `Sync / NonSync / If(S)`。`syncable` 二义性不能用约束表达（`Sync` 是 attribute 不是 type），只能靠 `If(S)` 承载；又因为它在 `assignableSync` 两侧同名抵消，不产生误判。

### 4.3 需要留意的辅助点

- **`@Async` 泛型函数体内的"边界假设"**：async 边界在调用点保证"实参可同步"，所以函数体内形参类型为 `If(S)` 时，应视作"假定可同步"（等价 `Sync`），而非报错。这是 sync 检查自身的传播逻辑，与泛型约束无关。

- **两阶段标注分离**（实现层）：不在 `analyzeSync(ClassDefinition)` 里提前下 `syncable(boolean)` 结论；给 `TypeDefinition` 加 `markSync`（对称 `TypeDeclarer.markSync`），声明期只记录标注，`syncable()` 改为递归查询。

## 5. 相关文件索引

| 关注点 | 位置 |
|---|---|
| `sync()` / `markSync` / `syncable()` 定义 | `src/main/java/org/cossbow/feng/ast/dcl/TypeDeclarer.java` |
| `TypeDefinition.syncable()` 基类 | `src/main/java/org/cossbow/feng/ast/TypeDefinition.java` |
| 类/接口的 syncable | `ast/oop/ClassDefinition.java`、`ast/oop/InterfaceDefinition.java` |
| 派生类型/数组/元组/类型变量的 syncable | `ast/dcl/DerivedTypeDeclarer.java`、`ArrayTypeDeclarer.java`、`TupleTypeDeclarer.java`、`GenericTypeDeclarer.java` |
| 同步分析 | `analysis/SemanticAnalyzer.java`（`analyzeSync` 1046-1112、`assignableSync` 1485-1500、`markSync` 2255-2274、`checkEnterAsync` 4366-4374） |
| @Async 声明处理 | `SemanticAnalyzer.java`（`declareFunc` 1394、`declareMethod` 1156、`analyse(FixedParameter)` 1472、`analyse(Prototype)` 1443） |
| 函数体只分析一次 | `SemanticAnalyzer.java:1421-1427` |
| 泛型约束语法 | `src/main/antlr4/org/cossbow/feng/parser/Feng.g4:363-378` |
| 泛型约束 AST | `ast/gen/TypeConstraint.java`、`TypeParameter.java` |
| 约束语义校验（TODO） | `SemanticAnalyzer.analyse(TypeParameters)` 589-591 |
