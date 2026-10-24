# Lambda 特性 — 实现设计

> 状态：设计稿（待评审）
> 前置：泛型约束系统（`generic-constraint-design.md`）为约束判别提供 `DomainTypeConstraint`/`TypeIncludedExpr` 基础。
> 本文以当前源码（`master`）为准。

## 1. 设计目标

引入两种匿名表达式，统一以 `var c = ...` 形式出现：

1. **函数 lambda**：`var c = func(){};` —— 纯函数指针，**不能捕获局部变量**，接受者只能是函数类型（`func(...)`）。
2. **类 lambda**：`var c = class(){};` —— 可捕获局部变量，编译期转换成匿名类，接受者只能是**单方法接口**。

两种 lambda 的接受者（目标类型）可以是：

- 一个具体函数类型（`func(int)int`）或单方法接口（`Callable`）；
- 一个泛型参数 `T`，其约束为「函数 | 单方法接口」。此时由用户用 `static if` 在编译期区分调用方式：

```
func invoke`T: func|interface`(c T) {
    static if (T <: func) {
        c();          // 函数 lambda：函数指针调用
    } else {
        c.call();     // 类 lambda：经单方法接口调用
    }
}
```

## 2. 语法（`Feng.g4`）

现有语法只有函数 lambda 的雏形（`operandExpr` 第 622 行）：

```
operandExpr
    : ...
    | FUNC procedure            # LambdaExpression
    ;
```

新增 `class` 变体，并让 `LambdaExpression` 区分两种形式：

```
operandExpr
    : ...
    | FUNC procedure            # LambdaExpression        // 函数 lambda
    | CLASS procedure           # ClassLambdaExpression   // 类 lambda（新增）
    ;
```

- `CLASS` 关键字复用现有 token（`CLASS : 'class'`，第 820 行附近），无需新词法。
- 两个分支共用 `procedure` 规则（参数表 + 返回类型 + 块体），`procedure` 解析逻辑（`SourceParseVisitor.visitProcedure`）已被函数/方法复用，lambda 直接复用。

### 解析

- `visitLambdaExpression`（现 `SourceParseVisitor.java:1001` 直接 `unsupported("lambda")`）→ 改为构建函数 lambda。
- 新增 `visitClassLambdaExpression` → 构建类 lambda。
- `LambdaExpression` AST 节点增加"是否类 lambda"标记（或新增 `ClassLambdaExpression` 子类，见 §4）。

## 3. 现状盘点

| 机制 | 现状 | lambda 的关系 |
|---|---|---|
| `func` 语法 + `procedure` 解析 | 完整（`visitProcedure` 被函数/方法复用） | 函数 lambda 直接复用 |
| 函数类型 `func(...)` | 完整：`AnonFuncTypeDeclarer` → `AnonFuncNormalizer` 去重 → `NamedFuncTypeDeclarer` → `Feng$Proto_<key>`（C 函数指针 typedef） | 函数 lambda 的值就是函数指针 |
| 函数值 | `FunctionExpression` 发射为 C 函数符号；已用于 `std/sort`、`std/math/calculus` 等 | 函数 lambda 生成合成 `FunctionDefinition` + `FunctionExpression` 值 |
| 泛型约束 `func`/`interface` | 完整：`DomainTypeConstraint(FUNC/INTERFACE)`；`func\|interface` = `BinaryTypeConstraint` 并集；`static if (T <: func)` 用 `TypeIncludedExpr` 判别 | 约束判别零新增 |
| 单方法接口 | **无现成检测** | 需新增 `InterfaceDefinition.singleMethod()` |
| 接口实现 → vtable/meta | 完整管线：`VTableBuilder` + `MetaWriter`，任何具体类实现接口生成 `Feng$Meta_<X>.i<iface>` + `Feng$IfaceEntry` | 匿名类直接复用 |
| 捕获/闭包 | **不存在**。分析期词法解析支持父作用域（`LocalSymbolContext` parent 链），但代码生成期每个 `Procedure` 是独立 C 函数、局部变量发射为 `name_id` | 类 lambda 的最大缺口 |

