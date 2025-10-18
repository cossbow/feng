【Fèng】语言描述
===================

考虑如果要改进C++语言，或者是改进版的C++，应该是优先考虑内存安全问题。
这个方案是从C语言出发的，当然不是语法上和C语言一样，而是从一样简单概念的基础上，
同时引入面向对象、内层安全约束和自动内存机制，

按上面的思路设计了Fèng这个编程语言，但目前仅写了[grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4)。

# 特性

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

假设函数调用`printf`是`fmt`提供的打印到终端的函数：

```feng
import fmt;
func main() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

可以没有返回值的函数：

```feng
import fmt;
func print(a, b int) {
    printf("%d\n", a + b);
}
```

可以有一个或多个返回值：

```feng
import fmt;
func divAndMod(a, b int) (int, int) {
    return a / b, a % b;
}
func main() {
    var a, b = 1, 3;
    var s, d = divAndMod(a, b);
    printf("%d / %d = %d\n%d % %d = %d\n", a, b, s, a, b, d);
}
```

## 自定义类型

提供自定义类型机制，这些自定义类型分为：
[类](#类)与[接口](#抽象与接口)、[结构](#结构类型)、[枚举](#枚举)和[属性](#属性)。
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
   例如在Linux下，module`com\jjj\base\util`对应的相对路径为`com/jjj/base/util`。

### 导出符号

比如当前module为`xxx\util`，其中的符号`Node.value`字段、`List`类及`List.size`方法导出给外部使用：

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

声明导入`com\cossbow\log`中的全部符号：

```feng
import com\cossbow\log {*};
func main() {
   println(string("Hello Feng!"));
}
```

可以在module路径后加上具体的符号列表，就能直接使用符号列表中的：

```feng
import com\cossbow\log {println};
func main() {
   println(string("Hello Feng!"));
}
```

符号列表同时导入多个，用`,`隔开：

```feng
import com\cossbow\log {println, sprintf};
func main() {
    var m Sring = sprintf("Hello Feng!");
   println(m);
}
```

## 基本类型

基本类型定位为可以直接用寄存器存储的内置类型，目前的寄存器均支持`8/16/32/64`位的整数和浮点数。

### 整数类型

有8个标明位宽的类型：

- 有符号：`int8`/`int16`/`int32`/`int64`
- 无符号：`uint8`/`uint16`/`uint32`/`uint64`

和两个未标明的类型`int`/`uint`，其位数由编译的目标平台决定。

默认支持[算术运算](#算术运算符)、[位运算](#位运算符)和[关系运算](#关系运算符)。

```feng
func main() {
   var a uint16 = 123;
   var b int32 = int32(a);
}
```

如果位宽从大的转到小的，超出会造成整数溢出。

### 浮点数类型

浮点数只有`float32`和`float64`两种，支持[算术运算](#算术运算符)和[关系运算](#关系运算符)。

### 布尔值类型

类型符号为`bool`，且只有两种取值：`true`和`false`。在内存中占1个字节，位宽是8，但只使用最低位，其余位必须清零。

* 支持逻辑运算，关系运算（但只支持相等和不等），及位运算（与、或、异或三种）。
* 关系运算的结果一定是布尔值。
* 不支持和整数、浮点数的转换。
* 特别的，`if`、`for`中的条件表达式返回值必须是`bool`类型的。

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

因为`bool`类型只有最低位有效，其他位清零，因此位运算的`&`、`~`、`|`对`bool`值运算的结构依然是`bool`值。
运算结果`&`与`&&`一致、`|`与`||`一致，但是`&&`和`||`两个运算会短路：
当左边的值决定了运算结果时，右边的表达式就不会被执行了。

### 其他运算符

#### 索引运算符

仅数组默认支持这种运算符。索引运算符是由中括号组成，括号中是获取索引值的表达式：

```feng
func test(arr [16]int) {
   var a int = arr[2]; // 读取索引为2的元素值并赋值给新变量a
   printf("%d\n", arr[5]); // 读取数组的索引为5的元素值并传给printf函数
   arr[9] = a + 10; // 赋值：修改索引为9的元素值
}
```

上面例子中展示了两种使用方式，在赋值的左边为写操作，否则为读操作。

#### new运算符

用于动态创建类、memory的实例。初始化表达式是可选的（传`nil`就表示清零）。

1. 创建类实例时，如果没有带初始化表达式，就把其内存空间清零。
2. 创建数组实例，类型参数中必须指定需要分配的数组长度，是否自动清零需要看元素类型。自动清零的元素类型包括：类、引用、接口、函数。
3. 创建memory实例

#### 断言运算符

用于判断是否能类的引用是否能进行转换的语法，比如类的实例的引用传递可以给接口或父类指针，反过来则需要判断其类型。
有两种用法：

1. 用一个变量去接收结果，运算表达式返回的是对应类型的引用，这种情况下如果不能转换则抛出[错误](#错误)。同时可以参与表达式计算。
   ```feng
   func test(o Object) {
      var f = o?(*File);   // 转换成File类的引用
      var w - o?(Writer);  // 转换成Writer接口
      o?(Writer).write("Hello!"); // 在表达式中使用
   }
   ```
2. 用两个变量去接收的话，就会返回[元组](#元组)，不能参与表达式计算。
   第一个值是类型引用参考第1条；第二个为`bool`值，表示是否能进行转换。这时不会抛出错误。
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

自定义类型默认不支持的运算，可支持一部分运算的自定义实现。

自定义的运算功能代码段与方法跟函数都不一样，而是由operator[宏](#宏)实现的。
每一种运算符都有固定名称和原型及操作数列表：

* 每种运算符有固定名称。
* 具体因不同运算符定义操作数（就是宏参数）。
* 可以在[泛型](#泛型)类中定义。
* 名称和其他方法名称可以相同。

#### 自定义表达式运算符

基本类型的默认4类运算符，只支持算术运算符和关系运算符的自定义。自定义某一种运算符之后，在表达式里有这种类型的数据时就可以正常编译了。

运算符的操作数和结果的类型要特别说明：

| 运算类型       | 右边类型 | 结果类型 |
|------------|------|------|
| 算术运算       | 同左边  | 同左边  |
| 关系运算       | 同左边  | 布尔值  |
| 位运算：&,\|,~ | 同左边  | 同左边  |

举个复数的栗子：

```feng
class Complex {
   var real,imag float64;
   // +运算符名称为add，操作数类型一定是当前类，左操作数名称lhs和右操作数名称rhs
   // result是自动创建的初始化的同类型对象
   #operator add(lhs, rhs, result) {
      result.real = lhs.real + rhs.real;
      result.imag = lhs.imag + rhs.imag;
   }
}
```

如果是泛型类：

```feng
// 假设R已经实现了运算符+
class Complex`R` {
   var real,imag R;
   #operator add(lhs, rhs, result) {
      result.real = lhs.real + rhs.real;
      result.imag = lhs.imag + rhs.imag;
   }
}
```

[泛型](#泛型)还未想好，尤其是是约束条件，grammar中只支持名称约束，但是宏的名称容易和其他名称混淆，……

**每个运算是有特定名称、特殊机制的宏函数，应该列出来。**

TODO：二元运算符表……

#### 自定义控制运算符

##### 自定义索引

默认的索引只有数组支持，分有读和写两种操作，放在右边既是读，左边既是写。

比如自定义一个字典类`Map`，功能是提供自定义类型的Key和Value的索引。用法示例：

```feng
class Map {
   // 索引读，运行时不存在则报错
   #operator indexGet(key int, lhs String) {
      var n = getNode(key);
      lhs = n.value;
   }
   // 索引读，带检查功能
   #operator indexTryGet(key int, lhs String, exists bool) {
      var n = getNode(key);
      lhs, exists = if (n != nil) n.value, true else nil, nil;
   }
   // 索引写
   #operator indexSet(key int, rhs String) {
      set(id, rhs);
   }
}
func main() {
   var m Map;
   m[100] = 159;
   var v int64 = m[100]; // 对应使用的是indexGet
   printf("m[100] = %s\n", v);
}
```

##### 自定义遍历

实现迭代是通过名为`Iterator`的helper宏实现的，但考虑循环是很常用的语法，所以利用宏直接由编译器展开。
宏的字段不限制，包含4个方法`initializer`、`condition`、`updater`、`get`

| 方法          | 作用     | 参数                   |
|-------------|--------|----------------------|
| initializer | 初始化迭代器 | 无                    |
| condition   | 循环条件   | 无                    |
| updater     | 更新迭代器  | 无                    |
| get         | 获取值    | 不限制，但设置多少个，使用时就接收多少个 |

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
        initializer() {
            cursor = head;
        }
        condition() {
            cursor != nil
        }
        updater() {
            cursor = cursor.next;
        }
        get(value) {
            value = cursor.value;
        }
    }
}
func test(src List`Team`) {
   for (i, t : src) {
      // t is const int
   }
}
```

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

