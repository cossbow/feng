【Fèng】草稿
===================

# 想法

尝试Java、C、C#、Go、js等编程语言的特性拼凑成。

主要特性：

1. 约定所有语义内的操作都应该是内存安全的，不包括并发场景，就是需要自动内存管理：
   1. 指针只能通过`new`获取，并且释放（可能是延迟回收）时必须合指针归零同时存在。
   2. 指针不能通过运算生成，不能将任意整数转换成指针值。
    3. 强制类型检查：在遇到类型转换时必须检查是否允许转换，即使编译时无法分析的场景，在运行时也要做检查。
    4. C语言取地址操作可以对变量和实例的字段操作。指向栈地址的指针不能满足第1点，指向字段的指针需要。
2. 内存结构映射：C语言的类型支持非安全操作，可以直接转换成字节数组进行传输或者下盘等，实现一些协议时比较方便。
   而在Go和C#等中也有struct，但不能像C语言一样使用，因此在处理协议时就只能用位大量的运算进行操作了。
   如果把C语言的struct加上一些限制，也可以引入到这里面。
3. 面向对象：类的继承、多态，接口抽象。
   1. 单继承，只允许一个父类。类是对实例的分类，分类通常都是树形的，比如物种，单继承对现实世界进行建模更合适。
   2. 通过接口的设计来实现抽象，抽象类。~~没有抽象类。
4. 自动资源管理，类似C++析构函数，能在对象释放前做一些回收动作。
   - Java的NIO会分配堆外内存，但也是用GC管理的。Java的其他一些native包，比如Netty，对非GC内存需要手动增减引用计数；
   - Go是采用GC管理内存，后来sdk里引入了手动管理内存的arena包，~~又变成手动的了。
   - 所以这个特性能让自动内存管理在程序员的配合下释放外部资源和解循环引用，避免了纯手动操作。
5. 值类型变量，可以帮助优化内存分配的性能：
   1. 如果直接声明类型，就是值类型，在变量或字段定义的地方分配类型所需要的空间。
    2. 在变量或字段所在的地方分配内存。
    3. 值类型的赋值是拷贝整个类型的数据，因为两个值类型的变量或字段实际上是两个内存区域。
    4. 值类型不参与抽象为接口，需要底层做更多封装。C++里的抽象原本就是对指针类型作用的。
    5. 值类型在栈上时，变量生命周期自动管理；如果是全局变量，不需要释放；如果是嵌入到其他类的，生命周期和母体实例一样。
6. 基于包的代码管理。
7. 运算操作符自定义：简化了C++运算符重载（Overloading），不能覆盖默认的行为。
8. 虚引用：被引用的对象可以是值类型变量和引用类型的变量，限定为局部变量，包括参数。
9. 泛型：泛型参数可以加约束条件，类成员方法也可以带参数。
10. ^^

# 语法

## 包定义和导入

包名称与目录名称一致，无声明，且目录名称的组成与变量命名要求一致，要是目录名不合规就报错吧。

让一个符号在外部可见，需要使用`export`关键字修饰。比如在包`calc`里面有代码：

```feng
export
func sin(a, b int) int {
    return a + b;
}
```

在另一个包里使用关键字`import`导入包`calc`，就能使用这个包的函数：

```feng
import calc {sin};
func test() int {
   return sin(1);
}
```

导入可以用通配符`*`，表示导入下面所有（编译器应当检查冲突）：

```feng
import calc {*};
func test() int {
   return sin(1);
}
```

## 程序入口

像大多数语言那样，有一个入口函数名称为`main`，无参数无返回值，作为可自行程序的入口：

```feng
func main() {
    println("Hello Fèng!");
}
```

如果程序将编译成一个独立的可自行文件，则需要`main`函数，如果编译成library那就不需要。

## 输入输出

可以封装系统的输入输出，甚至可以直接封装C语言的标准库，语法不需要提供。

## 函数

函数用`func`关键字开头定义的，与C语言函数一样，区别是运行多个返回值。

定义`sum`函数，两个`int`参数，返回`int`类型值

```feng
func sum(a, b int) int {
    return a + b;
}
```

假设函数调用`printf`是开发包提供的打印到终端的函数：