## 4. AST 设计

### 4.1 `LambdaExpression`（函数 lambda）

保持现状字段（`Procedure`），补足占位方法：

- `mono(GenericMap)`：无捕获、无类型变量（函数 lambda 体不引用外层类型变量，§8 说明）——但**原型**可能来自泛型上下文，需按 `gm` 替换。实现与现有表达式一致（`monoCopy`）。
- `mirror()`：目前返回 `this`，改为按需镜像（若 lambda 参与静态分支复制）。

分析期 `optimize(LambdaExpression)` 产出：

- **推断原型**：来自 `expectType`（`func(...)` 目标类型），参数类型可省略由左侧补全；返回类型由函数体推断或与期望类型匹配。
- **合成 `FunctionDefinition`**：mangle 唯一名（如 `Lambda_<n>`），`TypeParameters.empty()`，复用 `FuncWriter` 发射。
- **值**：`FunctionExpression` 包装合成函数 → C 函数指针。

### 4.2 `ClassLambdaExpression`（类 lambda，新增节点）

承载捕获分析的全部中间产物，供 lowering pass 消费：

| 字段 | 内容 |
|---|---|
| `procedure` | lambda 体（参数表 + 返回 + 块体），分析期已按嵌套作用域检查 |
| `captures` | 捕获集：`List<Variable>`（引用的外层局部变量） |
| `captureRefers` | 每捕获变量的引用类别（值拷贝 / `&` 引用 / `*` 拥有） |
| `typeVars` | 提升的类型变量集（lambda 引用到的外层类型变量，§8 泛型提升） |
| `targetIface` | 目标单方法接口（`DerivedType`，可为泛型接口） |
| `inferredPrototype` | 从接口唯一方法推断的原型 |

> 是否新增独立类 vs 复用 `LambdaExpression` 加标记：**建议新增 `ClassLambdaExpression` 子类**，避免函数 lambda 分支被类 lambda 字段污染，且 lowering pass 的类型判别更清晰。

## 5. 架构决策

### 5.1 先当代码块分析，到 AnonFuncNormalizer 期（或其后的 lowering pass）再转成类

**不做「先转类再分析」**，原因：

1. **接口与原型未知**：匿名类要实现的单方法接口由赋值目标（`expectType`）或 lambda 体签名决定，而 `expectType` 只在 `SemanticAnalyzer.optimize` 注入。解析阶段无接口信息，提前转类必然二次修正。
2. **泛型上下文未建立**：泛型接口/泛型函数内的 `T` 只有分析期 `GenericMap` 建立后才确定。提前转类会造出带悬空类型变量的类。
3. **与 `AnonFuncNormalizer` 定位一致**：该 pass 已是"匿名函数类型 → 命名 `PrototypeDefinition`"的 lowering（分析后、mono 前），匿名类是它的"类版"，同一阶段同构注入。

**选「先当代码块分析」**，因为：

1. **词法解析已支持**：`context.findVar` → `LocalSymbolContext.findVar` 沿 parent 链解析外层局部变量（`StackedContext.java:65`、`LocalSymbolContext.java:57`）。lambda 作为嵌套作用域分析时，体内 `SymbolExpression` 天然解析到外层变量 → 捕获集分析期就能收集。
2. **语法零改动**：lambda 体就是普通 `BlockStatement`。
3. **语义绑定可重定向**：`analyse(ReturnStatement)` 把 `return` 绑到 `enterProc`（`SemanticAnalyzer.java:3218`）。类 lambda 作为嵌套 Procedure 分析时 `return` 先绑到 lambda 自身原型（中间状态正确），转类时重定向到合成方法（`Monomorphization.retarget` 已有先例，`Monomorphization.java:1207`）。

