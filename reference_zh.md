**【Fèng】编程语言**

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
func test() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

可以没有返回值的函数：

```feng
import fmt *;
func test(a, b int) {
    printf("%d\n", a + b);
}
```

有返回值的函数：

```feng
import fmt *;
func div(a, b int) int {
    return a / b;
}
func test() {
    var a, b = 1, 3;
    var s = div(a, b);
    printf("%d / %d = %d\n", a, b, s);
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

## 模块

作为代码组织单元，同一个目录下的文件都属于一个模块，且模块名与目录名相同，因此无需在文件里声明。
不支持循环依赖，即依赖关系只能是与向无环图。

## main函数

main函数是可执行程序的入口函数，这个和其他语言一致。
入口函数没有返回值，只有一个参数且参数类型必须是`[&!#][*!#]byte`；

```feng
func main(args [&!#][*!#]byte) {
	hello();
}
```

有main函数的模块会编译成一个可执行文件，并且不能作为库被其他模块导入；没有的则被作为库使用，即允许被导入使用。

# 概念

下面将详细描述各个语法元素的定义及用法。

## 模块

模块是代码的基本管理单元。

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

声明导入`com$cossbow$fmt`模块：

```feng
import com$cossbow$fmt;
func test() {
   fmt$println(string("Hello Fèng!"));
}
```

可以设置module别名：

```feng
import com$cossbow$fmt ccfmt;
func test() {
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
| 2  | 断言,索引,引用字段,函数调用,块 |       |
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
| &    | 位与   |
| ~    | 位异或  |
| \|   | 位或   |

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

### 其他表达式

#### 索引表达式

仅数组默认支持这种运算符。索引运算符是由中括号组成，括号中是获取索引值的表达式。
其用法有两种：

1. 索引表达式在右边是读操作，即获取索引对应元素的值作为运算结果。
   左边一个变量或者在表达式中使用时，仅返回元素值，如果元素不存在则终止运行并抛出[异常](#异常-_未完成_)。
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
   数组的容量是创建之后就不能变了，所以索引越界自然也要终止运行并抛出[异常](#异常-_未完成_)。

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
对于数组可以传递[数组表达式](#数组表达式)，而对类和结构体可以传递[字段表达式](#字段表达式)。

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

特殊的字面量表达式，专门用于初始化可定义字段的派生类型，比如结构类型和类：

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

初始化的其他细节请参考[类](#类)和[结构类型](#结构类型)。

#### 断言表达式

用于判断是否能类的引用是否能进行转换的语法，比如类的实例的引用传递可以给接口或父类指针，反过来则需要判断其类型。

返回的是对应类型的引用，如果类型不能匹配则返回`nil`，因此可能引发空指针：

```feng
func test(o *Object) {
   var f *File = o?(*File);   // 转换成File类的引用
   var w Writer = o?(*Writer);  // 转换成Writer接口
   o?(*Writer).write("Hello!"); // 在表达式中使用，有空指针风险
   if (var w Writer = o?(*Writer); w != nil) {
      // 这样避免空指针
   }
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
`bool`类型是特殊的基本类型，编译器可以采用高效的方式存储，因此不能使用。

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

#### 自定义特殊运算 _[未完成]_

##### 自定义索引运算

默认只有数组支持的[索引表达式](#索引表达式)也可以自定义。
由于索引运算符分有读和写两种操作，因此分成`indexGet`和`indexSet`两个过程宏。

比如自定义一个字典类`Map`，功能是提供派生类型的Key和Value的索引。用法示例：

```feng
class Map {
   // 索引读
   macro operator indexGet(key, operand, exists) {
      var n = getNode(key);
      operand, exists = if (n != nil) n.value, true else nil, nil;
   }
   // 索引写
   macro operator indexSet(key int, value String) {
      set(key, value);
   }
}
func test() {
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

[变量](#变量)的类型定义基本都适用于类的字段，除了：

1. 字段不能定义为[虚引用类型](#虚引用类型)，但可以定义为[弱引用类型](#弱引用类型)。
2. 字段只能用`const`和`var`定义。

```feng
class Cat {
   const id int;
   var name *rom;
   var mothr,father *Cat;
   var children []*Cat;
   // var who Cat; // ✖
}
```

类的字段的定义不要求顺序，和实际内存布局中的位置不需要一一对应。
不同于[结构类型](#结构类型)的字段。

#### 实例初始化

在实例化时，`const`字段则必须初始化指定，`var`字段则可选初始化。
比如上面的`Cat`类，`id`必须指定初始化值，`name`则不强制：

```feng
func test() {
   var c1 Cat = {id=1001};
   var c2 Cat = {id=1001, name="Tom"};
   // 下面是错误用法
   // var c3 Cat = {name="Tom"};
   // var c4 Cat;
   // var c5 Cat = {};
}
```

同样通过`new`动态实例化也是一样：

```feng
func test() {
   var c1 *Cat = new(Cat, {id=1001, name="Tom"});
   // 下面是错误用法
   // var c2 *Cat = new(Cat);
   // var c3 *Cat = new(Cat, {name="Tom"});
}
```

显然如果类里面没有`const`的字段则不强制要求初始化。

```feng
class Mouse {
   var id int;
   var name *rom;
}
func test() {
   var m1 Mouse;
   var m2 *Mouse = new(Cat);
}
```

如果没有初始化，或者初始化中没有指定的字段，一律置为默认状态：对应内存全`0`，引用类型则是`nil`值。

副作用：一个导出类的，但它有未导出`const`字段，在其他模块就无法实例化该类。
比如下面的`Dog`类就只能在当前模块实例化：

```feng
export
class Dog {
    const id int;
    var name *rom;
}
export
func newDog(id int) *Dog {
    return new(Dog, {id=id});
}
export
func dog(id int, name *rom) Dog {
    return {id=id, name=name};
}
```

#### 弱引用类型

字段特有的一种的引用类型，表示方法为：`~` 类型名称。
这种类型的引用不影响实例释放，且所引用的实例在释放后将自动置空（`nil`）。
在使用引用计数管理内存时，循环引用是个逻辑问题，但可以通过弱引用手动解决这个问题：

1. 弱引用不影响实例释放这个原则，那就是说不会导致计数+1，因此计数会归零。
2. 归零后实例释放了，将弱引用字段置空，这是内存安全的保证。

例如，将双向链表`Node`向前引用的字段`prev`定义为弱引用，就不会发生计数无法归零的问题：

```feng
class Node {
    var key String;
    var next *Node;
    var prev ~Node;
}
```

### 方法

方法与[函数](#函数)基本相同，区别是：

1. 必须通过类实例来调用。
2. 在方法内部能使用当前实例的类成员。
3. 方法名称作为唯一ID，在当前类的方法集中是唯一的，包括继承来的方法，但子类会覆盖同名的父类方法。

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
func sample1() {
   var task Task;
   task.start();
   printf("task state '%s'\n", task.state.name);   // 打印：task state: 'RUN'
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

`this`能传递给[虚引用类型](#虚引用类型)的变量，但如果强引用调用方法时可传递给强引用，
如果值类型调用时可赋值给值变量。例如：

```feng
class Foo {
   var name String;
   func aaa() {
      var x &Cat = this;
   }
   func bbb() {
      var x *Cat = this;
   }
   func ccc() {
      var x Cat = this;
   }
}
func use1() {
    var f Foo;
    f.aaa();
    // f.bbb(); // ✖
    f.ccc();
}
func use2(f *Foo) {
    f.aaa();
    f.bbb();
    // f.ccc(); // ✖
}
func use3(f &Foo) {
    f.aaa();
    // f.bbb(); // ✖
    // f.ccc(); // ✖
}
```

`this`可以放在返回值的地方，但也是不表示某种类型，而是当前实例本身。
当然能立即调用方法和引用字段，也可以赋值给与调用者同类型的变量：

```feng
class Car {
   var speed int;
   func forward() this {}
   func stop() this {}
   func backward() this {}
}
func sample1(c Car) {
   var speed = c.forward().stop().backward().speed;   // 链式调用
   var c2 Car = c.forward();
}
func sample2(c *Car) {
    var c2 *Car = c.forward();
}
func sample2(c &Car) {
    var c2 &Car = c.forward();
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
    var name *rom;
    func eat(food *rom) {
        printf("Animal %s eating %s\n", name, food);
    }
}
```

然后定义一个子类`Cat`，继承了父类字段`name`，下面实现一个与父类的方法`eat`同名同原型的方法：

```feng
class Cat : Animal {
    func eat(food *rom) {
        printf("Cat %s eating %s.\n", age, name, food);
    }
}
```

允许`Animal`的引用指向一个子类实例，通过父类引用调用`eat`方法时，允许时实际会调用子类的`eat`方法：

```feng
func test() {
   var animal *Animal = new(Cat, {name="Tom"});
   animal.eat("fish-meat"); // 将打印的是：Cat Tom eating fish-meat.
}
```

通过这个例子可以看到，方法`eat`在继承之后可以允许有多个实现，父类引用的不同子类均会指向子类实现的方法。
**子类在重实现父类方法时要求原型必须一致**。

支持[断言表达式](#断言表达式)来判断子类型：

```feng
func test(animal *Animal) {
    var cat, ok = animal?(*Cat);
    if (ok) cat.eat("mouse");
}
func test() {
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
func test() {
    asyncRun(new(MyTask));      // 打印：Run my task!
    asyncRun(new(YourTask));    // 打印：Run your task!
}
```

当然也支持[断言表达式](#断言表达式)判断类型。

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

### final类

如果希望定义的类仅用于保存数据，或者有简单的逻辑，而不希望用于复杂的继承和接口设计，可以给类加上final。
这个类就不能被继承，也不会有基类（不是Object的子类），不能抽象接口（应该可以，但是未实现）。

```feng
class User final {
   var id int;
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

有些外部资源的关闭往往是耗时操作，比如文件，如果放在这里处理可能对性能的影响难以预料，而且还需要处理IO错误或异常，
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
   read(b [*]byte) (int, Error);
}
interface Writer {
   write(b [*#]byte, off, len int) (int, Error);
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

枚举类型的值的个数是有限的，且必须在定义时把全部值都列举出来：

```feng
enum TaskState {WAIT, RUN, DONE,}   // 注意结尾必须有个逗号“,”
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
   var n [*#]byte = s2.name;                // n初始化为rom引用，内容为字符串"DONE"
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
func test() {
   for ( s : TaskState )
      printf("name: %s, id: %d \n", s.name, s.id);
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

位域（bit-field）是字段实际使用的位宽，只能用于基本类型的字段。
位域取值为该字段类型的位宽范围，放在在字段名称后面。
例如设置`code`的位域为`6`（`type`未设置）：

```feng
struct Request {
    type, code:6 int;
    data [56]uint8;
}
```

比较特殊的是，联合体在初始化时只能指定其中一个字段：

```feng
union Foo {
   tag int;
   fly uint8;
}
var foo Foo = {tag=1};
// var foo Foo = {tag=1,fly=2}; // 错误×
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
    var bar [*]int64;
}
```

注意：[结构类型](#结构类型)的字段类型不能为[引用](#引用类型变量)，必须指定长度。

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
   var a1 [*]int; // 可映射
   var a2 [*][2]int; // 可映射
   var a3 [*][3][4]int; // 可映射
   
   var a4 [*]*int; // 不可映射
   var a4 [*][*]int; // 不可映射
   var a4 [*][5]*int; // 不可映射
   var a4 [*][6][*]int; // 不可映射
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

### 函数原型

函数原型是变量类型的一种，函数的定义去掉函数体就是原型：`func` 函数名 `(` 参数表 `)` 返回表。
这种类型的[变量](#函数原型变量)要么为空，要么指向与原型兼容的函数。
例如：

```feng
func add(a, b int) int { return a + b; }
func sub(a, b int) int { return a - b; }
func mul(a, b int) int { return a * b; }
func div(a, b int) int { return a / b; }

func Calc(a, b int) int;
func test(c Calc) {
    printf("%d\n", c(rand(), rand()));
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

原型变量并非引用类型，但可以为空（即`nil`），也可以加非空前缀标记（`!`）来表示不能为空，这点与引用的非空规则一样：

```feng
func use1(a !func()) {
   var c1 func() = a;
   var c2 !func() = a;
   // var c3 !func() = c1; // 错误×：不能反向传递
   if (c1 != nil) {
      var c3 !func() = c1; // 显示判断空之后才能反传
   }
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

#### 变量初始化

[变量声明语句](#变量声明语句)右边的初始化赋值是可选的，支持定义多个变量并初始化：

```feng
func test() {
   var a,b,c = 1, "ggyy", 1.6;
}
```

### 变量声明语句

声明一个或一组[变量](#变量)使用关键词`var`或`const`开头，后面紧跟变量的名称，然后是变量类型。

1. `var`声明一个普通变量。后面可以有初始化值。TODO：允许未初始化并置默认值？还是强制使用前必须赋值？
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
   // var a,b int = 1, "ggyy"; // 错误✖，必须拆成两个语句
}
```

### 异常语句

分为抛出和处理[异常](#异常-_未完成_)两种语句。

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

抛出的异常的类型需要自己定义，在[异常](#异常-_未完成_)中详细说明。

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

## 变量

声明方式参考[变量声明语句](#变量声明语句)。

变量的声明方式有两种：可变的`var`和不可变的`const`，区别是后者在首次赋值后就不能再修改了。

### 变量值的类型

变量的类型分三种情况：值类型、引用类型、枚举和函数原型。

#### 值类型变量

变量与实例一体，变量的值就是实例本身，赋值相当于复制实例的数据：

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

##### 非空引用

引用默认是可空的（即`nil`），可以标注为非空（加`!`号），两种只能单向传递：非空 → 可空。
如需反向传递，必须显示对变量进行判断非空（只支持本地变量，不支持字段）：

```feng
func f(a *!int, b *int) {
   var x *int = a;
   // var y *!int = b; // 错误✖：不能直接传递
   if (b != nil) {
      var y *!int = b; // 必须显式判断空，在非空的分支内传递
   }
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

虚引用只能是本地变量或参数。仅有下面几种情况能传递给虚引用：

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
3. 当内层声明同名的变量时（不要求同类型），外层的同名变量则被遮住，不能使用。

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
      var v = "Dear Fèng"; // 内层重新声明同名的变量，外层的变量v就被遮住了
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

字符串并不是基本类型，编译器对字符串字面量进行编码。
字符串字面量即字符串常量，本身不能修改，所以只能用unmodifiable变量引用。
字符串常量不是在函数栈上分配的，而是一律放在常量区：

```feng
func moduleName() [*#]byte {
    var r [*#]byte = "test-module";
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
   box.set(new[15]int);
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

## 异常 _[未完成]_

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
   macro error tracestack(fn uint64, line uint32) {
      tracestack(fn, line);
   }
}
```
