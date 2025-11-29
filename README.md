# 【Fèng】语言描述

Fèng 是一种改进版的类 C++ 编程语言，专注于内存安全和面向对象特性。它引入了自动内存管理、指针安全使用、强制类型检查等机制，同时支持面向对象编程、结构类型、接口抽象、泛型、运算符重载等特性。

## 特性

- **内存安全机制**：自动内存管理、指针安全使用、强制类型检查。
- **面向对象**：支持继承、多态和接口抽象。
- **结构类型**：`struct` 和 `union` 类型，字段类型不能是指针。
- **资源管理**：类似 C++ 的 RAII 模式，支持对象释放前的清理操作。
- **模块化**：代码管理单元 `module`，支持导出和导入符号。
- **值类型变量**：生命周期自动管理，嵌入到其他类时生命周期与母体一致。
- **运算符重载**：简化了 C++ 的运算符重载机制。
- **虚引用**：被引用的对象可以是值类型变量和引用类型的本地变量。
- **泛型**：支持泛型参数加约束条件。

## 语法

### 语句与表达式

语句以 `;` 结尾，表达式由多个运算符根据优先级组合而成。

```feng
var s = a + b; // 赋值语句
start(); // 调用语句
```

### 函数和方法

函数和方法将一组语句封装成可重复使用的计算体。

```feng
func sum(a, b int) int {
    return a + b;
}

func main() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

### 自定义类型

支持类、接口、结构、枚举和属性等自定义类型。

### module

代码组织的基本单元，支持导出和导入符号。

```feng
import com.cossbow.log.{println};
func main() {
   println(string("Hello Feng!"));
}
```

## 基本类型

包括整数、浮点数、布尔值等基本类型，支持算术运算、位运算、关系运算等。

### 整数类型

支持 `int8`, `int16`, `int32`, `int64`, `uint8`, `uint16`, `uint32`, `uint64` 和未标明位宽的 `int`/`uint`。

### 浮点数类型

支持 `float32` 和 `float64`。

### 布尔类型

类型为 `bool`，取值为 `true` 或 `false`。

## 运算符

支持算术运算符、位运算符、关系运算符、逻辑运算符等。

### 算术运算符

| 运算符 | 描述 |
|------|------|
| `+`  | 加法 |
| `-`  | 减法 |
| `*`  | 乘法 |
| `/`  | 除法 |
| `%`  | 取模 |
| `^`  | 幂运算 |

### 位运算符

| 运算符 | 描述 |
|------|------|
| `&`  | 按位与 |
| `|`  | 按位或 |
| `~`  | 按位异或 |
| `!`  | 按位取反 |
| `<<` | 左位移 |
| `>>` | 右位移 |

### 关系运算符

| 运算符 | 描述 |
|------|------|
| `==` | 等于 |
| `!=` | 不等于 |
| `<`  | 小于 |
| `<=` | 小于或等于 |
| `>`  | 大于 |
| `>=` | 大于或等于 |

### 逻辑运算符

| 运算符 | 描述 |
|------|------|
| `&&` | 逻辑与 |
| `||` | 逻辑或 |
| `!`  | 逻辑非 |

## 其他特性

### 索引运算符

支持数组的索引操作。

```feng
var a int = arr[2];
arr[9] = a + 10;
```

### new 运算符

用于动态创建类、数组、memory 的实例。

```feng
var node *Node = new(Node);
```

### 断言运算符

用于类型转换检查。

```feng
var f, ok = w?(*File);
```

### 赋值运算符

支持 `+=`, `-=`, `*=`, `/=`, `%=`, `^=`, `&=`, `|=`, `~=`, `<<=`, `>>=` 等。

```feng
var i = 0;
i += 2;
```

### 自定义运算符

支持自定义类的运算符重载。

```feng
class Complex {
   var real,imag float64;
   #operator add(lhs, rhs, result) {
      result.real = lhs.real + rhs.real;
      result.imag = lhs.imag + rhs.imag;
   }
}
```

## 类

支持继承、多态、字段和方法定义。

```feng
class Animal {
    var name rom;
    func eat(food rom) {
        printf("Animal %s eating %s\n", name, food);
    }
}

class Cat : Animal {
    func eat(food rom) {
        printf("Cat %s eating %s.\n", name, food);
    }
}
```

## 枚举

枚举类型的取值是有限的，定义时需要将所有值列举出来。

```feng
enum TaskStatus {WAIT, RUN, DONE, ;}
```

## 接口与抽象

接口是一组方法原型的集合，支持接口组合。

```feng
interface Cache`K, V` {
   Get(K) (V, bool);
   Set(K,V) bool;
   Size() int;
}
```

## 结构类型

结构类型与 C 语言类似，字段只能为基本类型和结构类型。

```feng
struct A {
    a1 int;
    a2 : 8 int;
    a3 Base;
}
```

## 数组

支持固定长度和不定长度的数组。

```feng
var a [16]int;
var d []int = new([32]int, [1,2,3,4]);
```

## memory 类型

用于对一段连续内存空间的管理和使用，支持只读 (`rom`) 和可写 (`ram`)。

```feng
var buf1 ram = new([256]uint8, nil);
```

## 引用

支持强引用和虚引用，虚引用不参与内存管理。

```feng
var a *Bus = nil;
var b *Bus = new(Bus);
```

## 资源类

添加 `release` 方法的类，用于自动释放资源。

```feng
class CBuffer {
   const buf uint64;
   func release() {
      cFree(buf);
   }
}
```

## 函数

函数定义与类方法类似，区别在于函数没有类上下文。

```feng
func run() {}
func start() { run(); }
```

## 语句

支持块语句、分支语句 (`if`, `switch`)、循环语句 (`for`)、赋值语句、异常处理等。

```feng
if (a < b) {
   printf("a < b");
} else {
   printf("a >= b");
}
```

## 元组

支持多值返回和多值赋值。

```feng
func divAndMod(a, b int) (int, int) {
    return a / b, a % b;
}
```

## 变量

支持值类型变量和引用类型变量，引用类型分为强引用和虚引用。

```feng
var a int = 1;
const pi float64 = 3.1415926;
```

## 字面量

支持整数、浮点数、布尔值、字符串和空值字面量。

```feng
var i int = 100;
var f float64 = 3.14;
var s rom = "Hello";
```

## 泛型

支持泛型类和泛型方法，泛型参数可以加约束条件。

```feng
class Complex`R` {
   var real,imag R;
   #operator add(lhs, rhs, result) {
      result.real = lhs.real + rhs.real;
      result.imag = lhs.imag + rhs.imag;
   }
}
```

## 宏

宏是一种有特定格式的代码片段，用于实现特定语言特性。

```feng
class Vector {
    var x float;
    var y float;
    #operator add(left, right, sum) {
        sum.x = left.x + right.x;
        sum.y = left.y + right.y;
    }
}
```

## 错误处理

支持异常抛出和捕获。

```feng
func step1() {
   throw new(Object);
}

func main() {
   try {
      step1();
   } catch(e *NilPointerError) {
      // TODO
   } finally {
      // TODO
   }
}
```

## 属性

支持自定义属性，用于描述类、方法、字段等的元信息。

```feng
attribute Author {
    name string;
    email string;
}

@Author(name="John", email="john@example.com")
class MyClass {
    // ...
}
```

## 详细文档

更多详细语法和使用示例请参考 [Fèng 语言文档](https://gitee.com/cossbow/feng)。