# AST Mirror 深拷贝机制设计文档

## 1. 问题背景

可变参数函数展开（variadic inline expansion）在 `expandInlined` 中通过 `inline.addAll(body.list())` 直接引用被展开函数的 body AST 节点。当同一 variadic 函数被多次展开时，所有展开副本共享同一组 AST 节点对象。语义分析遍历这些节点时会修改其可变状态，第二次展开的状态会覆盖第一次的，导致代码生成错误。

### 典型症状

```
gen.cpp:28:29: error: use of undeclared identifier '$i_2'; did you mean '$i_1'?
```

第一个 `{ var i = 100; os$printf("{}", i); }` 块中声明 `$i_1` 但引用 `$i_2`，因为两次展开共享同一个 `CallStatement` 节点，其 `replace` 字段被第二次展开覆盖。

## 2. 解决方案

为所有参与展开的 AST 节点类型添加 `mirror()` 方法，实现深拷贝。每次展开时对函数体子树做 mirror，使各次展开拥有独立的节点副本，可变状态互不干扰。

## 3. Mirror 范围

### 需要 mirror 的类型

| 类别 | 具体类型 | 原因 |
|---|---|---|
| **Statement** | BlockStatement, DeclarationStatement, AssignmentsStatement, CallStatement, IfStatement, ReturnStatement, ThrowStatement, ForStatement(含子类), SwitchStatement, BreakStatement, ContinueStatement, CatchClause | 有可变状态（Lazy、List、stack） |
| **Expression** | 全部子类（Binary, Unary, Call, Symbol, Variable, Block, Conditional, MemberOf, IndexOf, Derefer, Convert, Paren, Array, Object, Tuple, New, Literal, Enum系列, Is, CheckNil, ReferEqual, Sizeof, Method, Lambda, Pairs, Current, TupleIndex, Lambda） | Expression 基类有 expectType/resultType/expectCallable 三个可变字段 |
| **Variable** | Variable | mirror() 已存在，新增 mirrorForExpansion() 重置 type/value 为 nil |
| **Lazy** | Lazy\<T\> | 重置为 nil()，或保留 clone() 行为视上下文而定 |
| **Operand** | Operand(基类), VariableOperand, DereferOperand, FieldOperand, IndexOperand, TupleOperand | 有 type: Lazy、relay: List、variable: Lazy 等可变状态 |
| **Assignment** | Assignment | 有 replacer: Lazy 可变状态 |
| **Tuple(stmt)** | Tuple | 包装 Expression 列表 |
| **Branch** | Branch, SwitchBranch | 有 stack: List 可变状态 |

### 不需要 mirror 的类型（共享即可）

| 类别 | 具体类型 | 原因 |
|---|---|---|
| **Identifier** | Identifier | 不可变，equals/hashCode 基于 value 字符串 |
| **Symbol** | Symbol | 不可变（module + name 都是 final） |
| **Position** | Position | 不可变坐标 |
| **TypeDeclarer** | 全部子类 | 类型推导共用同一对象，修改发生在函数定义分析阶段而非展开后；共享避免重复计算缓存值 |
| **全局定义** | ClassDefinition, InterfaceDefinition, StructureDefinition, EnumDefinition, Method, ClassMethod, InterfaceMethod 等 | 全局唯一，引用身份必须一致 |
| **函数签名** | Prototype, ParameterSet, FixedParameter, VariadicParameter | 函数定义的一部分，不在 body 子树中 |
| **枚举/字面量** | Primitive, BinaryOperator, UnaryOperator, Declare, Modifier, Refer, ReferKind, Literal 子类 | 不可变 |
| **泛型** | TypeArguments, TypeParameters, GenericMap | 不可变 |

### 禁止展开的类型

| 条件 | 处理 |
|---|---|
| Procedure 有 labels（非空 Map） | 在 expandVariadic 中报语义错误 |

## 4. Mirror 实现规则

### 4.1 通用规则

- **递归深拷贝**：所有子节点引用按其自身类型规则处理（需要 mirror 的递归 mirror，不需要的共享）
- **可变状态重置**：Lazy 字段重置为 `nil()`，List 字段重置为空列表，boolean/long 缓存字段重置为初始值
- **不可变值共享**：Identifier、Symbol、Position、枚举、全局定义等直接引用原始对象
- **容器重新创建**：List、IdentifierMap、Optional 等容器创建新实例，内容按元素类型规则处理