### 5.2 关键冲突：`enterProc` 单例

`analyse(Procedure)` 有 `assert enterProc == null`（`SemanticAnalyzer.java:1682`）。类 lambda 作为嵌套 Procedure 分析时，外层 Procedure 仍在分析中（`enterProc` 非空）。

**解法**：不把 lambda 当完整 `Procedure` 递归分析，而是**分析 lambda 表达式时临时切换 `enterProc`**（保存/恢复，模式同 `enterClass`/`enterMethod` 的 1363-1445 行）。lambda 的 `Procedure` 原型此时用推断原型（来自 `expectType` 或 lambda 体签名），体内 `return` 绑到它。

### 5.3 匿名类在 mono 前生成

**生成时机 = AnonFuncNormalizer 期（或紧随其后的新 lowering pass）**，理由：

- **mono 期太晚**：匿名类必须赶在 mono 前进入 `dagClasses`，否则 `VTableBuilder`/`MetaWriter`/依赖排序都看不到它。
- **AnonFuncNormalizer 期正好**：分析后、mono 前，读完整 `AnalyseSymbolTable`，已有"匿名→命名"规范化 + `dagPrototypes` 注入先例。匿名类注入 `dagClasses` 同构。
- **lambda 本身可能用泛型变量** → 匿名类作为**带类型参数的模板**进 `dagClasses`，具体化交给 Monomorphization（§8 泛型提升）。

### 5.4 捕获 = 局部变量转匿名类字段

捕获的本质（用户已确认）：lambda 体内引用的外层局部变量 → 匿名类字段。匿名类实例化时 `new(AnonClass, {cap1=x, cap2=y})` 拷入。

**引用类别**（与逃逸分析一致，见 `ReferKind`），P5 实测语义：
- **强引用捕获（`*x`）**：字段类型 = 变量类型（STRONG）。捕获时匿名类持有自己的强引用（发射为原始指针，无 inc/dec——所有权从外层转移，外层置 nil 不影响），destroy 里 `dec_ns` + `vDestroy` + `free`。
- **值捕获（无 refer）**：字段类型 = 变量类型（内嵌类值）。捕获时深拷贝（发射为 `Feng$copy_<key>`），destroy 里释放内嵌强引用内容。值捕获是**创建时快照**，之后外层修改不影响。
- **SRef 数组捕获（`[*]T`）**：字段类型 = 数组类型。捕获时 `inc_ns` 保留自己的引用（**共享引用**，外层修改对匿名类可见），destroy 里 `dec_ns` + `free`。
- **虚引用捕获（`&x`）**：**拒绝**（`can't capture phantom-reference ... in class-lambda`）。虚引用只能是本地变量或参数，不能是字段（reference_zh.md「虚引用」），且匿名类可逃逸捕获作用域 → 悬垂。须按强引用/值捕获。

### 5.5 函数 lambda 不捕获

函数 lambda 保持纯函数指针，语义干净，接受者只能是 `func(...)`。用户需要捕获时写 `class(){}`。

## 6. 单方法接口检测

`InterfaceDefinition` 新增 helper：

```java
public boolean singleMethod() {
    return allMethods().size() == 1;
}
```

- 复用现有 `allMethods()`（自身 + 组合接口的并集）。
- 目标接口校验：类 lambda 的 `expectType` 必须是 `singleMethod()` 的接口；否则报错（`can't use class-lambda as non-single-method-interface ...`）。

## 7. 类 lambda → 匿名类 lowering pass

### 7.1 输入 / 输出

- **输入**：`AnalyseSymbolTable`（分析已完成），含所有 `ClassLambdaExpression` 节点（已挂捕获集、类型变量集、目标接口、推断原型）。
- **输出**：`dagClasses` 新增匿名类；lambda 表达式替换为 `new(AnonClass`args`, {cap=...})`；`AnonFuncNormalizer` 的遍历器补 `ClassLambdaExpression` 分支（当前无该分支）。

