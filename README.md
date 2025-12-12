【Fèng】编程语言
===================

C++最初在C基础上扩展了面向对象，可以更方便的使用类进行封装，使得对象的使用也越来越频繁。
对一个大型项目，总有大量的对象相互引用组成很复杂的有向图，C++的手动内存管理越来越繁琐。
而且C++依然允许不安全的操作，C11中的cast语义但并不是强制性的，还缺少边界检查，甚至允许将整数转成指针等。
这些内存问题虽然可以用ASAN来检查，但ASAN无法检查出所有情况，比如非法指针恰好落在正在使用的区间时ASAN逻辑上无法判断是否非法指针。
因此内存安全需要从语法语义的约束设计才能保证。

考虑改进C++语言，或者改进“C with class”，应该是在安全的自动内存基础上扩展面向对象。

# 特性简介

下面是按这个的思路设计来Fèng编程语言，但目前仅写了[grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4)。

先整理一下主要的特性：

1. 内存安全机制的设计：
    1. 自动内存管理：指针必须指向一个对象或者为空，而对象在没有被指针时将被释放，释放的对象可以立即或延迟回收空间。
    2. 指针安全使用：指针不能通过运算生成，不能将任意整数转换成指针值，这也包括对指针进行自增自减。
    3. 强制检查边界：比如数组及buffer空间的操作。
    4. 强制检查类型：类型转换时必须检查是否允许转换，即使编译时无法分析时，也要在运行时做检查。
    5. 虚引用：用虚引用代替C语言取地址运算（&），限制在实例生命周期内进行使用。
2. 面向对象的基本元素，包括继承多态和接口抽象。
3. 资源类是参考C++析构函数设计的，可用回收底层库分配的资源、处理循环引用等。
4. 用一种类型来管理一段连续空间，并且可以映射为struct和union使用。
5. 模块化代码管理，模块之间使用符号需要导出和导入。

以及几个次要的特性：

6. 值类型变量，就是变量或字段与类型的实例是一体的，相对于引用的动态内存分配有比较高的性能。
7. 异常处理机制。
8. _泛型或者模版，泛型参数可以加约束条件，类成员方法也可以带参数。_
9. _允许给类自定义运算符，从C++的运算符重载简化来。_

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

假设`printf`是module`fmt`提供的打印到终端的函数：

