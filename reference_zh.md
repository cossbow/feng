**Fēng编程语言**

# 语法

## 语句与表达式

语句是程序序列的基本指令单元，除了块语句和控制语句外需要以`;`结尾：

比如赋值语句，等号分割成左右两个部分，左边是需要被赋值的操作数，右边是计算值的表达式：
`var s = a + b;`。

再比如调用语句也是常用的，仅包含一个call表达式：
`start();`。

语句右边的表达式是计算的主体，由多个运算根据优先级组合成：`a + (b + c) * d - sin(e)`。

## 函数和方法

[函数](#函数)和[方法](#方法)，是一个可重用、独立执行的代码块，它接收输入（参数），执行一系列操作，并可以选择性地返回一组输出值，
用于将复杂任务分解成更小、更易管理和维护的模块，提高代码复用性。

下面举例子来说明函数格式，这些格式也适用于方法。

定义`sum`函数，两个`int`参数，返回`int`类型值

```feng
func sum(a, b int) int {
    return a + b;
}
```

假设`printf`是module`std$os`提供的打印到终端的函数：

```feng
import std$os;
func test() {
    os$printf("{} + {} = {} \n", 1, 3, sum(1, 3));
}
```

可以没有返回值的函数：

```feng
import std$os;
func test(a, b int) {
    os$printf("{}\n", a + b);
}
```

有返回值的函数：

```feng
import std$os;
func div(a, b int) int {
    return a / b;
}
func test() {
    var a, b = 1, 3;
    var s = div(a, b);
    os$printf("{} / {} = {}\n", a, b, s);
}
```

## 派生类型

开发者可以自定义以下类型：
[类](#类)与[接口](#接口)、[结构](#结构类型)、[枚举](#枚举)。

例如自定义派生类`Complex`以及使用它定义变量`c1`和`c2`：

```feng
class Complex {
    var real, imag float64;
}
func sample() {
    var c1 Complex = {real=1.5,imag=2.4};
    var c2 *Complex = new(Complex, {real=3.3,imag=0.6});
}
```

## main函数

main函数是可执行程序的入口函数，这个和其他语言一致。
但入口函数没有返回值和参数；

```feng
import std$os;

func main() {
	os$printf("Welcome to programming with Feng language");
}
```

有main函数的模块会编译成一个可执行文件，并且不能作为库被其他模块导入；没有的则被作为库使用，即允许被导入使用。

# 概念

下面将详细描述各个语法元素的定义及用法。

## 模块

作为代码组织单元，同一个目录下的文件都属于一个模块，且模块名与目录名相同，因此无需在文件里声明。主要有下面的要求：

1. 模块内部的全局符号不能重用，相当于单文件内部一样。
2. 在模块内部所有的内容都可见，跨模块只能访问导出的符号。
3. 模块名称与路径一一对应，在文件中不声明模块名称。要求目录名称的规则和变量名称一样。
   例如在Linux下，模块`com$jjj$base$util`对应的相对路径为`com/jjj/base/util`。

### 导出符号

支持导出任何全局符号；比较特殊的是成员：

1. 导出的类，其成员不跟随导出的，需要单独导出。
2. 导出的接口的方法默认跟随导出。
3. 导出的结构类型的字段跟随导出，
4. 导出的枚举类型的所有值都跟随导出。

例如，下面导出全局变量`gFoo`、函数`aFoo`、类`Foo`及其字段`bar`和方法`go`：

```feng
export var gFoo Foo;
export
func aFoo() Foo {
    return gFoo;
}
export
class Foo {
    export
    var bar int;
    export
    func go() {
        //
    }
}
```

### 导入符号

声明导入`std$math`模块：

```feng
import std$math;
func test() {
   var s = math$sin(0.1);
}
```

可以设置module别名：

```feng
import std$math m;
func test() {
    var s = m$sin(0.1);
}
```

不支持循环导入，即依赖关系只能是有向无环图。比如：`a`模块导入了`b`模块，`b`模块也导入了`a`模块，这样是错误的，无法编译。

## 原始类型

原始类型是语言内置的类型，从内存角度看都能直接放在寄存器中。原始类型包含整数、浮点数和布尔三种类型。

虽然有字符串字面量，但没有内置字符串类型：字符串不能被寄存器直接存放，且字符串只在字符处理时才有意义。

### 整数类型

内置的全部整数类型如下：

- 有符号：`int8`/`int16`/`int32`/`int64`/`int`
- 无符号：`uint8`/`uint16`/`uint32`/`uint64`/`uint`

后缀的数字表示其位宽，无位数字后缀的是根据编译的目标平台决定。

有符号数最高位为符号位：默认`0`为正数，`1`为负数。因此有符号数数值的位宽少了一位。

支持[算术运算](#算术运算)、[位运算](#位运算)和[关系运算](#关系运算)。

不同整数类型之间必须显式转换：

```feng
func test() {
   var a uint16 = 123;
   var b int32 = int32(a); // 将uint16转换为int32
}
```

整数类型显式转换，相当于从低位到高位按位复制，因此可能会出现整数溢出：

1. 如果位宽从大的转到小的会被截断，造成整数溢出。
2. 有符号与无符号之间转换时，符号位会复制到对应数位上，导致整数值发生变化。

语言本身不检查溢出，需要程序员自行处理。

### 浮点数类型

浮点数是由[IEEE 754标准](https://standards.ieee.org/ieee/754/6210/)定义的，
包括单精度数`float32`和双精度数`float64`两种。

浮点数支持[算术运算](#算术运算)和[关系运算](#关系运算)。

### 布尔类型

类型符号为`bool`，且只有`true`/`false`两种取值：

* 支持逻辑运算，关系运算（但只支持相等和不等），及位运算（与、或、异或三种）。
* 关系运算的结果一定是布尔值。
* 不支持和整数、浮点数的互相转换。
* `if`、`for`中的条件表达式返回值必须是`bool`类型的。

布尔类型占1个字节，只使用最低位表示，且其他位的值不能影响布尔运算结果。
最低位的值与布尔值对应：

| 布尔值   | 整数值 |
|-------|-----|
| false | 0   |
| true  | 1   |

布尔类型支持[逻辑运算](#逻辑运算)，而[关系运算](#关系运算)的结果为布尔类型。

## 运算符与表达式

### 基本性质

#### 优先级

下表列出了主要运算符的优先级（自上而下优先级递减）：

| 顺序 | 运算符集              | 备注    |
|----|-------------------|-------|
| 1  | new(),圆括号,字面量     |       |
| 2  | is,索引,引用字段,函数调用,块 |       |
| 3  | +,-,!             | 一元运算符 |
| 4  | ^                 | 幂运算   |
| 5  | *,/,%             |       |
| 6  | +,-               |       |
| 7  | <<,>>             |       |
| 8  | &                 |       |
| 9  | ~                 |       |
| 10 | \|                |       |
| 11 | <,<=,==,!=,>,>=   |       |
| 12 | &&                |       |
| 13 | \|\|              |       |

显然第1、2行是特殊的操作符，且都是左结合的。
而第3行的一元运算符是右结合的，而二元运算符除了幂运算均是左结合的。
这里特殊的是幂运算，是右结合的，其优先级高于左边的一元运算，而低于右边的：

```feng
func test(a,b int) {
   var x int;
   x = -a^b;   // 等效于：-(a^b)
   x = a^-b;   // 等效于：a^(-b)
}
```

### 运算表达式

#### 算术运算

| 运算符 | 描述  |
|-----|-----|
| ^   | 幂运算 |
| *   | 乘法  |
| /   | 除法  |
| %   | 取模  |
| +   | 加法  |
| -   | 减法  |

#### 位运算

| 运算符  | 描述   |
|------|------|
| !    | 按位取反 |
| <<   | 左位移  |
| \>\> | 右位移  |
| &    | 按位与  |
| ~    | 按位异或 |
| \|   | 按位或  |

#### 关系运算

| 运算符 | 描述（左边*右边） |
|-----|-----------|
| <   | 小于        |
| <=  | 小于或等于     |
| ==  | 等于        |
| !=  | 不等于       |
| \>  | 大于        |
| \>= | 大于或等于     |

#### 逻辑运算

| 运算符  | 描述               |
|------|------------------|
| !    | 逻辑非（与按位取反是相同的符号） |
| &&   | 逻辑与              |
| \|\| | 逻辑或              |

因为[布尔类型](#布尔类型)只有最低位有效，其他位忽略，因此位运算的`&`、`~`、`|`对布尔值运算的结构依然是有效的布尔值，
且运算结果`&`与`&&`一致、`|`与`||`一致，差别是`&&`和`||`具有“短路”效应：
即当左边的计算结果可以决定最终结果时，右边的表达式就不会被执行了。

下面举例说明`&&`的“短路”效应：

```feng
func contains(v int) bool {
    // TODO: check v is in the collection
}
func isEmpty() bool { return true; }
func test(v int) bool {
    // isEmpty返回true，显然右边就不需要再计算了，即contains不会被调用
    return !isEmpty() && contains(v);
}
```

### 其他表达式

#### 索引表达式

仅数组默认支持这种运算符。索引运算符是由中括号组成，括号中是获取索引值的表达式。
其用法有两种：

1. 索引表达式在右边是读操作，即获取索引对应元素的值作为运算结果。
   左边一个变量或者在表达式中使用时，仅返回元素值，如果元素不存在则终止运行并抛出[异常](#异常)。
   ```feng
   func test(arr [16]int) int {
       return arr[16]; // 索引越界，终止运行并抛出异常
   }
   ```
2. 放左边为写操作，即修改索引对应元素的值。
   ```feng
   func test() {
       var arr [16]int;
       arr[15] = 0; // 修改索引为15的元素值为0
   }
   ```
   数组的容量是创建之后就不能变了，所以索引越界自然也要终止运行并抛出[异常](#异常)。

#### new表达式

用于动态创建实例，使用格式：new(类型, 初始化参数)，例如：

```feng
// 创建类Device的实例
var a *Device = new(Device);
// 后面的参数为初始化
var b *Device = new(Device, {});
```

[引用类型](#引用类型变量)的变量和实例是分离的，其中[强引用](#强引用类型)只能引用通过`new`创建的实例。

参数是可选的，无参数则初始化为默认值。

对类和结构体可以传递[字段表达式](#字段表达式)，也可以是值类型的表达式，但值的类型必须与待创建的实例相同。

```feng
struct A { id int; }
func test(a A) {
   var x = new(A, {id=12});
   var y = new(A, a);
}
```

对于数组可以传递[数组表达式](#数组表达式)，也可以定长数组。但当值的长度超过新建数组的长度时会截取实际长度。例如：

```feng
func test(a [2]int) {
   var x = new([2]int, [1]);
   var y = new([4]int, a);
   var z = new([1]int, a);
}
```

#### 数组表达式

特殊的字面量表达式，专门给数组初始化用的，即直接列举出数组的所有元素，元素可以是任意表达式。

例如初始化一个`int`的数组类型变量，这里直接列举了全部元素：

```feng
var a [4]int = [1,2,3,4];
```

##### 数组表达式长度

在初始化数组时，实际列举的元素可以少于数组大小，后续元素将置默认值：

```feng
var a [4]int = [1,2,3,4];
var a [4]int = [1,2,3];
```

当然可以为空：

```feng
var a [4]int = [];
```

表达式前可以指定数组类型，当然数组元素必须与变量相同，且长度不能大于变量长度：

```feng
var a [4]int = [4]int[1,2,3,4];
var a [4]int = [3]int[1,2,3];
// var a [4]int = [5]int[1,2,3]; // 错误×：指定的长度大于变量长度
```

指定数组类型的长度不能少于实际元素数量：

```feng
var a [4]int = [3]int[1,2,3];
// var a [4]int = [3]int[1,2,3,4]; // 错误×：元素个数超出了指定长度3
var a [4]int = [4]int[1,2,3,4]; // 应该这样
```

指定类型的长度可以省略，那么长度等于实际元素个数：

```feng
var a [4]int = []int[1,2,3];
```

如果省略变量类型，就可以根据元素个数自动推断变量的类型和长度了：

```feng
var a = []int[1,2,3]; // a的类型为：[3]int
```

##### 数组表达式类型

元素类型必须与数组类型匹配，要求符合允许赋值的规则，具体参考变量类型及各自定义类型，下面举两个简单的错误例子：

```feng
// var a = []int[1,false]; // 第2个元素是bool值
// var a = []int[1,3.1];   // 第2个元素是float值
```

如果不指定类型，则以第一个元素自动推导出元素类型：

```feng
var a = [1,2,3];     // a的类型自动推导为：[3]int
// var a = [1,3.1];  // 根据第1个元素推导类型是int，而第2个元素是float值
```

举个比较复杂的例子：

```feng
class Animal {}
class Cat : Animal {}
var a = [new(Animal), new(Cat)];    // 推导数组类型为：[2]*Animal
// var a = [new(Cat), new(Animal)]; // 错误×：推导类型为[2]*Cat，不能存放*Animal元素
```

##### 数组表达式嵌套

可以嵌套用于多维数组初始化：

```feng
var a = [2][3]int[[1,2],[3,4]];
// var a = [2][3]int[[],[],[]];  // 错误×：第一维度超了
// var a = [2][3]int[[1,2,3,4]]; // 错误×：第二维度超了
```

与字段表达式嵌套：

```feng
class Car {
   var id int;
}
var ca [2]Car = [{id=1},{id=2}];
```

#### 字段表达式

和数组表达式一样，是特殊的字面量表达式，专门用于初始化可定义字段的派生类型的值类型，比如结构类型和类。

例如：

```feng
class Car {
   var id int;
   var speed float;
}
var car Car = {id=10,speed=80.5};
```

初始化的字段无需按定义的顺序填写，并且不能重复指定初始化字段。

```feng
// var car Car = {id=10,id=100}; // 错误×：重复指定id字段
```

与数组表达式一样，不能自动推导类型，因此要么是已知类型，要么必须给表达式加上类型前缀；

```feng
var car = Car{id=10,speed=80.5};
```

初始化的其他细节请参考[类](#类)和[结构类型](#结构类型)。

#### 元组表达式

用于初始化[元组](#元组)类型的值。元组表达式就是将值排列在圆括号里，并且与元组的定义对应，至少需要2个值。

例如：

```feng
var empty (bool,int) = (false,0);
```

与数组表达式和字段表达式不同，元组的类型标记是写在值后面的：

```feng
var empty = (false:bool,0:int);
```

如果元组中的值是字面量，除了`true`/`false`因为能明确推导出类型一定是`bool`值而可以省略，
其他均不能省略。比如一个整数字面量，虽然默认是`int`但也能对应`uint`、`int32`等等。

```feng
func far() {
   var a = (false,1:int);     // √：false后的类型标注省略了
   // var b = (false,1);      // ×：1后的类型标注不能省略
   var i int = 0;
   var c = (false,i);         // √：i变量的类型是明确的，可以省略
}
```

#### is表达式

[协变](#协变)操作之后，丢失原来的类型信息，而这个表达式的作用就是可以在允许时查询能否转换。

表达式返回的是期望的类型（只能是支持的引用），如果类型不能匹配则返回`nil`，因此只能返回一个可空引用：

```feng
func test(o *Object) {
   var f = o?(*?File);     // 转换成File类的引用
   var w = o?(*?Writer);   // 转换成Writer接口引用
   if (w!=nil) w.write("Hello!");
}
```

#### sizeof表达式

用于编译期计算类型的占内存大小（单位字节），适用类型必须是编译器已知的，支持的类型有：

1. 整数与浮点数类型。
2. 结构类型。
3. 以上两种的定长数组（也支持多维数组）

比如数组引用`[*]int`就不能使用，因为其大小只能在运行时获取。
类允许字段重排序，因此不能使用。
枚举类型要保持简单，不能使用。
`bool`类型是特殊的原始类型，编译器可以采用高效的方式存储，因此不能使用。

```feng
struct Buf {
   v [64]int;
}
class Cat {
   var name [*#]byte;
}
enum State {Halt,}
func test() {
   var s1 = sizeof(int);            // ✔：返回值为int的字节大小8
   var s2 = sizeof([3]int);         // ✔：返回 8x3=24
   // var s3 = sizeof([*]int);      // ✖：不能用于变长数组
   var s4 = sizeof(Buf);            // ✔：返回结构类型的大小
   var s5 = sizeof([2]Buf);         // ✔：结构类型的定长数组大小也可以获取
   // var s6 = sizeof([*]Buf);      // ✖：也是变长数组，不能用
   // var s7 = sizeof(Cat);         // ✖：不能用于类
   // var s8 = sizeof(State);       // ✖：也不能用于枚举
   // var s9 = sizeof((int,bool));  // ✖：也不能用于元组
   // var s10 = sizeof(bool);       // ✖：也不能用于bool
}
```

#### 条件表达式

条件表达式的作用是根据条件（值为`bool`类型）选择两个可选值中的一个作为返回值。格式为：条件 `?` 值1 `:` 值2
如果条件的值为`true`则返回值1，`false`则返回值2

例如下面这个取绝对值的函数：

```feng
func abs(n int) int {
   return n<0 ? -n : n;
}
```

与`if`语句可以省略`else`分支的特点不同，两个分支都是必须的。

#### 赋值运算

赋值运算符相当于运算的一种简写，即左操作数自己与右操作数参与对应的运算后再赋值给左操作数。因此也要求赋值运算的左右操作数是同类型的。
也就是说，赋值运算对应的运算是操作数与结果都是类型相同的。那也可以约定，支持了自定义运算符的类型，也可以采用相应的赋值运算符。
比如：

```feng
func test() {
   var i = 0;
   i += 2;
}
```

如果实现了运算符`+`，作用是按左右顺序拼接字符串，那就可以使用`+=`运算符，其作用是：
右边字符串拼接到左边的字符串变量的右边，再将结果传递给左边变量。

这类运算符没有返回值，不能用于表达式中，只能用于[特定语句](#赋值运算语句)。

#### 块表达式

块表达式像块语句，只不过最后必须有个表达式作为返回值：

```feng
class Foo {var id int;}
func test() {
   var r *Foo = {
      var f = new(Foo);
      f.id = 0;
      f // 表达式值
   };
}
```

进入块内部后是新的作用域，退出时里面的变量会自动清理。

### 自定义运算

[类](#类)是不支持运算符的，但是可自定义一部分运算实现。

自定义的运算功能代码段与方法跟函数都不一样，而是由operator[宏](#宏)实现的。
每一种运算符都有固定名称和原型及操作数列表：

* 每种运算符有固定名称。
* 具体因不同运算符定义操作数（就是宏参数）。
* 名称和其他方法名称可以相同。

#### 自定义表达式运算符

仅有一部分支持自定义：

| 运算符 | 宏名称 | 右操作数类型 | 结果类型  |
|-----|-----|--------|-------|
| *   | mul | 同左操作数  | 同左操作数 |
| /   | div | 同左操作数  | 同左操作数 |
| %   | mod | 同左操作数  | 同左操作数 |
| +   | add | 同左操作数  | 同左操作数 |
| -   | sub | 同左操作数  | 同左操作数 |
| <   | lt  | 同左操作数  | 布尔类型  |
| <=  | le  | 同左操作数  | 布尔类型  |
| ==  | eq  | 同左操作数  | 布尔类型  |
| !=  | ne  | 同左操作数  | 布尔类型  |
| \>  | gt  | 同左操作数  | 布尔类型  |
| \>= | ge  | 同左操作数  | 布尔类型  |

举个复数的例子：

```feng
class Complex {
   var real,imag float64;
   // 实现+运算
   // 并计算完成后返回
   macro operator add(rhs) {
      {
        real = real + rhs.real,
        imag = imag + rhs.imag
      }
   }
}
func testAdd(a,b Complex) Complex {
    return a + b;
}
```

#### 自定义特殊运算

##### 自定义索引运算

默认只有数组支持的[索引表达式](#索引表达式)也可以自定义。
由于索引运算符分有读和写两种操作，因此分成`indexGet`和`indexSet`两个过程宏。

比如自定义一个`Vector`（自动扩容的数组），要实现索引操作，可以这样：

```feng
import std$os;
class Vector`E` {
    var values [*]E;
    // TODO: 假如实现了自动扩容 grow(index int)
    macro index get(index int) E {
        get[index]
    }
    macro index set(index int, value E) {
        grow(index);
        values[index] = value;
    }
}
func test() {
   var m Vector`int` = {values=new([1024]int)};
   m[100] = 159;     // 调用 macro index set
   var v = m[100];   // 调用 macro index get
   os$printf("m[100] = {}\n", v);
}
```

在写操作时索引不存在的处理取决于内部实现：
比如一般情况的`Map`是可以新增key的，而数组是不能自动扩容的。

## 类

[类](https://en.wikipedia.org/wiki/Class_(programming))是面向对象编程的核心概念，描述了所创建的实例共同的特性和方法。
类对应在现实世界中人类知识中的分类，在程序中则是对实例（对象）的分类，并且定义了这些实例的共性（字段）和行为（方法）。

一个常规的类定义如下（包含一个字段和一个方法）：

```feng
class Car {
   var engine *Engine;
   func start() {
      engine.start();
   }
}
```

定义了类之后需要实例化才能使用：声明一个类的值类型，或者使用`new`动态的创建一个类的实例。

```feng
func sample(engine *Engine) {
   var c1 Car = {engine=engine};
   // 或者
   var c2 *Car = new(Car);
   c2.engine = engine;
}
```

### 字段

[变量](#变量)的类型定义基本都适用于类的字段，但不能定义为[虚引用类型](#虚引用类型)。

```feng
class Cat {
   const id int;
   var name [*#]byte;
   var mothr,father *Cat;
   var children []*Cat;
   // var who Cat; // ✖
}
```

类的字段的定义不要求顺序，和实际内存布局中的位置不需要一一对应。
不同于[结构类型](#结构类型)的字段。

#### 实例初始化

在实例化时，字段则可指定初始化。比如上面的`Cat`类：

```feng
func test() {
   var c1 Cat = {id=1001};
   var c2 Cat = {id=1001, name="Tom"};
   var c3 Cat = {name="Tom"};
   var c4 Cat;
   var c5 Cat = {};
}
```

同样通过`new`动态实例化也是一样：

```feng
func test() {
   var c1 *Cat = new(Cat, {id=1001, name="Tom"});
   var c2 *Cat = new(Cat);
   var c3 *Cat = new(Cat, {name="Tom"});
}
```

如果没有初始化，或者初始化中没有指定的字段，一律置为默认状态：对应内存全`0`，引用类型则是`nil`值。

### 方法

方法与[函数](#函数)基本相同，区别是：

1. 必须通过类实例来调用。
2. 在方法内部能使用当前实例的类成员。
3. 在当前类的方法集（含继承）中方法名必须唯一，唯一的例外是子类覆写父类的同名同原型方法。

比如定义`Task`类用于管理任务（枚举`TaskState`是任务的状态）：

```feng
enum TaskState {WAIT, RUN, DONE,}
class Task {
   var state TaskState;
   func isRunning() bool {
      return state == RUN;
   }
   func start() {
      if (isRunning()) return;   // 调用另一个方法
      state = RUN;     // 修改状态
   }
}
```

通过值类型的变量来调用方法：

```feng
import std$os;
func sample1() {
   var task Task;
   task.start();
   os$printf("task state '{}'\n", task.state.name);   // 打印：task state: 'RUN'
}
```

也可以通过引用来调用：

```feng
func sample2() {
   var task *Task = new(Task);
   task.start();
}
```

### this关键字

`this`是一个类内部的特殊关键字，用于指代当前实例本身。

在成员方法内使用当前类的成员，本地变量可能和字段同名，那么需要通过`this`来使用字段：

```feng
import std$os;
class Cat {
   var name String;
   func setName(name String) {
      log();   // 上文没有log函数，那就指向log成员方法
      this.name = name; // 上文有name变量/参数，与成员字段name冲突，必须加this
   }
   func log() {
      os$printf("{}: miao~~\n", name); // 上文没有name变量，可以省略this
   }
}
```

在方法调用时`this`即会引用当前实例，保证实例不会被释放：

```feng
func sample() {
   new(Cat).log();  // log方法退出后创建的实例才能被释放
}
```

`this`能传递给[虚引用类型](#虚引用类型)的变量。例如：

```feng
class Foo {
   var name String;
   func aaa() {
      var x1 &Foo = this;     // ✔：允许传给虚引用
      // var x2 *Foo = this;  // ✖：不能传给强引用
      // var x3 Foo = this;   // ✖：类型不匹配：引用传给值类型
      var x4 Foo = *this;     // ✔：支持解引用
   }
}
```

`this`只有在[逃逸方法](#逃逸方法)内才能传递给强引用。

### super关键字

`super`这个关键字用于指代直接父类，主要作用是调用父类方法，尤其是想调用被覆写了的父类方法。

### 继承

继承也叫扩展，即扩展已有的类以便增加新的字段和方法。
子类继承了父类的字段和方法，自己新增字段和方法是可选的。

在继承时，子类的字段不能和父类重名：

```feng
class Device {
    var id int;
}
class Disk : Device {
    // var id int; // ✖：重名了
    var diskId int;
}
```

方法允许重名，但必须原型一致，也就是[多态](#多态)。

#### 多态

多态（polymorphic）是指同一个行为具有多个不同表现形式或形态。
所以严格来讲抽象（详见[接口](#接口)）也属于多态。

下面举例说明类的多态：先定义一个父类`Animal`，并且有一个字段`name`和一个方法`eat`：

```feng
import std$os;
class Animal {
    var name [*#]byte;
    func eat(food [*#]byte) {
        os$printf("Animal {} eating {}\n", name, food);
    }
}
```

然后定义一个子类`Cat`，继承了父类字段`name`，下面实现一个与父类的方法`eat`同名同原型的方法：

```feng
class Cat : Animal {
    func eat(food [*#]byte) {
        os$printf("Cat {} eating {}.\n", name, food);
    }
}
```

#### 抽象

多态的父类的方法也有自己的实现，但[接口](#接口)的方法没有具体实现，而是接口的“子类”给出实现，因此叫抽象。
抽象出接口更好的作为约定和规范：

1. 管理者只关注接口的实现，隐藏具体的类，并提供已实现接口的实例。
2. 使用者不必关心是什么类，只要实现了接口就可以使用。

比如定义一个接口`Task`，仅包含一个简单方法`run`：

```feng
interface Task {
   run();
}
```

定义两个实现类`MyTask`和`YourTask`：

```feng
class MyTask (Task) {
   func run() {
      println("Run my task!");
   }
}
class YourTask {
   func run() {
      println("Run your task!");
   }
}
```

### 协变

协变是一种运行时的动态特性，并且不是所以的类都支持协变，只有非final类才支持，而[final类](#final类)是不支持的。

#### 继承协变

父类引用指向子类实例、接口引用指向实现类实例，面向对象编程中把这种操作称为**协变**。

比如允许`Animal`的引用指向一个子类实例，通过父类引用调用`eat`方法时，实际会调用子类的`eat`方法：

```feng
import std$os;
class Animal {
    var name [*#]byte;
    func eat(food [*#]byte) {
        os$printf("Animal {} eating {}\n", name, food);
    }
}
class Cat : Animal {
    func eat(food [*#]byte) {
        os$printf("Cat {} eating {}.\n", name, food);
    }
}
func test() {
   var animal *Animal = new(Cat, {name="Tom"});
   animal.eat("fish-meat"); // 将打印的是：Cat Tom eating fish-meat.
}
```

前面`Task`接口的引用可以指向它的任何一个实现类：

```feng
interface Task {
   run();
}
class MyTask (Task) {
   func run() {
      println("Run my task!");
   }
}
class YourTask {
   func run() {
      println("Run your task!");
   }
}
func asyncRun(t *Task) {
    t.run(); // 假装这里在异步执行
}
func test() {
    asyncRun(new(MyTask));      // 打印：Run my task!
    asyncRun(new(YourTask));    // 打印：Run your task!
}
```

父类与子类仅支持引用传递，且在传递时引用类型的转换参考[引用类型](#引用类型)一节；值类型变量之间不能传递。

#### 动态转换

因为协变操作，导致原来的类型信息丢失了，但可以在运行时通过[is表达式](#is表达式)来转换。
例如：

```feng
func use(animal *Animal) {
    var cat = animal?(*Cat);
    if (cat!=nil) cat.eat("mouse");
}
func test() {
    use(new(Cat));
}
```

#### 方法协变

多态的方法返回值支持协变，即子类方法的返回类型可以是 父类方法的返回类型 的子类或实现类。
但其参数必须一致，而且方法上的逃逸标记、不可修改标记也必须一致。

这里只举返回值协变的例子：

```feng
class A {}
class B:A {}
class ABox {
   func get() *A {
      return new(A);
   }
}
class BBox:ABox {
   func get() *B {   // 覆写get()方法，但返回类型是 父类的get()方法返回类型 的子类
      return new(B);
   }
}
```

接口的实现类也支持协变：

```feng
class A {}
class B:A {}
class I {
   func get() *A;
}
class Box (I) {
   func get() *B {   // 实现get()方法，但返回类型是 接口的get()方法返回类型 的子类
      return new(B);
   }
}
```

#### Object类

由于类是单继承的，所有非final类都会按继承关系形成一棵树，而这棵树的根类就是`Object`类。
这是内置的类，没有声明继承任何父类的类则默认直接继承`Object`类。
`Object`类没有任何成员，可以创建一个`Object`的对象。

```feng
class Device {
   var name [*]byte;
}
func test() {
    var o *Object = new(Object); // 创建一个Object实例
    o = new(Device);             // 可以指向任何一个非final类实例
}
```

显然，由于[协变](#协变)仅适用于非final类，接口的引用只能指向它的一个实现类的实例，而这个实现类一定是非final类，
所以被接口引用的实例一定是`Object`的子类。例如：

```feng
interface I {}
func test(i *I) {
   var o *Object = i;
}
```

### final类

final类是在类名称后加上`final`关键字修饰的类，不加`final`的我们称非final类，也就是支持[协变](#协变)的类。
final类主要用于简单的数据封装。

例如一个简单final类：

```feng
class User final {
   var id int;
}
```

与非final类的对比解释final类的特征：

1. 首先，`Object`是非final类。
2. final类只能继承final类，非final类只能继承非final类。
3. final类可以实现任意接口。
4. final类不支持协变（[多态](#多态)和[抽象](#抽象)），也就是不能转换引用的类型。

下面我们分别举例说明：

final类继承：

```feng
class A final {}
class B : A final {}
```

final类不能继承非final类：

```feng
class A {}
// class B final : A {}  // ✖：final类不能继承非final类
```

非final类也不能继承final类：

```feng
class A final {}
// class B : A {}  // ✖：final类不能被非final类继承
```

可以实现接口：

```feng
interface I {}
class A final (I) {}
```

最后，不支持[协变](#协变)：

```feng
interface I {}
class A final {}
class B final : A (I) {}
func test1() {
   var b = new(B);
   // var a *A = b;     // ✖：不支持继承协变
   // var i *I = b;     // ✖：不支持抽象协变
}
func test2(a *A, i *I) {
   // var b1 = a?(*?B); // ✖：既然不支持协变，那也无法使用is表达式
   // var b2 = i?(*?B); // ✖：同上，无法使用is表达式
}
```

### 导出成员

类的成员可以单独设置导出，并且默认不导出。

在`util`里有代码：

```feng
export List`T`{
   var elements []T; // elements这个成员就是需要对外隐藏的
   export func get(i int) { // 但是get这个成员就需要暴露出去
      // TODO： 检查下标越界
      return elements[i];
   }
}
```

### 资源类

当一个类添加了`resource free`宏方法，这个类就被标记为资源类，这个宏的代码会在这个类的实例释放时调用。

这个特性可以用于自动释放其他资源。比如C语言lib中分配的缓冲区：

```feng
class CBuffer {
   const buf uint64; // 假设这个字段保存的是buf指针值
   macro resource free() {
      cFree(buf); // 假设可以这样调C语言的释放函数free
   }
}
```

资源类只能通过`new`创建实例。这个限制可以避免重复调用。
比如上面的`CBuffer`类，值类型在赋值中复制了`buf`的值，多个实例在释放时就会重复调用`cFree(buf);`。

析构按「子先父后」顺序执行：继承链中最派生的类的`free`先运行，再释放它自身声明的字段，最后调用父类析构（逐层向上）。
每个类内部都是「先`free`后释放字段」，这样子类的`free`能安全访问继承来的字段（此时父类字段尚未释放）。

有些外部资源的关闭往往是耗时操作，比如文件，如果放在这里处理可能对性能的影响难以预料，而且还需要处理IO错误或异常，
所以应该采用[异常语句](#异常语句)来处理。

### 不可修改方法

添加了不可修改标记的方法内，`this`就是不可修改引用，也就是说无法修改实例。
如果常量[值类型变量](#值类型变量)和[不可修改引用](#不可修改引用)调用了会修改实例的方法，
常量与不可修改的规则就被破坏，因此规定只能调用不可修改方法。

不可修改标记为`#`，放在参数括号前面，例如`get`方法：

```feng
class User {
   var id int;

   func get#() int {
      // this.id = 0;            // ✖：不能修改字段
      // id = 0;                 // ✖：不能修改字段
      // *this = {};             // ✖：不能解引用赋值
      // const r &User = this;   // ✖：不能传递给可修改引用

      // this.set(0);            // ✖：不能调用可修改方法
      // set(0);                 // ✖：不能调用可修改方法
      return id;
   }

   func set(id int) {
      this.id = id;
   }
}
```

### 逃逸方法

一般方法内`this`只能作为虚引用使用；在逃逸方法内`this`则是作为`const`的强引用使用。

给方法加上逃逸标记之后，只能通过[强引用](#强引用类型)调用：

```feng
class User {

   func foo*() {
      const r &User = this;      // ✔：默认可以作为虚引用
      const r *User = this;      // ✔：可以作为强引用
      gar();                     // ✔：可以调用非逃逸方法
   }

   func gar() {
      const r &User = this;      // ✔：默认可以作为虚引用
      // const r *User = this;   // ✖：不能作为强引用
      // foo();                  // ✖：不能调用逃逸方法
   }
}
```

## 接口

接口是从多态分离出来的特性，是去掉了具体实现的父类，而且没有字段。
这样接口看上去是由一组方法的集合，在定义时省去了方法前面的`func`关键字。

接口仅仅是约定和规范，不支持实例化，因此接口类型变量只能是引用。

### 接口组合

接口可以进行组合：

1. 组合成的接口包含各个组件的所有方法原型。
2. 组合接口可以传递给组件接口，因为实现了组合接口当然也实现了组件接口。
3. 接口的方法名称会检查冲突，不同组件中同名的方法被视为同一个方法，如果原型不一致则不能编译。

比如文件可以读和写，那可以这样设计接口：

```feng
interface Input {
   read(b [&]byte) int;
}
interface Output {
   write(b [&#]byte) int;
}
// 组合成的接口DataStorage包含read和write方法
interface DataStorage {
   Input;
   Output;
   query() [*#]byte;
}
// 实现File接口的实例自然也实现了Write接口
func use(ds *DataStorage) Output {
   return ds;
}
```

### 接口类型变量

接口类型变量是引用类型变量，并且只能引用实现类的实例。
接口的变量声明需要加上引用标识符来标识引用类型。

允许的传递：

1. 引用类型相同的情况，实现类可以传递给接口。
2. 类型允许的条件下，接口的常量强引用可以传递给接口的虚引用。
3. 类型允许的条件下，实现类的常量强引用可以传递给接口的虚引用。

比如接口`Cache`和实现类`LocalCache`之间传递：

```feng
func sample1(lc *LocalCache) {
    var c1 *Cache = lc;
}
func sample2(lc &LocalCache) {
    var c2 &Cache = lc;
}
func sample3(lc *Cache) {
    var c2 &Cache = lc;
}
func sample4(lc *LocalCache) {
    var c2 &Cache = lc;
}
```

## 枚举

枚举定义为该类型的取值是有限集合，即取值范围是被强制限定在集合中的，当然取值不能为空，默认为定义的第一个值。
因为值的数量是有限的，因此必须在定义时把全部值都列举出来：

```feng
enum TaskState {WAIT, RUN, DONE,}
```

[枚举变量](#枚举变量)的值必须是枚举值中的一个，不能为空（`nil`）。

枚举类型内置了特殊属性，这些属性在编译时就已经确定：

* `id`：自动按定义的顺序递增产生的整数值，就是说修改了顺序就会变化。
* `name`：就是定义的字面名称。比如上面定义的`WAIT`，其名称就是`"WAIT"`。
* `value`：允许自定义的属性，整数类型。在未定义情况下，第一个枚举值的`value`等于`0`，后面的等于上一个的`vaue`递增1。

使用枚举的值通常需要枚举类型为前缀，当然如果变量的类型明确就可以省略前缀，例如：

```feng
enum TaskState {WAIT, RUN, DONE,}   // 未设置value，就等于id
enum BillState {WAIT, PAID=4, SEND, DONE,} // 这里WAIT=0，SEND=5，DONE=6，……
func test() {
   var s1 = TaskState.WAIT;             // s1初始化为枚举值：WAIT
   s1 = RUN;                            // s1类型已知，因此省略前缀
   var s2 TaskState = DONE;             // s2已知，也可以省略前缀
   var i int = TaskState.RUN.id;        // i初始化为整数：1
   i = s2.id;                           // i赋值为：3
   var n [*#]byte = s2.name;            // n初始化为byte数组引用，内容为字符串"DONE"
   var v int = BillState.SEND.value;    // v初始化为整数：5
}
```

类型明确的情况还有`switch`语句中，如果case未覆盖所有值，那必须加`default`分支；反之则不能加：

```feng
func sample(s BillState) {
    switch(s) {
        case WAIT {
        }
        case PAID, SEND {
        }
        default {}
    }
}
```

支持[迭代循环](#迭代循环)所有枚举值：

```feng
import std$os;
func test() {
   for ( s : TaskState )
      os$printf("name: {}, id: {} \n", s.name, s.id);
}
```

支持直接通过索引取值：

```feng
func sample() {
    var s1 TaskState = TaskState[0];
    var s2, ok = TaskState[4];
}
```

枚举的变量或数组元素的默认值为第一个枚举值（`id`等于`0`）。

## 结构类型

### 结构类型定义

结构体和联合体统称为结构类型，定义和内存布局与C语言一致：

1. 结构体：所有字段的存储按顺序分配。
2. 联合体：所有字段的存储是重叠的。

字段类型只能是[整数](#整数类型)、[浮点数](#浮点数类型)和结构类型，及这两种的[定长数组](#定长数组)。

结构体和联合体的定义格式一样，只是开头的关键字不同：

1. 结构体的定义格式为：`struct` 名称 `{` 字段列表 `}`。
    ```feng
    struct Message {
        type int;
        success byte;
        value float32;
        ext [12]int;
    }
    ```
2. 联合体的定义格式为：`union` 名称 `{` 字段列表 `}`。
    ```feng
    union DataType {
        type int;
        success byte;
        uv float32;
    }
    ```

相邻且相同类型的字段可以合并，当然不相邻的不能合并。以结构体为例：

```feng
struct Request {
    type, code int;
    data [56]uint8;
}
```

可以指定字段实际使用的位宽，只能对[原始类型](#原始类型)的字段设置。
设置的位宽取值范围为1~类型位宽，比如对于`int32`类型，字段位宽取值范围为1~32之间。

设置的位宽是放在字段名称后面的括号内：字段名称 `(` 位宽 `)` 类型。
位宽必须是[编译期常量](#编译期常量)。
例如设置`code`字段的位域为`6`（`type`字段未设置）：

```feng
struct Request {
    type, code(6) int;
}
```

联合体在初始化时只能指定其中一个字段：

```feng
union Foo {
   tag int;
   fly uint8;
}
var foo Foo = {tag=1};
// var foo Foo = {tag=1,fly=2}; // 错误×
```

在结构类型内部可以定义嵌套的匿名的结构类型：

```feng
struct A {
   b struct {
      v union {
         v1 int64;
         v2 struct { u [2]uint16; };
      };
   };
}
```

### 结构类型实例

结构类型可以有两种实例化方式：

1. 定义为[值类型](#值类型变量)，支持变量、类或结构类型的字段。
2. 通过`new`动态分配实例。

## 数组

### 数组元素

数组是用于存储一组连续重复元素的类型，元素可以为任意类型。
每个元素相当于一个[变量](#变量)，也分[值类型](#值类型变量)和[引用类型](#引用类型变量)：

```feng
var a [4]int;       // 原始类型数组
var b [4]Host;      // 类数组
var c [16]*Bus;     // 类引用数组
var d [12][4]int;   // 定长数组的数组：即多维数组
var e [10][]int;    // 变长数组的数组，区别于多维数组，元素其实为引用
```

值类型数组的元素所需空间是和数组一起分配的，可以直接使用：

```feng
func test() {
    // 原始类型数组
    var a [4]int = [1,2,3,4];
    a[0] += a[1];
    // 类数组
    var b [4]Host = [{id=1}];
    b[3].id = 111;
    // 定长数组的数组：即多维数组
    var c [4][8]int = [[1],[2]];
    c[3][4] = 222;
}
```

引用数组的元素需要额外引用其他实例，默认值为`nil`（不引用任何实例）。

```feng
func test() {
    var a [4]Device;
    // a[2].name = "dev-2"; // 错误✖：这里会抛出空指针异常
    a[0] = new(Device);
    a[0].name = "dev-0";    // 只有a[0]可使用，其他元素依然是nil
}
```

上面以[定长数组](#定长数组)为例，[变长数组](#变长数组)只在初始化时有差别，用法一样。

### 数组类型

数组长度是指能容纳的元素总数，声明变量类型时指定和不指定分别表示两种类型的变量。

#### 定长数组

声明时如果指定了大小是定长数组，也就是说数组方括号中的必须是整数字面量，或者整数常量表达式。

```feng
var a [4]int;
```

这种类型的数组是[值类型变量](#值类型变量)。

初始化为[数组字面量](#数组字面量)，初始化值数量不能超过数组长度；
如果小于则从第一个位置开始顺序初始化，后面则归零：

```feng
// var a [4]int = [1,2,3,4,5];
var b [4]int = [1,2];   // b初始化为：[1,2,0,0]
```

作为值类型，赋值时要求必须是相同的数组类型，包括元素类型和长度：

```feng
func foo() [4]int {
    return [1,2,3,4]
}
func foobar() {
    var a [4]int = foo();     // 相同的数组类型
    // var b [2]int = foo();  // ✖：长度不同，不是同一个类型
    // var c [2]bool = foo(); // ✖：元素不同，不是同一个类型
}
```

不支持从无类型声明的[数组字面量](#数组字面量)上推导类型。

左操作数的类型是已知的：

```feng
func far() {
   var a [4]int = [1,2,3]; // 显示声明类型
   a = [11,22];   // a的类型是已知的
}
```

如果左操作数的类型未知，比如在声明变量时省略了类型，那么就会从右边推导。这时候就不能声明字面量前面的类型了：

```feng
func far() {
   var a = [4]int[1,2,3];  // 自动推导类型为：[4]int
   var b = []int[1,2,3];   // 自动推导类型为：[3]int
   // var c = [1,2,3];     // ✖：无法推导类型
}
```

#### 变长数组

不指定长度是[引用类型变量](#引用类型变量)，也就是数组引用，可指向任意长度的数组实例。

数组实例是通过`new`分配的，并且在分配时必须指定分配的长度。格式为：new(\[长度\]类型)

例如创建int类型数组：

```feng
func test(size uint) {
    var a []int = new([4]int);
    var b []int = new([size]int);
}
```

### 数组类型字段

[类](#类)的字段类型可以为数组或数组引用，这和变量用法一样：

```feng
class Foobar {
    var foo [4]int32;
    var bar [*]int64;
}
```

注意：[结构类型](#结构类型)的字段类型不能为[引用](#引用类型变量)，必须指定长度。

## 元组

元组是一种简单的聚合类型，简单地将不同类型聚合到一起，表示方法：`(` 类型1 `,` 类型2 `,` 类型3 `)`。
元组中的类型可以是除了[虚引用](#虚引用类型)的任意类型，元素至少有2个。

例如：

```feng
var ga (bool,int);
var gb (bool,*int);
var gc (int,[*]uint64,[16]byte);
class Car {
   var speed int;
}
var gd (int,Car);
```

元组是值类型，可以直接进行赋值，比如用[元组表达式](#元组表达式)赋值。例如：

```feng
var gr (bool,int) = (true,-1);
```

元组可以访问其中一个元素，访问方式是：元组 `.` 索引。
注意：元组是用的`.`来访问元素，但后面跟的不是字段名称，而是索引。

例如：

```feng
func far(a (bool,int,[*]byte)) {
   var ok bool = a.0;
   var code int = a.1;
   var name = a.2;      // 元组是确定类型，因此可以推导name的类型为[*]byte
}
```

上面的例子中[参数](#参数表)是不可变的，因此不能修改，下面是修改的例子：

```feng
func bar(ok bool, code int) {
   var a (bool,int);
   a.0 = ok;
   a.1 = code;
}
```

## mappable

mappable定义为其引用可以互相转换，与类的转换协变逆变不同，不需要做类型检查，而只检查边界。

支持映射的类型：[结构类型](#结构类型)、[整数](#整数类型)、[浮点数](#浮点数类型)和这两个类型的[定长数组](#定长数组)。
这些类型占据的是连续空间，并且不包含引用（即指针），因此允许他们的引用像C语言那样自由转换，唯一的约束是边界检查。

比如下例中将`int`的引用转换成`int16`的引用：

```feng
func f1(a *int) *int16 {
   return a;
}
```

因为size是编译器已知的，可以在编译时检查到。上面的引用不会越界，但下面会编译不通过：

```feng
func f1(a *int8) *int16 {
   return a;
}
```

结构体转换类似，显然f1能是允许的，f2则越界了：

```feng
struct Foo {
   v int32;
}
func f1(a *Foo) *int16 {
   return a;
}
func f2(a *Foo) *int64 {
   return a;
}
```

因为数组引用可以指向任意长度的数组，因此其长度是运行时计算的。如果其元素大小超出了目标则size为`0`：

```feng
struct Foo {
   v int32;
}
func f1(a *Foo) [*]int16 { // length为2
   return a;
}
func f2(a *Foo) [*]int64 { // length为0
   return a;
}
```

允许mappable的类型都是明确内存布局的，并与C语言一致，只不过不会包含引用（即指针）。
[结构类型](#结构类型)明确定义了字段不能为引用，同时数组的元素也不能包含引用，包括多维数组：

```feng
func test() {
   var a1 [*]int;       // 可映射
   var a2 [*][2]int;    // 可映射
   var a3 [*][3][4]int; // 可映射

   var a4 [*]*int;      // 不可映射
   var a5 [*][*]int;    // 不可映射
   var a6 [*][5]*int;   // 不可映射
   var a7 [*][6][*]int; // 不可映射
}
```

## 函数

定义格式为：`func` 函数名 `(` 参数表 `)` 返回表 `{` 函数体 `}`

其中函数名是必须的，参数表、返回表及函数体都可以为空。下面举3个例子：

```feng
func run() {}
func start() { run(); }
func exec(a [*#]String) *Error {
   return nil;
}
```

### 函数名

函数名是函数的唯一ID，在模块内的函数集中是唯一的；并且需要通过函数名调用函数。

```feng
func add(a,b int) int { return a + b; }
func test() {
   var s = add(1, 2);
}
```

### 参数表

参数是参数名和类型组成，且都是常量（省略了`const`），作用域在当前函数内。

```feng
func test(a int, b[12]byte) {
   // a = 1;      // ✖：a是不可变的
   // b[0] = 1;   // ✖：b的元素是不可变的
}
```

下面的例子定义了类型为`Queue`的`l`和类型为`int`的`a`两个参数：

```feng
func send(l Queue, a int) {
   l.push(a);
}
```

相邻且相同类型的参数可以合并定义，比如定义两个类型为`int`的参数`a`和`b`可以这样：

```feng
func add(a, b int) int {
    return a + b;
}
```

### 返回值类型

在参数和代码段之间声明返回值类型，例如函数`foo`返回一个`float`：

```feng
func foo() float {
   return 0.1;
}
```

### 函数体

函数体由一组[语句](#语句)序列组成的：

```feng
func run(s int) {
    var i = s+1;
    do(i);
    ...
}
```

函数体内部能访问的变量组成上下文，函数内的上下文包括[全局变量](#全局变量)、[参数表](#参数表)和[本地变量](#本地变量)。

```feng
const PI = 3.14;
func circlyArea(diameter float) float {
    var radius = diameter * 0.5;
    return radius * radius * PI;
}
```

### 函数原型

函数原型是变量类型的一种，函数的定义去掉函数体就是原型：`func` 函数名 `(` 参数表 `)` 返回表。
这种类型的[变量](#函数原型变量)要么为空，要么指向与原型兼容的函数。
例如：

```feng
import std$os;

func add(a, b int) int { return a + b; }
func sub(a, b int) int { return a - b; }
func mul(a, b int) int { return a * b; }
func div(a, b int) int { return a / b; }

func Calc(a, b int) int;
func test(c Calc) {
    os$printf("{}\n", c(rand(), rand()));
}
func test() {
    test(add);
    test(sub);
    test(mul);
    test(div);
}
```

函数原型支持匿名定义：

```feng
func test(c func(a, b int) int) {}
func supply(c int) func(a, b int) int {
    switch(c) {
        case 0 { return add; }
        case 1 { return sub; }
        case 2 { return mul; }
        case 3 { return div; }
        default { return nil; }
    }
}
func test() {
    var c1 func(a, b int) int = add;
    var c2 = sub;   // 也可以省略类型，自动推导
}
```

原型变量并非引用类型，但可以为空（`nil`），也可以加可空前缀标记（`?`）来表示允许空，默认非空的。
这点与[引用的规则](#可空引用)一样。举例说明：

```feng
func use1(a func()) {
   var c1 ?func() = a;     // 非空 → 可空
   var c2 func() = a;      // 非空 → 非空
   // var c3 func() = c1;  // 错误×：不能反向传递
   if (c1 != nil) {
      var c3 func() = c1;  // 显示判断空之后才能反传
   }
}
```

注意：可空的原型变量不能被调用，例如：

```feng
func use2(a ?func()) {
   // a();           // ✖：不能调用
   if (a!=nil) a();  // ✔：非空才能调用
}
```

函数原型支持协变，类似[方法协变](#方法协变)，右边值的返回类型可以是左边操作数的返回类型的子类或实现类。

### 可变参数函数

可变参数函数是一种特殊函数，尾部参数数量和类型均不固定，且在编译时会自动展开。
目前的作用仅用于格式化和封装格式化。

#### format函数

内置的字符串格式化函数，是一个可变参数函数：

1. 第一个参数是传入的`&Writer`，也就是实现了内置的`Writer`接口的对象。
2. 格式化字符串字面量：`"This is for {}!"`，其中`{}`是占位符，用于格式化后面的参数。
3. 要输出的参数实例，数量与占位符必须相同，类型可以是基本类型和类、接口。

用法：

```feng
import std$bytes;

func test() {
   var buf bytes$BufferWriter;
   format(buf, "This is first line.");
}
```

#### 自定义可变参数

目前可以自定义可变参数，但可变参数只能传递给另一个可变参数。也就是说这个特性目前仅用于封装format函数。

可变参数使用`...`表示，只能放在参数表尾部，因此只能定义一组可变参数。

例如，定义一个打印到标准输出的函数：

```feng
// 上文定义的全局对象stdout
func printf(fmt [&#]byte, ...) {   // 定义可变参数
    format(stdout, fmt, ...);       // 传递可变参数
}
```

## 语句

### 块语句

块语句是由`{`与`}`括起来的语句序列组成的，块内上下文会嵌套，内声明的[本地变量](#本地变量)不能在外部使用：

```feng
func test() {
   println("block 1");
   {
      println("block 2");
      {
         println("block 3");
         // 嵌套没有限制
      }
   }
}
```

### 分支语句

根据控制条件选择执行其中一个分支，有两种类型。

#### if语句

`if`紧跟带括号的条件表达式，然后是当匹配条件时执行的语句；之后的`else`开始的语句是未匹配时执行的，这个分支不是必须的。

表达式结果作为条件，必须是`bool`类型。

简单的条件语句：

```feng
func abs(m int) int {
   if (m < 0)
      return -m;
   else
      return m;
}
```

可以省略`else`语句：

```feng
import std$os;
func printIfError(err uint) {
   if (err == 0) return;
   os$printf("Error: %u\n", err);
}
```

可以在条件表达式前面加一个初始化语句：

```feng
import std$os;
func test(m Map`int,*Node`, k int) {
   if (var n,ok = m[k]; ok) { // 这里的n和ok变量只属于当前块
      os$printf("value of {} is: {}\n", k, n.value());
   }
   // os$printf("value of {} is: {}\n", k, n.value()); // 错误✖：外层不能使用
}
```

显然`else`可以嵌套`if`，就组成了多分支：

```feng
func compare(a, b int) int {
    if (a > b) {
        return 1;
    } else if (a < b) {
        return -1;
    } else {
        return 0;
    }
}
```

#### switch语句

`switch`语句有一个条件表达式作为需要匹配的值，支持多个匹配规则（`case`），每个规则支持多个常量，匹配到规则后将后面的块语句。

```feng
func numberName(k int) {
    switch(k) {
        case 0 {
            println("zero");
        }
        case 1 {
            println("one");
        }
        case 2,3,4 {
            println("more");
        }
        default {
            println("Error");
        }
    }
}
```

### 循环语句

#### 条件循环语句

`for`后面的括号内是控制体，控制体可以且必须有一个控制条件表达式，也可以包括初始化和更新子语句；
之后是需要执行的语句或语句序列，称循环体。
当控制条件满足时重复执行循环体：

1. 控制条件是一个`bool`类型的条件表达式，当结果为`true`时才会执行循环体。
2. 循环体是一个语句，如果需要多个语句操作则需使用[块语句](#块语句)包起来。

简单的循环语句为括号内只有条件表达式：

```feng
func test() {
    var i = 0;
    for ( i < 100 ) {
        println(i);
        i += 1;
    }
}
```

完整的控制体格式为：【初始化】;【表达式】;【更新】

1. 【初始化】在循环前执行一次，然后再进入循环过程。
2. 循环过程的每一轮：先判断【表达式】，`false`则结束循环，`true`则执行循环体，最后执行【更新】。
3. 循环体中可以有控制循环的操作：
    1. 遇到`continue`语句则直接进入下一轮循环，也就是2中描述的。
    2. 遇到`break`则直接跳出当前循环或指定循环。

例如循环100次，并每次打印变量`i`的值：

```feng
func test() {
    for (var i = 0; i < 100; i += 1) {
        println(i);
    }
}
```

#### 迭代循环

对于变量数组，可以用更简单的方式遍历所有元素：

```feng
func test() {
    var src []int = [0,1,2,3,4,5,6,7,8,9];
    for ( v : src )  // 只获取值
      handle(v);
    for ( i,v : src) //  同时获取索引和值
      println(i, v);
}
```

当然`continue`和`break`语句对迭代循环依然有效。

#### 自定义迭代循环 _[未完成]_

循环语句遍历形式默认只对数组使用，对自定义类可以实现自定义迭代器，然后就可以用迭代循环来遍历了。
实现迭代是通过名为`Iterator`的helper宏实现的，但考虑循环是很常用的语法，所以利用宏直接由编译器展开。
宏的字段不限制，包含4个方法`initializer`、`condition`、`updater`、`get`

| 方法          | 作用     | 参数  |
|-------------|--------|-----|
| initializer | 初始化迭代器 | 无   |
| condition   | 循环条件   | 无   |
| updater     | 更新迭代器  | 无   |
| get         | 获取值    | 不限制 |

其中`get`可以写多个，但参数个数不能相同。

示例：

```feng
class Node`T` {
    var next *Node`T`;
    var value T;
}
export
class List`T` {
    var head *Node`T`;
    macro helper Iterator {
        cursor *Node`T`,
        index int;
        initializer() {
            cursor = head;
            index = 0;
        }
        condition() {
            cursor != nil
        }
        updater() {
            cursor = cursor.next;
            index += 1;
        }
        get(v) {
            v = cursor.value;
        }
        get(i, v) {
            i = index;
            v = cursor.value;
        }
    }
}
func test(src List`*Team`) {
   for ( t : src) { // 匹配第一个get
      // TODO
   }
   for (i, t : src) { // 匹配第二个get
      // TODO
   }
}
```

### 赋值运算语句

[赋值运算](#赋值运算)只能用于语句中，即：

```feng
func test() {
   var i = 0;
   i += 2;
}
```

### 赋值语句

#### 修改赋值

赋值语句的左边是操作数（指将要被修改值的对象），后边是由表达式列表：

```feng
func test(x,y int, u *User, a []int) {
   x = 2;
   u.id = 1;
   a[0] = 8;
   x, y = 2, 4;
   u.id, x, y, a[0] = 1, 2, 4, 8;
}
```

### 变量声明语句

声明一个或一组[变量](#变量)使用关键词`var`或`const`开头，后面紧跟变量的名称，然后是变量类型。

1. `var`声明一个普通变量。后面可以有初始化值，没有则是默认值——即零值（引用对于的是`nil`）。
2. `const`用于定义不变的量，不能重新赋值，且必须在声明时初始化值。

```feng
func test() {
    var r int = 5;
    var g float64;
    var a float64 = 0;
    const pi float64 = 3.1415926;
    const pi = 3.1415926; // 当设置了初始化值时，类型可以省略
    g = 2 * r * pi;
    a = r * r * pi;
}
```

由于声明的类型可以省略，因此会出现两种情况：

1. 省略时，左边可以是不同类型的表达式，这样右边对应的变量类型会自动推导为不同的类型。
2. 如果显式加上，显然左边的类型统一了，右边的类型自然必须兼容。

```feng
func test() {
   var a,b int = 1,2;
   // var a,b int = 1, "ggyy"; // 错误，必须拆成两个语句
   var x,y = 1,false; // x是int类型，y则是bool类型
}
```

### 返回语句

返回语句的作用是无条件终止当前过程（函数或方法）的执行；在函数需要返回值时，返回语句将会携带一个值给调用者。

一个无返回值的函数，其返回语句不能带返回值，例如：

```feng
func foo(n int) {
   if (n == 0) {
      return;
      // return 0; // 错误：不能有返回值
   }
   // TODO something with n
}
```

如果过程定义[返回值类型](#返回值类型)：

1. 那么所有的返回语句都必须带返回值。
2. 返回值必须与函数的返回值类型兼容，与赋值的规则一样。
3. 要求每个可达的分支都必须有终止语句。

例如：

```feng
func test(n int) bool {
   return n > 0;
   // return;     // 错误：缺少返回值
   // return n;   // 错误：返回值类型不匹配
}
```

返回语句是一种[终止语句](#终止语句)。

### 异常语句

异常语句分为抛出和处理异常两种语句。

能逃逸的异常类型在[异常](#异常)中有规定，并且只能抛出一个逃逸的实例——`new`创建。

#### 抛出异常语句

抛出异常是为了处理返回值没有处理的错误。抛出异常后：

1. 会终止当前过程的执行，不执行返回语句，而是抛出一个包含错误信息的实例。
2. 如果调用的过程抛出了一个异常A，会从调用处终止当前过程的执行，继续抛出异常A。

```feng
func example1() {
   throw new(Exception);
}
func example2() {
    example1();
    println("example1()必然抛出异常，所以这一行不会执行！");
}
func example3() {
    example2();
    println("example2()也会抛出异常，所以这一行也不会执行！");
}
```

如果发生了抛出异常，那这个会一直按调用链往外抛，直到被`catch`匹配到为止。

#### 捕获异常语句

异常处理语句分三个部分：

1. `try`部分：必须的部分，将需要处理的代码块包裹起来。
2. `catch`部分：可以有多个，分配匹配不同的异常类型。匹配到就执行对应的代码块，否则继续往后匹配。
   如果都未匹配成功则继续往外抛出。因为必须是逃逸的实例，捕获的类型只能是[强引用](#强引用类型)。
   并且必须声明为非空、不可修改的强引用。
3. `final`部分：上面两部分无论什么情况，都必须执行这部分。
   如果第1部分有`return`语句，先执行`return`后的表达式，再执行`final`部分，最后再正式返回。
   如果第2部分没有或者未捕获到异常，则先执行`final`部分后继续抛出。

第2和3部分至少必须有一个。

完整的例子：

```feng
func calc() {
   try {
      step1();
      step2();
   } catch(e *#NilException) {
      println("捕获到了空指针");
   } catch(e *#IllegalStateException | *#IllegalArgumentException) {
      println("捕获到了状态错误或者参数错误");
   } final {
      println("最终经过这里再往下执行");
   }
   return getResult();
}
```

没有`final`部分，只有`catch`部分：

```feng
func calc() {
   try {
      step1();
   } catch(e *#IllegalStateException) {
      println("捕获到了状态错误或者参数错误");
   }
   return getResult();
}
```

没有`catch`部分，只有`final`部分：

```feng
func calc() {
   try {
      step1();
      step2();
      return getResult();
   } final {
      println("最终经过这里再往下执行");
   }
}
```

`final`可以用来释放外部资源，避免资源泄露。比如文件关闭：

```feng
func readTxt() String {
   var f, er = open("tmp.txt");
   if er != nil {
      return string("");
   }
   try {
      step1(f);
      step2(f);
      return getTxt(f);
   } final {
      f.close();
   }
}
```

注意：`catch`匹配括号里的参数`e`是常量参数。

### assert语句

这个语句仅在开启Debug时才会生效，否则编译期会忽略。

assert语句是用来断言一个表达式结果为`true`，如果是`false`则抛出`AssertException`（内置异常）。

用法举例：

```feng
func query(id int) {
   assert(id >= 0);
   // Do querying ...
}
func test1() {
   query(-1);  // 传负数不满足条件，assert将会抛出AssertException
}
```

注意：如果未开启Debug情况下会忽略assert语句，如果在语句中调用了会导致副作用的操作，可能会影响代码逻辑。

例如：

```feng
var counter int = 0;
func getAndInc() int {
   counter += 1;
   return counter;
}
func test() {
   assert(getAndInc() > 0);   // 调用了会增加计数器的getAndInc()
   var c = getAndInc();       // 在开启Debug时比关闭Debug时，c的值会不同
}
```

### 终止语句

终止语句不是一种语句，而是返回语句和异常语句的统称。

一个有返回值的函数，其语句列表必须以一个**终止语句**结尾。

比如一个合法的例子：

```feng
func abs(n int) int {
   if (n > 0) {
      return n;
   } else {
      return -n;
   }
}
```

这是一个反例：

```feng
func abs(n int) int {
   if (n > 0) {
      // 这里缺少返回语句
   } else {
      return -n;
   }
   // n > 0 的分支将会走到这里，显然缺少返回语句
}
```

所以下面的例子就是合法的：

```feng
func abs(n int) int {
   if (n > 0) {
      // 啥也没干
   } else {
      return -n;
   }
   return n;
}
```

但有一种特殊情况也属于有终止语句，就是编译器可推导的无限循环。永远不会退出等于终止，
因为退出的方式只有循环中的语句抛出异常或者强行结束程序。

例如下面的例子，循环条件为常量`true`，且循环体中没有会导致退出循环的语句：

```feng
func test() {
   while(true) {
      run();
   }
}
```

注意：如果run()抛出异常，整个test都终止了，而不是退出循环，所以可以认为是死循环。

终止语句要求编译器进行流程控制分析来推导更复杂的例子。

## 变量

声明方式参考[变量声明语句](#变量声明语句)。

变量的声明方式有两种：可变的`var`和不可变的`const`，区别是后者在首次赋值后就不能再修改了。

### 变量值的类型

变量的类型分三种情况：值类型、引用类型、枚举和函数原型。

#### 值类型变量

变量与实例一体，变量的值就是实例本身，赋值相当于复制实例的数据：

1. 原始类型的变量本身只是一个寄存器值，修改通常只需一个机器指令：
   ```feng
   var a int = 1; // 变量a赋值为字面量数值1，那a的值就是1
   var b int = a; // 变量b赋值为变量a，则将a的值复制给b
   b = 2; // a和b是两个不同变量，修改其中任何一个不会影响另一个
   ```
2. 派生类型通常会占用超过寄存器位宽的空间，所以实现上往往需要一组指令，将类型的字段数据全部复制：
   ```feng
   class Vector { var x,y,z float64; }
   var a Vector = { x=1.0, y=0, z=-1.0 };
   var b Vector = a; // 和原始类型一样，复制a的所有字段数据给b
   b.x += 2.0; // 同样修改b不影响a，a.x的值依然是'1.0'
   ```
3. [定长数组](#定长数组)赋值等效于遍历数组的所有元素进行赋值：
   ```feng
   var a [4]int = [1,2]; // 遍历每个元素初始化，没写出来的为默认值，int默认值为0
   var b [4]int;
   b = a; // 就是把a的数据复制给b
   // 等效于循环赋值
   for (var i = 0; i < a.size; i++) b[i] = a[i];
   b[0] += 10; // 修改了b[0]不会影响a，a[0]值依然是'1'
   ```
   派生类型的数组，如果元素是值类型的，也是一起复制的：
   ```feng
   var a [4]Vector = [{x=1.0}, {x=2.0}]; // 遍历每个元素初始化，没写出来的为默认值，Vector默认值的每个字段都是0
   var b [4]Vector;
   b = a; // 就是把a的数据复制给b
   // 也等效于循环赋值
   for (var i = 0; i < a.size; i++)
       b[i] = a[i]; // 这里的赋值参考第2点
   b[0].x += 5.0; // 同样修改b[0].x不会影响a，a[0].x的值还是'1.0'
   ```

#### 引用类型变量

引用类型变量是与实例分离，即给赋值变量只是改变引用的指向。

变量能引用的实例有类型安全的约束：

1. [类](#类)和[接口](#接口)的引用有[多态](#多态)与[抽象](#抽象)的约束。
2. [接口](#接口)引用类的实例有抽象兼容的约束。

比如下面的`Device`和`Bus`尽管结构一样，但却不能引用：

```feng
class Device {}
class Bus {}
func test() {
    var a *Device = new(Device);
    var b *Bus = new(Bus);
    // a = b;   // 错误✖：Device的引用变量不能引用Bus的实例
}
```

##### 可空引用

引用默认是非空的（即`!=nil`），可以标注为可空（加`?`号），两种只能单向传递：非空 → 可空。
如需反向传递，必须显示对变量进行判断非空（只支持本地变量，不支持字段）：

```feng
func f(a *int, b *?int) {
   var x *?int = a;   // 非空引用可以传递给可空引用
   // var y *int = b; // 错误✖：不能直接传递
   if (b != nil) {
      var y *int = b; // 必须显式判断空，在非空的分支内传递
   }
}
```

总之：如果流程分析能证明变量在某个scope内非空的，那就可以作为非空变量使用：

```feng
func f(a *?int) {
   var v = a!=nil ? *a : 0;   // 条件表达式的第一个分支是非空的
   if (a==nil) return;        // 检查到nil即返回（终止语句），后面的分支一定非空
   v = *a;     // 可以解引用
}
```

但在已证明的非空分支，变量又被赋值了，且不是被赋了一个非空值，那就将回到未可空状态：

```feng
func f(a *?int, b *?int) {
   if (a != nil) {
      a = b;               // 被赋了可空的值，后面就不在是非空了
      // var y *int = a;   // ✖：被取消了非空状态
   }
   if (a != nil) {
      a = new(int);        // 被赋了非空值（new创建的是非空值）
      var y *int = a;      // ✔：依然是非空的
   }
}
```

上面的例子中有赋非空值的操作，这个操作在未判断非空的情况下，也能证明是非空的：

```feng
func test(r *?int) {
   var a *?int = nil;   // 可空引用a
   a = new(int);        // 赋了一个非空值
   *a = 0;              // 那么它就是非空的，相当于：*int
   a = r;               // 赋了可空的值后，就不能证明是非空的了
   // *a = 0;           // ✖：a必须再次检查非空
}
```

可空引用不能进行的一些操作，就是说必须经过非空证明（本地变量`!=nil`）之后才能操作。具体如下：

1. 可空引用不能[解引用](#解引用操作)、访问字段（包括读写）、调用方法。
2. 可空数组引用不能操作索引，包括读写。
3. 可空[函数原型](#函数原型)不能调用。

例如：

```feng
class Car {
   var id int;
   func go() {}
}
func test1(a *?int, b *?Car) {
   // *a = 1;        // ✖：不能解引用写
   // var i = *a;    // ✖：不能解引用读
   // b.id = 1;      // ✖：不能修改字段
   // var j = b.id;  // ✖：不能读取字段
   // b.go();        // ✖：不能调用方法
}
func test2(a [*?]byte) {
   // a[0] = 1;      // ✖：不能修改元素
   // var i = a[0];  // ✖：不能获取元素值
}
```

##### 非空引用初始化

注意！这里是非空的重要规则：
引用初始化为默认值为`nil`，显然非空引用不能是默认值，那就必须指定一个非空值来初始化它。

1. 这个规则适用于类、元组和定长数组。
2. 由于初始化必须是显式指定，变长数组的长度未知，无法正确初始化，因此变长数组的元素不能有非空。

对于一个变量：

```feng
func test(i *?int) {
   // var a *int;          // ✖：必须初始化
   // var b *int = i;      // ✖：必须初始化为非空值
   var c *int = new(int);  // ✔
}
```

由于类的字段可以为引用，因此如果一个类有非空引用字段，那这个类就必须显式初始化字段，如果值类型的字段的定义类有非空引用也必须初始化。
例如：

```feng
class A {
   var i *int;
}
func test1() {
   // var a A;                 // ✖：A有非空字段i，所以必须初始化
   // var b A = {};           // ✖：初始化时必须给非空字段i置为非空值
   var c A = {i=new(int)};    // ✔
}
class B {
   var a A;
}
func test2() {
   // var a B;                   // ✖：字段a的类型有非空字段i，所以必须初始化
   // var b B = {};              // ✖：理由同上
   // var c B = {a={}};          // ✖：理由也同上
   var d B = {a={i=new(int)}};   // ✔
}
```

当元素类型包含非空引用时，定长数组必须初始化全部元素：

```feng
func test1(r *int) {
   // var a [2]*int;          // ✖：未初始化
   // var b [2]*int = [r];    // ✖：有元素未初始化
   var C [2]*int = [r,r];     // ✔
}
```

元组类似：

```feng
func test1(r *int, s *bool) {
   // var a (*int,*bool);           // ✖：未初始化
   // var b (*int,*bool) = (r);     // ✖：有元素未初始化
   var c (*int,*bool) = (r,s);      // ✔
}
```

注意！上面有关于类嵌套初始化的例子，但是类与定长数组、元组是存在任意嵌套的场景的。下面只举一个简单例子：

```feng
class A {
   var i *int;
}
func test1(r *int) {
   // var a (*int, [1]A);              // ✖：未初始化
   // var b (*int, [1]A) = (r,[]);     // ✖：未初始化完整
   // var c (*int, [1]A) = (r,[{}]);   // ✖：还是未初始化完整
   var d (*int, [1]A) = (r,[{i=r}]);   // ✔
}
```

##### 不可修改引用

引用可以标注为不可修改（加`#`号），表示不能通过该引用修改实例，同样也是单向传递：可修改 → 不可修改。

```feng
class Foo { var id int; }
func f(a *int, b *Foo) {
   var x *#int = a;  // 转为不可修改引用
   // *x = 1; // 错误✖：不可修改实例
   var y *#Foo = b;
   // y.id = 1; // 错误✖：不可修改实例
}
```

不可修改不能反向传递。

##### 解引用操作

除了数组引用，其他引用均支持解引用操作`*`，改操作相当于直接对指向的实例进行操作，包括取值和赋值：

1. 取值能获取实例的值，并赋给值类型变量：
   ```feng
   class Complex {
      var real, imag float;
   }
   func test(a &int, b *Complex) {
      var x int = *a;
      var y Complex = *b;
   }
   ```
2. 赋值可以直接修改实例，当然不可修改的引用是不能赋值的：
   ```feng
   class Complex {
      var real, imag float;
   }
   func test(a &int, b *Complex, c &#Complex) {
      *a = 1;
      *b = {real=1.0, imag=-1.0};
      // *c = {}; // ✖ 不可修改
   }
   ```

##### 引用类型

###### 强引用类型

强引用表示为`*`带类型符号，比如：`var aDev *Device;`声明了强引用变量`aDev`。
它可以指向一个类`Device`的实例，或者`Device`的[子类](#多态)的实例：

```feng
import std$os;
func test() {
    var b *Device = new(Device);    // 初始化指向一个新分配的Bus实例
    var a *Device = b;              // 将b引用的实例传递给a
    a.speed = 10;                   // a和b的修改都会更新同一个实例
    os$printf("speed={}", b.speed);  // 打印：speed=10
}
```

`const`声明的常量引用，必须初始化指向一个实例（或`nil`），然后不能再改变指向了：

```feng
const a *Bus = new(Bus);
// a = new(Bus); // ✖
// a = nil; // ✖
```

[变长数组](#变长数组)也是引用类型的变量，可以引用元素类型相同但长度任意的数组实例。

强引用在自动内存管理中的作用是标识实例是否被使用：

* 被强引用变量引用的实例不能被内存管理器回收；
* 当一个实例没有被强引用变量引用时就应该被回收。

###### 虚引用类型

虚引用（Phantom Reference）是指不影响内存释放的引用。
可以引用动态创建的实例，也可以引用值类型变量的实例，但只在一定条件下才能使用。

虚引用变量是常量，即只能用`const`声明：

```feng
func test() {
    var gh Host;
    const h1 &Host = gh;
}
```

虚引用只能是本地变量或参数，只能指向一个在作用域内“不动”的实例，“不动”是指这个实例能证明的不会被释放。
因此仅有下面几种情况能传递给虚引用：

1. 值类型变量在作用域内可以被虚引用指向。
2. 常量引用变量在作用域内，可以传递实例给虚引用。
3. 虚引用可以传递实例给新的虚引用。
4. 本地变量是强引用类型，在传递给虚引用之后，在虚引用作用域之内不可被修改。
5. 一个类实例在可以被虚引用的作用区间内：
    1. 它的值类型字段可以被虚引用。
    2. 它的常量字段引用的实例可以被虚引用。
6. 虚引用参数独有特性：允许引用临时实例（即即将销毁释放的实例，包括字面量、初始化表达式、new创建的和返回值）。

显然全局变量能在所有代码中被引用：

```feng
var gDrv Driver;
const rDrv *Driver = new(Driver, {});
func use() {
    const d1 &Driver = gDrv;
    const d2 &Driver = rDrv;
}
```

本地变量需要在作用域内使用虚引用：

```feng
func sample1() {
    var drv Driver;
    const d1 &Driver = drv;
}
func sample2() {
    const drv *Driver = new(Driver);
    const d1 &Driver = drv;
}
func sample3() {
    var drv *Driver = new(Driver);
    {
        const d1 &Driver = drv;
        // drv = nil; // ✖
    }
    drv = nil;
}
```

允许被虚引用指向的实例的字段：

```feng
class Device {
    const driver *Driver;
    var disk Disk;
}
func sample1(dev Device) {
    const drv &Driver = dev.driver;
    const dk &Disk = dev.disk;
}
func sample2(dev *Device) {
    const drv &Driver = dev.driver;
    const dk &Disk = dev.disk;
}
```

虚引用参数允许这样使用：

```feng
func use1(a &int) {
}
func use2(a &Device) {
}
func use3(a [&]int) {
}
func sample() {
    use1(0);
    use1(new(int));
    use1(get());
    use2(Device{});
    use2(new(Device));
    use3([]int[1,2]);
    use3(new([2]int));
}
func get() int {
    return 0;
}
```

#### 枚举变量

枚举变量的使用详见[枚举](#枚举)。

#### 函数原型变量

改变量要么为空，要么指向一个函数，详见[函数原型](#函数原型)。

### 常量

上面讲述了变量的值，而常量的不可变就是指变量值不可变：

1. 值类型常量，其所有内容都不可变，对除了原始类型以外的类型：
    1. 数组常量的每个元素都分别是常量。
    2. 类和结构的字段值都不能修改了。
2. 引用类型常量在一经声明初始化后就只能固定指向一个实例了，直到离开作用域。

```feng
class Vector { var x,y,z int; }
class Data { var ve Vector; }
func test() {
   const vec Vector = {x=1.0,y=2.0,z=3.0};
   // vec.x = 4.0; // 错误✖
   const vecs [4]Vector = [{x=1.0,y=2.0,z=3.0}];
   // vecs[1].x = 4.0; // 错误✖
   const data Data = {v={x=1.0,y=2.0,z=3.0}};
   // data.ve.x = 4.0; // 错误✖
}
```

### 变量作用域

作用域就是变量生效的范围：变量的生命周期从声明开始，直到离开作用域变量的生命周期结束。
作用域一般分本地和全局两种情况。

#### 本地变量

本地变量声明在函数或方法中：

1. 作用域是声明的代码块及其中嵌套内层代码块。
2. 但是同一层不能重复声明同名的变量。
3. 当内层声明同名的变量时（不要求同类型），外层的同名变量则被遮住，不能使用。

```feng
import std$os;
func test() {
   var v = "Hello"; // 变量v的生命周期在当前函数内
   {
      var s = "Fēng!"; // 变量s的生命周期在当前块内
      os$printf("{} {}\n", v, s);   // 可使用外部声明的变量v
   }
   // os$printf("{} {}\n", v, s);  // 错误✖：不能使用内层块内的变量s
   {
      // os$printf("{} {}\n", v, s);  // 错误✖：不能使用另一个块内的变量s
   }
   {
      var v = "Dear Fēng"; // 内层重新声明同名的变量，外层的变量v就被遮住了
      os$printf("{}\n", v); // 打印：Dear Fēng
   }
   // var v = "Fēng"; // 错误✖：不能重新声明
}
```

#### 全局变量

全局变量必须放在代码的最顶层，即在函数和类型定义的外面声明。
不论是变量还是常量都必须初始化。
作用域为全局，生命周期为运行时。

```feng
var count int = 0;
var qps int = 0;
var avg float64 = 0.0;
func doCount() int {
   count+=1;
   return count;
}
```

可以使用`export`导出给其他module使用：

```feng
export const PI float64 = 3.1415926;
export var delay int = 0;
```

_这里定义声明周期为运行时。_

## 字面量

### 整数字面量

### 实数字面量

### 布尔值字面量

`bool`的字面量只能是`true`或`false`。

### 空值字面量 _[未完成]_

空值就是`nil`，表示变量或字段初始值，即不指向任何实例。
可适用于[引用类型变量](#引用类型变量)和[函数原型变量](#函数原型变量)。

### 字符串字面量

字符串并不是原始类型，编译器对字符串字面量进行编码。
字符串字面量即字符串常量，本身不能修改，所以只能用unmodifiable变量引用。
字符串常量不是在函数栈上分配的，而是一律放在常量区：

```feng
import std$os;
func moduleName() [*#]byte {
    var r [*#]byte = "test-module";
    return r; // 离开函数moduleName还是能使用
}
func test() {
    os$printf("module: {}\n", moduleName());
}
```

### 数组字面量

这种字面量仅用于初始化[定长数组](#定长数组)类型。

将数组元素列出来放在方括号中：`[1,2,3]`、`["Hello", "Good"]`等等。

可以在字面量前显示的放置数组类型，这个与类型推导有关，参考[定长数组](#定长数组)。

例如：

```feng
var ga1 = [4]int[1,2,3];   // 显示声明长度为4的int数组
```

如果省略长度，就以元素个数为长度进行推导：

```feng
var ga2 = []int[1,2,3];    // 等同于：[3]int
```

## 宏

宏是一种有特定格式的代码片段，这种特定格式不是随意的，而是由特定用途决定的。
特定用途就是指某种语言特性，比如当宏用于实现[自定义运算](#自定义运算)时，由运算本身设定了代码格式。
目前宏仅支持在类和接口里。

宏统一由`macro`开头定义，主要格式有过程宏和类宏两种。

### 过程宏

过程宏类似一般过程（函数或方法），有名称、参数表和语句序列组成：

- 名称和其他名称互不干扰，可以与其他元素重名。
- 参数表和函数参数不同，而是相当于上下文的变量。
- 语句序列就是普通的语句序列，末尾可以有过可选的表达式。
- 宏不能被调用。

一般语法示例请参考[自定义运算](#自定义运算)：

### 类宏 _[未完成]_

包含名称、字段表和过程宏组成，能保存中间状态。
比如派生类型的[迭代循环](#迭代循环)的实现。

## 泛型

泛型的概念是一般的通用的类型，类似C++的模板的概念，用于实现比较通用的类与函数。
不同与C++，这里的泛型是在展开之前检查，因此展开之前不能做任何具体的操作，只能传值。

泛型使用反引号（\`）标记。

在定义时指定泛型形参：

```feng
class Box`T`{var t T;}
func save`T`(t T) {}
```

在使用时传入具体的实参。

```feng
func f() {
   var ib Box`int` = {t=100};
   save`int`(100);
}
```

可以指定多个参数：

```feng
class Pair`K,V`{
   var k K;
   var v V;
}
func make`S,T`(s S, t T) *Pair`S,T` {
   return new(Pair`S,T`, {k=s,v=t});
}
func test(k int) {
   var b Pair`int,bool` = {k=k, v=k%2==0};
   var p *Pair`int,bool` = make`int,bool`(k, k%2==1);
}
```

支持定义泛型形参的有函数、接口、类及类的方法。

泛型除了解决样板代码外，还能解决自依赖问题，比如：

```feng
var bb Box`Box`int``;
var bbb Box`Box`Box`int```;
```

在没有泛型时，上面的`Box`类会陷入递归初始化导致无法编译：

```feng
class Box {
   var t Box;
}
```

### 泛型函数

函数定义的泛型形参在函数体内可以被当做类型使用：

```feng
func go`R`(r R) {
   var v R = r;
}
```

传入具体参数使用，支持任意类型：

```feng
func test(i int, b bool, a [16]byte, r *A) {
   go`int`(i);
   go`bool`(b);
   go`[16]byte`(a);
   go`*A`(r);
}
```

在另一个泛型函数中可以传入另一个泛型参数：

```feng
func run`P`(p P) {
   go`P`(p);
}
```

### 泛型类

在类上定义的泛型形参可用于字段和方法及方法内部使用：

```feng
class Box`E` {
   var value E;
   func set(v E) {
      value = v;
   }
   func get() E {
      return value;
   }
}
```

上面定义的盒子类看上去可以装任何实例：

```feng
func use() {
   var box Box`[*]int`;
   box.set(new([15]int));
   box.get()[0] = 100;
}
```

类的方法也可以像函数那样有自己的泛型参数：

```feng
class Box`E` {
   var value E;
   func set(v E) {
      value = v;
   }
   func get() E {
      return value;
   }
   func map`R`(f func(E)R) Box`R` {
      return {value=f(value)};
   }
}
func positive(i int) bool {
   return i > 0;
}
func use() {
   var b1 Box`int`;
   b1.set(-100);
   var b2 = b1.map`bool`(positive);
}
```

带有泛型的方法不支持多态，不能覆盖或者被覆盖。

继承一个泛型类时，可以传入实际类型，也能传泛型参数：

```feng
class Pair`K,V` {
   var k K;
   var v V;
}
class MyPair`V` : Pair`int,V` {
   // 当前类实际只有1个泛型参数'V'
}
func use() {
   var p1 MyPair`*int` = {k=1,v=new(int)};
}
```

接口实现也是如此：

```feng
class Node`T` (Inode`T`) {
}
```

### 泛型接口

接口的泛型只能定义在类型上，方法不支持：

```feng
interface Box`V` {
   set(V);
   get()V;
}
```

实现类示例：

```feng
class MyBox`E` (Box`E`) {
   var value E;
   func set(v E) {
      value = v;
   }
   func get() E {
      return value;
   }
}
```

### 泛型推断

当前已支持泛型推断，就是根据接受者的类型参数来推断提供者的类型参数。

下面列举支持的推断场景。

泛型函数可根据实际调用时传参类型进行推断：

```feng
func gen`T`(t T) T {
    return t;
}
func use(n int) {
    var v int = gen(n); // 传参是int，因此T被推断为int，所以返回是int
}
```

也可以根据返回值推断：

```feng
func empty`T`() T {
    var t T;
    return t;
}
func use(n int) {
    var v int = empty(); // 左边类型是int，因此T被推断为int
}
```

类型推断支持复合类型上的推断，比如元组、数组、泛型类等等：

```feng
func gen1`T1,T2`(a (T1,T2)) T1 {
    return a.0;
}
class A`T1,T2` {
    var t1 T1;
    var t2 T2;
}
func gen2`T1,T2`(a &A`T1,T2`) T1 {
    return a.t1;
}
func gen3`T`(a [&]T) T {
    return a[0];
}
func use() {
    {
        var a (int, bool) = (11,true);
        var v int = gen1(a);
    }
    {
        var a A`int,bool` = {};
        var v int = gen2(a);
    }
    {
        var a [4]int;
        var v int = gen3(a);
    }
}
```

但是对初始化表达式（数组表达式、字段表达式及元组表达式），必须要标记上类型的才能推断：

```feng
func use() {
    {
        // var v int = gen1((11,true));         // ✖ 不能推断
        var v int = gen1((11:int,true:bool));
    }
    {
        // var v int = gen2({t1=1,t2=false});   // ✖ 不能推断
        var v int = gen2(A`int,bool`{});
    }
    {
        // var v int = gen3([2]);               // ✖ 不能推断
        var v int = gen3([]int[2]);
    }
}
```

类的方法支持泛型参数，推断用法与函数相同。

在`new`一个泛型对象时，如果左边有对于类型参数，也可以推断出来：

```feng
class Box`T` {
    var v T;
}
class BigBox`S`:Box`S`{}
func makeInt() *Box`int` {
    return new(Box);
}
func makeBigInt() *Box`int` {
    return new(BigBox);
}
func make`E`() *Box`E` {
    return new(Box);
}
func makeBig`E`() *Box`E` {
    return new(BigBox);
}
```

当[函数原型](#函数原型)指向一泛型函数时，也可以推断类型：

```feng
func filter`T`(t T) T {
    return t;
}
func test() {
    var f func(int)int = filter;
}
```

## 异常

能被抛出的异常类型必须是内置异常`Exception`类或者它的子类，这个`Exception`的定义如下（内置）：

```feng
class Exception {
   var fn uint64;
   var line uint32;
   func trace(fnAddr uint64, lineNum uint32) {
      fn = fnAddr;
      line = lineNum;
   }
}
```

除了内置`Exception`外，还内置了两个内置的异常类。之所以内置是因为在运行会动态的抛出：

1. `NilException`：运行时使用的引用如果是`nil`就会抛出这个异常。
2. `OutOfBoundsException`：通过索引访问数组时，如果索引超出实际长度则抛出此异常。

可以自定义异常：

```feng
class MyException : Exception {
}
func run() {
   throw new(MyException);
}
```

## 编译期常量

编译期常量是在编译期就能推导和计算出值的常量。下面列出编译期常量：

1. 字面量。
2. 声明为`const`的变量，且类型为[原始类型](#原始类型)和[字符串字面量](#字符串字面量)。
3. 所有由编译期常量组成的表达式也是编译期常量，因为通过运算能在编译期计算出结果。

## 并发检查

并发检查的目的是为了提供方案以解决并发引入的内存问题，尤其是采用引用计数管理内存时，不仅能解决计数器问题
（引用计数并不是原子操作，即使采用Atomic操作），还能进行计数器优化（非Atomic化）。当然，即使是用GC内存管理也会因此受益。

### 并发检查机制

与类型系统的严格检查不同，并发检查仅仅是一种基于“信任边界”原理的约定式检查框架，目的是约束仅可同步的实例才能被并发共享访问。
这个“信任边界”是指带有`@Async`属性的函数或方法被调用。
_当然，并发访问包括并发读和修改，对于是否只读取决于变量或字段的实际类型。_

并发检查的基本逻辑是：仅[可同步的实例](#可同步实例)才能“穿过”[并发边界](#并发边界)。

有的类型默认是可同步的，有的需要显式的标注可同步属性`@Sync`，详情在[可同步实例](#可同步实例)中。

例如，调用异步函数时，只有可同步实例通过参数传递进去：

```feng
@Async
func startThread(a *int) { // @Async 声明该函数将发起异步执行
}
func test1() {
    @Sync var r = new(int); // 这是最简单的例子，有@Sync属性的变量r将被允许在并发下共享访问
    startThread(r);        // ✔：因此这里可以将r传给参数
}
func test2() {
    var r = new(int);       // 无属性标注的变量r是不允许在并发下共享访问的
    // startThread(r);     // ✖：因此这里不能将r传给参数
}
```

再例如，调用异步方法时，必须是通过可同步实例发起的：

```feng
class Thread {
    @Async
    func start() {}
}
func test1() {
    @Sync var t = new(Thread);  // 这是最简单的例子，有@Sync属性的变量t将被允许在并发下共享访问
    t.start();                  // ✔：因此这里可以调用方法
}
func test2() {
    var t = new(Thread);        // 无属性标注的变量t是不允许在并发下共享访问的
    // t.start();               // ✖：因此这里不能调用异步方法
}
```

### 同步中的赋值规则

核心规则是：在赋值时，同步的和非同步的不能互相转换。

先明确一点：`new`创建的实例是已知的无主的实例，可以直接传递给同步引用：

```feng
class A {
    @Sync var i *?int;
}
func test() {
    @Sync var r *A = new(A);    // ✔：传递给已同步变量
    r.i = new(int);             // ✔：传递给已同步字段
}
```

但是同步引用与非同步引用之间不能赋值：

```feng
func test1() {
    @Sync var r *int = new(int);
    var s *int = new(int);
    // r = s;              // ✖：非同步引用不能传递给同步引用
    // s = r;              // ✖：同步引用不能传递给非同步引用
}
```

下面举个常量字段需要在初始化时赋值的例子，也是允许的，因为`new(int)`创建的是无主实例，当然可以赋值给字段`i`：

```feng
class A {
    @Sync const i *int;
}
func test() {
    @Sync var r *A = new(A, {i=new(int)});
}
```

对值类型不能标注`@Sync`属性。因为值类型的传值是赋值，最后得到的两个独立的实例，标注是无意义的。例如：

```feng
class A {
   @Sync const i *int;
}
@Async func startThread(a A) {}
func test() {
   var a A;             // ✔：值类型自动同步，条件是类型是可同步的
   // @Sync var b A;    // ✖：不能标注@Sync，避免歧义
   startThread(a);
}
```

[虚引用](#虚引用类型)不能同步，下面举两个反例：

```feng
func test1() {
   var i int;
   // @Sync const r2 &int = i;   // ✖：@Sync不能标注虚引用
   @Sync var j = new(int);
   // const r1 &int = j;         // ✖：按前面的规则，这里是不能转换的
}
```

注意：上面的赋值规则是建立在类型检查基础之上的。即优先检查类型，再检查同步属性。

### 可同步实例

并发检查的核心就是检查可同步实例。

具体某个实例是自动可同步的还是需要显式标注的，主要是看访问路径上是否有引用。

对[值类型](#值类型变量)和[引用类型](#引用类型)两种实例类型的规则不同：

1. 值类型传值是复制一份副本，因而两个线程其实会用于两个不同的实例，所以值类型是自动同步的。
2. 但类作为值类型使用时（例如：`var a Car;`），进入方法后的`this`是虚引用类型，因此不能调用并发边界方法。
3. 关于两种引用类型，**虚引用**是禁止同步的，且只针对**强引用**做并发检查。

一个实例可能会引用到其他实例，当这个实例同步之后，被它引用的实例也可能会被另一个线程访问到。
所以按[并发检查机制](#并发检查机制)的规则要求，被引用的实例也必须是可同步的，编译器也会对实例的类型进行并发检查。

例如：

```feng
class A {
    var i *?int;
}
func test() {
    @Sync var r *A = new(A);
    // startThread(r);      // ✖：变量r标注了@Sync属性准备同步，但类A是不可同步的，推导出r为不可同步的
}
```

实例类型有原始类型、枚举类型、结构类型、函数原型、类、接口以及它们对应的数组类型，元组，根据类型定义，按是否能引用其他实例，可以划分为两部分：

1. 原始类型、枚举类型、结构类型、函数原型，这4种类型将自动获得可同步属性。因为他们不能引用其他类型的。
2. 其余的类型，类、接口和数组的规则在下面分别讲述：
    1. 类的规则为：
        1. 类如果没有引用类型字段，则自动获得可同步属性。
        2. 引用类型字段全部显式标注`@Sync`，例如：`class A { @Sync var i *int; @Sync var b *bool; }`，那么类`A`就可同步了。
        3. 在类上显式标注`@Sync`，就无需单独标注每个引用类型字段了，该类是可同步的。
           注意！！！**被引用的实例也必须是可同步的**！！！因此当一个值类型字段也是一个类时，这个类也必须是可同步的，以此类推。
           例如：`class A { var i *int; } class B { var a A; }`这个类`B`无引用类型字段，但它的字段`a`的类型`A`有一个未标注
           `@Sync`的引用类型字段`i`，因此类`B`是不可同步的。
        4. 同步属性不能继承，父类已经实现同步，但子类会被独立检查。因此按规则2.1.2和2.1.3就有下面的情况：
            1. 例1：`@Sync class A { var i *int; } class B:A {}` 其中类`B`不能继承类型上的属性`@Sync`，所以不可同步。
            2. 例2：`class A { @Sync var i *int; } class B:A {}` 其中类`B`会被独立检查，因类`B`的唯一引用字段`i`是有
               `@Sync`的，因此可同步。
    2. 接口是不能实例化的，它的引用只能指向一个类的实例，因此实现同步的方式就是在接口上加标注`@Sync`，表明它只能指向一个可同步的类。
    3. 首先，[变长数组](#变长数组)不可同步（数组元素不能标注属性）。由于数组可以嵌套的情况，所以要实现可同步，必须是嵌套路径上无引用。
       即：嵌套路径上都是[定长数组](#定长数组)，并且最终元素是可同步的。
        1. 例1：`[2][*]int`、`[2][*][4]int`、`[*][3][*]int`等在路径上有变长数组，都是不可同步的。
        2. 例2：`[2]*A`、`[2][4]*B`等，最内层元素是引用的，不可同步。
        3. 例3：`[2]A`、`[2][4]B`等，最内层是值类型，但要求最内层元素的类型是可同步的。
    4. 元组是值类型，与数组一样存在嵌套情况。前面讲的是只有数组的情况，但元组与数组是允许互相嵌套的，所以情况就变的稍微复杂一些：
       数组是线性嵌套，而元组是树形嵌套，元组与数组组成的嵌套类型也是树形的，且每个分支都是嵌套路径，在检查时每个分支都要求满足路径上无引用。
       例如：
        1. 例1：`([2][*]int, [2]int)`，元组的第一个元素是数组，其嵌套路径上有引用`[*]int`，不可同步。
        2. 例2：`[3](int, *A)`，数组的元素是元组，元组的第二个元素是引用，不可同步。
        3. 例3：`[3](int, A)`、`([2]int, [3]A)`，路径上无引用，是否可同步取决于类型`A`（因为`int`是可同步的）。

例如下面的类`A`和`B`都是可同步的：

```feng
class A {
    @Sync var i *?int;
}
class B {
    var a A;
}
func test() {
    @Sync var r *A = new(A);
    startThread(r); // ✔：完整实现可同步属性
}
```

在规则2.1.4中，还有另一种情况：

```feng
class A {
   var i *?int;
}
class B:A {
   @Sync var j *int;
}
func test() {
   @Sync var b *B = new(B);
   // startThread(b);      // ✖：检查B类发现继承的字段 i 没有同步属性，所以不可同步
}
```

对数组的情况，可同步的情况是：

```feng
class A {
    @Sync var i *?int;       // 注意：唯一字段i已标记同步，则A可同步
}
class B {
    var i *?int;             // 注意：字段i未实现同步，则B不可同步
}
class C {
    var a1 [2]A;
    // var a2 [*]A;         // ✖：嵌套第一级是变长数组，会导致C不可同步
    // var a3 [2][*]A;      // ✖：嵌套第二级是变长数组，会导致C不可同步
    // var a4 [2][4]*A;     // ✖：如上
    // var b1 [2]B;         // ✖：B类型是不可同步的，会导致C不可同步
}
func test() {
    @Sync var r *C = new(C);    // ✔：类型C已实现可同步
    startThread(r);             // ✔：完整实现可同步属性
}
```

可同步的接口只能指向可同步的类，例如：

```feng
@Sync interface I {}            // 可同步接口
class A (I) {
    @Sync var i *?int;       // ✔：标记变量i未允许同步
}
func test() {
    @Sync var r *I = new(A);// ✔：A可同步，接口I的引用就能指向A的实例
}
```

下面是反例：

```feng
@Sync interface I {}            // 可同步接口
class A (I) {
    var i *?int;             // 注意：字段i未标记同步
}
func test() {
    @Sync var r *I = new(A);// ✖：A不可同步，接口I的引用不能指向A的实例
}
```

目前此标记不支持泛型，包括类型和函数。

```feng
// @Sync    // ✖：泛型类不能标注
class Box`T` {
    // @Sync    // ✖：泛型类中都不能标注
    var t T;
}
// @Async    // ✖：泛型类不能标注
class startThread`R`() {}
```

### 并发边界

注意：边界函数或边界方法不能有返回值——这个规则仅为了防止无意义未定义行为。举2个反例：

```feng
@Async func run() int {
    return 0;
}
class Thread {
    @Async func run() int {
        return 0;
    }
}
```

另外类在继承时支持覆写父类方法，但如果父类方法是并发边界，子类覆写的方法也必须是并发边界。例如：

```feng
class Thread {
    var state int;

    @Async func run() {
    }
    func state() int {
        return state;
    }
}
class MyThread : Thread {
    @Async func run() {     // 注意：同父类的run()方法一样标注
    }
    func state() int {
        return state;       // 注意：同父类的state()方法一样无标注
    }
}
```

在实现接口时也一样：

```feng
@Sync
interface Runner {
    @Async run();
}
class Thread (Runner) {
    @Async func run() {     // 注意：必须同接口的run()方法一样标注
    }
}
```

当一个函数或方法作为边界时，它的参数将被默认标注上可同步属性`@Sync`，而且要求参数的实例类型也是可同步的。
例如：

```feng
@Sync
class ThArg {
    // ..
}
@Async
func thread(t *ThArg) {
    @Sync var r = t;           // 参数t是可同步的，这个赋值是合法的
    // var s *ThArg = t;   // ✖：不能赋值给非同步变量
}
```

因为非同步的实例不能调用并发边界方法，因此在并发边界方法中`this`是可同步实例。
另外，在普通方法中不能证明`this`是否可同步，因此调用另一个并发边界方法。
例如：

```feng
class A {
   @Async
   func run() {}
   @Async
   func start() {
      this.run();       // ✔：this是可同步的，可以调用run()
   }
   func calc() {
      // this.run();    // ✖：this的可同步性是未知的，不能调用run()
   }
}
```

### 编译器优化

并发检查仅仅是语法语义上的检查，而编译器后端对`@Sync`属性的处理分两种情况：

1. 字段和变量的同步处理很频繁且简单，编译器处理起来很高效，因此由编译器直接生成相应的并发操作。
2. 而类的同步处理主要是考虑复杂场景，比如无锁并发队列等，编译器无需做任何并发处理。

当前编译器是按以上两点来处理的。

## 单元测试

编译期在测试模式下会只编译单元测试。

### 单元测试用例

单元测试的测试用例函数需要加上`@Test`属性：

```feng
func far() {}
@Test
func testFar() {
   far();
}
```

测试用例函数可以在模块的任意位置，可以与正常代码混合编写，编译期会自动区分。
例如和`main`函数一起，在测试模式下不会编译`main`函数，只会编译测试用例；而编译模式下则忽略所有测试用例：

```feng
import std$os;
@Test
func testFar() {
   os$printf("This is testcase\n");
}
func main() {
   os$printf("This is main\n");
}
```

测试用例的函数不能带参数和返回值，下面是反例：

```feng
@Test
func test1() int { return 0; }   // ✖：不能有返回值
@Test
func test2(a int) {}             // ✖：不能有参数
```

测试用例不能被其他函数或方法调用，下面也是反例：

```feng
@Test
func test1() {}
@Test
func test2() {
   test1();    // ✖：不能被其他测试用例调用
}
func far() {
   test1();    // ✖：不能被函数调用
}
func main() {
   test1();    // ✖：不能被main函数调用
}
class Tr {
   func run() {
      test1(); // ✖：不能被方法调用
   }
}
```

## 调用C语言

支持让Fēng直接使用C语言的函数和类型。

### C语言类型

Fēng的`struct`/`union`与C语言不仅关键字相同，内存布局也一致，因此可以直接传递。

Fēng的原始类型与C语言在位宽和符号上完全对应，可以直接传值；其中`bool`类型也对应C语言的`bool`类型。

**指针处理**：Fēng不能直接使用C语言的指针，编译器在解析时会将指针统一转为`uint64`类型。
Fēng的普通[引用类型变量](#引用类型变量)可以转换为`uint64`类型（只能转为`uint64`），这样就可以传递给C语言的指针参数。
**注意**：反向转换则是严格禁止的——引用的值只能通过`new`创建实例获得（参考[new表达式](#new表达式)），因此在Fēng层面不存在安全隐患。
[数组引用](#变长数组)不能直接转换为`uint64`，而是通过内置字段`values`获得指针（类型同样为`uint64`），以此传递给C语言。

例如：

```feng
func use(a *int) {
    var p uint64 = uint64(a);
    // var r *int = p;  // ✖ 不允许从 uint64 反向转换为引用
}
```

### C模块

支持使用纯C语言编写模块，但不支持一个模块中混合C代码和Fēng代码。

C模块的头文件中声明需要被引用的符号（函数、类型或全局变量），编译器会解析并加上模块路径前缀，在 Fēng 中可以当作普通模块导入和使用。

例如，路径为`jjj$mm`的C模块，其目录结构为：

```
jjj/mm/
├── mm.h       ← 头文件：声明结构体和函数
└── mm.c       ← 实现文件：函数实现
```

头文件`mm.h`：

```C
struct Complex { float r, i; };
struct Complex add(struct Complex a, struct Complex b);
```

实现文件`mm.c`：

```C
#include "mm.h"

struct Complex add(struct Complex a, struct Complex b) {
    return (struct Complex){.r = a.r + b.r, .i = a.i + b.i};
}
```

在Fēng中就可以直接使用了：

```feng
import jjj$mm;

func test() {
    var a mm$Complex = {r = 1.1};  // i 默认为 0
    var b mm$Complex = {i = 1.1};  // r 默认为 0
    var c = mm$add(a, b);
}
```

### 调用C库

要调用外部C库（libc或第三方库），只需在模块目录下放置一个头文件，在其中包含目标库的头文件并声明需要使用的符号。

**libc** 是自动链接的，无需额外配置。参考 [`std/os`](std/os) 模块：头文件 [`file.h`](std/os/file.h) 包含了 `<stdio.h>`
，就能直接在 [`file.feng`](std/os/file.feng) 中调用 `fopen`、`fwrite` 等 libc 函数。

**第三方库**（如 `pthread`、`m`、`dl`）不会自动链接，需要在模块目录下创建 `feng.cfg` 文件，声明链接的库名：

```properties
# feng.cfg —— 声明需要链接的库，逗号分隔（不含 -l 前缀）
link=pthread
```

多个库用逗号分隔：`link = pthread,m`。