### 7.2 合成步骤

1. **扫描**：收集 lambda 体引用的外层局部变量（捕获集）与类型变量（泛型提升集）。
2. **提升类型参数**：外层类型变量集 → 匿名类 `TypeParameters`（§8）。
3. **生成字段**：捕获变量 → `ClassField`，类型按引用类别处理（值拷贝 / `&` 引用 / `*` 拥有）。
4. **生成方法**：推断原型（来自接口唯一方法）→ `ClassMethod`，方法体 = lambda 体，`impl` = 目标接口。
5. **替换表达式**：原 lambda → `new(AnonClass`args`, {cap1=x, cap2=y})`。
6. **重定向语义绑定**：lambda 体内 `return` 的 `procedure` → 合成方法；`this` → 合成类 symbol（`wrapThis` 模式，`SemanticAnalyzer.java:1458`）。
7. **注入 DAG**：匿名类进 `dagClasses`，参与 VTableBuilder/MetaWriter/mono 全流程。

### 7.3 去重策略

匿名类 key = **lambda 语法位置（pos）**。同一位置循环中多次执行 → 同一泛型类 → 一个具体化实例。不同位置各自成类。**不做类型组合去重**——代码膨胀大头是泛型，位置 key 换来实现简单、调试直观（用户已确认）。

### 7.4 P4 实现记录

- **命名**：匿名类符号 `Anon_<hex>`（命名符号）。`CommonUtil.rand()` 返回 unnamed `Identifier`（值含 `$` 前缀），`CWriter` 对 unnamed 不加 `$` 而 `ClassMeta.symbolName` 恒加 `$`，会生成 `$Anon_*` / `$$Anon_*` 不一致。用命名符号两路统一为 `$Anon_<hex>`。
- **合成方法参数**：必须用 lambda 自身原型 `procedure().prototype()` 的参数集（P3 `analyse(nproc)` 时 `FixedParameter` 已建 Variable 缓存，方法体引用同一批变量）；返回类型/catchAll 用接口方法原型。
- **捕获字段**：`ClassField` 名 = 捕获变量名，类型 = 变量类型去引用。
- **捕获重写**：`VariableExpression(捕获变量)` → `MemberOfExpression(CurrentExpression($self), 字段)`，`$self` 结果类型 = `DerivedTypeDeclarer(anonDt, STRONG)`。只查最内层上下文。
- **new 表达式**：`NewExpression(NewDefinedType(anonDt), ObjectExpression{cap=变量引用})`。变量引用先走 `rewriteCapture`——嵌套时外层捕获须以 `$self->字段` 传递。
- **嵌套类 lambda 捕获提升**：`collectCaptures` 遇嵌套 `ClassLambdaExpression` 须把其 `captures()` 中不属本层局部的变量提升为本层捕获，否则外层匿名类不捕获、内层 `new` 引用悬空变量。

### 7.5 P5 实现记录

- **匿名类 DAG 依赖边**：值类型捕获字段（内嵌类值，如 `var b Box`）要求目标类结构先于匿名类结构发射（C 内嵌非完整类型字段不合法）。`rebuildClassGraph()` 补字段类型依赖边（镜像 `findInitDeps` 的字段规则：有 refer 的字段只存指针不需完整定义，值字段/值数组/元组需要）。
- **捕获发射语义**（见 §5.4）：强引用 → 所有权转移（原始指针）；值 → `Feng$copy_<key>` 深拷贝快照；SRef 数组 → `inc_ns` 共享引用；primitive → 值。
- **匿名类 destroy**：ReleaserBuilder 自动收录（anon 类进 dagClasses 后 classMetas 生成），按字段类别释放：强引用 `dec+vDestroy+free`、值内嵌 `destroy_X(&field)`、SRef `dec+free`、虚引用 no-op。
- **虚引用捕获拒绝**：P3 `optimize(ClassLambdaExpression)` 收集捕获集后检查每个捕获变量的 `maybeRefer`，PHANTOM 报 `can't capture phantom-reference`（与「虚引用不能是字段」的既有规则一致，避免匿名类逃逸作用域后悬垂）。