```feng
func main() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

可以没有返回值的函数：

```feng
func print(a, b int) {
    println(a + b);
}
```

可以有一个或多个返回值：

```feng
func divAndMod(a, b int) (int, int) {
    return a / b, a % b;
}
func main() {
    var a, b = 1, 3;
    var s, d = divAndMod(a, b);
    printf("%d / %d = %d\n%d % %d = %d\n", a, b, s, a, b, d);
}
```

## 变量/常量

### 局部变量

定义一个变量/常量使用关键词`var`/`const`/`let`，后面紧跟变量的名称，然后是变量类型：

1. `var`定义一个变量。
2. `const`用于定义不变的量，不能重新赋值，且必须在声明时初始化值。
3. `let`用于定义引用名，作用写在后面[引用](#引用)里。

`var`/`const`变量使用方式：

```feng
func main() {
    var r int = 5;
    var g float64;
    var a float64 = 0;
    const pi float64 = 3.1415926;
    g = 2 * r * pi;
    a = r * r * pi;
}
```

### 全局变量

全局变量必须放在代码的最顶层，即在函数和类型定义的外面，可访问的作用域为当前包内：

```feng
var count int = 0;
var qps int = 0;
var avg float64 = 0.0;

func doCount() int {
   count+=1;
   return count;
}
```

可以使用`export`导出：

```feng
export const PI float = 3.1415926;
```

## 类与实例

### 定义类

类的成员分为字段和方法，可以继承父类的成员，并且可以在本类的方法中访问父类的成员。如果父类是从另一个包导入的，那么只能访问
`export`
修饰的成员。

类的定义示例：

```feng
class Node {
    const key int;
    var value int;
    func setValue(value int) int {
        var oldVal = this.value;
        this.value = value;
        return oldVal;
    }
}
```

### 操作实例

使用`new`语法可以创建一个实例，并传递一个引用类型的变量，通过变量使用实例，也可以传递给另一个引用类型的变量：

```feng
func main() {
    var n *Node = new(Node);  // 创建的实例传递给n
    var m *Node = n; // 通过赋值将n指向的对象传递给m
    m.setValue(123); // 通过m修改实例
    printf("n.value = %d.\n", n.value); // n和m指向同一个对象，所以打印结果为：n.value = 123.
}
```

### 继承与多态

先要有一个父类，并且有一个字段`name`和一个方法`eat`：

```feng
class Animal {
    var name rom;
    func eat(food rom) {
        printf("Animal %s eating %s\n", name, food);
    }
}
```

然后定义一个子类，就可以继承父类字段`name`，并覆盖父类的方法`eat`

```feng
class Cat : Animal {
    func eat(food rom) {
        printf("Cat %s eating %s.\n", age, name, food);
    }
}
```

父类的引用可以指向一个子类实例，这种情况下调用方法时，应该调用子类的方法：

```feng
func main() {
   var animal *Animal = new(Cat, {name: "Tom"});
   animal.eat("fish-meat"); // 将打印的是：Cat Tom eating fish-meat.
}
```

## 分支语句

和其他语言的定义一样，程序流程根据指定条件进行判断，决定执行哪一个操作。

### 条件语句

条件语句`if`紧跟带括号的表达式，表达式结果作为条件，必须是`bool`类型。

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

复杂一些的情况，`else`可以嵌套`if`，变成多分支：

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

_`if..else`可以作为条件表达式使用：_

```feng
func compare(a, b int) int {
    return if (a < b) -1 else 0;
}
```

### 开关语句

由关键字`switch`开始的，带有一个条件，多个匹配规则，每个规则由`case`开始，其下有一组语句。  
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

## 循环

还是和其他语言一样的定义，程序在指定条件满足时重复某一操作。有三种方式：

### 完整遍历

`for`关键字后面的括号里面的控制器，有初始化、条件和迭代三个部分：

```feng
func main() {
    for (var i = 0; i < 100; i += 1) {
        println(i);
    }
}
```

### 条件遍历

可以简化的循环控制器，只保留条件部分：

```feng
func main() {
    var i = 0;
    for ( i < 100 ) {
        println(i);
        i += 1;
    }
}
```

### 迭代遍历

变量数组

```feng
func main() {
    var src []int = [0,1,2,3,4,5,6,7,8,9];
    for ( v : src )  // 只获取值
      handle(j);
    for ( i,v : src) //  同时获取索引和值
      println(i, v);
}
```

实现迭代的类实例，这个还没想好怎么搞。不同于Java是用接口实现的，想设计成一种类似宏的东西。

### 异常exception

异常控制在许多语言都有，在有些场合使用很方便，比如资源打开和关闭。虽然有资源类，但这主要和内存管理一起的，如果在关闭资源操作中有耗时的，比如等待IO，就会严重影响性能，且可能无法预料影响的发生。

使用`throw`可以抛出一个对象，`try`中的代码块将尝试正常执行，任何一点有抛出，`catch`就能捕捉它并执行当前代码块，`finally`
中的代码块是`try`/`catch`结束之后的必经之路。
这里抛出的对象是任意一个类的引用类型。
TODO：这里再研究研究~^~

```feng
func step1() {
   throw new(Object);
}
class Exception {}
func step2() {
   throw new(Exception);
}
class Error {}
func step2() {
   throw new(Error);
}
func main() {
   try {
      step1();
      step2();
   } catch(Error e) {
      // TODO
   } catch(Exception e) {
      // TODO something else
   } finally {
      // TODO 清理一下
   }
}
```

# 概念

## 包

包是代码的基本管理单元，目录名即为包名，所以目录名的要求就是变量名的要求。
在包内部所有的内容都可见。  
包之间除了`export`标识的内容均不可见。
使用`import`显示导入某个包，可以显式使用这个包里`export`标识的内容。

### 基本用法

包`xxx.util`有代码：

```feng
class Node {
   var prev, next *Node;
   export var value int;
}
export class List {
   var head *Node;
   var size int;
   export func size() {
      return size;
   }
}
```

包`test`里使用的示例代码：

```feng
import xxx\util {List};
func testList() {
   // var node *Node = new(Node);  // 错误，不能显式使用
   var li *List = new(List);
   // var s int = li.size; // 还是错误， 不能显式使用
   var s int = li.size();
}
```

包名称与路径一一对应，在文件中不声明包名称。  
在项目跟目录下，包`com\jjj\base\util`对应的相对路径为`"com/jjj/base/util"`（假设在Linux下面）。所以同时要求目录名称的规则和变量名称一样。

如果需要继承的类在另一个包，父类应加上`export`，成员如果没有`export`也不能被显示的使用。

### 导入格式

声明导入包路径，在使用的符号名称前面需要加包前缀：

```feng
import com\cossbow\log;
func main() {
   log.println("Hello Feng!");
}
```

可以在包路径后加上具体的符号表，就能直接使用符号表中的：

```feng
import com\cossbow\log {println};
func main() {
   println("Hello Feng!");
}
```

符号表可以用通配符`*`，表示全部都使用此规则：

```feng
import com\cossbow\log {*};
func main() {
   println("Hello Feng!");
}
```

## 基本类型

基本类型定位为可以直接用寄存器存储的内置类型，目前的寄存器均支持`8/16/32/64`位的整数和浮点数

### 整数

有16个指明位宽的类型：有符号：`int8`/`int16`/`int32`/`int64`，无符号：`uint8`/`uint16`/`uint32`/`uint64`
，默认内置`int`/`uint`，根据平台类型选择位数。

#### 支持运算

整数支持算术运算、位运算和关系运算。

#### 类型转换

不同的类型需要显示的转换，转换的规则都是算术转换：

* 如果绝对值大小、符号都兼容，不会改变原数值的，直接转换。
* 如果不兼容的：
    * 如果是有符号数转换为无符号数，需要用`abs`操作符转换。`abs`只处理符号，转变成同位宽的无符号数。
    * 左值位宽小于右值，根据上限溢出（不同于内存溢出，而是判断源值大小如果是超过目标类型最大值/最小值就赋值为最大值/最小值）。

比如，`uint16`最大值是小于`int32`最大值的，可以直接转换：

```feng
func main() {
   var a uint16 = 1;
   var b int32 = int32(a);
}
```

而`int32`虽然最大值小于`uint32`，但有符号要处理，需要`abs`操作符：

```feng
func main() {
   var a int32 = 1;
   var b uint32 = abs(a);
}
```

位宽不兼容时，取最大值填充：

```feng
func main() {
   var a uint16 = 0x1FF;
   var b uint8 = uint8(a); // b = 0xFF
   var c int16 = 0x1FF;
   var d uint8 = uint8(abs(c)); // b = 0xFF，abs(c)结果应为uint16类型
}
```

如果需要对内存区进行拷贝，需用`copy`操作符：左边位宽不小于右边时，直接copy；小于时自动按内存截断高位。

```feng
func main() {
   var a int16 = 0x1;
   var b int32 = copy(a);
}
```

### 浮点数

有`float32`和`float64`两种类型，是由【IEEE 754】标准指定的32位和64位浮点数。

#### 支持运算

整数支持算术运算（除了取模运算）和关系运算（只有大于和小于，由于精度问题就把等于去掉了）。

#### 类型转换

转换规则也是算术转换：

* 符号不变。
* 当`float64`转换成`float32`时，和整数一样根据上限溢出。
* 转换成整数时，只能先转换成有符号的，也是根据上限溢出。
* 从整数转换过来时，虽然浮点数取值范围更大，但会丢失精度。

浮点数也可以用`copy`操作符拷内存值。

### 布尔值

类型符号为`bool`，且只有两种取值：`true`和`false`。在内存中占1个字节，位宽是8，但只使用最低位，其余位必须清零。

* 支持逻辑运算，关系运算（但只支持相等和不等），及位运算（与、或、异或三种）。
* 关系运算的结果一定是布尔值。
* 不支持和整数、浮点数的转换。
* 特别的，`if`、`for`中的条件表达式返回值必须是`bool`类型的。

## 运算符

### 基本性质

#### 优先级

下面特别展示优先级（自上而下优先级递减）：

| 等级 | 运算符集            | 备注      |
|----|-----------------|---------|
| 1  | +,-,!           |         |
| 2  | ^               | 优先级比较特殊 |
| 3  | *,/,%           |         |
| 4  | +,-             |         |
| 5  | <<,>>           |         |
| 6  | &               |         |
| 7  | ~               |         |
| 8  | \|              |         |
| 9  | <,<=,==,!=,>,>= |         |
| 10 | &&              |         |
| 11 | \|\|            |         |

等级2的运算只有`^`即幂运算，它的优先级高于其他二元运算，与等级1的一元运算的优先级为：

1. 幂运算在右边时高于一元运算
2. 幂运算在左边时低于一元运算

```feng
func test(a,b int) {
   var x int;
   x = -a^b;   // 等效于：-(a^b)
   x = a^-b;   // 等效于：a^(-b)
}
```

#### 结合性

### 分类

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

| 运算符  | 描述            |
|------|---------------|
| !    | 按位取反          |
| <<   | 左位移，高位丢弃，低位补零 |
| \>\> | 右位移，高位补零，低位丢弃 |
| &    | 按位与           |
| ~    | 按位异或          |
| \|   | 按位或           |

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

因为`bool`类型只有最低位有效，其他位清零，因此位运算的`&`、`~`、`|`也有效。
此外`&`与`&&`、`|`与`||`的区别是：后者有短路效果，即：当左边的值决定了运算结果时，右边没必要参与计算了，那右边的表达式也不会被执行。

#### 其他运算符

##### new

用于动态创建类、内存的实例。

##### copy

用于直接拷贝内存值。

##### assert

用于判断是否能转换类型的语法。

类的对象可以赋值给接口或父类指针，反过来则需要判断其类型：

```feng
func valid(w Writer) {
   var f = w?(*File); // 如果不匹配则报类型转换错误
   var f, ok = w?(*File); // 如果不匹配则ok为false
   f.close(); // 如果匹配上，转换的结构放在变量f上
}
```

这个语法只用于类的断言，其他地方的转换比如`struct`、`union`及`ram`、`rom`显然不需要。

### 实现运算符

类和枚举默认不支持运算符，但可以实现自定义运算符。

自定义的运算功能代码段与方法跟函数都不一样，是类似宏一样的语句序列，但又各自包含特定的自动功能，每一种运算符都有固定名称和原型及操作数列表：

* 自定义以`operator`开始而不是`func`，每种运算符有固定名称。
* 名称前面的是操作数列表，操作数如果是当前类型；后面的是参数列表，是其他类型，具体因不同运算符而已。
* 不能继承和抽象，但是可以在泛型类中定义，操作数也支持泛型。
* 不能调用其他运算符。
* 名称和其他方法名称可以相同。

#### 表达式运算符

基本类型的默认4类运算符，只支持算术运算符和关系运算符的自定义。自定义某一种运算符之后，在表达式里有这种类型的数据时就可以正常编译了。

运算符的操作数和结果的类型要特别说明：

| 运算类型       | 右边类型 | 结果类型 |
|------------|------|------|
| 算术运算       | 同左边  | 同左边  |
| 关系运算       | 同左边  | 布尔值  |
| 位运算：&,\|,~ | 同左边  | 同左边  |
| 位运算：<<,>>  | 无要求  | 同左边  |

举个栗子，给下面的链表类添加`+`运算，其作用是左右拼接两个链表为一个新链表：

```feng
class List`T` {
   var head *Node`T`;
   var size int;
   func addAll(o List`T`) {
      // TODO：实现
   }
   // +运算符名称为add，操作数类型一定是当前类，左操作数名称lhs和右操作数名称rhs
   // result是自动创建的初始化的同类型对象
   operator add(lhs, rhs, result) {
      result.size = lhs.size + rhs.size;
      result.addAll(lhs);
      result.addAll(rhs);
   }
}
```

下面是使用的代码：

```feng
func main() {
   var a *List`int` = newList();
   a.add(1);
   a.add(2);
   var b *List`int` = newList();
   a.add(8);
   a.add(9);
   var c = a + b; // 新的链表c的内容为：1,2,8,9
}
```

#### 控制型运算符

##### 索引符

数组可以进行索引运算，当然实际上有读和写两种操作。放在右边既是读，左边既是写。

比如自定义一个字典类`Map`，功能是提供自定义类型的Key和Value的索引。用法示例：

```feng
class Map {
   // TODO：此处省略其他代码
   operator indexedGet(lhs Map, key int) (int64, bool) { // 
      return get(key);
   }
   operator indexedSet(lhs Map, key int, value int64) {
      set(id, value);
   }
}
func main() {
   var m Map;
   m[100] = 159;
   var v int64 = m[100]; // indexedGet的第二个参数可以直接忽略
   printf("m[100] = %d\n", v);
}
```

##### 遍历操作

循环语句遍历形式默认只对数组及其引用有效，这里做成操作符的形式，但是由三个`operator`
组成：`forInitialize`、`forCheck`、`forIterate`。

```feng
class List<T> {
   var head,tail *Node<T>;
   // TODO：此处省略其他代码
}
```

### 赋值运算符

赋值运算符相当于运算的一种简写，即左操作数自己与右操作数参与对应的运算后再赋值给左操作数。因此也要求赋值运算的左右操作数是同类型的。
也就是说，赋值运算对应的运算是操作数与结果都是类型相同的。那也可以约定，支持了自定义运算符的类型，也可以采用相应的赋值运算符。

如果实现了运算符`+`，作用是按左右顺序拼接字符串，那就可以使用`+=`运算符，其作用是：右边字符串拼接到左边的字符串变量的右边，再将结果传递给左边变量。

## 类

定义关键字`class`，就像字面意思一样，用于给程序设计中的所有概念进行分类，目前给出的支持有下面两点：

1. 支持继承：子类可以继承父类的全部特性，比如字段、方法及运算符。
2. 支持多态：子类可以覆盖继承过来的方法。

### 字段

字段用`const`和`var`定义，和变量定义的概念一样：`const`定义的字段是不允许修改的，必须要求初始化指定。

```feng
class Cat {
   const id int;
   var name rom;
}
```

在定义值类型时可以对类进行初始化：

```feng
func main() {
   var c1 Cat = {id: 1001, name: "Tomcat"};
   var c2 Cat = {id: 1001};
   // var c3 Cat = {name: "Tomcat"}; // 未显示初始化id，不允许
   // var c4 Cat; // 未初始化id，也不行
   var c5 Cat = nil; // 初始化为0
}
```

`new`创建实例时初始化表达式放在里面：

```feng
func main() {
   var node *Cat = new(Cat, {id: 1001, name: "Tomcat"});
}
```

如果类里面没有`const`的字段则不强制要求初始化表达式。

```feng
class Cat {
   var id int;
   var name rom;
}
func main() {
   var c1 Cat;
}
```

如果没有指定初始化的字段，编译器必须一律归零（`nil`），也就是内存意义上的零，参考C语言的`memset(p, 0, len)`
。比如上面示例中c1的内存区域都应该归零。

### 方法

方法定义与函数的差别就是放在类定义内部，上下文可以通过`this`关键字使用当前实例。

#### 覆盖方法

覆盖的要求方法的名称相同并且原型一致。

```feng
class Bird {
   func fly() {
      printf("Bird fly.\n");
   }
}
class Dove : Bird {
   func fly() {
      printf("Dove fly.\n");
   }
}
```

当子类覆盖了父类的方法后，如果通过值类型使用实例，对使用哪一个方法实现，并没有歧义，而是会进入当前类实现的方法中：

```feng
func main() {
   var d Dove;
   d.fly();
}
```

当通过父类引用类型的变量使用子类实例时，应该使用实例具体类型中实现的方法。

#### this引用

这个引用相当于C++的`this`指针，所以对值类型变量的实例，`this`如果传递出去会导致安全问题：

```feng
class Cat {
   var name rom;
   func name(name rom) *Cat {
      this.name = name;
      return this;
   }
}
```

这种场景就是安全的：

```feng
func main() {
   var c *Cat = new(Cat);
   var c1 = c.name("Tom");
}
```

如果是这种场景就有问题了：

```feng
var myCat *Cat = nil
func foo() {
   var c1 Cat = nil;
   var myCat = c1.name("Tom");
}
func main() {
   foo();
   myCat = nil;   // 错误的释放
}
```

当`foo`函数调用结束后，`myCat`指向了一个历史的栈地址，显然是无效的引用，使用时自动内存管理进行释放时等都是危险的。因此编译器应当根据实际调用的场合分析并提示错误用法。

这需要编译器检查出这样的错误并停止编译，主要检查出两个条件：

1. 值类型变量或字段在调用方法A。
2. A方法内传递了`this`。

### 根类

用来解决先有鸡还是先有蛋的问题的，所有的分类总要有个根类，因此内置了`Object`类作为这个根类。
所有类在不声明继承时默认继承`Object`类，当然可以创建一个`Object`的实例。

```feng
class Object {}
```

`Object`类没有任何成员，其实也不需要。

## 枚举

枚举类型的取值是有限的，可全部列举出来，所以在定义时需要将所有值列举出来。_相当于所有枚举值都是全局的const变量_。

```feng
enum TaskStatus {WAIT, RUN, DONE, ;}
```

枚举类型内置了特殊字段：

* 使用`ordinal`字段可以获取列举的序数，为整数字面量，从0开始按序列递增
* 使用`name`字段可以获取其定义的名称，为字符串字面量

```feng
func main() {
   var a int = TaskStatus.RUN;   // a初始化为整数：1
   var b rom = TaskStatus.DONE;  // b初始化为字符串："DONE"
}
```

枚举类型的变量是引用类型，不同于类的实例，无需用`#`来表示：