如果没有指定初始化的字段，编译器必须一律清零（`nil`），也就是内存意义上的零，参考C语言的`memset(p, 0, len)`。
比如上面示例中c1的内存区域都应该清零。TODO：默认初始化？还是强制显示初始化？

### 方法

方法定义与函数的差别就是放在类定义内部，上下文可以通过`this`关键字使用当前实例的成员，`this`自然就表示访问当前方法的实例本身。

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

## 枚举

枚举类型的取值是有限的，可全部列举出来，所以在定义时需要将所有值列举出来。

```feng
enum TaskStatus {WAIT, RUN, DONE, ;}
```

枚举类型内置了特殊属性，这些属性在编译时就已经确定：

* `ordinal`是枚举定义的序数，从0开始按序列递增。
* `name`是定义的名称，是一个字符串字面量。
* `value`是枚举的值，默认等于`ordinal`或上一个值递增1。可以自定义，但必须为整数的常量表达式

```feng
enum TaskStatus {WAIT, RUN, DONE, ;}   // 未设置value，就等于ordinal
enum BillStatus {WAIT, PAID=4, SEND, DONE, ;} // 这里WAIT=0，SEND=5，DONE=6，……
func main() {
   var a int = TaskStatus.RUN;   // a初始化为整数：1
   var b rom = TaskStatus.DONE;  // b初始化为字符串："DONE"，b的类型不能是ram
}
```