### 7.6 P6 实现记录

- **类型变量收集**：P3 `optimize(ClassLambdaExpression)` 从推断原型、lambda 自身原型、捕获变量类型递归收集引用到的外层类型变量（`collectTypeVars`），存入 `e.typeVars()`。
- **泛型提升**：`lowerClassLambda` 克隆外层类型变量（`TypeParameter.clone()`，保 id）作匿名类自身类型参数。同 id 使外层函数 gm（`{T→实参}`）与匿名类 gm（`{T'→实参}`）都靠 HashMap equals 命中（`branchGm` 模式）。
- **impl map**：用调用点实例化后的 `DerivedType`（`e.targetType()`），泛型接口在调用点已是 `ValueGetter`T``/`ValueGetter`int``，`concretizeClass` 再经 `{T'→实参}` 映射到具体接口。裸 `iface.link(pos)` 只填接口自身形参，无法表达调用点实参。
- **方法原型**：用 P3 实例化后的推断原型（`e.inferredPrototype()`），返回/参数类型已含调用点实参或外层 T（同 id → 匿名类 gm 命中）。
- **匿名类型**：`anonClass.link(pos, typeArgs)`（调用点类型参数 = 外层 T 原 T 非克隆），外层函数 gm 扫描 new 时映射为 `Anon_具体`；`link` 设置 def + gm=`{T'→T}`。
- **匿名符号模块**：匿名符号须带定义模块（`ast.module` 的 `ModulePath`）。泛型具体化时 use 点用 `Mangle.name(dt)`（`[module$]Anon_<hex>_<args>`），decl 点用 `symbolName(Mangle.symbol(dt))`（恒在名称前加 `$`）；模块为空时前者无 `$` 前缀而后者有，两路不一致（`Feng$meta_Anon_` vs `Feng$meta_$Anon_`），C 编译失败。带模块后两路统一为 `test$Anon_<hex>_<args>`。
- **P3 前置修复（接口原型实例化）**：`ipt = dt.gm().instantiate(im.prototype())`——目标接口是泛型（如 `ValueGetter`T``）时，接口方法原型里的接口类型参数 `T_iface` 须替换为调用点实参（`int`）或外层类型变量（外层函数 T），否则与 lambda 签名比较残留 `T_iface`（不同 id）恒不等（`class-lambda signature mismatch`）。

### 7.7 P7 实现记录

泛型约束 `func|interface` + `static if` 判别 lambda 调用，端到端测试。