### 4.2 Expression 基类处理

Expression 有三个可变字段：
```java
public final Lazy<TypeDeclarer> expectType = Lazy.nil();
public final Lazy<TypeDeclarer> resultType = Lazy.nil();
private boolean expectCallable;
```

mirror 时这些字段通过 `new` 构造自动获得初始值（Lazy.nil() 和 false），无需额外处理。

### 4.3 Variable 的两种 mirror 语义

| 方法 | 用途 | type | value |
|---|---|---|---|
| `mirror()` | 参数镜像（现有） | `type.clone()`（保留已推导的类型） | `value.clone()`（保留初始化表达式） |
| `mirrorForExpansion()` | 展开深拷贝（新增） | `Lazy.nil()`（重置，语义分析重新推导） | `Lazy.nil()`（重置） |

### 4.4 各节点类型的具体 mirror 规则

#### Statement 子类

| 类型 | 子节点 mirror 规则 | 可变字段重置 |
|---|---|---|
| BlockStatement | list 中每个 Statement 递归 mirror | stack → List.of() |
| DeclarationStatement | variables 中每个 Variable mirrorForExpansion | 无额外 |
| AssignmentsStatement | list 中每个 Assignment mirror | 无额外 |
| CallStatement | call(CallExpression) 递归 mirror | replace → Lazy.nil() |
| IfStatement | init/condition/yes/not 递归 mirror | cond → Lazy.nil(), stack → List.of() |
| ReturnStatement | result 递归 mirror | procedure → Lazy.nil(), local → List.of(), relay → Lazy.nil() |
| ThrowStatement | exception 递归 mirror | procedure → Lazy.nil(), local → List.of() |
| ForStatement | body 递归 mirror | stack → List.of() |
| ConditionalForStatement | initializer/condition/updater/body 递归 mirror | cond → Lazy.nil(), stack 继承自 ForStatement |
| IterableForStatement | arguments(Identifier列表共享)/iterable/body 递归 mirror | replace → Lazy.nil(), stack 继承 |
| SwitchStatement | init/value/branches/defaultBranch 递归 mirror | stack → List.of() |
| SwitchBranch | constants/body 递归 mirror | stack 继承自 Branch |
| Branch | body 递归 mirror | stack → List.of() |
| BreakStatement | label(Identifier共享) | target → Lazy.nil() |
| ContinueStatement | label(Identifier共享) | target → Lazy.nil() |
| CatchClause | argument(Variable mirrorForExpansion)/typeSet(共享)/body 递归 mirror | stack → List.of() |

#### Expression 子类

| 类型 | 子节点处理 | 可变字段 |
|---|---|---|
| BinaryExpression | left/right 递归 mirror Expression | 无额外（基类字段由 new 初始化） |
| UnaryExpression | operand 递归 mirror | 无额外 |
| CallExpression | callee 递归 mirror, arguments 列表逐个 mirror | asExpr → false |
| SymbolExpression | symbol(共享), generic(共享/不可变) | 无额外 |
| VariableExpression | variable mirrorForExpansion, symbol(共享) | 无额外 |
| BlockExpression | block 列表逐个 mirror, result 递归 mirror | origin → Lazy.nil(), stack → List.of() |
| ConditionalExpression | condition/yes/not 递归 mirror | 无额外 |
| MemberOfExpression | subject 递归 mirror, member(Identifier共享), generic(共享), field(共享) | 无额外 |
| IndexOfExpression | subject/index 递归 mirror | 无额外 |
| DereferExpression | subject 递归 mirror | 无额外 |
| ConvertExpression | primitive(枚举共享), operand 递归 mirror | 无额外 |
| ParenExpression | child 递归 mirror | 无额外 |
| ArrayExpression | elements 列表逐个 mirror, type(共享) | 无额外 |
| ObjectExpression | entries IdentifierMap mirror(value mirrorExpression, key共享), type(共享) | 无额外 |
| TupleExpression | elements 列表逐个 mirror, types 列表(共享TypeDeclarer) | 无额外 |
| NewExpression | type(共享NewType), arg 递归 mirror | 无额外 |
| LiteralExpression | literal(不可变共享) | 无额外 |
| EnumExpression系列 | def(全局定义共享) | 无额外 |
| EnumIdExpression | def(共享), index 递归 mirror | 无额外 |
| IsExpression | subject 递归 mirror, type(DerivedTypeDeclarer共享) | needCheck → false |
| CheckNilExpression | subject 递归 mirror, nil(boolean) | 无额外 |
| ReferEqualExpression | left/right 递归 mirror, same(boolean) | 无额外 |
| SizeofExpression | type(TypeDeclarer共享) | size → -1 |
| MethodExpression | subject 递归 mirror, method(全局Method共享), generic(共享) | 无额外 |
| LambdaExpression | **不支持展开**（含 Procedure，labels 检查会报错） | — |
| CurrentExpression | 不出现在 variadic 函数体中 | — |
| PairsExpression | 查看具体实现确定 | — |
| TupleIndexExpression | subject 递归 mirror, index(int) | 无额外 |