使用类型名可以获取枚举值序列，这个序列不是数组，是只读的，就像字面量一样，可以使用`for`遍历：

```feng
func main() {
   for ( s : TaskStatus )
      printf("name: %s, ordinal: %d \n", s.name, s.ordinal);
}
```

下面这样可以获取一个初始化的[数组](#固定长度的数组)：

```feng
var statuses [16]TaskStatus = TaskStatus; // 指明长度
var statuses = TaskStatus; // 不指明就等于枚举数量大小
```

## 抽象与接口

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

## 结构类型

### 结构类型定义

结构类型是指`struct`和`union`两种自定义类型，设计与C语言基本一致。
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

当然编译器自然不会搞错。

## 数组

上面看到许多数组，但这里才定义。
数组是用于存储连续重复元素的类型：

1. 元素类型可以是任意类型，当然包括数组类型和数组引用类型。
2. 支持获取元素个数的查询，内置字段`size`，类型为`int`。
3. 数组可以支持一些方法函数进行快捷操作。
4. 数组支持[索引运算](#索引运算符)。

### 固定长度的数组

声明时如果指定了大小，是[值类型变量](#值类型变量)，且不同长度的数组不能赋值：

```feng
var a [16]int; // 未初始化时元素值是元素类型的默认值
var b [16]int = [0,1,2,3,4,5,6,7,8,9]; // 初始化的元素不能超过声明长度
// var c [8]int = b; // 错误✖：长度不同
var cs [16]Cat;   // 元素为自定义类Cat的数组
var crs [16]*Cat; // 元素为引用的数组
```

### 不定长度的数组

如果不指定大小则是[引用类型变量](#引用类型变量)，叫数组引用，可指向任意长度的数组实例，这里数组实例是通过`new`分配的。示例代码：

```feng
var c []int = new([16]int, nil);
var d []int = new([32]int, [1,2,3,4]);
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

关于数组内置的快捷方法用于处理一些常见操作（先打个表格）：

| 方法名   | 原型 | 功能                    |
|-------|----|-----------------------|
| shift |    | 左移或右移元素，后面的元素依次放前面空位上 |
| range |    | 创建一个子集，引用原数组连续的一部分    |
|       |    |                       |

## memory

### memory类型

用于对一段连续内存空间的管理和使用，作用类似C语言的char数组或者Java的byte数组等，`buffer`。
当然要保证是内存安全的，必须进行访问越界检查。

支持内置的字段`size`获取长度（字节数），类型为`int`。

### 只读与可写

memory设计有两种子类型：`ram`和`rom`：

* `ram`允许读写操作，而`rom`是只读的。
* `ram`变量可以将实例传递给`rom`变量，不能反过来。

```feng
func convert(a ram) rom {
    return a;
}
```

不能直接创建`ram`或`rom`实例，而是创建一个基本类型或结构类型（及其数组）的实例后传递给`ram`或`rom`：

```feng
func main() {
   // 创建一个256字节大小的memory实例，并初始化清零
   var buf1 ram = new([256]uint8, nil);
   // 创建一个256字节大小的memory实例，不初始化
   var buf2 ram = new([256]uint8);
}
```

### 内置方法

内置一些实用字段和方法（还没想好）：

|   |   |   |
|---|---|---|
|   |   |   |
|   |   |   |
|   |   |   |

## 引用

### 声明引用变量

引用类型可以用`const`/`var`定义。`const`的引用是指变量本身不可变，而非引用的实例。

```feng
class Task {
   var status int;
}
func main() {
   const t *Task = new(Task);
   // t = nil; // 错误用法
   t.status = 1; // Task实例并不属于变量t，只是被t引用了而已
}
```

### 引用实例

使用`new`创建的实例（包括类、memory及数组的实例），必须用对应类型的引用变量或字段引用，否则该实例是无用的，需立即清理或稍后清理。

```feng
func main() {
   var a *Car = new(Car);  // 将a指向一个新建的实例，此时这个实例也只被a引用
   a = nil;   // a指向nil之后，不指向任何实例。这里的新建的实例没有被任何变量引用，就是无用的。
}
```

### 资源类

#### 资源类的定义

当一个类添加了release方法时，这个类就被标记为资源类，release方法会在这个类的实例释放前调用。

以引用计数为例，release方法将在下面的时机被调用：

```C
if (object_ref_dec(obj) == 1) {
   obj->release();
   // TODO：details...
   free(obj);
}
```

GC的释放过程比较复杂，当然也是在最后回收对象之前调用release的。

这个特性可以用于自动释放其他资源。比如C语言实现的缓冲区的释放：

```feng
class CBuffer {
   const buf uint64; // 假设这个字段保存的是buf指针值
   func release() {
      cFree(buf); // 假设可以这样调C语言的释放函数free
   }
}
```

如果使用引用计数，这个机制能辅助程序员解决循环引用。
但是网络链接和文件等资源的关闭往往是耗时操作，如果放在这里处理可能对性能的影响难以预料。

## 函数

函数定义和类方法的格式完全一样，都使用`func`关键字定义，区别是方法里面默认带有类的上下文，所以函数的特性方法都有。

一般函数的形式为：
`func` 函数名 `(` 参数表 `)` 返回值表 `{` 函数体 `}`

比如：

```feng
func run() {}
func start() { run(); }
func exec(a []Sting) Error {
   return nil;
}
```

### 函数名

函数名

### 参数表

参数是参数名和类型的组合，参数对函数内代码来说是[本地变量](#本地变量)，作用域在当前函数内：

```feng
func send(l Queue, a int) {
   l.push(a);
}
```

除了类型是[虚引用](#虚引用类型)的参数是常量以外，其他都是[变量](#变量)。

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

当然实际情况不仅仅这里需要，有需要返回结果和错误码的场景里也非常有用（争议较大~）。
这样就避免了传入一个可修改的参数来实现目的（这在C语言里很常见，C#也提供了out参数）。

### 函数体

函数内部是由语句序列组成的。对于声明了返回列表的函数，在每个分支的最后必须是`return`语句。

## 语句



### 块语句

块语句是由`{`与`}`括起来的语句序列组成的，块内上下文会嵌套，内声明的[本地变量](#本地变量)不能在外部使用：

```feng
func test() {
   println("block 1");
   ｛
      println("block 2");
      ｛
         println("block 3");
         // 嵌套没有限制
      ｝
   ｝
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

当控制条件满足时重复执行循环体：

1. 控制条件是一个`bool`类型的条件表达式，当结果为`true`时才会执行循环体。
2. 循环体是一个语句，如果需要多个语句操作则需使用[块语句](#块语句)包起来。

#### 条件遍历

`for`后面的括号内只有条件表达式

```feng
func main() {
    var i = 0;
    for ( i < 100 ) {
        println(i);
        i += 1;
    }
}
```

#### 迭代遍历

`for`关键字后面的括号里面的控制器，有初始化、条件和更新三个部分，条件是一个结果为`bool`值的表达式，初始化与更新都是赋值语句。

示例：

```feng
func main() {
    for (var i = 0; i < 100; i += 1) {
        println(i);
    }
}
```

#### foreach遍历

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

循环语句遍历形式默认只对数组使用，自定义类可以实现为名为`for`的宏：

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

[变量](#变量)声明语句右边的初始化赋值是可选的，且也是先计算右边表达式元组，再赋值给左边变量：

```feng
func test() {
   var a,b,c = 1, "ggyy", 1.6;
}
```

#### 返回值

函数或方法的返回值必须和声明的类型兼容：

```feng
func call() (int,Complex,rom) {
   return 2, {real=1,imag=2}, "ggyy";
}
```

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

异常控制在许多语言都有，在有些场合使用很方便，比如资源打开和关闭。虽然有资源类，但这主要和内存管理一起的，如果在关闭资源操作中有耗时的，
比如等待IO，会对性能影响难预测，因为`release`是在计数为`0`时或者GC释放前调用的。因此还是有必要加异常机制。

异常语句分为抛出和捕捉：

1. 抛出一个对象，当前程序将会从此处中止，并从向异常机制提交一个异常实例，如果是有返回值的过程则忽略返回值。如果过程调用者没有捕捉到，则继续中止并将此异常实例提交给异常机制。
2. 捕捉是由`try`/`catch`组成的结构，机制将`try`中的块语句抛出的异常实例，然后匹配到`catch`
   语句中指定的类型，匹配到则捕捉成功，否则继续向下匹配。如果没有都未匹配成功则继续抛出。
3. 不论前面的`try`和`catch`中的执行情况如何，最终结束后一定会执行`finally`。

这里抛出的对象是任意一个类的实例：

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
   } catch(e *NilPointerError) {
      // TODO
   } catch(e *IllegalStateError | *IllegalArgumentError) { // 同时catch两种错误
      // TODO something else
   } finally {
      // TODO 清理一下
   }
}
```

TODO：未想好的：抛出机制怎么收集栈信息呢？就是怎么自定义实现可以收集栈信息的异常类呢？

```feng
class Error {
   // 提供一个方法
   func tracestack() {
      var as *List`*A` = new(List`*B`);
   }
   // 采用宏方式
   # tracestack() {
   }
}
```

## 元组

元组是编译器内的一个概念，由一组表达式值组成，不能单独表示或定义。

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

常量也是变量，即不可修改值的变量。
_常量只是在语法上不能修改的变量，且仅仅不能修改变量本身的值，而被常量引用的实例并不受此约束。_

### 变量值的类型

变量按值的类型分两种情况：值类型和引用类型。

#### 值类型变量

变量的值就是类型定义的数据，对其赋值能修改对应的值：

1. 基本类型的变量本身只是一个寄存器值，修改通常只需一个机器指令：
   ```feng
   var a int = 1; // 变量a赋值为字面量数值1，那a的值就是1
   var b int = a; // 变量b赋值为变量a，则将a的值复制给b
   b = 2; // a和b是两个不同变量，修改其中任何一个不会影响另一个
   ```
2. 自定义类型通常会占用超过寄存器位宽的空间，所以实现上往往需要一组指令，将类型的字段数据全部复制：
   ```feng
   class Vector { var x,y,z float64; }
   var a Vector = { x=1.0, y=0, z=-1.0 };
   var b Vector = a; // 和基本类型一样，复制a的所有字段数据给b
   b.x += 2.0; // 同样修改b不影响a，a.x的值依然是'1.0'
   ```
3. [固定长度的数组](#固定长度的数组)赋值就是遍历数组的所有元素进行赋值：
   ```feng
   var a [4]int = [1,2]; // 遍历每个元素初始化，没写出来的为默认值，int默认值为0
   var b [4]int;
   b = a; // 就是把a的数据复制给b
   // 等效于循环赋值
   for (var i = 0; i < a.size; i++) b[i] = a[i];
   ```
   自定义类型的数组：
   ```feng
   var a [4]Vector = [{x=1.0}, {x=2.0}]; // 遍历每个元素初始化，没写出来的为默认值，Vector默认值的每个字段都是0
   var b [4]Vector;
   b = a; // 就是把a的数据复制给b
   // 也等效于循环赋值
   for (var i = 0; i < a.size; i++) 
       b[i] = a[i]; // 这里的赋值参考第2点
   ```
   元素是引用类型也一样的规则。

_这里是复制同类型的数据，不同类型需要先转换成同类型转换再赋值。_

#### 引用类型变量

这种变量不保存数据，它的值仅包含引用具体实例需要的必要信息。被引用的实例才是保存数据的“数据块”。
比较简单的一个例子就是C语言的指针，指针保存了一个地址，地址所在的空间才是具体需要的内容。

##### 强引用类型

被强引用变量引用的实例是在使用中的，不能被内存管理器回收；同时当一个实例没有被强引用变量引用时就应该被回收。

变量必须引用存在的实例，或者为`nil`值（不引用任何实例）：

```feng
var a *Bus = nil;      // 初始化nil
var b *Bus = new(Bus); // 初始化指向一个新分配的Bus实例
a = b;                 // 让a指向“b指向的那个实例”，然后a和b实际上的引用同一个实例
a.speed += 10;         // 由于是同一个实例，如果查看b.speed则也是修改后的值
const ca *Bus = b;
```

变量能引用实例受类型安全的约束：

```feng
// 假设Book和Bus并没有继承关系
var a *Book = nil;     // 初始化nil
var b *Bus = new(Bus); // 初始化指向一个新分配的Bus实例
// a = b;              // 错误✖：Book的引用变量不能引用Bus的实例
```

类型安全需要在每种类型下分别讲述，比如类的实例有继承和抽象的约束、结构类型与memory及数组等。

##### 虚引用类型

虚引用和强引用相似，但虚引用不参与内存管理，不会影响内存回收，因此对虚引用有以下约束：

1. 虚引用变量只能用`const`声明。
2. 只能引用两种类型的变量：
   1. 值类型变量：支持引用[值类型变量](#变量)。如果[值类型常量](#常量)，那是否允许通过引用修改字段值？或者给引用语法加只读标识？
   2. 引用类型常量：支持引用类型常量。如果是变量，当被指向另一个实例后，虚引用也需要跟着变化？
3. 显然虚引用不能传递引用给强引用。
4. 虚引用只能用于[本地变量](#本地变量)，比如[返回值](#返回值)和类[字段](#字段)的类型不能是虚引用。

作为常量的虚引用必须初始化的，因此虚引用在声明时就已经与一个变量绑定了，作用域不会超过绑定变量的，因此不会指向无效实例。
示例：

```feng
func use(v &Vector) { // v默认是常量
   // TODO：……省略使用的代码
}
func test1(vec1 Vector, vec2 *Vector) {
   // vec1是值类型的，声明一个本地虚引用v1指向它
   const v1 &Vector = vec1;
   // 也可以直接传递vec1给虚引用参数
   use(vec1);
   // 引用另一个虚引用v1
   use(v1);
   // 传递引用常量v2给虚引用参数
   const v2 *Vector = vec2;
   use(v2);
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
   ｛
      // printf("%s %s\n", v, s);  // 错误✖：不能使用另一个块内的变量s
   ｝
   ｛
      var v = "Dear Fèng"; // 内层重新声明同名的变量，外层的变量v就被隐藏了
      printf("%s\n", v); // 打印：Dear Fèng
   ｝
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

### 整数

### 实数

### 布尔值

`bool`的字面量只能是`true`或`false`。

### 字符串

字符串并不是基本类型，编译器对字符串字面量支持处理，应当根据指定编码转换为rom。

### 空值

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
   // var ts TaskStatus = nil;   // 错误✖：枚举值不能
}
```

## 泛型

像C++、rust及Go的那种编译时的模板泛型……好像比较难……

## 宏

宏是一种有特定格式的代码片段，这种特定格式不是随意的，而是由特定用途决定的。
特定用途就是指某种语言特性，比如当宏用于实现[自定义运算符](#自定义运算符)时，由运算本身设定了代码格式。
目前宏仅支持在类和接口里。

宏统一由`#`开头定义，主要格式有过程宏和类宏两种。

### 过程宏

过程宏类似一般过程（函数或方法），有名称、参数表和语句序列组成：

- 名称和其他名称互不干扰，可以与其他元素重名。
- 参数表和函数参数不同，而是可以是传入和传出。
- 语句序列就是普通的语句序列，末尾可以有过可选的表达式。

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

## 错误

## 属性

## 别名