- **判别机制**：`kind`T func|interface`(c T)` 用 `static if (T <: func) / (T <: interface)` 在 mono 期按实例裁决——`T <: func` 恒真、`T <: interface` 恒真（域判别本身已工作），返回 1/2 区分两条路径。
- **接口收窄派发（本阶段核心缺口）**：`static if (T <: Callable)` 真分支收窄后 `c.call()` 直接经接口派发调用类 lambda。原实现的分支收窄只对分支树内新声明的局部变量生效（`var q T = c` 后 `q` 的声明类型在分支树内被 mono 映射）；**对分支树外的参数直接引用 `c` 失效**——`findSymbol`/`findCallable`/`optimize(VariableExpression)` 重新从 `c.variable().type()`（原始未收窄类型）读取，符号表不随分支重建。修复：`SemanticAnalyzer` 新增 `branchGms` 栈 + `narrowType(t)`（把 `t` 依次经各层分支收窄 `GenericMap` 映射），在 `findSymbol`、`findCallable`（var 分支）、`optimize(VariableExpression)` 三处对取回的类型套用 `narrowType`，使参数直接引用经收窄后的约束取成员（`T <: Callable` → `MemberSet` 并集含 `call`）。
- **测试**：`std-tests/test_lambda_p7.feng` 5 个用例全过（判别 func/interface、`T <: Callable` 接口派发、泛型接口 `T <: Callable2`int`` 派发、类 lambda 经单方法接口传入后判别调用、具名函数原型约束 `T (Task)` 下直接 `t()` 调用），ASan + memchk 无泄漏。
- **具名函数原型约束（func 分支的显式形态，本次补强）**：设计文档 §1 示例 `func Task = () int; func f`T (Task)`(t T) int { return t(); }` 打通。为此修复两个既存缺陷：
  - `SemanticAnalyzer.assignValue` 缺 `l instanceof DerivedTypeDeclarer`（其 `def()` 为 `PrototypeDefinition`）接收 `FuncTypeDeclarer` 的分支 → 补 `compatible(pd.prototype(), rf.prototype(), e)`，消除 `can't use '!Task' as 'Task'`。
  - `FuncWriter.writeParamCleanupDecls` 对函数类型参数无条件生成 `_own` 副本 → C 层 `typedef Int test$Task();` 下局部变量 `test$Task $x_own = $x;` 非法初始化（函数类型不能有对象实例）。跳过 `FuncTypeDeclarer` 及 `DerivedTypeDeclarer(PrototypeDefinition)` 参数。

**与设计文档（§1 字面示例）的差异**：
- **func 分支的 `c()` 函数指针调用**：裸 `func` 域约束不含原型，分析期 `funcPrototype` 仅解析单个原型约束、无法解析函数类型原型，且 mono 期不重分析，故 `c()` 在裸 func 域下不可调用。本阶段只做域判别（`T <: func` 返回 1）；实际函数调用经**显式具名原型约束**（`T (Task)`）实现（见上），或直接传函数值调用。
- **`T func|interface` 的 else 分支收窄到 `interface` 域无成员**：plan §1 字面示例的 else `c.call()` 不可直接实现，须收窄到具体接口形态（`T <: Callable`），即本阶段 `invoke`T interface`` 的形态。
- **嵌套 static if 分支收窄**：有既存 mono 折叠缺陷——外层收窄 mono 会把内层 static if 条件按克隆约束（仍是类型变量）过早裁决，内层分支恒走同一侧。测试只用单层判别。

## 8. 泛型提升

lambda 在泛型函数/泛型类内部时，捕获字段类型、接口方法原型可能引用外层类型变量 `T`。既然匿名类 mono 前生成，就让它参与 mono 具体化：

- **类型参数** = lambda 引用到的外层类型变量集合（如 `T`）；
- **字段/方法原型** 用 `GenericTypeDeclarer` 引用这些参数；
- **调用点** = `new(AnonClass`T`, {cap=...})`；
- mono 期 `concretizeClass` 照常具体化，`T` 由 `GenericMap` 替换。

```
// 泛型函数内的类 lambda
func map`T`(arr [&]T, fn func(T) int) [&]int {
    ...
    var cb = class() { ... };   // 捕获 arr，引用 T
    ...
}
// 匿名类：class Anon`T` { ... } → new(Anon`T`, {cap=arr})
```

**边界（第一版范围）**：
- 支持捕获字段/接口原型引用外层类型变量（提升为匿名类类型参数）。
- 目标接口本身是泛型（`interface Caller`T``）：匿名类继承该类型参数，具体化时一并替换。
- 不支持：lambda 体内定义嵌套泛型（第一版不做）。

## 9. 实现阶段拆解

