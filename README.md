【Fèng】语言描述
===================

考虑如果要改进C++语言，或者是改进版的C++，应该是优先考虑内存安全问题。
这个方案是从C语言出发的，当然不是语法上和C语言一样，而是从一样简单概念的基础上，
同时引入面向对象、内层安全约束和自动内存机制，

按上面的思路设计了Fèng这个编程语言，但目前仅写了[grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4)。

# 特性简介

引入了面向对象之后，对象之间互相引用很可能会形成复杂的对象图，对于什么时候在哪儿释放对象是个复杂的问题。
而且对象的字段可能是指针，类型转换时需考虑类型的兼容性。所以主要设计是在内存安全基础上引入面向对象特性。

主要的特性如下：

1. 内存安全机制：
    1. 自动内存管理：指针只能通过`new`获取，并且释放（可能是延迟回收）时必须与指针清零同时存在。
       这不仅接近内存泄露问题，还有效解决Use-After-Free问题。
    2. 指针安全使用：指针不能通过运算生成，不能将任意整数转换成指针值，这也包括对指针进行自增自减。
    3. 强制类型检查：在遇到类型转换时必须检查是否允许转换，即使编译时无法分析时，也要在运行时做检查。
    4. 取地址操作：这就不支持了。_不过在后面有虚引用来代替取地址操作_。
2. 面向对象：支持继承、多态，和接口抽象。
3. 内存结构：struct和union是另一种类型，和class不同，字段类型不能是指针。
4. release：类似C++的RAII，就是在对象释放前调用的函数。可用回收C语言分配的资源，或处理循环引用等。
5. module：代码管理单元，内部分多个文件，但符号引用不受限制，而module之间需要导出导入才能引用。

下面是次要的特性：

6. 值类型变量，在变量或字段定义的地方分配类型所需要的空间：值类型在栈上时，变量生命周期自动管理；
   如果是全局变量，不需要释放；如果是嵌入到其他类的，生命周期和母体实例一样。
7. 运算操作符自定义：简化了C++运算符重载（Overloading），不能覆盖内置的实现。
8. 虚引用：被引用的对象可以是值类型变量和引用类型的变量，限定为本地变量，包括参数。
9. 泛型：泛型参数可以加约束条件，类成员方法也可以带参数。
10. 异常机制：异常机制可以方便的处理设计之外的错误，避免使用goto处理。

# 语法

## 语句与表达式

语句是程序序列的基本指令单元，除了块语句和控制语句外需要以`;`结尾：

比如赋值语句，等号分割成左右两个部分，左边是需要被赋值的操作数，右边是计算值的表达式：
`var s = a + b;`。

再比如调用语句也是常用的，仅包含一个call表达式：
`start();`。

语句右边的表达式是计算的主体，由多个运算根据优先级组合成：`a + (b + c) * d - sin(e)`。

## 函数和方法

函数和方法都是将一组语句封装成一个独立的计算体，可以在其他地方被重复使用。

下面举函数的例子来说明格式。

定义`sum`函数，两个`int`参数，返回`int`类型值

```feng
func sum(a, b int) int {
    return a + b;
}
```

假设函数调用`printf`是module`fmt`提供的打印到终端的函数：

```feng
import fmt.{printf};
func main() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

可以没有返回值的函数：

```feng
import fmt.{printf};
func print(a, b int) {
    printf("%d\n", a + b);
}
```

可以有一个或多个返回值：

```feng
import fmt.{printf};
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
这些类型之间有很大差异，分别在后面描述。

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

声明导入`com.cossbow.log`中的全部符号：

```feng
import com.cossbow.log.*;
func main() {
   println(string("Hello Feng!"));
}
```

可以在module路径后加上具体的符号列表，就能直接使用符号列表中的：

```feng
import com.cossbow.log.{println};
func main() {
   println(string("Hello Feng!"));
}
```

符号列表同时导入多个，用`,`隔开：

```feng
import com.cossbow.log.{println, sprintf};
func main() {
    var m Sring = sprintf("Hello Feng!");
   println(m);
}
```

## 基本类型

基本类型是语言内置的，分为整数、浮点数和布尔值三种，而整数分有符号和无符号。

### 整数类型

内置的全部整数类型如下：

- 有符号：`int8`/`int16`/`int32`/`int64`/`int`
- 无符号：`uint8`/`uint16`/`uint32`/`uint64`/`uint`

后缀的数字表示其位数，无位数后缀的是由编译的目标平台决定位数。