```feng
import fmt *;
func main() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

可以没有返回值的函数：

```feng
import fmt *;
func print(a, b int) {
    printf("%d\n", a + b);
}
```

可以有一个或多个返回值：

```feng
import fmt *;
func divAndMod(a, b int) (int, int) {
    return a / b, a % b;
}
func main() {
    var a, b = 1, 3;
    var s, d = divAndMod(a, b);
    printf("%d / %d = %d\n%d % %d = %d\n", a, b, s, a, b, d);
}
```

## 派生类型

提供派生类型机制，这些派生类型分为：
[类](#类)与[接口](#接口)、[结构](#结构类型)、[枚举](#枚举)和[属性](#属性)。

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

## module

作为代码组织单元，同一个目录下的文件都属于一个module，且module名与目录名相同，因此无需在文件里声明。

1. module内定义的符号在内部都可使用，但是如果在外部则需要使用`export`导出。
2. 如果想使用其他module里的符号，需先要`import`对应的module路径：

# 概念

下面将详细描述各个语法元素的定义及用法。

## module

module是代码的基本管理单元。

1. 在module内部所有的内容都可见，跨module只能访问导出的符号。
2. module名称与路径一一对应，在文件中不声明module名称。要求目录名称的规则和变量名称一样。
   例如在Linux下，module`com.jjj.base.util`对应的相对路径为`com/jjj/base/util`。

### 导出符号

比如当前module为`xxx.util`，其中的符号`Node.value`字段、`List`类及`List.size`方法导出给外部使用：

```feng
class Node {
   var prev, next *Node;
   export var value int;
}
export class List {
   var head *Node;
   var size int;
   const tag int; // 类的成员需要单独导出才能被外面使用
   export func size() {
      return size;
   }
}
```

另外接口、结构类型及属性的成员不需要单独导出。

### 导入符号

声明导入`com.cossbow.fmt`模块：

```feng
import com.cossbow.fmt;
func main() {
   fmt$println(string("Hello Fèng!"));
}
```

可以导入全部可见的符号，就不需要加module前缀了：

```feng
import com.cossbow.fmt *;
func main() {
   println(string("Hello Fèng!"));
}
```

可以设置module别名：

```feng
import com.cossbow.fmt ccfmt;
func main() {
    var m Sring = ccfmt$sprintf("Hello Fèng!");
    ccfmt$println(m);
}
```

## 基本类型

基本类型是语言内置的类型，从内存角度看都能直接放在寄存器中。基本类型包含整数、浮点数和布尔三种类型。

虽然有字符串字面量，但没有内置字符串类型：字符串不能被寄存器直接存放，且字符串只在字符处理时才有意义。

### 整数类型

内置的全部整数类型如下：

- 有符号：`int8`/`int16`/`int32`/`int64`/`int`
- 无符号：`uint8`/`uint16`/`uint32`/`uint64`/`uint`

后缀的数字表示其位宽，无位数字后缀的是根据编译的目标平台决定。

有符号数最高位为符号位：默认`0`为正数，`1`为负数。因此有符号数数值的位宽少了一位。

支持[算术运算](#算术运算符)、[位运算](#位运算符)和[关系运算](#关系运算符)。

不同整数类型之间必须显式转换：

```feng
func main() {
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

浮点数支持[算术运算](#算术运算符)和[关系运算](#关系运算符)。

### 布尔类型

类型符号为`bool`，且只有`true`/`false`两种取值：

* 支持逻辑运算，关系运算（但只支持相等和不等），及位运算（与、或、异或三种）。
* 关系运算的结果一定是布尔值。
* 不支持和整数、浮点数的互相转换。
* `if`、`for`中的条件表达式返回值必须是`bool`类型的。

实际机器上是用整数表示布尔值的，因此规定布尔类型只用最低位表示，且其他位的值不能影响布尔运算结果。
整数值与布尔值对应：

| 布尔值   | 整数值 |
|-------|-----|
| false | 0   |
| true  | 1   |

布尔类型支持[逻辑运算符](#逻辑运算符)，而[关系运算符](#关系运算符)的结果为布尔类型。

## 运算符

### 基本性质

#### 优先级

下表列出了主要运算符的优先级（自上而下优先级递减）：

| 顺序 | 运算符集                 | 备注    |
|----|----------------------|-------|
| 1  | new(),圆括号,lambda,字面量 |       |
| 2  | 断言,索引,引用字段,调用函数      |       |
| 3  | +,-,!                | 一元运算符 |
| 4  | ^                    | 幂运算   |
| 5  | *,/,%                |       |
| 6  | +,-                  |       |
| 7  | <<,>>                |       |
| 8  | &                    |       |
| 9  | ~                    |       |
| 10 | \|                   |       |
| 11 | <,<=,==,!=,>,>=      |       |
| 12 | &&                   |       |
| 13 | \|\|                 |       |

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

### 运算符类型

#### 算术运算符

| 运算符 | 描述  |
|-----|-----|
| ^   | 幂运算 |
| *   | 乘法  |
| /   | 除法  |
| %   | 取模  |
| +   | 加法  |
| -   | 减法  |

#### 位运算符

| 运算符  | 描述   |
|------|------|
| !    | 按位取反 |
| <<   | 左位移  |
| \>\> | 右位移  |
| &    | 位与   |
| ~    | 位异或  |
| \|   | 位或   |

#### 关系运算符

| 运算符 | 描述（左边*右边） |
|-----|-----------|
| <   | 小于        |
| <=  | 小于或等于     |
| ==  | 等于        |
| !=  | 不等于       |
| \>  | 大于        |
| \>= | 大于或等于     |

#### 逻辑运算符

| 运算符  | 描述   |
|------|------|
| !    | 逻辑非  |
| &    | 按位与  |
| ~    | 按位异或 |
| \|   | 按位或  |
| &&   | 逻辑与  |
| \|\| | 逻辑或  |

因为[布尔类型](#布尔类型)只有最低位有效，其他位忽略，则位运算的`&`、`~`、`|`对布尔值运算的结构依然是有效的布尔值，
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

### 其他运算符

#### 索引运算符

仅数组默认支持这种运算符。索引运算符是由中括号组成，括号中是获取索引值的表达式。
其用法有两种：

1. 索引表达式在右边是读操作，即获取索引对应元素的值作为运算结果。在语句中可以有两种方式接收取值：
    1. 左边用两个变量接收：第一个接收的是值；第二个接收的是布尔值，表示索引对应元素是否存在。
       ```feng
       func test(arr [16]int) {
           if (var v, exists = arr[16]; exists) {
               // 索引16越界了，也就是不存在，所以exists=false，无法进入这个分支
           }
       }
       ```
    2. 左边一个变量或者在表达式中使用时，仅返回元素值，如果元素不存在则终止运行并抛出[异常](#异常)。
       ```feng
       func test(arr [16]int) int {
           return arr[16]; // 索引越界，终止运行并抛出异常
       }
       ```
2. 放左边为写操作，即修改索引对应元素的值。
   ```feng
   func test(arr [16]int) {
       arr[15] = 0; // 修改索引为15的元素值为0
   }
   ```
   数组的容量是创建之后就不能变了，所以索引越界自然也要终止运行并抛出[异常](#异常)。

#### new运算符

使用格式：new(类型, 参数)，例如：

```feng
// 创建类Device的实例
var a *Device = new(Device);
// 后面的参数为初始化
var b *Device = new(Device, {});
```

[引用类型](#引用类型变量)的变量和实例是分离的，这种实例则都是通过`new`创建的。

参数是可选的，并且对不同类型的意义也不同：

1. 如果创建[mem类型](#mem类型)，则参数是长度，且分配的空间不需要归零。
2. [类](#类)、[结构类型](#结构类型)和[数组](#数组)的创建参数为初始化值。

#### 断言运算符

用于判断是否能类的引用是否能进行转换的语法，比如类的实例的引用传递可以给接口或父类指针，反过来则需要判断其类型。
有两种用法：

1. 用一个变量去接收结果，运算表达式返回的是对应类型的引用，这种情况下如果不能转换则抛出[异常](#异常)。同时可以参与表达式计算。
   ```feng
   func test(o *Object) {
      var f *File = o?(*File);   // 转换成File类的引用
      var w Writer = o?(*Writer);  // 转换成Writer接口
      o?(*Writer).write("Hello!"); // 在表达式中使用
   }
   ```
2. 用两个变量去接收的话，就会返回[元组](#元组)，不能参与表达式计算。
   第一个值是类型引用参考第1条；第二个为`bool`值，表示是否能进行转换。这时不会抛出[异常](#异常)。
   ```feng
   func valid(w *Writer) {
      var f, ok = w?(*File);
      if (ok) f.close(); // 如果匹配上，转换的结构放在变量f上
   }
   ```

#### 赋值运算符

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

### 自定义运算符

[类](#类)是不支持运算符的，但是可自定义一部分运算实现。

自定义的运算功能代码段与方法跟函数都不一样，而是由operator[宏](#宏)实现的。
每一种运算符都有固定名称和原型及操作数列表：

* 每种运算符有固定名称。
* 具体因不同运算符定义操作数（就是宏参数）。
* 名称和其他方法名称可以相同。

#### 自定义表达式运算符

仅有一部分支持自定义：

| 运算符 | 宏名称   | 右操作数类型 | 结果类型  |
|-----|-------|--------|-------|
| *   | mul   | 同左操作数  | 同左操作数 |   
| /   | div   | 同左操作数  | 同左操作数 |   
| +   | add   | 同左操作数  | 同左操作数 |   
| -   | sub   | 同左操作数  | 同左操作数 |   
| <   | less  | 同左操作数  | 布尔类型  |   
| <=  | 无     | 同左操作数  | 布尔类型  |   
| ==  | equal | 同左操作数  | 布尔类型  |   
| !=  | 无     | 同左操作数  | 布尔类型  |   
| \>  | more  | 同左操作数  | 布尔类型  |   
| \>= | 无     | 同左操作数  | 布尔类型  |   

类型是指实际表达式中的操作数类型，实现中不需要指定：

1. 表中的类型相同，不仅是类相同，而是全相同，包括是否是引用类型。
2. 如果结果的类型是引用，那会自动创建一个初始化的实例给结果
3. 表中无宏名称的运算符，是依赖其他运算符的实现：
    1. `<=`依赖的`<`和`==`都实现了就可以直接使用了，相当于这两个运算的结果的或`||`。
    2. `>=`依赖的是`>`和`==`，也是两个结果的或。
    3. `!=`仅依赖`==`，就是后者的结果取反`!`。

举个复数的例子：

```feng
class Complex {
   var real,imag float64;
   // 实现+运算
   // result存放结果，并计算完成后返回
   #operator add(lhs, rhs, result) {
      result.real = lhs.real + rhs.real;
      result.imag = lhs.imag + rhs.imag;
   }
   // 实现*运算
   #operator mul(lhs, rhs, result) {
      result.real = lhs.real * rhs.real + lhs.imag * rhs.imag;
      result.imag = lhs.real * rhs.imag + lhs.imag * rhs.real;
   }
}
func testAdd(a,b Complex) Complex {
    return a + b;
}
func testMul(a,b *Complex) *Complex {
    return a * b;
}
```

#### 自定义特殊运算符

##### 自定义索引运算符

默认只有数组支持的[索引运算符](#索引运算符)也可以自定义。
由于索引运算符分有读和写两种操作，因此分成`indexGet`和`indexSet`两个过程宏。

比如自定义一个字典类`Map`，功能是提供派生类型的Key和Value的索引。用法示例：

```feng
class Map {
   // 索引读
   #operator indexGet(key, operand, exists) {
      var n = getNode(key);
      operand, exists = if (n != nil) n.value, true else nil, nil;
   }
   // 索引写
   #operator indexSet(key int, value String) {
      set(key, value);
   }
}
func main() {
   var m Map;
   m[100] = 159;
   // 带检查读：运行时如果key不存在则exists为false
   var v, exists = m[100];
   // 直接读：运行时如果key不存在（exists为false）则终止执行并抛出异常
   var v int64 = m[100];
   printf("m[100] = %s\n", v);
}
```

在写操作时索引不存在是否终止执行取决于内部实现：
比如一般情况的Map是可以新增key的，而数组是不能自动扩容的。

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

类字段的类型支持任意类型， 也支持`const`和`var`方式定义，这些和变量一样：

```feng
class Cat {
   const id int;
   var name rom;
   var mothr,father *Cat;
   var children []*Cat;
}
```

在定义值类型变量时可以对类进行初始化，`const`定义的字段必须初始化。

```feng
func main() {
   var c1 Cat = {id: 1001, name: "Tomcat"};
   var c2 Cat = {id: 1001};
   // var c3 Cat = {name: "Tomcat"}; // 错误：未初始化id
   // var c4 Cat; // 错误：未初始化id
   // var c5 Cat = {}; // 错误：未初始化id
}
```

`new`创建实例时初始化表达式放在里面：

```feng
func main() {
   var c1 *Cat = new(Cat, {id: 1001, name: "Tomcat"});
   // var c2 *Cat = new(Cat, {name: "Tomcat"}); // 错误：未初始化id
}
```

如果类里面没有`const`的字段则不强制要求初始化。

```feng
class Cat {
   var id int;
   var name rom;
}
func main() {
   var c1 Cat;
   var c2 *Cat = new(Cat);
}
```

如果没有指定初始化的字段，编译器必须一律归零，即底层内存初始化为全`0`的状态，引用类型的字段值就是`nil`。
但`const`的字段必须初始化，这样一来就有个副作用：如果`const`的字段未导出，在模块外则就无法创建类的实例。


### 方法

方法与[函数](#函数)基本相同，区别是：

1. 必须通过类实例来调用。
2. 在方法内部能使用当前实例的类成员。
3. 方法名称作为唯一ID，在当前类的方法集中是唯一的，包括继承来的方法，但子类会覆盖同名的父类方法。

比如定义`Task`类用于管理任务（枚举`TaskState`是任务的状态）：

```feng
enum TaskState {WAIT, RUN, DONE,}
class Task {
   var state int;
   func isRunning() bool {
      return state == TaskState.RUN;
   }
   func start() {
      if (isRunning()) return;   // 调用另一个方法
      state = TaskState.RUN;     // 修改状态
   }
}
```

通过值类型的变量来调用方法：

```feng
func sample1() {
   var task Task;
   task.start();
   printf("task state: %d\n", task.state);   // 打印：task state: 1
}
```

也可以通过引用来调用：

```feng
func sample2() {
   var task *Task = new(Task);
   task.start();
   printf("task state: %d\n", task.state);   // 打印：task state: 1
}
```

### this关键字

`this`是一个类内部的特殊关键字，用于指代当前实例本身。

在成员方法内使用当前类的成员，本地变量可能和字段同名，那么需要通过`this`来使用字段：

```feng
class Cat {
   var name String;
   func setName(name String) {
      log();   // 上文没有log函数，那就指向log成员方法
      this.name = name; // 上文有name变量/参数，与成员字段name冲突，必须加this
   }
   func log() {
      printf("%s: miao~~\n", name); // 上文没有name变量，可以省略this
   }
}
```

在方法调用时`this`即会引用当前实例，保证实例不会被释放：

```feng
func sample() {
   new(Cat).log();  // log方法退出后创建的实例才能被释放
}
```

`this`只能传递给[虚引用类型](#虚引用类型)的变量：

```feng
class Foo {
   var name String;
   func bar() {
      var x &Cat = this;
      // var y *Cat = this; // ✖
   }
}
```

`this`可以放在返回值的地方，但不能声明其他返回值，然后该方法返回值不能传递给变量，但可以在后面使用该类的成员：

```feng
class Car {
   var speed int;
   func forward() this {
   }
   func stop() this {
   }
   func backward() this {
   }
}
func sample() {
   var c Car;
   var speed = c.forward().stop().backward().speed;   // 链式调用
}
```

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
class Animal {
    var name rom;
    func eat(food rom) {
        printf("Animal %s eating %s\n", name, food);
    }
}
```

然后定义一个子类`Cat`，继承了父类字段`name`，下面实现一个与父类的方法`eat`同名同原型的方法：

```feng
class Cat : Animal {
    func eat(food rom) {
        printf("Cat %s eating %s.\n", age, name, food);
    }
}
```

允许`Animal`的引用指向一个子类实例，通过父类引用调用`eat`方法时，允许时实际会调用子类的`eat`方法：

```feng
func main() {
   var animal *Animal = new(Cat, {name="Tom"});
   animal.eat("fish-meat"); // 将打印的是：Cat Tom eating fish-meat.
}
```

通过这个例子可以看到，方法`eat`在继承之后可以允许有多个实现，父类引用的不同子类均会指向子类实现的方法。
**子类在重实现父类方法时要求原型必须一致**。

支持[断言运算](#断言运算符)来判断子类型：

```feng
func test(animal *Animal) {
    var cat, ok = animal?(*Cat);
    if (ok) cat.eat("mouse");
}
func main() {
    test(new(Cat));
}
```

父类与子类仅支持引用传递，值类型变量之间不能传递。且传递规则为：

1. 引用类型相同的情况，子类可以传递给父类。
2. 子类的常量强引用可以传递给父类的虚引用。

比如父类`Animal`和子类`Cat`之间传递：

```feng
func sample1(lc *Animal) {
    var c1 *Cat = lc;
}
func sample2(lc &Animal) {
    var c2 &Cat = lc;
}
func sample2(lc *Animal) {
    var c2 &Cat = lc;
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

用法和多态类似了：

```feng
func asyncRun(t *Task) {
    t.run(); // 假装这里在异步执行
}
func main() {
    asyncRun(new(MyTask));      // 打印：Run my task!
    asyncRun(new(YourTask));    // 打印：Run your task!
}
```

当然也支持[断言运算](#断言运算符)判断类型。

### 根类

由于类是单继承的，因此所有类都会按继承关系形成一棵树，而这棵树的根类就是`Object`类。
这是内置的类，没有声明继承任何父类的类则默认直接继承`Object`类。
`Object`类没有任何成员，可以创建一个`Object`的对象。

```feng
func test() {
    var o *Object = new(Object);
    o = new(Device);
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

当一个类添加了release方法时，这个类就被标记为资源类，release方法会在这个类的实例释放时调用。

这个特性可以用于自动释放其他资源。比如C语言lib中分配的缓冲区：

```feng
class CBuffer {
   const buf uint64; // 假设这个字段保存的是buf指针值
   func release() {
      cFree(buf); // 假设可以这样调C语言的释放函数free
   }
}
```

资源类只能通过`new`创建实例。这个限制可以避免重复调用。
比如上面的`CBuffer`类，值类型在赋值中复制了`buf`的值，多个实例在释放时就会重复调用`cFree(buf);`。

如果使用引用计数管理内存，那这个特性能辅助程序员解决循环引用的问题，当然也适合其他数据结构（比如红黑树）。

```feng
class Node {
   var prev,next *Node;
}
class DualQueue {
   var head *Node;
   func release() {
      for (var p = head; p != nil; p = p.next) {
         p.prev = nil;
      }
   }
}
```

但是外部资源的关闭往往是耗时操作，如果放在这里处理可能对性能的影响难以预料，而且还需要处理IO错误或异常，
所以应该采用[异常语句](#异常语句)来处理。

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
interface Reader {
   read(b ram) (int, Error);
}
interface Writer {
   write(b rom, off, len int) (int, Error);
}
// 组合成的接口File包含read和write方法
interface File {
   Reader;
   Writer;
   query() *FileInfo;
}
// 实现File接口的实例自然也实现了Write接口
func use(file *File) Write {
   return file;
}
```

### 接口类型变量

接口类型变量是引用类型变量，并且只能引用实现类的实例。
接口的变量声明需要加上引用标识符来标识引用类型。

允许的传递：

1. 引用类型相同的情况，实现类可以传递给接口。
2. 实现类的常量强引用可以传递给接口的虚引用。

比如接口`Cache`和实现类`LocalCache`之间传递：

```feng
func sample1(lc *LocalCache) {
    var c1 *Cache = lc;
}
func sample2(lc &LocalCache) {
    var c2 &Cache = lc;
}
func sample2(lc *LocalCache) {
    var c2 &Cache = lc;
}
```

## 枚举

枚举类型的值的个数是有限的，且必须在定义时把全部值都列举出来：

```feng
enum TaskState {WAIT, RUN, DONE,}
```

枚举类型内置了特殊属性，这些属性在编译时就已经确定：

* `id`：自动按定义的顺序递增产生的整数值，就是说修改了顺序就会变化。
* `name`：就是定义的字面名称。比如上面定义的`WAIT`，其名称就是`"WAIT"`。
* `value`：允许自定义的属性，整数类型。在未定义情况下，第一个枚举值的`value`等于`0`，后面的等于上一个的`vaue`递增1。

使用枚举的值需要枚举类型为前缀：枚举名称.枚举值，例如：

```feng
enum TaskState {WAIT, RUN, DONE,}   // 未设置value，就等于id
enum BillState {WAIT, PAID=4, SEND, DONE,} // 这里WAIT=0，SEND=5，DONE=6，……
func main() {
   var a int = TaskState.RUN;   // a初始化为整数：1
   var b rom = TaskState.DONE;  // b初始化为字符串："DONE"，b的类型不能是ram
}
```

支持[迭代循环](#迭代循环)所有枚举值：

```feng
func main() {
   for ( s : TaskState )
      printf("name: %s, id: %d \n", s.name, s.id);
}
```

支持直接通过索引取值：

```feng
var statuses TaskState = TaskState[0];
```

枚举的变量或数组元素的默认值为第一个枚举值（`id`等于`0`的）。

## 结构类型

### 结构类型定义

结构类型是指`struct`和`union`两种派生类型，设计与C语言基本一致。
字段只能为基本类型和结构类型的值类型，总之不能出现与指针相关的元素。

```feng
struct A {
    a1 int;
    a2 : 8 int;
    a3 Base;
}
union B {
    b1 int;
    b2 : 8 int;
    b3 Base;
}
func test() {
   var a A = {a1= 10, a2= 2, a3= {v=-1}};
   var b B = {b2= 4}; // 显然union在初始化时只能写一个字段
}
```

当然字段的内存布局完全与定义的顺序一致。

TODO：内存对齐怎么办？……

### 结构类型与内存

结构类型没有实例的概念，使用`new`语法创建的不是实例，而是内存类型引用的映射。

```feng
struct Foo {
   score int;
}
var foo *Foo = new(Foo, {score= 0});
func main() {
   foo.score+=1;
   println(foo.score);
}
```

基本类型也允许这样使用，不过基本类型没有字段，如果要修改其值需要用复制。

可以将一个陌生的内存映射到结构类型上使用，但要在运行时检查越界。

```feng
func getScore(r rom) int {
   var f *Foo = r;  // 转换时自动计算并检查大小，防止越界
   return f.score;
}
func writeFoo(f Foo, out Writer) {
   var r rom = f;
   out.write(r);
}
```

这个操作只对基本类型和结构类型的引用及其数组引用有效。

```feng
func count(r rom) int {
   var list []Foo = r;  // 这里必须
   var t = 0;
   for ( f : list ) {
      t += f.score;
   }
   return t;
}
```

这里的是真的基本类型和结构类型的数组，但是有些情况的容易被看错：

```feng
var gList [][]Foo;   // 这个数组的元素类型是[]Foo，上文提到这时引用类型
var gUser []*Foo;    // 这个数组类型元素是*Foo
```

## 数组

### 数组元素

数组是用于存储一组连续重复元素的类型，元素可以为任意类型。
每个元素相当于一个[变量](#变量)，也分[值类型](#值类型变量)和[引用类型](#引用类型变量)：

```feng
var a [4]int;       // 基本类型数组
var b [4]Host;      // 类数组
var c [16]*Bus;     // 类引用数组
var d [12][4]int;   // 定长数组的数组：即多维数组
var e [10][]int;    // 变长数组的数组，区别于多维数组，元素其实为引用
```

值类型数组的元素所需空间是和数组一起分配的，可以直接使用：

```feng
func test() {
    // 基本类型数组
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

初始化为表达式时，表达式的结果必须是同类型长度相同的数组：

```feng
func foo() [4]int {
    return [1,2,3,4]
}
func foobar() {
    var a [4]int = foo();
    // var b [2]int = foo(); // 错误✖
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
    var bar []int64;
}
```

注意：[结构类型](#结构类型)的字段类型不能为[引用](#引用类型变量)，必须指定长度。

## mem类型

用于管理一段连续的内存空间：

1. 只能通过`new`创建mem实例，且变量也是引用类型的。
2. 能映射成具体类型来使用。
3. 自动检查边界，越界则抛出[异常](#异常)。
4. 能进行只读控制。

### 创建mem实例

mem类型的变量是[引用](#引用类型变量)，而mem的实例也是用`new`创建的。

在创建时需要传入长度，比如创建一个16字节的`ram`实例：

```feng
var a ram = new(ram, 16);
```

创建时带上映射类型，这时长度是已知的：

```feng
var a ram = new(ram`[4]int`); // 创建一个size为16字节的ram
```

注意：创建时`new`的类型参数只能是`ram`，创建一个[只读](#限制读写)的没有意义。

### mem的映射

支持映射的类型：[结构类型](#结构类型)、[基本类型](#基本类型)和这两个类型的[定长数组](#定长数组)。
这些类型占据的是连续空间，而mem就是一段连续的空间，映射就是从起始地址开始一一对应起来。

映射结构类型后可以直接使用字段：

```feng
struct Message {
    key uint32;
    value [60]uint8;
}
func foo(a ram) {
    var m ram`Message` = a;
    printf("key=%u\n", m.key); // m.key对应的是ram的前4过字节
}
```

映射数组后可以直接使用索引：

```feng
func foo(a ram) {
    var m ram`[]uint` = a;      // 不固定长度的
    printf("m[9]=%u\n", m[9]);  // 和数组一样会检查边界
    var n ram`[16]uint` = a;    // 固定了长度，在映射时会检查边界
    printf("m[9]=%u\n", m[9]);
}
```

映射成基本类型后支持所有的基本[运算符](#运算符)：

```feng
func foo(a rom) int {
    var i rom`int` = a;
    return i + 5;
}
```

但是不能直接赋值，因为变量是引用类型的，想修改其指向的mem类型实例，只能用[复制](#复制语句)。
例如下面例子可以修改传入参数所引用的实例内容：

```feng
func foo(a rom) int {
    var i rom`int` = a;
    i := i + 5;
}
func bar() {
    var a ram = new(ram`int`);
    foo(a);
    printf("a = %d\n", a); // 应该打印：a = 5
}
```

不同映射之间可以直接转换，当然依然会检查边界：

```feng
func foo(a ram`Response`) {
    var r ram`Message` = a;
}
```

也可以反过来去映射：

```feng
func foo(a ram`[]int`) {
    var r ram = a;
}
```

### mem长度

长度为字节数，可以通过函数`sizeof`获取。

```feng
func testLen(b ram) int {
    return sizeof(b);
}
```

在映射时计算所需空间大小，如果所需空间超过实际`size`则抛出越界[异常](#异常)：

```feng
func testLen() {
    var b ram = new(ram`[4]uint8`);
    var c ram`int64` = b; // 这一行会抛出越界错误
}
```

基本类型和结构类型size是确定的，而且这两个类型的[定长数组](#定长数组)的size也是确定的，因此只存在一个边界检查的问题：

```feng
struct Message {
    id uint64;
    val float64;
}
func testLen(a ram) {
    var b ram`int32` = a;       // 需要4字节
    var c ram`[2]int32` = a;    // 需要8自己
    var d ram`Message` = a;     // 需要16字节
    var e ram`[4]Message` = a;  // 需要64字节
}
```

而映射后变长数组的`size`则是动态计算的，即：mem的长度除以元素大小并向下取整：

```feng
func testLen(a ram) {
    var b ram`[]int32` = a; // b.size == sizeof(a) / 4
    var c ram`[]Request` = a; // c.size == sizeof(a) / sizeof(Request)
}
```

如果实例长度小于元素大小，那么数组长度就等于`0`了。只读

### 限制读写

mem设计有两种子类型：

1. `ram`允许读写操作，可以传递给`rom`，允许创建。
2. `rom`是只读的，不能传递给`ram`，不能创建。

只读是指在语法语义上的，映射之后不能进行复制、修改元素和字段等操作：

```feng
func foo(a rom`[4]int`, b rom`Request`) {
    // 下面操作均不允许
    // a := [0];
    // a[0] = 0;
    // b.id = 0;
}
```

只运行`ram`传递给`rom`：

```feng
func foo(a ram) {
    var r rom = a;
    // var s ram = r;  // 错误✖
}
```

不同映射之间、映射与原mem之间可以直接传递：

```feng
func foo(a ram) {
    var b ram`Message` = a;
    var c rom`Message` = a;
    var d rom`Message` = b;
    // var e ram`Message` = c; // 错误✖
}
```

## 函数

定义格式为：`func` 函数名 `(` 参数表 `)` 返回表 `{` 函数体 `}`

其中函数名是必须的，参数表、返回表及函数体都可以为空。下面举3个例子：

```feng
func run() {}
func start() { run(); }
func exec(a []Sting) *Error {
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

下面的例子定义了类型为`Queue`的`l`和类型为`int`的`a`两个参数：

```feng
func send(l Queue, a int) {
   l.push(a);
}
```

相邻且相同类型的参数可以合并定义，比如定义两个类型为`int`的参数`a`和`b`可以这样：

```feng
func add(a, b int) int {
    reutrn a + b;
}
```

### 返回值类型表

在参数和代码段之间声明返回值类型表，用圆括号括起来。例如函数`foo`返回一个`int`和一个`float`：

```feng
func foo() (int, float) {}
```

只有单一返回值的时候可以省略括号：

```feng
func online() bool {};
```

多返回值对程序设计的影响较大。比如，在不仅需要得到函数的执行结果，还需要知道函数执行的错误信息时，
就不必加个引用传参来修改外部变量了，直接返回即可：

```feng
func createDevice(host *Host) (*Device, *Error) {
   if (host.inRecovery()) {
      return nil, errorInRecovery;
   }
   // TODO
}
```

错误信息还可以作为异常[抛出](#抛出异常)，这两种方案在这里都是可行的。

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
func printIfError(err uint) {
   if (err == 0) return;
   printf("Error: %u\n", err);
}
```

可以在条件表达式前面加一个初始化语句：

```feng
func test(m Map`int,*Node`, k int) {
   if (var n,ok = m[k]; ok) { // 这里的n和ok变量只属于当前块
      printf("value of %d is: %s\n", k, n.value());
   }
   // printf("value of %d is: %s\n", k, n.value()); // 错误✖：外层不能使用
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

`if..else`可以作为[元组](#元组)使用，但是`else`分支不能省略了，且每个分支提供的元组长度必须一致：

```feng
func compare(a, b int) int {
    return if (a < b) -1 else 0;
}
func minMax(x,y int) (int,int) {
   return if (x < y) x,y else y,x;
}
```

#### switch语句

由`switch`开始的带有一个条件表达式，多个匹配规则，每个规则由`case`开始，其下有一组语句。  
匹配到的`case`规则其下的语句组会被执行，当执行结束后会跳出`switch`语句，不会继续下降，除非语句组最后一个是`fallthrough`语句。

```feng
func numberName(k int) {
    switch(k) {
    case 0: 
        println("zero");
    case 1: 
        println("one");
    case 2: 
        println("two");
    case 3:
        fallthrough;
    default:
        println("Error");
    }
}
```

和`if`类似可以在条件表达式前面加一个初始化语句：

```feng
func test(n Node) {
   switch(var v=n.value; v) {
   case 1:
      println("one");
   }
}
```

也可以作为[元组](#元组)使用，规则与条件语句一样：

### 循环语句

#### 条件循环语句

`for`后面的括号内是控制体，控制体可以且必须有一个控制条件表达式，也可以包括初始化和更新子语句；
之后是需要执行的语句或语句序列，称循环体。
当控制条件满足时重复执行循环体：

1. 控制条件是一个`bool`类型的条件表达式，当结果为`true`时才会执行循环体。
2. 循环体是一个语句，如果需要多个语句操作则需使用[块语句](#块语句)包起来。

简单的循环语句为括号内只有条件表达式：

```feng
func main() {
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
func main() {
    for (var i = 0; i < 100; i += 1) {
        println(i);
    }
}
```

#### 迭代循环

对于变量数组，可以用更简单的方式遍历所有元素：

```feng
func main() {
    var src []int = [0,1,2,3,4,5,6,7,8,9];
    for ( v : src )  // 只获取值
      handle(j);
    for ( i,v : src) //  同时获取索引和值
      println(i, v);
}
```

当然`continue`和`break`语句对迭代循环依然有效。

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
    #helper Iterator {
        cursor *Node`T`;
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

[赋值运算符](#赋值运算符)只能用于语句中，即：

```feng
func test() {
   var i = 0;
   i += 2;
}
```

### 赋值语句

#### 修改赋值

赋值语句的左边是操作数（指将要被修改值的对象），后边是由表达式列表组成的[元组](#元组)：

```feng
func test(x,y int, u *User, a []int) {
   x = 2;
   u.id = 1;
   a[0] = 8;
   x, y = 2, 4;
   u.id, x, y, a[0] = 1, 2, 4, 8;
}
```

1. 赋值语句并不是拆成多个语句独立执行的，而是先计算操作数的表达式，再计算表达式元组，再一一赋值。
2. 显然，不同类型的赋值可以放在一起。

#### 变量初始化

[变量声明语句](#变量声明语句)右边的初始化赋值是可选的，且也是先计算右边表达式元组，再赋值给左边变量：

```feng
func test() {
   var a,b,c = 1, "ggyy", 1.6;
}
```

### 复制语句

[赋值](#赋值语句)只能修改变量本身的值，对[引用类型变量](#引用类型变量)只能修改其指向。
而复制是针对引用类型变量的专用语法，作用就是复制其引用的实例内容。

可以从值类型复制：

```feng
func copy1(u *User, v User) {
    u := v;
}
```

或者从另一个引用复制：

```feng
func copy2(u *User, v *User) {
    u := v;
}
```

复制的时候要求原类型必须相同，如果是数组则按最小长度复制。

### 变量声明语句

声明一个或一组[变量](#变量)使用关键词`var`或`const`开头，后面紧跟变量的名称，然后是变量类型。

1. `var`声明一个普通变量。后面可以有初始化值。TODO：允许未初始化并置默认值？还是强制使用前必须赋值？
2. `const`用于定义不变的量，不能重新赋值，且必须在声明时初始化值。

```feng
func main() {
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
   // var a,b int = 1, "ggyy"; // 错误✖，必须拆成两个语句
}
```

### 异常语句

分为抛出和处理[异常](#异常)两种语句。

#### 抛出异常

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

抛出的异常的类型需要自己定义，在[异常](#异常)中详细说明。

#### 处理异常

异常处理语句分三个部分：

1. `try`部分：必须的部分，将需要处理的代码块包裹起来。
2. `catch`部分：可以有多个，分配匹配不同的异常类型。匹配到就执行对应的代码块，否则继续往后匹配。
   如果没有都未匹配成功则继续往外抛出。
3. `finally`部分：上面两部分无论什么情况，都必须执行这部分。
   如果第1部分有`return`语句，先执行`return`后的表达式，再执行`finally`部分，最后再正式返回。
   如果第2部分没有或者未捕获到异常，则先执行`finally`部分后继续抛出。

第2和3部分至少必须有一个。

完整的例子：

```feng
func calc() {
   try {
      step1();
      step2();
   } catch(e *NilPointerError) {
      println("捕获到了空指针");
   } catch(e *IllegalStateError | *IllegalArgumentError) {
      println("捕获到了状态错误或者参数错误");
   } finally {
      println("最终经过这里再往下执行");
   }
   return getResult();
}
```

没有`finally`部分，只有`catch`部分：

```feng
func calc() {
   try {
      step1();
   } catch(e *IllegalStateError) {
      println("捕获到了状态错误或者参数错误");
   }
   return getResult();
}
```

没有`catch`部分，只有`finally`部分：

```feng
func calc() {
   try {
      step1();
      step2();
      return getResult();
   } finally {
      println("最终经过这里再往下执行");
   }
}
```

`finally`可以用来释放外部资源，避免资源泄露。比如文件关闭：

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
   } finally {
      f.close();
   }
}
```

注意：`catch`匹配括号里的参数`e`是常量参数。

## 元组

元组是语言内部的特殊类型，由一组元素显示组成。不支持显示定义元组类型的变量。

### 用途

声明了多返回值函数或方法，如果是返回数组，那无法自动解构成多个变量，因此采用元组来处理。

```feng
func getValue(key int) (int, bool) {
   var node = get(key);
   if (node == nil) return 0, false;
   return node.value, true;
}
```

运行同时赋值给多个操作对象：

```feng
func test() {
   u.id, ok = 1, true;
}
```

在声明变量时也可以使用：

```feng
func test() {
   var id, ok = 1, true;
}
```

### 特殊元组

调用函数或方法返回的结构是一个元组，当然可以直接当做元组来使用。

```feng
func result(e int, r *Res) (int, *Res) {
   return e, r;
}
func success(r *Res) (int, *Res) {
   return result(0, r);
}
```

多返回值的函数或方法不能参与表达式计算，但是单返回值的函数可以（编译器应该自动拆开）：

```feng
func sin(x float64) float64 {    // 单返回值可以作为元组返回也可以参与表达式计算
   // TODO：……
}
func cos(x float64) float64 {
   return sqrt(1 - sin(x)^2); // 需要自动把元组拆开成单值
}
```

if元组和if语句类似，不同的是直接返回的是元组而不是语句：

```feng
func getValue(key int) (int, bool) {
   var node = get(key);
   return if (node == nil) 0, false else node.value, true;
}
```

以及与switch语句对应的switch元组：

```feng
func createIf(c int) (bool, *Object) {
   return switch(c) {
   case 0: true, new(Res);
   default: false, nil;
   };
}
```

不同类型元组可以嵌套组合：

```feng
func createIf(c int) (bool, *Object) {
   return if (c > 0) false, nil 
      else if (c < 0) true, new(Res)
      else true, new(Res, {code=1000});
}
```

## 变量

声明方式参考[变量声明语句](#变量声明语句)。

变量的声明方式有两种：可变的`var`和不可变的`const`，区别是后者在首次赋值后就不能再修改了。

### 变量值的类型

变量按值的类型分两种情况：值类型和引用类型。

#### 值类型变量

变量与实例是一体的，即变量的值就是类型定义的内容，一个重要的特性就是对其赋值能修改变量的值：

1. 基本类型的变量本身只是一个寄存器值，修改通常只需一个机器指令：
   ```feng
   var a int = 1; // 变量a赋值为字面量数值1，那a的值就是1
   var b int = a; // 变量b赋值为变量a，则将a的值复制给b
   b = 2; // a和b是两个不同变量，修改其中任何一个不会影响另一个
   ```
2. 派生类型通常会占用超过寄存器位宽的空间，所以实现上往往需要一组指令，将类型的字段数据全部复制：
   ```feng
   class Vector { var x,y,z float64; }
   var a Vector = { x=1.0, y=0, z=-1.0 };
   var b Vector = a; // 和基本类型一样，复制a的所有字段数据给b
   b.x += 2.0; // 同样修改b不影响a，a.x的值依然是'1.0'
   ```
3. [定长数组](#定长数组)赋值等效于遍历数组的所有元素进行赋值：
   ```feng
   var a [4]int = [1,2]; // 遍历每个元素初始化，没写出来的为默认值，int默认值为0
   var b [4]int;
   b = a; // 就是把a的数据复制给b
   // 等效于循环赋值
   for (var i = 0; i < a.size; i++) b[i] = a[i];
   ```
   比如派生类型的数组：
   ```feng
   var a [4]Vector = [{x=1.0}, {x=2.0}]; // 遍历每个元素初始化，没写出来的为默认值，Vector默认值的每个字段都是0
   var b [4]Vector;
   b = a; // 就是把a的数据复制给b
   // 也等效于循环赋值
   for (var i = 0; i < a.size; i++) 
       b[i] = a[i]; // 这里的赋值参考第2点
   ```
   元素是引用类型也一样的规则。

#### 引用类型变量

引用类型变量是与实例分离，即变量的值改变并不影响实例的内容，而是将变量指向另一个实例。

变量能引用实例受类型安全的约束：

1. [类](#类)和[接口](#接口)的引用有[多态](#多态)与[抽象](#抽象)的约束；
2. [接口](#接口)引用类的实例有抽象兼容的约束；

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

##### 强引用类型

强引用表示为`*`带类型符号，比如：`var aDev *Device;`声明了强引用变量`aDev`。
它可以指向一个类`Device`的实例，或者`Device`的[子类](#多态)的实例：

```feng
func test() {
    var b *Device = new(Device);    // 初始化指向一个新分配的Bus实例
    var a *Device = b;              // 将b引用的实例传递给a
    a.speed = 10;                   // a和b的修改都会更新同一个实例
    printf("speed=%d", b.speed);  // 打印：speed=10
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

##### 虚引用类型

自动内存管理在回收实例时不会被虚引用影响。虚引用不能直接引用一个实例。比起强引用，虚引用的用法有一些约束。

虚引用变量只能用`const`声明，且不能为空：

```feng
var gh Host;
func test() {
    const h1 &Host = gh;
    // var h2 &Host = gh; // ✖
    // const h3 &Host = nil; // ✖
}
```

数组不能被虚引用，且数组元素不能是虚引用。

虚引用变量的作用域必须在被引用的实例作用域内：

1. 值类型变量可以在作用域内被虚引用。
2. 常量引用的作用域内，其引用的实例可以被虚引用。虚引用本身也是常量引用。
3. 一个类实例在可以被虚引用的作用区间内：
    1. 它的值类型字段可以被虚引用。
    2. 它的常量字段引用的实例可以被虚引用。

全局变量可以被任意代码段虚引用：

```feng
var gDrv Driver;
const rDrv *Driver = new(Driver, {});
func use() {
    const d1 &Driver = gDrv;
    const d2 &Driver = rDrv;
}
```

本地变量需要在作用域内：

```feng
func sample1() {
    var drv Driver;
    const d1 &Driver = drv;
}
func sample2(drv *Driver) { // 参数都是常量
    const d1 &Driver = drv;
}
```

类的字段被虚引用：

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

**虚引用仅限为上面的场景才能使用**，例如函数返回值不能是虚引用类型的。

### 常量

上面讲述了变量的值，而常量的不可变就是指变量值不可变：

1. 值类型常量，其所有内容都不可变，对除了基本类型以外的类型：
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
3. 当内层声明同名的变量时（不要求同类型），外层的同名变量则被隐藏，不能使用。

```feng
func test() {
   var v = "Hello"; // 变量v的生命周期在当前函数内
   {
      var s = "Fèng!"; // 变量s的生命周期在当前块内
      printf("%s %s\n", v, s);   // 可使用外部声明的变量v
   }
   // printf("%s %s\n", v, s);  // 错误✖：不能使用内层块内的变量s
   {
      // printf("%s %s\n", v, s);  // 错误✖：不能使用另一个块内的变量s
   }
   {
      var v = "Dear Fèng"; // 内层重新声明同名的变量，外层的变量v就被隐藏了
      printf("%s\n", v); // 打印：Dear Fèng
   }
   // var v = "Fèng"; // 错误✖：不能重新声明
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

_这里定义声明周期为运行时，但如果是动态库，这里可能会有歧义，姑且定义动态库加载期间为运行时吧。_

## 字面量

### 整数字面量

### 实数字面量

### 布尔值字面量

`bool`的字面量只能是`true`或`false`。

### 空值字面量

除了基本类型、枚举以外的类型的变量都可以给赋`nil`，表示需要将变量所占的内存区清零。

1. 如果是引用类型则是指针清零，不指向任何实例。
2. 如果是值类型则会将变量所有的空间都清零。
3. 如果是数组，就把每个元素都清零。

```feng
func main() {
   var i [4]int = nil;           // 数组元素都为0
   var pi []int = nil;           // 空引用
   var a ram = nil;              // 空引用
   var b *Cat = nil;             // 空引用
   var c []*Cat = nil;           // 空引用
   var d [5]*Cat = nil;          // 数组元素都是空引用
   var e Cat = nil;              // 字段都清零
   var f [3]Cat = nil;           // 数组所有元素的所有字段都清零
   // var i int = nil;           // 错误✖：枚举值不能
   // var ts TaskState = nil;   // 错误✖：枚举值不能
}
```

### 字符串字面量

字符串并不是基本类型，编译器对字符串字面量进行编码。
字符串字面量即字符串常量，本身不能修改，所以只能用`rom`类型变量引用。
字符串常量不是在函数栈上分配的，而是一律放在常量区：

```feng
func moduleName() rom {
    var r rom = "test-module";
    return r; // 离开函数moduleName还是能使用
}
func test() {
    printf("module: %s\n", moduleName());
}
```

### 数组字面量

将数组元素列出来放在方括号中：`[1,2,3]`、`["Hello", "Good"]`等等。
数组元素类型为兼容所有元素的类型，如果没有可兼容的类型则不允许。

## 宏

宏是一种有特定格式的代码片段，这种特定格式不是随意的，而是由特定用途决定的。
特定用途就是指某种语言特性，比如当宏用于实现[自定义运算符](#自定义运算符)时，由运算本身设定了代码格式。
目前宏仅支持在类和接口里。

宏统一由`#`开头定义，主要格式有过程宏和类宏两种。

### 过程宏

过程宏类似一般过程（函数或方法），有名称、参数表和语句序列组成：

- 名称和其他名称互不干扰，可以与其他元素重名。
- 参数表和函数参数不同，而是相当于上下文的变量。
- 语句序列就是普通的语句序列，末尾可以有过可选的表达式。
- 宏不能被调用。

下面举自定义加法运算符的例子：

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

### 类宏

包含名称、字段表和过程宏组成，能保存中间状态。
比如派生类型的[迭代循环](#迭代循环)的实现。

## 异常

一个能被抛出的异常类需要定义`#error`宏`tracestack`，在这个宏里追踪并收集栈信息：

```feng
class Stack {
    var fn uint64;
    var line uint32;
}
export
class Error {
   var stacks List`Stack`;
   func tracestack(fn uint64, line uint32) {
      var s Stack = {fn=fn,line=line};
      stacks.add(s);
   }
   #error tracestack(fn uint64, line uint32) {
      tracestack(fn, line);
   }
}
```

## 属性