| 阶段 | 内容 | 依赖 |
|---|---|---|
| **P0** | 语法：`CLASS procedure` 替代分支 + `ClassLambdaExpression` 节点；`visitLambdaExpression` 改 `unsupported`；`visitClassLambdaExpression` 新增 | 无 |
| **P1** | 函数 lambda：无捕获，接受者 `func(...)`。改 `SemanticAnalyzer.optimize(LambdaExpression)`，复用 `AnonFuncNormalizer` + `FuncWriter`，产出合成 `FunctionDefinition` + `FunctionExpression` 值 | P0 |
| **P2** | 单方法接口检测：`InterfaceDefinition.singleMethod()` + 类 lambda 目标接口校验 | P1 |
| **P3** | 类 lambda 分析：嵌套作用域检查（`enterProc` 临时切换）、捕获集收集、推断原型、`return`/`this` 绑定暂存 | P1、P2 |
| **P4** | 匿名类 lowering pass：合成匿名类（字段/方法/impl）、表达式替换、`dagClasses` 注入 | P3 | ✅ 已完成（见 §7.4） |
| **P5** | 捕获引用类别与生命周期：值/`&`/`*` 捕获 → 字段引用类别；匿名类 destroy（`ReleaserBuilder`） | P4 | ✅ 已完成（见 §7.5） |
| **P6** | 泛型提升：捕获字段/接口原型引用外层类型变量 → 匿名类类型参数，mono 具体化 | P4 | ✅ 已完成（见 §7.6） |
| **P7** | 泛型约束 `func\|interface` + `static if` 判别 lambda 调用，端到端测试 | P1-P6 | ✅ 已完成（见 §7.7） |

## 10. 待决问题

1. **类 lambda 捕获语法**：目前捕获是隐式的（体内引用的外层变量自动捕获）。是否要显式捕获列表（类似 Rust `move` / C++ capture list）？隐式捕获 + 引用类别推断更简洁，但生命周期陷阱需要仔细设计（§5.4）。
2. **函数 lambda 的返回类型推断**：`var f func(int) int = func(x){ return x; }` 返回类型由体推断，还是严格与 `expectType` 匹配？
3. **类 lambda 的 `this`**：体内 `this` 指匿名类实例，还是外层对象？建议指匿名类实例（与"转成类"语义一致）。
4. **匿名类命名**：`Lambda$<n>` / `Anon$<pos>`，进入 mangle 体系，需与 `Monomorphization` 的跨模块 owner 逻辑兼容。
5. **捕获变量的可变性**：捕获变量被 lambda 修改时，如何写回外层？第一版建议**只读捕获**（按值拷贝 / 引用），修改语义（`&mut` 捕获）留待后续。

## 11. 相关文件

| 文件 | 改动 |
|---|---|
| `src/main/antlr4/org/cossbow/feng/parser/Feng.g4` | `operandExpr` 加 `CLASS procedure` 分支 |
| `src/main/java/org/cossbow/feng/parser/SourceParseVisitor.java` | `visitLambdaExpression` 实现；新增 `visitClassLambdaExpression` |
| `src/main/java/org/cossbow/feng/ast/expr/LambdaExpression.java` | 补 `mono`/`mirror` |
| `src/main/java/org/cossbow/feng/ast/expr/ClassLambdaExpression.java` | 新增节点（捕获集、类型变量集、目标接口、推断原型） |
| `src/main/java/org/cossbow/feng/ast/oop/InterfaceDefinition.java` | `singleMethod()` |
| `src/main/java/org/cossbow/feng/analysis/SemanticAnalyzer.java` | `optimize(LambdaExpression)`；`optimize(ClassLambdaExpression)`；`enterProc` 临时切换 |
| `src/main/java/org/cossbow/feng/analysis/AnonFuncNormalizer.java` | 遍历器加 lambda 分支；类 lambda → 匿名类合成（或新增独立 pass） |
| `src/main/java/org/cossbow/feng/analysis/mono/Monomorphization.java` | `scanExpr`/`scanStmt` 加 lambda 分支；匿名类具体化（若泛型提升） |
| `src/main/java/org/cossbow/feng/coder/*` | 匿名类走既有 class 发射；函数 lambda 走既有 func 发射（`FuncWriter`） |