支持[算术运算](#算术运算符)、[位运算](#位运算符)和[关系运算](#关系运算符)。

不同整数类型之间必须显示转换：

```feng
func main() {
   var a uint16 = 123;
   var b int32 = int32(a); // 将uint16转换为int32
}
```

整数类型显示转换，相当于从低位到高位按位复制，因此：

1. 如果位宽从大的转到小的会被截断，造成整数溢出。
2. 相同位数的无符号数转为有符号数时，如果最高位为`1`则会导致整数变为负数；反过来负数的符号也会转换成数值，整数值会增大。

### 浮点数类型

由IEEE 754标准定义的浮点数包括单精度数`float32`和双精度数`float64`两个类型。
均支持基本的[算术运算](#算术运算符)和[关系运算](#关系运算符)。

### 布尔类型

类型符号为`bool`，且只有两种取值：`true`和`false`，主要特性：

* 支持逻辑运算，关系运算（但只支持相等和不等），及位运算（与、或、异或三种）。
* 关系运算的结果一定是布尔值。
* 不支持和整数、浮点数的互相转换。
* `if`、`for`中的条件表达式返回值必须是`bool`类型的。

实际机器上是没有布尔类型的，只是当做一种整数类型处理的。且布尔类型应该只需使用1位即可，
比如最低位表示，那这时候位运算的结果就和逻辑运算对应上（参考[逻辑运算符](#逻辑运算符)）：
`&&`与`&`对应，`||`与`|`对应，而逻辑非与按位取反的结果对应且符号都为`!`，异或运算也符合预期了。

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

如果[布尔类型](#布尔类型)只有最低位有效，其他位忽略，则位运算的`&`、`~`、`|`对布尔值运算的结构依然是有效的布尔值，
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
   func test(o Object) {
      var f *File = o?(*File);   // 转换成File类的引用
      var w Writer = o?(Writer);  // 转换成Writer接口
      o?(Writer).write("Hello!"); // 在表达式中使用
   }
   ```
2. 用两个变量去接收的话，就会返回[元组](#元组)，不能参与表达式计算。
   第一个值是类型引用参考第1条；第二个为`bool`值，表示是否能进行转换。这时不会抛出[异常](#异常)。
   ```feng
   func valid(w Writer) {
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

和Java、C++等一样，使用关键字`class`定义：

1. 支持继承：子类可以继承父类的全部特性，比如字段、方法及运算符。
2. 支持多态：子类可以覆盖继承过来的方法。

### 字段

在自定义的类中可以定义任意类型的字段， 用`const`和`var`定义，和变量定义的概念一样：

```feng
class Cat {
   const id int;
   var name rom;
}
```

在定义值类型时可以对类进行初始化，`const`定义的字段是不允许修改的，必须要求初始化指定。

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

如果没有指定初始化的字段，编译器必须一律清零（`nil`）。
比如上面示例中c1的内存区域都应该清零。TODO：默认初始化？还是强制显示初始化？

### 方法

方法的名称在同一个类里是唯一的，与函数名称类似，当然也不与函数冲突。

方法定义与函数的差别就是放在类定义内部可以通过`this`关键字使用当前实例的成员，`this`自然就表示访问当前方法的实例本身。

示例：

```feng
class Task {
   var state int;
   func setState(state int) int {
      // 参数名或全局变量名与字段冲突了，可以用this指针来访问
      var old = this.state;
      this.state = state;
      return old;
   }
   func getState() int {
      return state;  // 上下文没有冲突时，访问成员可以省略this
   }
}
```

上面举字段的例子，对成员方法也一样处理。

#### 访问成员

#### this指针

相当于C++的`this`指针，所以对值类型变量的实例，`this`如果传递出去会影响安全性：

```feng
class Cat {
   var name rom;
   func name(name rom) *Cat {
      this.name = name;
      return this;
   }
}
```

在下面这种场景就是安全的：

```feng
func main() {
   var c *Cat = new(Cat);
   var c1 = c.name("Tom");
}
```

如果是这种场景就有问题了：

```feng
var myCat *Cat = nil;
func foo() {
   var c1 Cat = nil;
   var myCat = c1.name("Tom");
}
func main() {
   foo();
   myCat.name("Jerry"); // 这下myCat指向无效内存了
}
```

当`foo`函数调用结束后，`myCat`指向了一个历史的栈地址，显然是无效的引用，使用时自动内存管理进行释放时等都是危险的。
因此编译器应当根据实际调用的场合分析并提示错误用法。

这需要编译器检查出这样的错误并停止编译，主要检查出两个条件：

1. 值类型变量或字段在调用方法A。
2. A方法内传递了`this`给类的字段或返回值。

### 继承

#### 类的实例

使用`new`语法可以创建一个实例，并传递一个引用类型的变量，通过变量使用实例，也可以传递给另一个引用类型的变量：

```feng
func main() {
    var n *Node = new(Node);  // 创建的实例传递给n
    var m *Node = n; // 通过赋值将n指向的对象传递给m
    m.setValue(123); // 通过m修改实例
    printf("n.value = %d.\n", n.value); // n和m指向同一个对象，所以打印结果为：n.value = 123.
}
```

#### 继承与多态

先要有一个父类，并且有一个字段`name`和一个方法`eat`：

```feng
class Animal {
    var name rom;
    func eat(food rom) {
        printf("Animal %s eating %s\n", name, food);
    }
}
```

然后定义一个子类，就可以继承父类字段`name`，也可以覆盖父类的方法`eat`：

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
   var animal *Animal = new(Cat, {name="Tom"});
   animal.eat("fish-meat"); // 将打印的是：Cat Tom eating fish-meat.
}
```

但是子类定义的字段不能与继承来的字段重名。

### root类

语言内置了`Object`类，是所有类的root类，自定义类没有声明继承父类时默认继承`Object`类。`Object`类没有任何成员，其实也不需要。

```feng
class Object {}
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

#### 资源类的定义

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

如果使用引用计数，这个机制能辅助程序员解决循环引用。
但是外部资源的关闭往往是耗时操作，如果放在这里处理可能对性能的影响难以预料。

## 接口

接口定位为一组方法原型的集合。因此除了能声明一组方法原型外，什么都不能加。

### 接口使用

接口的设计主要用于实例的管理者和使用者代码，管理者掌管细节，而对使用者屏蔽细节。

接口的本质是一些方法原型的集合。

```feng
interface Cache`K, V` {
   Get(K) (V, bool);
   Set(K,V) bool;
   Size() int;
}
```

某个类的方法原型与接口里的全部方法兼容（符合类型安全），那这个类就实现了此方法。

```feng
class Auto {}
class Bus:Auto {}
interface Factory {
   make() *Auto;
}
class Ford (Factory) {
   func make() *Bus { // 返回类型兼容
      return new(Bus);
   }
}
func test() {
   var fac Factory = new(Ford); // use
}
```

### 组合接口

接口可以进行组合：

1. 组合成的接口包含各个组件的所有方法原型
2. 组合的接口可以赋值给组件接口，因为实现了组合的接口当然也实现了组件接口

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
func use(file File) Write {
   return file;
}
```

方法原型需要检查名称冲突，不允许同名的方法存在

### 抽象对象

只允许类引用实例和枚举值被抽象。

1. 可以不用声明要实现的接口，在传递的地方，编译器是可以判断是否已实现的。
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

支持断言：

```feng
func runTask(r Object) {
   if (var t, ok = r?(Task); ok) {
      t.run();
   }
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

函数定义和类方法的格式完全一样，都使用`func`关键字定义，区别是方法里面默认带有类的上下文，所以函数的特性方法都有。

一般函数的形式为：
`func` 函数名 `(` 参数表 `)` 返回值表 `{` 函数体 `}`

比如：

```feng
func run() {}
func start() { run(); }
func exec(a []Sting) *Error {
   return nil;
}
```

### 函数名

同一个module内的函数名称是唯一的，但与类的方法不冲突。

### 参数表

参数是参数名和类型组成，且都是常量（省略了`const`），作用域在当前函数内。

下面的例子定义了类型为`Queue`的`l`和类型为`int`的`a`两个参数：

```feng
func send(l Queue, a int) {
   l.push(a);
}
```

一个类型可以带多个参数名，比如定义两个类型为`int`的参数`a`和`b`：

```feng
func add(a, b int) int {
    reutrn a + b;
}
```

### 多值返回

允许有多个返回值，这个在某些情况比较好用，比如：

```feng
interface Map`K,V` {
   keyExists(K) bool;
   get0(K) V;
   get1(K) (V,bool);
}
func test(a Map`int,int`) {
   // get返回值是int默认值是0，但当key不存在时，默认值0就无法区分了了
   var v0 = a.get0(i);
   // 需要提供另一个方法来判断
   var exists0 = a.keyExists(i);
   // 如果能返回另一个结果来表示key是否存在，就不用去做两次查询了
   var v1,exists1 = a.get1(i);
}
```

当然实际情况不仅仅这里需要，有需要返回结果和错误码的场景里也非常有用，只是这个争论比较激烈。

有了多返回值就不需要传入一个可修改的参数指针来实现目的。
虽然可以通过传入的[引用](#引用类型变量)来修改外部的实例，但实际大家更倾向于无副作用（不修改外部实例）。

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

#### 返回值表

函数或方法的返回值必须和声明的类型兼容：

```feng
func call() (int,Complex,rom) {
   return 2, {real=1,imag=2}, "ggyy";
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

变量能引用实例受类型安全的约束，一般情况不能引用不同类型实例，除了：

1. [类](#类)的引用受到继承关系的约束；
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
它可以指向一个类`Device`的实例，或者`Device`[兼容类](#继承与多态)的实例：

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