```feng
func main() {
   var s TaskStatus = TaskStatus.WAIT;
   var t TaskStatus = s;   // s和t指向同一个值
   var eq bool = s == t;   // eq结果为true
}
```

使用类型名可以获取枚举值序列，这个序列不是数组，是只读的，就像字面量一样，可以使用`for`语法遍历

```feng
func main() {
   for ( s : TaskStatus )
      printf("name: %s, ordinal: %d \n", s.name, s.ordinal);
}
```

枚举类型可以有成员字段和方法，字段的初始化必须是常量表达式；可以声明接口实现，和类一样。代码示例：

```feng
export
enum TaskStatus (Coding) {
   WAIT(1),
   RUN({code: 2}),
   DONE(4),
   ;
   const code int;
   export getCode() int {
      return code;
   }
}
```

**_[感觉这样太复杂了，是不是应该简化成最简单的形式？](#枚举)_**

## 抽象与接口

接口定位为一组方法原型的集合。因此除了能声明一组方法原型外，什么都不能加。

### 接口使用

接口的设计用于衔接实例的管理者和使用者。

### 组合接口

接口可以进行组合：

```feng
interface Reader {
   read(b ram) (int, Error);
}
interface Writer {
   write(b rom, off, len int) (int, Error);
}
interface File {
   Reader;
   Writer;
   query() *FileInfo;
}
```

组合后要检查方法冲突，比如名称相同但原型不同就不允许。

### 抽象对象

只允许类引用实例和枚举值被抽象。

1. 可以不用声明要实现的接口，编译器根据实际实现方法集合判断是否能抽象。
2. 可声明要实现的接口，声明多个用逗号隔开。声明后将强制检查实现。

使用示例：

```feng
interface Task {
   run();
}
class SubTask {
   func run() {
      println("Run Sub Task!");
   }
}
class MyTask (Task) { // 声明实现接口Task
   func run() {
      println("Run My Task!");
   }
}
```

已声明的可以被查询到接口：

```feng
func query(r Object) {
   if (impl(r, Task)) {
      // TODO：做点什么
   }
}
```

## 结构类型

### 结构类型定义

结构类型是指`struct`和`union`两种自定义类型，设计与C语言基本一致。
字段只能为基本类型和结构类型的值类型，总之不能出现与指针相关的元素。

```feng
struct A {
    a1 int;
    a2 : 8 int;
    a3 xxx.Base;
}
union B {
    b1 int;
    b2 : 8 int;
    b3 xxx.Base;
}
func main() {
   var a A = {a1: 10, a2: 2, a3: {}};
   var b B = {b2: 4}; // 显然union在初始化时只能写一个字段
}
```

字段的内存布局完全与定义的顺序一致，内存按C语言的规范对齐。

### 结构类型与内存

结构类型没有实例的概念，使用`new`语法创建的不是实例，而是内存类型引用的映射。

```feng
struct Foo {
   score int;
}
var foo *Foo = new(Foo, {score: 0});
func main() {
   foo.score+=1;
   println(foo.score);
}
```

基本类型也允许这样使用，不过基本类型没有字段，如果要修改其值需要用`copy`方法。

在大小不超过的情况下，可以将一个陌生的内存映射到结构类型上使用，实现类似C指针类型转换的效果。

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

这里的是真的基本类型和结构类型的数组，对下面这些的容易被看错，注意它们的元素不是基本类型和结构类型而是引用：

```feng
var gList [][]Foo;   // 这个数组的元素类型是[]Foo，上文提到这时引用类型
var gUser []*Foo;    // 这个数组类型元素是*Foo
```

## 数组

### 数组定义

上面看到许多数组，但这里才定义。
数组是用于存储连续重复元素的类型：

1. 元素类型可以是任意类型，当然包括数组类型和数组引用类型
2. 支持获取元素个数的查询，内置字段`length`，类型为`int`。
3. 数组可以支持一些内置函数进行快捷操作

### 值类型

声明一个数组有两个方式：

声明时如果指定了大小，是值类型变量：

```feng
var a [16]int;
var b [16]int = [0,1,2,3,4,5,6,7,8,9]; // 初始化的元素不能超过声明长度
var cs [16]Cat;   // 元素为自定义类Cat的数组
var crs [16]*Cat; // 元素为引用的数组
```

### 数组引用

如果不指定大小则是引用类型变量——数组引用，可指向一个使用`new`语法分配的数组：

```feng
var c []int;
var d []int = new([16]int, [1,2,3,4]);
```

### 类的数组

类的字段类型可以为数组或数组引用

```feng
class Shop {
   var state [8]State;
   var goods []Goods;
}
```

### 结构类型的数组

结构类型的字段类型不能为引用，因此不制定大小的数组有另外的语义——弹性数组。弹性数组要求：

1. 并且这种字段必须放在最末尾。
2. 同时有这种字段的结构类型在嵌入时也必须放末尾。

```feng
struct Message {
   var magic [4]uint8;
   var hash [4]uint64;
   var list [s]int;
}
```

当然，基本类型的数组和结构类型一样。

### 内置方法

关于数组内置的快捷方法用于处理一些常见操作（先打过表格）：

| 方法名    | 原型 | 功能                    |
|--------|----|-----------------------|
| shift  |    | 左移或右移元素，后面的元素依次放前面空位上 |
| subset |    | 创建一个子集，引用原数组连续的一部分    |
| copy   |    | 复制数组的一部分或全部           |
|        |    |                       |

## 内存

### 类型定义

用于对一段连续内存空间的管理和使用，作用类似C语言的char数组或者Java的byte数组等，当然要保证是内存安全的，必须进程访问越界检查。

支持内置的字段`size`获取长度（字节数），类型为`int`。

### 只读与可写

内存有两种类型：`ram`和`rom`。 他们的区别是：

* ram允许读写操作，而rom是只读的。
* ram引用可以传递给rom引用的变量，不能反过来。

```feng
func convert(a ram) rom {
    return a;
}
```

可以创建一个`ram`创；显然建`rom`没什么用，因此就不支持了。用法示例：

```feng
func main() {
   var buf ram = new(ram, 256, true);  // 创建一个256字节大小的内存实例
}
```

### 内置方法

内置一些实用字段和方法（。。。）：

|   |   |   |
|---|---|---|
|   |   |   |
|   |   |   |
|   |   |   |

## 引用

### 变量声明

引用类型可以用`const`定义一个常量引用，`var`定义一个变量引用，而`let`定义的是不是变量或常量，而只是一个具名引用。
`let`定义的引用，在传递对象之后和重新定义时，声明周期就结束了。也就是说`let`引用不能与其他引用共享对象。

```feng
func main() {
   let a *Object = new(Object);
   var b *Object = a;   // 从这一行开始，a的声明周期结束
   // var c *Object = a;   // 取消这一行注释后编译器会报找不到符号的错误。
   var a int = 0; // 然后可以重新声明一个名称为a的变量
}
```

### 引用实例

使用`new`创建的实例（包括类和内存的实例），必须用对应类型的引用变量或字段引用，否则该实例是无用的，需立即清理或稍后清理。

```feng
func main() {
   var a *Car = new(Car);  // 将a指向一个新建的实例，此时这个实例也只被a引用
   a = nil;   // a指向nil之后，不指向任何实例。这里的新建的实例没有被任何变量引用，就是无用的。
}
```

### 资源类

#### 资源类的定义

当一个类添加了release方法时，这个类就被标记为资源类，这时候实例只能通过`new`创建。

可以用于自动释放其他资源。比如C语言实现的缓冲区是需要手动分配和释放的，这样就可以自动释放缓冲区了：

```feng
class CBuffer {
   const buf uint64;
   func release() {
      cFree(buf); // 假设可以这样调C语言实现的接口
   }
}
```

#### 自动内存

引用计数是常用的自动内存管理的方式之一，另一种是GC。
类的实例被即将被清理之前，会检查该类是否是资源类，如果有，会调用release方法。

假如实例管理是使用的引用计数，那release方法在下面的时机被调用：

```C
if (object_ref_dec(obj) == 1) {
   obj->release();
   free(obj);
}
```

_如果使用引用计数，这个机制能帮助程序员解决循环引用。_
显然，结构类型字段和结构类型数组的元素、基本类型数组的元素都不会是其引用类型，自然不会形成循环引用了，所以完全可以采用引用计数来管理内存类型。

## 函数

## 变量

## 属性

## 别名

## 字面量

### 整数

### 实数

### 布尔值

### 字符串

字符串并不是基本类型，编译器对字符串的字面量支持处理，应当根据指定编码转换为rom。

### 空值

除了基本类型以外的类型的变量都可以给赋`nil`，表示需要将变量清零。

```feng
func main() {
   var a ram = nil;     // 空引用
   var b Cat = nil;     // c的字段都清零
   var c *Cat = nil;   // 空引用
   var d TaskStatus = nil;    // 空引用
}
```