#### Operand 子类

| 类型 | 子节点处理 | 可变字段 |
|---|---|---|
| Operand(基类) | — | type → Lazy.nil(), relay → new ArrayList |
| VariableOperand | symbol(Symbol共享) | variable → Lazy.nil() |
| DereferOperand | subject 递归 mirror | 继承基类 |
| FieldOperand | subject 递归 mirror, field(Identifier共享) | 继承基类 |
| IndexOperand | subject/index 递归 mirror | 继承基类 |
| TupleOperand | subject 递归 mirror, index(int) | 继承基类 |

#### Assignment

| 类型 | 子节点处理 | 可变字段 |
|---|---|---|
| Assignment | operand/value 递归 mirror | replacer → Lazy.nil() |

#### Tuple(stmt包)

| 类型 | 子节点处理 | 可变字段 |
|---|---|---|
| Tuple | values 列表逐个 mirror Expression | 无额外 |

## 5. 修改 expandInlined

将 `inline.addAll(body.list())` 改为对每个 statement 调用 `mirror()`：

```java
// 旧代码：
inline.addAll(body.list());

// 新代码：
for (var s : body.list()) {
    inline.add(s.mirror());
}
```

## 6. 修改 expandVariadic

在展开前检查 labels：

```java
if (!fd.procedure().get().labels().isEmpty()) {
    return semantic("variadic function with labels cannot be inlined: %s", fd.symbol());
}
```

## 7. 实现策略

### mirror() 方法签名

在需要 mirror 的基类/接口中添加：

```java
// Statement
public abstract Statement mirror();

// Expression (abstract基类已有，但子类需覆盖)
// 不在Expression加abstract mirror()，因为部分子类如LiteralExpression不需要
// 改为在需要mirror的子类中各自实现

// Variable — 新增 mirrorForExpansion()
public Variable mirrorForExpansion() {
    return new Variable(pos(), modifier, declare, name,
            Lazy.nil(), Lazy.nil());
}

// Operand
public abstract Operand mirror();

// Assignment
public Assignment mirror() { ... }

// Tuple(stmt包)
public Tuple mirror() { ... }

// Branch/SwitchBranch
public Branch mirror() { ... }
public SwitchBranch mirror() { ... }
```

### 不添加 mirror 的类型

以下类型不添加 mirror 方法（语义上不应被深拷贝）：
- Identifier, Symbol, Position
- TypeDeclarer 及全部子类
- 全局定义类型
- 函数签名类型（Prototype, ParameterSet, FixedParameter 等）
- 不可变值类型（Literal, Primitive, 枚举等）

## 8. 测试验证

使用以下测试文件验证修复：

```feng
import std$os;

func main(args [&!#][*!#]byte) {
    {
        var i = 100;
        os$printf("{}", i);
    }
    {
        var i = 100;
        os$printf("{}", i);
    }
}
```

编译命令：
```
-p gen -t f -b m -i D:\cossbow\f\generic.feng -o D:\cossbow\CLionProjects\cpp-test\file -Lstd=std
```

预期结果：生成的 C++ 代码中第一个块引用 `$i_1`，第二个块引用 `$i_2`，编译通过无错误。
