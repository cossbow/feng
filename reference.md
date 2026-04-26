**【Fèng】Programming Language**

# Syntax

## Statements and Expressions

Statements are the basic instruction units of a program sequence. Except for block statements and control statements,
they must end with a `;`:

For example, an assignment statement, where the equal sign separates the left and right parts—the left is the operand to
be assigned, and the right is the expression that computes the value:
`var s = a + b;`.

Another commonly used statement is the call statement, which contains only a call expression:
`start();`.

The expression on the right side of a statement is the main body of computation, composed of multiple operations
combined according to precedence: `a + (b + c) * d - sin(e)`.

## Functions and Methods

[Functions](#functions) and [methods](#methods) are reusable, independently executable blocks of code that take input (
parameters), perform a series of operations, and optionally return a set of output values. They are used to decompose
complex tasks into smaller, more manageable, and maintainable modules, improving code reusability.

Examples below illustrate function formats, which also apply to methods.

Define a `sum` function with two `int` parameters, returning an `int` value:

```feng
func sum(a, b int) int {
    return a + b;
}
```

Assume `printf` is a function provided by module `fmt` for printing to the terminal:

```feng
import fmt *;
func test() {
    printf("%d + %d = %d \n", 1, 3, sum(1, 3));
}
```

A function with no return value:

```feng
import fmt *;
func test(a, b int) {
    printf("%d\n", a + b);
}
```

A function with a return value:

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

## Derived Types

Developers can define the following custom types:
[Classes](#classes) and [interfaces](#interfaces), [structs](#struct-types), [enumerations](#enumerations),
and [attributes](#attributes-_[Incomplete]_).

For example, define a custom derived class `Complex` and use it to define variables `c1` and `c2`:

```feng
class Complex {
    var real, imag float64;
}
func sample() {
    var c1 Complex = {real=1.5,imag=2.4};
    var c2 *Complex = new(Complex, {real=3.3,imag=0.6});
}
```

## Modules

As a code organization unit, all files in the same directory belong to the same module, and the module name is the same
as the directory name, so no declaration is needed in the file.
Circular dependencies are not supported; the dependency graph must be a directed acyclic graph (DAG).

## main Function

The `main` function is the entry point of an executable program, consistent with other languages.
The entry function has no return value and only one parameter, whose type must be `[&!#][*!#]byte`:

```feng
func main(args [&!#][*!#]byte) {
    hello();
}
```

A module containing a `main` function will be compiled into an executable file and cannot be imported as a library by
other modules. Modules without a `main` function are used as libraries, meaning they can be imported for use.

# Concepts

The following sections describe the definitions and usage of each syntactic element in detail.

## Modules

A module is the basic unit of code management.

1. Global symbols within a module cannot be reused; it's the same as within a single file.
2. Everything inside a module is visible internally. Cross-module access is limited to exported symbols.
3. Module names correspond one-to-one with paths; module names are not declared in files. Directory names must follow
   the same rules as variable names.
   For example, on Linux, module `com$jjj$base$util` corresponds to the relative path `com/jjj/base/util`.

### Exporting Symbols

Any global symbol can be exported. Special cases for members:

1. For an exported class, its members are not exported by default and must be exported individually.
2. Methods of an exported interface are exported by default.
3. Fields of an exported struct type are exported by default.
4. All values of an exported enumeration type are exported by default.

For example, the following exports global variable `gFoo`, function `aFoo`, class `Foo` along with its field `bar` and
method `go`:

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

### Importing Symbols

Declare importing the `com$cossbow$fmt` module:

```feng
import com$cossbow$fmt;
func test() {
   fmt$println(string("Hello Fèng!"));
}
```

You can set a module alias:

```feng
import com$cossbow$fmt ccfmt;
func test() {
    var m Sring = ccfmt$sprintf("Hello Fèng!");
    ccfmt$println(m);
}
```

## Basic Types

Basic types are built into the language and, from a memory perspective, can be placed directly in registers. Basic types
include integers, floating-point numbers, and booleans.

Although string literals exist, there is no built-in string type: strings cannot be stored directly in registers, and
strings are only meaningful when processing characters.

### Integer Types

All built-in integer types are as follows:

- Signed: `int8`/`int16`/`int32`/`int64`/`int`
- Unsigned: `uint8`/`uint16`/`uint32`/`uint64`/`uint`

The suffix number indicates bit width; types without a suffix are determined by the compilation target platform.

The highest bit of signed numbers is the sign bit: `0` for positive, `1` for negative. Thus, signed numbers have one
fewer bit for the numeric value.

Supports [arithmetic operations](#arithmetic-operations), [bitwise operations](#bitwise-operations),
and [relational operations](#relational-operations).

Explicit conversion is required between different integer types:

```feng
func test() {
   var a uint16 = 123;
   var b int32 = int32(a); // Convert uint16 to int32
}
```

Explicit conversion of integer types is equivalent to bitwise copying from low to high bits, which may cause integer
overflow:

1. Converting from a larger bit width to a smaller one truncates, causing overflow.
2. When converting between signed and unsigned, the sign bit is copied to the corresponding bit position, causing the
   integer value to change.

The language itself does not check for overflow; programmers must handle it themselves.

### Floating-Point Types

Floating-point types are defined by the [IEEE 754 standard](https://standards.ieee.org/ieee/754/6210/), including
single-precision `float32` and double-precision `float64`.

Floating-point types support [arithmetic operations](#arithmetic-operations)
and [relational operations](#relational-operations).

### Boolean Type

The type symbol is `bool`, and it has only two values: `true`/`false`:

* Supports logical operations, relational operations (equality and inequality only), and bitwise operations (AND, OR,
  XOR).
* The result of a relational operation is always a boolean.
* No conversion to/from integers or floating-point numbers.
* Conditional expressions in `if` and `for` statements must evaluate to `bool`.

The boolean type occupies 1 byte, using only the lowest bit; the values of other bits do not affect boolean operation
results.
The mapping between the lowest bit and boolean value:

| Boolean | Integer Value |
|---------|---------------|
| false   | 0             |
| true    | 1             |

Boolean type supports [logical operations](#logical-operations), and the results
of [relational operations](#relational-operations) are of boolean type.

## Operators and Expressions

### Basic Properties

#### Precedence

The following table lists the precedence of major operators (decreasing from top to bottom):

| Level | Operator Set                                         | Notes           |
|-------|------------------------------------------------------|-----------------|
| 1     | new(), parentheses, literals                         |                 |
| 2     | assertion, index, field access, function call, block |                 |
| 3     | +, -, !                                              | Unary operators |
| 4     | ^                                                    | Exponentiation  |
| 5     | *, /, %                                              |                 |
| 6     | +, -                                                 |                 |
| 7     | <<, >>                                               |                 |
| 8     | &                                                    |                 |
| 9     | ~                                                    |                 |
| 10    | \|                                                   |                 |
| 11    | <, <=, ==, !=, >, >=                                 |                 |
| 12    | &&                                                   |                 |
| 13    | \|\|                                                 |                 |

Levels 1 and 2 are special operators and are left-associative.
Level 3 unary operators are right-associative, while binary operators (except exponentiation) are left-associative.
Exponentiation is special: it is right-associative, with precedence higher than unary operators on the left but lower
than those on the right:

```feng
func test(a,b int) {
   var x int;
   x = -a^b;   // Equivalent to: -(a^b)
   x = a^-b;   // Equivalent to: a^(-b)
}
```

### Operation Expressions

#### Arithmetic Operations

| Operator | Description    |
|----------|----------------|
| ^        | Exponentiation |
| *        | Multiplication |
| /        | Division       |
| %        | Modulo         |
| +        | Addition       |
| -        | Subtraction    |

#### Bitwise Operations

| Operator | Description |
|----------|-------------|
| !        | Bitwise NOT |
| <<       | Left shift  |
| >>       | Right shift |
| &        | Bitwise AND |
| ~        | Bitwise XOR |
| \|       | Bitwise OR  |

#### Relational Operations

| Operator | Description (left * right) |
|----------|----------------------------|
| <        | Less than                  |
| <=       | Less than or equal to      |
| ==       | Equal to                   |
| !=       | Not equal to               |
| >        | Greater than               |
| >=       | Greater than or equal to   |

#### Logical Operations

| Operator | Description |
|----------|-------------|
| !        | Logical NOT |
| &        | Bitwise AND |
| ~        | Bitwise XOR |
| \|       | Bitwise OR  |
| &&       | Logical AND |
| \|\|     | Logical OR  |

Since the [boolean type](#boolean-type) only uses the lowest bit (others ignored), bitwise operations `&`, `~`, `|` on
boolean values still produce valid boolean results. The results of `&` and `&&` are consistent, as are `|` and `||`. The
difference is that `&&` and `||` have "short-circuit" behavior: when the left operand's result determines the final
outcome, the right expression is not executed.

Example illustrating the short-circuit behavior of `&&`:

```feng
func contains(v int) bool {
    // TODO: check v is in the collection
}
func isEmpty() bool { return true; }
func test(v int) bool {
    // isEmpty returns true, so the right side need not be evaluated; contains will not be called
    return !isEmpty() && contains(v);
}
```

### Other Expressions

#### Index Expression

Only arrays support this operator by default. The index operator consists of square brackets containing an expression
that evaluates to the index value.
There are two usage patterns:

1. Index expression on the right side is a read operation, retrieving the value of the element at the index as the
   result. There are two ways to receive the value in a statement:
    1. Use two variables on the left: the first receives the value; the second receives a boolean indicating whether the
       element exists. _[Incomplete]_
       ```feng
       func test(arr [16]int) {
           if (var v, exists = arr[16]; exists) {
               // Index 16 is out of bounds, so exists=false, this branch cannot be entered
           }
       }
       ```
    2. When using a single variable on the left or using it in an expression, only the element value is returned. If the
       element does not exist, execution terminates and an [exception](#exceptions-_[Incomplete]_) is thrown.
       ```feng
       func test(arr [16]int) int {
           return arr[16]; // Index out of bounds, terminates and throws an exception
       }
       ```
2. On the left side as a write operation, modifying the value of the element at the index.
   ```feng
   func test() {
       var arr [16]int;
       arr[15] = 0; // Modify element at index 15 to value 0
   }
   ```
   The capacity of an array cannot change after creation, so out-of-bounds access also terminates execution and throws
   an [exception](#exceptions-_[Incomplete]_).

#### new Expression

Used to dynamically create instances. Format: `new(type, initialization parameters)`. Example:

```feng
// Create an instance of class Device
var a *Device = new(Device);
// The following parameter is for initialization
var b *Device = new(Device, {});
```

[Reference type variables](#reference-type-variables) are separate from
instances; [strong references](#strong-reference-type) can only reference instances created via `new`.

The parameter is optional; without it, the instance is initialized to default values. For arrays, you can pass
an [array expression](#array-expression); for classes and structs, you can pass a [field expression](#field-expression).

#### Array Expression

A special literal expression specifically for array initialization, listing all elements of the array directly. Elements
can be arbitrary expressions.

Example: initialize an `int` array variable by listing all elements:

```feng
var a [4]int = [1,2,3,4];
```

##### Array Expression Length

When initializing an array, the number of elements listed can be less than the array size; subsequent elements are set
to default values:

```feng
var a [4]int = [1,2,3,4];
var a [4]int = [1,2,3];
```

It can also be empty:

```feng
var a [4]int = [];
```

You can specify the array type before the expression. The element types must match the variable, and the length cannot
exceed the variable's length:

```feng
var a [4]int = [4]int[1,2,3,4];
var a [4]int = [3]int[1,2,3];
// var a [4]int = [5]int[1,2,3]; // Error: specified length exceeds variable length
```

The specified array type length cannot be less than the actual number of elements:

```feng
var a [4]int = [3]int[1,2,3];
// var a [4]int = [3]int[1,2,3,4]; // Error: number of elements exceeds specified length 3
var a [4]int = [4]int[1,2,3,4]; // Correct
```

The specified type length can be omitted, in which case the length equals the actual number of elements:

```feng
var a [4]int = []int[1,2,3];
```

If the variable type is omitted, the variable's type and length are automatically inferred from the number of elements:

```feng
var a = []int[1,2,3]; // a's type is: [3]int
```

##### Array Expression Type

The element type must match the array type, following the assignment rules. Refer to variable types and their
definitions. Two simple error examples:

```feng
// var a = []int[1,false]; // Second element is bool
// var a = []int[1,3.1];   // Second element is float
```

If the type is not specified, the element type is inferred from the first element:

```feng
var a = [1,2,3];     // a's type inferred as: [3]int
// var a = [1,3.1];  // First element infers type int, but second is float
```

A more complex example:

```feng
class Animal {}
class Cat : Animal {}
var a = [new(Animal), new(Cat)];    // Inferred array type: [2]*Animal
// var a = [new(Cat), new(Animal)]; // Error: inferred type [2]*Cat, cannot store *Animal element
```

##### Array Expression Nesting

Can be nested for multi-dimensional array initialization:

```feng
var a = [2][3]int[[1,2],[3,4]];
// var a = [2][3]int[[],[],[]];  // Error: first dimension exceeds
// var a = [2][3]int[[1,2,3,4]]; // Error: second dimension exceeds
```

Nesting with field expressions:

```feng
class Car {
   var id int;
}
var ca [2]Car = [{id=1},{id=2}];
```

#### Field Expression

A special literal expression for initializing derived types with definable fields, such as struct types and classes:

```feng
class Car {
   var id int;
   var speed float;
}
var car Car = {id=10,speed=80.5};
```

Initialized fields need not be in the order defined, and fields cannot be initialized repeatedly.

```feng
// var car Car = {id=10,id=100}; // Error: duplicate field id
```

Refer to [Classes](#classes) and [Struct Types](#struct-types) for other initialization details.

#### Assertion Expression

Used to check whether a class reference can be converted. For example, a reference to a class instance can be passed to
an interface or parent class pointer; conversion back requires type checking.

Returns a reference of the corresponding type. If the type does not match, it returns `nil`, which may cause a null
pointer:

```feng
func test(o *Object) {
   var f *File = o?(*File);   // Convert to File class reference
   var w Writer = o?(*Writer);  // Convert to Writer interface
   o?(*Writer).write("Hello!"); // Used in an expression, risk of null pointer
   if (var w Writer = o?(*Writer); w != nil) {
      // This avoids null pointer
   }
}
```

#### sizeof Expression

Used at compile time to calculate the memory size (in bytes) of a type. The type must be known to the compiler.
Supported types:

1. Integer and floating-point types.
2. Struct types.
3. Fixed-length arrays of the above (including multi-dimensional arrays)

For example, array reference `[*]int` cannot be used because its size is only known at runtime.
Classes do not support `sizeof` because field reordering is allowed.
Enumerations must remain simple and do not support `sizeof`.
`bool` is a special basic type that the compiler may store efficiently, so `sizeof` is not supported.

#### Assignment Operation

Assignment operators are shorthand for operations: the left operand participates in the operation with the right
operand, and the result is assigned back to the left operand. This requires that both operands be of the same type. If a
type supports custom operators, the corresponding assignment operator is also supported. For example:

```feng
func test() {
   var i = 0;
   i += 2;
}
```

If an operator `+` is implemented to concatenate strings in order, then the `+=` operator appends the right string to
the left string variable and assigns the result back to the left variable.

Assignment operators have no return value and cannot be used in expressions; they can only be used
in [specific statements](#assignment-operation-statement).

#### Block Expression

A block expression resembles a block statement but must end with an expression as the return value:

```feng
class Foo {var id int;}
func test() {
   var r *Foo = {
      var f = new(Foo);
      f.id = 0;
      f // expression value
   };
}
```

Entering the block creates a new scope; variables are automatically cleaned up upon exit.

### Custom Operations

[Classes](#classes) do not support operators by default, but some operations can be custom-implemented.

Custom operation code segments differ from methods and functions; they are implemented via
`operator` [macros](#macros). Each operator has a fixed name, prototype, and operand list:

* Each operator has a fixed name.
* Operands are defined per operator (as macro parameters).
* Names can be the same as other method names.

#### Custom Expression Operators

Only certain operators support customization:

| Operator | Macro Name | Right Operand Type | Result Type  |
|----------|------------|--------------------|--------------|
| *        | mul        | Same as left       | Same as left |   
| /        | div        | Same as left       | Same as left |   
| %        | mod        | Same as left       | Same as left |   
| +        | add        | Same as left       | Same as left |   
| -        | sub        | Same as left       | Same as left |   
| <        | lt         | Same as left       | bool         |   
| <=       | le         | Same as left       | bool         |   
| ==       | eq         | Same as left       | bool         |   
| !=       | ne         | Same as left       | bool         |   
| \>       | gt         | Same as left       | bool         |   
| \>=      | ge         | Same as left       | bool         |   

Example with complex numbers:

```feng
class Complex {
   var real,imag float64;
   // Implement + operation
   // result holds the result and is returned after computation
   macro operator add(lhs, rhs, result) {
      result.real = lhs.real + rhs.real;
      result.imag = lhs.imag + rhs.imag;
   }
   // Implement * operation
   macro operator mul(lhs, rhs, result) {
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

#### Custom Special Operations _[Incomplete]_

##### Custom Index Operation

The [index expression](#index-expression), which is only supported by arrays by default, can also be customized.
Index operations are divided into read and write operations, implemented via two procedural macros: `indexGet` and
`indexSet`.

For example, a custom dictionary class `Map` providing indexed access with derived key and value types. Usage example:

```feng
class Map {
   // Index read
   macro operator indexGet(key, operand, exists) {
      var n = getNode(key);
      operand, exists = if (n != nil) n.value, true else nil, nil;
   }
   // Index write
   macro operator indexSet(key int, value String) {
      set(key, value);
   }
}
func test() {
   var m Map;
   m[100] = 159;
   // Checked read: if key does not exist at runtime, exists is false
   var v, exists = m[100];
   // Direct read: if key does not exist (exists false), execution terminates and an exception is thrown
   var v int64 = m[100];
   printf("m[100] = %s\n", v);
}
```

Whether a write operation terminates execution when an index does not exist depends on the implementation:
For a typical Map, new keys can be added; arrays cannot be automatically resized.

## Classes

[Classes](https://en.wikipedia.org/wiki/Class_(programming)) are a core concept of object-oriented programming,
describing the common characteristics and methods of the instances they create.
Classes correspond to categories in human knowledge and, in programs, classify instances (objects) and define their
common properties (fields) and behaviors (methods).

A typical class definition (containing one field and one method):

```feng
class Car {
   var engine *Engine;
   func start() {
      engine.start();
   }
}
```

After defining a class, it must be instantiated for use: declare a value type variable of the class, or use `new` to
dynamically create an instance.

```feng
func sample(engine *Engine) {
   var c1 Car = {engine=engine};
   // Or
   var c2 *Car = new(Car);
   c2.engine = engine;
}
```

### Fields

Most [variable](#variables) type definitions apply to class fields, except:

1. Fields cannot be of [phantom reference type](#phantom-reference-type), but can be
   of [weak reference type](#weak-reference-type).
2. Fields can only be defined with `const` and `var`.

```feng
class Cat {
   const id int;
   var name *rom;
   var mothr,father *Cat;
   var children []*Cat;
   // var who Cat; // ✖
}
```

The order of field definitions in a class does not need to correspond to actual memory layout. This differs
from [struct types](#struct-types).

#### Instance Initialization

When instantiating, `const` fields must be initialized; `var` fields are optional.
In the `Cat` class above, `id` must be initialized, while `name` is optional:

```feng
func test() {
   var c1 Cat = {id=1001};
   var c2 Cat = {id=1001, name="Tom"};
   // Incorrect usage below
   // var c3 Cat = {name="Tom"};
   // var c4 Cat;
   // var c5 Cat = {};
}
```

The same applies to dynamic instantiation via `new`:

```feng
func test() {
   var c1 *Cat = new(Cat, {id=1001, name="Tom"});
   // Incorrect usage below
   // var c2 *Cat = new(Cat);
   // var c3 *Cat = new(Cat, {name="Tom"});
}
```

If a class has no `const` fields, initialization is not required.

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

If not initialized, or if a field is not specified in initialization, it is set to the default state: all memory set to
`0`, and reference types set to `nil`.

Side effect: If an exported class has unexported `const` fields, it cannot be instantiated in other modules.
For example, the `Dog` class below can only be instantiated in the current module:

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

#### Weak Reference Type

A special reference type for fields, denoted by `~` before the type name.
This type of reference does not affect instance deallocation, and the referenced instance is automatically set to `nil`
when deallocated.
When using reference counting for memory management, circular references are a logical problem that can be manually
solved with weak references:

1. Weak references do not affect instance deallocation (no increment of reference count), allowing the count to reach
   zero.
2. When the instance is deallocated, the weak reference field is set to `nil`, ensuring memory safety.

For example, defining the forward reference field `prev` in a doubly linked list `Node` as a weak reference prevents the
reference count from never reaching zero:

```feng
class Node {
    var key String;
    var next *Node;
    var prev ~Node;
}
```

### Methods

Methods are largely the same as [functions](#functions), with differences:

1. They must be called via a class instance.
2. Inside a method, the current instance's class members are accessible.
3. Method names are unique IDs within the current class's method set, including inherited methods, but subclasses
   override parent methods with the same name.

For example, define a `Task` class for managing tasks (enum `TaskState` represents task status):

```feng
enum TaskState {WAIT, RUN, DONE,}
class Task {
   var state TaskState;
   func isRunning() bool {
      return state == RUN;
   }
   func start() {
      if (isRunning()) return;   // Call another method
      state = RUN;     // Modify state
   }
}
```

Call methods via value type variables:

```feng
func sample1() {
   var task Task;
   task.start();
   printf("task state '%s'\n", task.state.name);   // Prints: task state: 'RUN'
}
```

Or via references:

```feng
func sample2() {
   var task *Task = new(Task);
   task.start();
}
```

### this Keyword

`this` is a special keyword inside a class that refers to the current instance itself.

Inside a member method, when a local variable has the same name as a field, `this` is used to access the field:

```feng
class Cat {
   var name String;
   func setName(name String) {
      log();   // No log function above, so this refers to the log member method
      this.name = name; // The name variable/parameter above conflicts with field name; must use this
   }
   func log() {
      printf("%s: miao~~\n", name); // No name variable above; can omit this
   }
}
```

During method invocation, `this` references the current instance, ensuring it is not deallocated:

```feng
func sample() {
   new(Cat).log();  // The created instance can only be deallocated after the log method exits
}
```

`this` can be passed to [phantom reference type](#phantom-reference-type) variables. If called via a strong reference,
it can be passed to a strong reference; if called via a value type, it can be assigned to a value variable. Example:

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

`this` can be used as a return value, but it does not represent a specific type—it represents the current instance
itself.
It can be used for method chaining or assigned to variables of the same type as the caller:

```feng
class Car {
   var speed int;
   func forward() this {}
   func stop() this {}
   func backward() this {}
}
func sample1(c Car) {
   var speed = c.forward().stop().backward().speed;   // Chained calls
   var c2 Car = c.forward();
}
func sample2(c *Car) {
    var c2 *Car = c.forward();
}
func sample2(c &Car) {
    var c2 &Car = c.forward();
}
```

### Inheritance

Inheritance (also called extension) extends an existing class by adding new fields and methods.
A subclass inherits the fields and methods of its parent class and may optionally add its own.

When inheriting, subclass fields cannot have the same name as parent class fields:

```feng
class Device {
    var id int;
}
class Disk : Device {
    // var id int; // ✖: Name conflict
    var diskId int;
}
```

Methods can have the same name but must have identical prototypes, enabling [polymorphism](#polymorphism).

#### Polymorphism

Polymorphism means that the same behavior can have multiple different forms or manifestations.
Strictly speaking, [abstraction](#abstraction) (see [Interfaces](#interfaces)) is also a form of polymorphism.

Example of class polymorphism: first define a parent class `Animal` with a field `name` and a method `eat`:

```feng
class Animal {
    var name *rom;
    func eat(food *rom) {
        printf("Animal %s eating %s\n", name, food);
    }
}
```

Then define a subclass `Cat` that inherits the `name` field and implements a method `eat` with the same name and
prototype:

```feng
class Cat : Animal {
    func eat(food *rom) {
        printf("Cat %s eating %s.\n", age, name, food);
    }
}
```

An `Animal` reference can point to a subclass instance. When calling the `eat` method via the parent class reference,
the subclass's `eat` method is actually invoked:

```feng
func test() {
   var animal *Animal = new(Cat, {name="Tom"});
   animal.eat("fish-meat"); // Prints: Cat Tom eating fish-meat.
}
```

This example shows that the `eat` method can have multiple implementations after inheritance, and parent class
references pointing to different subclasses will call the subclass's implementation.
**Subclasses must have identical prototypes when overriding parent class methods.**

[Assertion expressions](#assertion-expression) are supported for subtype checking:

```feng
func test(animal *Animal) {
    var cat, ok = animal?(*Cat);
    if (ok) cat.eat("mouse");
}
func test() {
    test(new(Cat));
}
```

Parent and child classes only support reference passing; value type variables cannot be passed between them. Passing
rules:

1. When reference types are the same, a subclass can be passed to a parent class.
2. A constant strong reference of a subclass can be passed to a phantom reference of the parent class.

Example with parent class `Animal` and subclass `Cat`:

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

#### Abstraction

Polymorphic parent classes have their own method implementations, but [interface](#interfaces) methods have no
implementation; the "subclasses" of the interface provide implementations. This is called abstraction.
Abstraction serves as a better contract and specification:

1. Managers focus only on interface implementations, hiding concrete classes, and provide instances that implement the
   interface.
2. Users need not care about the specific class as long as the interface is implemented.

For example, define an interface `Task` containing only a simple method `run`:

```feng
interface Task {
   run();
}
```

Define two implementation classes `MyTask` and `YourTask`:

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

Usage is similar to polymorphism:

```feng
func asyncRun(t *Task) {
    t.run(); // Pretend this is asynchronous execution
}
func test() {
    asyncRun(new(MyTask));      // Prints: Run my task!
    asyncRun(new(YourTask));    // Prints: Run your task!
}
```

[Assertion expressions](#assertion-expression) are also supported for type checking.

### Root Class

Since classes support single inheritance, all classes form a tree based on inheritance relationships, with the root
class being `Object`.
`Object` is a built-in class. Any class without an explicit parent class inherits from `Object` by default.
`Object` has no members; an `Object` instance can be created.

```feng
func test() {
    var o *Object = new(Object);
    o = new(Device);
}
```

### final Class

If a class is intended only for storing data or simple logic, and not for complex inheritance or interface design, it
can be marked as `final`.
Such a class cannot be inherited, has no base class (is not a subclass of Object), and cannot abstract interfaces (
should be possible, but not implemented).

```feng
class User final {
   var id int;
}
```

### Exporting Members

Class members can be individually exported and are not exported by default.

Code in module `util`:

```feng
export List`T`{
   var elements []T; // elements is hidden from external access
   export func get(i int) { // get is exposed
      // TODO: check index bounds
      return elements[i];
   }
}
```

### Resource Classes

When a class is annotated with the `resource free` macro method, it is marked as a resource class. The macro's code is
called when an instance of this class is deallocated.

This feature can be used to automatically release other resources, such as buffers allocated from C libraries:

```feng
class CBuffer {
   const buf uint64; // Assume this field holds a buffer pointer value
   macro resource free() {
      cFree(buf); // Assume this calls C's free function
   }
}
```

Resource classes can only be instantiated via `new`. This restriction prevents duplicate calls.
For example, in the `CBuffer` class above, if a value type variable copies the `buf` value, multiple instances would
call `cFree(buf);` repeatedly.

Some external resource releases, like file closures, are often time-consuming and may involve I/O errors or exceptions.
Such operations should be handled with [exception statements](#exception-statements) rather than in `free`.

## Interfaces

Interfaces are a feature separated from polymorphism—they are parent classes without concrete implementations and
without fields.
Thus, an interface appears as a collection of methods, omitting the `func` keyword before method definitions.

Interfaces are only contracts and specifications; they cannot be instantiated, so interface type variables can only be
references.

### Interface Composition

Interfaces can be composed:

1. A composed interface includes all method prototypes from its components.
2. A composed interface can be passed to a component interface because implementing the composed interface naturally
   implements the component interface.
3. Method name conflicts are checked; methods with the same name from different components are considered the same
   method. Compilation fails if prototypes do not match.

For example, a file can be read and written; interfaces can be designed as follows:

```feng
interface Reader {
   read(b [*]byte) (int, Error);
}
interface Writer {
   write(b [*#]byte, off, len int) (int, Error);
}
// Composed interface File includes both read and write methods
interface File {
   Reader;
   Writer;
   query() *FileInfo;
}
// An instance implementing the File interface naturally implements the Writer interface
func use(file *File) Write {
   return file;
}
```

### Interface Type Variables

Interface type variables are reference type variables and can only reference instances of implementing classes.
Interface variable declarations require a reference identifier to indicate the reference type.

Allowed passing:

1. When reference types are the same, an implementing class can be passed to the interface.
2. Under allowed type conditions, a constant strong reference of an interface can be passed to a phantom reference of
   the interface.
3. Under allowed type conditions, a constant strong reference of an implementing class can be passed to a phantom
   reference of the interface.

Example with interface `Cache` and implementation class `LocalCache`:

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

## Enumerations

Enumeration types have a finite number of values, all of which must be listed at definition time:

```feng
enum TaskState {WAIT, RUN, DONE,}   // Note the required trailing comma ","
```

[Enumeration variables](#enumeration-variables) must be one of the enumeration values; they cannot be `nil`.

Enumeration types have built-in special attributes determined at compile time:

* `id`: An integer value automatically incremented in definition order. Changing the order changes the IDs.
* `name`: The literal name as defined. For example, `WAIT` has name `"WAIT"`.
* `value`: A custom integer attribute. If not defined, the first enumeration value's `value` is `0`, and subsequent
  values increment by 1.

Enumeration values typically require the enum type as a prefix. If the variable type is clear, the prefix can be
omitted. Example:

```feng
enum TaskState {WAIT, RUN, DONE,}   // value not set, so equals id
enum BillState {WAIT, PAID=4, SEND, DONE,} // WAIT=0, SEND=5, DONE=6, ...
func test() {
   var s1 = TaskState.WAIT;             // s1 initialized to enum value WAIT
   s1 = RUN;                            // s1 type known, so prefix omitted
   var s2 TaskState = DONE;             // s2 known, prefix can be omitted
   var i int = TaskState.RUN.id;        // i initialized to integer 1
   i = s2.id;                           // i assigned 3
   var n [*#]byte = s2.name;            // n initialized to rom reference containing string "DONE"
   var v int = BillState.SEND.value;    // v initialized to integer 5
}
```

When the type is clear (e.g., in a `switch` statement), if the `case` statements do not cover all values, a `default`
branch is required; otherwise, `default` is not allowed:

```feng
func sample(s BillState) {
    switch(s) {
    case WAIT:
        // TODO
    case PAID, SEND:
        // TODO
    default:
        // TODO
    }
}
```

Supports [iteration loops](#iteration-loop) over all enumeration values:

```feng
func test() {
   for ( s : TaskState )
      printf("name: %s, id: %d \n", s.name, s.id);
}
```

Supports direct indexing:

```feng
func sample() {
    var s1 TaskState = TaskState[0];
    var s2, ok = TaskState[4];
}
```

The default value for enumeration variables or array elements is the first enumeration value (with `id` equal to `0`).

## Struct Types

### Struct Type Definition

Structs and unions are collectively called struct types. Their definition and memory layout are consistent with C:

1. Struct: All fields are allocated sequentially in order.
2. Union: All fields share overlapping storage.

Field types can only be [integers](#integer-types), [floating-point numbers](#floating-point-types), struct types,
and [fixed-length arrays](#fixed-length-array) of these types.

Struct and union definitions have the same format, differing only in the keyword:

1. Struct definition format: `struct` name `{` field list `}`.
    ```feng
    struct Message {
        type int;
        success byte;
        value float32;
        ext [12]int;
    }
    ```
2. Union definition format: `union` name `{` field list `}`.
    ```feng
    union DataType {
        type int;
        success byte;
        uv float32;
    }
    ```

Adjacent fields of the same type can be combined; non-adjacent fields cannot. Example with struct:

```feng
struct Request {
    type, code int;
    data [56]uint8;
}
```

Bit-fields specify the actual bit width used for a field, applicable only to basic types.
The bit-field value is within the range of the field's type width, placed after the field name.
Example setting `code` bit-field to `6` (with `type` unspecified):

```feng
struct Request {
    type, code:6 int;
    data [56]uint8;
}
```

A notable exception: unions can only specify one field during initialization:

```feng
union Foo {
   tag int;
   fly uint8;
}
var foo Foo = {tag=1};
// var foo Foo = {tag=1,fly=2}; // Error
```

### Struct Type Instances

Struct types can be instantiated in two ways:

1. Defined as [value types](#value-type-variables), supported as fields of variables, classes, or struct types.
2. Dynamically allocated via `new`.

## Arrays

### Array Elements

Arrays are types that store a contiguous sequence of repeated elements. Elements can be of any type.
Each element is equivalent to a [variable](#variables) and can be either a [value type](#value-type-variables) or
a [reference type](#reference-type-variables):

```feng
var a [4]int;       // Basic type array
var b [4]Host;      // Class array
var c [16]*Bus;     // Class reference array
var d [12][4]int;   // Array of fixed-length arrays: multi-dimensional array
var e [10][]int;    // Array of variable-length arrays; elements are references (different from multi-dimensional)
```

Value type array elements' memory is allocated together with the array and can be used directly:

```feng
func test() {
    // Basic type array
    var a [4]int = [1,2,3,4];
    a[0] += a[1];
    // Class array
    var b [4]Host = [{id=1}];
    b[3].id = 111;
    // Array of fixed-length arrays: multi-dimensional array
    var c [4][8]int = [[1],[2]];
    c[3][4] = 222;
}
```

Reference array elements require additional references to other instances; the default value is `nil`.

```feng
func test() {
    var a [4]Device;
    // a[2].name = "dev-2"; // Error: throws null pointer exception
    a[0] = new(Device);
    a[0].name = "dev-0";    // Only a[0] usable; other elements remain nil
}
```

The above uses [fixed-length arrays](#fixed-length-array) as an
example; [variable-length arrays](#variable-length-array) differ only in initialization; usage is the same.

### Array Types

Array length refers to the total number of elements the array can hold. Specifying or not specifying the size when
declaring the variable type indicates two different types.

#### Fixed-Length Array

If the size is specified at declaration, it is a fixed-length array—the value in square brackets must be an integer
literal or integer constant expression.

```feng
var a [4]int;
```

This type of array is a [value type variable](#value-type-variables).

Initialized with [array literal](#array-literal); the number of initial values cannot exceed the array length.
If fewer, elements are initialized sequentially from the first position; remaining elements are zeroed:

```feng
// var a [4]int = [1,2,3,4,5];
var b [4]int = [1,2];   // b initialized to: [1,2,0,0]
```

When initialized with an expression, the expression's result must be an array of the same type and length:

```feng
func foo() [4]int {
    return [1,2,3,4]
}
func foobar() {
    var a [4]int = foo();
    // var b [2]int = foo(); // Error
}
```

#### Variable-Length Array

Omitting the length creates a [reference type variable](#reference-type-variables), i.e., an array reference that can
point to array instances of any length.

Array instances are allocated via `new`, and the length must be specified at allocation time. Format:
`new([length]type)`

Example creating an int array:

```feng
func test(size uint) {
    var a []int = new([4]int);
    var b []int = new([size]int);
}
```

### Array Type Fields

[Class](#classes) fields can be arrays or array references, same as variable usage:

```feng
class Foobar {
    var foo [4]int32;
    var bar [*]int64;
}
```

Note: [Struct type](#struct-types) fields cannot be [references](#reference-type-variables); the length must be
specified.

## mappable

`mappable` defines types whose references can be freely converted to each other. Unlike class covariance/contravariance,
no type checking is required—only bounds checking.

Supported mappable
types: [struct types](#struct-types), [integers](#integer-types), [floating-point numbers](#floating-point-types),
and [fixed-length arrays](#fixed-length-array) of these types.
These types occupy contiguous memory and contain no references (pointers), so their references can be freely converted
like in C, with the only constraint being bounds checking.

Example: converting an `int` reference to an `int16` reference:

```feng
func f1(a *int) *int16 {
   return a;
}
```

Since the size is known at compile time, it can be checked. The above conversion does not cause out-of-bounds, but the
following fails to compile:

```feng
func f1(a *int8) *int16 {
   return a;
}
```

Struct conversion is similar; `f1` is allowed, while `f2` is out-of-bounds:

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

Array references can point to arrays of any length, so their length is computed at runtime. If the element size exceeds
the target, the size is `0`:

```feng
struct Foo {
   v int32;
}
func f1(a *Foo) [*]int16 { // length = 2
   return a;
}
func f2(a *Foo) [*]int64 { // length = 0
   return a;
}
```

Mappable types have well-defined memory layout consistent with C and contain no references (pointers).
[Struct types](#struct-types) explicitly prohibit reference fields, and array elements cannot contain references,
including multi-dimensional arrays:

```feng
func test() {
   var a1 [*]int; // mappable
   var a2 [*][2]int; // mappable
   var a3 [*][3][4]int; // mappable
   
   var a4 [*]*int; // not mappable
   var a4 [*][*]int; // not mappable
   var a4 [*][5]*int; // not mappable
   var a4 [*][6][*]int; // not mappable
}
```

## Functions

Definition format: `func` function-name `(` parameter-list `)` return-list `{` function-body `}`

The function name is required; parameter list, return list, and function body can all be empty. Three examples:

```feng
func run() {}
func start() { run(); }
func exec(a []Sting) *Error {
   return nil;
}
```

### Function Name

The function name is the function's unique ID within the module's function set; functions are called by name.

```feng
func add(a,b int) int { return a + b; }
func test() {
   var s = add(1, 2);
}
```

### Parameter List

Parameters consist of a parameter name and type, are constants (implicit `const`), and their scope is within the
function.

Example defining parameters `l` of type `Queue` and `a` of type `int`:

```feng
func send(l Queue, a int) {
   l.push(a);
}
```

Adjacent parameters of the same type can be combined. Example defining two `int` parameters `a` and `b`:

```feng
func add(a, b int) int {
    return a + b;
}
```

### Return Type List

The return type list is declared between the parameter list and the function body, enclosed in parentheses. Example:
function `foo` returns an `int` and a `float`:

```feng
func foo() (int, float) {}
```

When there is only a single return value, parentheses can be omitted:

```feng
func online() bool {};
```

Multiple return values have significant design implications. For example, when needing both a function's result and
error information, returning both avoids modifying an external variable via reference parameter:

```feng
func createDevice(host *Host) (*Device, *Error) {
   if (host.inRecovery()) {
      return nil, errorInRecovery;
   }
   // TODO
}
```

Error information can also be [thrown](#throwing-exceptions); both approaches are viable.

### Function Body

The function body consists of a sequence of [statements](#statements):

```feng
func run(s int) {
    var i = s+1;
    do(i);
    ...
}
```

The context accessible inside the function body
includes [global variables](#global-variables), [parameter list](#parameter-list),
and [local variables](#local-variables).

```feng
const PI = 3.14;
func circlyArea(diameter float) float {
    var radius = diameter * 0.5;
    return radius * radius * PI;
}
```

### Function Prototype

A function prototype is a type of variable. A function definition without its body is a prototype: `func` function-name
`(` parameter-list `)` return-list.
A [variable](#function-prototype-variable) of this type is either null or points to a function compatible with the
prototype. Example:

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

Function prototypes support anonymous definition:

```feng
func test(c func(a, b int) int) {}
func supply(c int) func(a, b int) int {
    switch(c) {
    case 0: return add;
    case 1: return sub;
    case 2: return mul;
    case 3: return div;
    default: return nil;
    }
}
func test() {
    var c1 func(a, b int) int = add;
    var c2 = sub;   // Type can be omitted; auto-inferred
}
```

Prototype variables are not reference types but can be `nil`. They can also be marked with a non-null prefix (`!`) to
indicate they cannot be null, following the same non-null rules as references:

```feng
func use1(a !func()) {
   var c1 func() = a;
   var c2 !func() = a;
   // var c3 !func() = c1; // Error: cannot pass in reverse direction
   if (c1 != nil) {
      var c3 !func() = c1; // After explicit null check, can pass
   }
}
```

## Statements

### Block Statement

A block statement is a sequence of statements enclosed by `{` and `}`. The inner context is
nested; [local variables](#local-variables) declared inside cannot be used outside:

```feng
func test() {
   println("block 1");
   {
      println("block 2");
      {
         println("block 3");
         // Nesting has no limit
      }
   }
}
```

### Branch Statements

Select one branch to execute based on a control condition. Two types.

#### if Statement

`if` is followed by a parenthesized conditional expression, then the statement executed when the condition matches; the
`else` branch is executed when it does not match and is optional.

The expression result, used as the condition, must be of type `bool`.

Simple conditional statement:

```feng
func abs(m int) int {
   if (m < 0)
      return -m;
   else
      return m;
}
```

The `else` branch can be omitted:

```feng
func printIfError(err uint) {
   if (err == 0) return;
   printf("Error: %u\n", err);
}
```

An initialization statement can be added before the conditional expression:

```feng
func test(m Map`int,*Node`, k int) {
   if (var n,ok = m[k]; ok) { // n and ok are only within this block
      printf("value of %d is: %s\n", k, n.value());
   }
   // printf("value of %d is: %s\n", k, n.value()); // Error: cannot use outside
}
```

Nesting `else` with `if` creates multi-branch structures:

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

`if..else` can be used as [tuples](#tuples-_[Incomplete]_), but the `else` branch cannot be omitted, and each branch
must produce a tuple of the same length:

```feng
func compare(a, b int) int {
    return if (a < b) -1 else 0;
}
func minMax(x,y int) (int,int) {
   return if (x < y) x,y else y,x;
}
```

#### switch Statement

A `switch` statement begins with a conditional expression and contains multiple match rules. Each rule starts with
`case` and is followed by a group of statements.
The statement group of the matched `case` is executed, and after execution, control exits the `switch` statement (no
fall-through) unless the last statement in the group is `fallthrough`.

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

Like `if`, an initialization statement can be added before the conditional expression:

```feng
func test(n Node) {
   switch(var v=n.value; v) {
   case 1:
      println("one");
   }
}
```

`switch` can also be used as [tuples](#tuples-_[Incomplete]_) following the same rules as conditional statements.

### Loop Statements

#### Conditional Loop Statement

The parentheses after `for` contain a control body, which must include a control condition expression and can also
include initialization and update substatements. This is followed by the statement(s) to be executed, called the loop
body.
The loop body repeats as long as the control condition is satisfied:

1. The control condition is a `bool` expression; the loop body executes only when the result is `true`.
2. The loop body is a single statement; if multiple statements are needed, they must be enclosed in
   a [block statement](#block-statement).

A simple loop with only a condition expression:

```feng
func test() {
    var i = 0;
    for ( i < 100 ) {
        println(i);
        i += 1;
    }
}
```

The complete control body format: [Initialization]; [Expression]; [Update]

1. [Initialization] executes once before the loop begins.
2. Each iteration: evaluate [Expression]; if `false`, exit the loop; if `true`, execute the loop body, then
   execute [Update].
3. Loop control operations within the body:
    1. `continue` jumps directly to the next iteration.
    2. `break` exits the current loop or a specified loop.

Example: loop 100 times, printing the value of `i` each time:

```feng
func test() {
    for (var i = 0; i < 100; i += 1) {
        println(i);
    }
}
```

#### Iteration Loop

For arrays, a simpler way to iterate over all elements:

```feng
func test() {
    var src []int = [0,1,2,3,4,5,6,7,8,9];
    for ( v : src )  // value only
      handle(j);
    for ( i,v : src) // both index and value
      println(i, v);
}
```

`continue` and `break` are also effective in iteration loops.

By default, iteration loops work only for arrays. For custom classes, a custom iterator can be implemented to enable
iteration loops.
Iteration is implemented via a helper macro named `Iterator`. Since loops are very common syntax, the macro is directly
expanded by the compiler.
The macro's fields are unrestricted and include four methods: `initializer`, `condition`, `updater`, `get`

| Method      | Purpose             | Parameters   |
|-------------|---------------------|--------------|
| initializer | Initialize iterator | None         |
| condition   | Loop condition      | None         |
| updater     | Update iterator     | None         |
| get         | Get value(s)        | Unrestricted |

Multiple `get` methods can be defined, but they must have different numbers of parameters.

Example:

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
   for ( t : src) { // matches the first get
      // TODO
   }
   for (i, t : src) { // matches the second get
      // TODO
   }
}
```

### Assignment Operation Statement

[Assignment operations](#assignment-operation) can only be used in statements:

```feng
func test() {
   var i = 0;
   i += 2;
}
```

### Assignment Statement

#### Modification Assignment

The left side of an assignment statement is an operand (the object whose value will be modified), and the right side is
a [tuple](#tuples-_[Incomplete]_) of expressions:

```feng
func test(x,y int, u *User, a []int) {
   x = 2;
   u.id = 1;
   a[0] = 8;
   x, y = 2, 4;
   u.id, x, y, a[0] = 1, 2, 4, 8;
}
```

1. Assignment is not executed as multiple independent statements. The left operands are evaluated first, then the right
   tuple, then the assignments are performed.
2. Different types can be assigned together.

#### Variable Initialization

The initialization assignment on the right side of a [variable declaration statement](#variable-declaration-statement)
is optional. The right tuple is evaluated first, then assigned to the left variables:

```feng
func test() {
   var a,b,c = 1, "ggyy", 1.6;
}
```

The original types must match. For arrays, copying follows the smaller length.

### Variable Declaration Statement

Declaring one or a group of [variables](#variables) starts with `var` or `const`, followed by the variable name(s), then
the variable type.

1. `var` declares an ordinary variable, optionally with an initial value.
2. `const` defines immutable values; they cannot be reassigned and must be initialized at declaration.

```feng
func test() {
    var r int = 5;
    var g float64;
    var a float64 = 0;
    const pi float64 = 3.1415926;
    const pi = 3.1415926; // When initialized, type can be omitted
    g = 2 * r * pi;
    a = r * r * pi;
}
```

Since the type can be omitted, two cases arise:

1. When omitted, the right side can contain expressions of different types; the corresponding variable types are
   inferred independently.
2. If types are explicitly specified, all left variables share the same type, and the right expressions must be
   compatible.

```feng
func test() {
   var a,b int = 1,2;
   // var a,b int = 1, "ggyy"; // Error: must be split into two statements
}
```

### Exception Statements

Two types: throwing and handling [exceptions](#exceptions-_[Incomplete]_).

#### Throwing Exceptions

Throwing an exception handles errors not addressed by return values. After throwing an exception:

1. Execution of the current procedure terminates; instead of executing the return statement, an instance containing
   error information is thrown.
2. If a called procedure throws an exception A, execution of the current procedure terminates at the call site, and
   exception A continues to be thrown.

```feng
func example1() {
   throw new(Exception);
}
func example2() {
    example1();
    println("example1() always throws an exception, so this line will not execute!");
}
func example3() {
    example2();
    println("example2() also throws an exception, so this line will not execute!");
}
```

Once an exception is thrown, it propagates up the call chain until caught by a `catch` block.

The type of the thrown exception must be defined by the user, as detailed in [Exceptions](#exceptions-_[Incomplete]_).

#### Handling Exceptions

Exception handling statements consist of three parts:

1. `try` block: mandatory, wraps the code to be monitored.
2. `catch` blocks: multiple allowed, each matching a different exception type. When matched, the corresponding code
   block executes; otherwise, matching continues.
   If no block matches, the exception continues to propagate outward.
3. `finally` block: executes regardless of whether an exception occurred or was caught.
   If a `return` statement exists in the `try` block, the expression after `return` is evaluated first, then the
   `finally` block executes, then the return is completed.
   If no `catch` block or no match, the `finally` block executes before re-throwing.

At least one of parts 2 and 3 must be present.

Complete example:

```feng
func calc() {
   try {
      step1();
      step2();
   } catch(e *NilPointerError) {
      println("Caught null pointer");
   } catch(e *IllegalStateError | *IllegalArgumentError) {
      println("Caught state error or argument error");
   } finally {
      println("Finally, execution continues after here");
   }
   return getResult();
}
```

With `catch` but no `finally`:

```feng
func calc() {
   try {
      step1();
   } catch(e *IllegalStateError) {
      println("Caught state error or argument error");
   }
   return getResult();
}
```

With `finally` but no `catch`:

```feng
func calc() {
   try {
      step1();
      step2();
      return getResult();
   } finally {
      println("Finally, execution continues after here");
   }
}
```

`finally` can be used to release external resources, avoiding resource leaks. Example: closing a file:

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

Note: The parameter `e` in `catch` matching parentheses is a constant parameter.

## Variables

Refer to [Variable Declaration Statement](#variable-declaration-statement) for declaration syntax.

Variables can be declared in two ways: mutable `var` and immutable `const`. The difference is that `const` cannot be
modified after its first assignment.

### Types of Variable Values

Variables have three categories: value types, reference types, enumerations, and function prototypes.

#### Value Type Variables

The variable and the instance are one; the variable's value is the instance itself. Assignment copies the instance's
data:

1. Basic type variables are just register values; modification usually requires a single machine instruction:
   ```feng
   var a int = 1; // Variable a assigned literal 1, so a's value is 1
   var b int = a; // Variable b assigned variable a, copying a's value to b
   b = 2; // a and b are different variables; modifying one does not affect the other
   ```
2. Derived types typically occupy more space than a register, so implementation often requires a set of instructions to
   copy all field data:
   ```feng
   class Vector { var x,y,z float64; }
   var a Vector = { x=1.0, y=0, z=-1.0 };
   var b Vector = a; // Like basic types, copy all field data from a to b
   b.x += 2.0; // Modifying b does not affect a; a.x remains '1.0'
   ```
3. [Fixed-length array](#fixed-length-array) assignment is equivalent to iterating over all elements and assigning each:
   ```feng
   var a [4]int = [1,2]; // Initialize each element; unspecified ones get default (0 for int)
   var b [4]int;
   b = a; // Copy data from a to b
   // Equivalent to loop assignment
   for (var i = 0; i < a.size; i++) b[i] = a[i];
   b[0] += 10; // Modifying b[0] does not affect a; a[0] remains '1'
   ```
   For arrays of derived types, if the elements are value types, they are copied as well:
   ```feng
   var a [4]Vector = [{x=1.0}, {x=2.0}]; // Initialize each element; unspecified get defaults (all fields 0)
   var b [4]Vector;
   b = a; // Copy data from a to b
   // Also equivalent to loop assignment
   for (var i = 0; i < a.size; i++) 
       b[i] = a[i]; // Assignment refers to point 2 above
   b[0].x += 5.0; // Modifying b[0].x does not affect a; a[0].x remains '1.0'
   ```

#### Reference Type Variables

Reference type variables are separate from instances; assignment changes the reference's target.

The instances a variable can reference are subject to type safety constraints:

1. [Class](#classes) and [interface](#interfaces) references have [polymorphism](#polymorphism)
   and [abstraction](#abstraction) constraints.
2. [Interface](#interfaces) references to class instances have abstraction compatibility constraints.

For example, `Device` and `Bus` cannot be cross-referenced even if they have identical structure:

```feng
class Device {}
class Bus {}
func test() {
    var a *Device = new(Device);
    var b *Bus = new(Bus);
    // a = b;   // Error: Device reference cannot reference a Bus instance
}
```

##### Non-Null Reference

References are nullable by default (can be `nil`). They can be marked as non-null (with `!`). Passing is one-way:
non-null → nullable.
For reverse passing, the variable must be explicitly checked for non-null (supported only for local variables, not
fields):

```feng
func f(a *!int, b *int) {
   var x *int = a;
   // var y *!int = b; // Error: cannot pass directly
   if (b != nil) {
      var y *!int = b; // Must explicitly check non-null; pass within the non-null branch
   }
}
```

##### Immutable Reference

References can be marked as immutable (with `#`). Immutable references cannot modify the instance. Passing is one-way:
mutable → immutable.

```feng
class Foo { var id int; }
func f(a *int, b *Foo) {
   var x *#int = a;  // Convert to immutable reference
   // *x = 1; // Error: cannot modify immutable instance
   var y *#Foo = b;
   // y.id = 1; // Error: cannot modify immutable instance
}
```

Immutable references cannot be passed in reverse.

##### Dereference Operation

Except for array references, other references support the dereference operator `*`, which operates directly on the
referenced instance, both for reading and writing:

1. Reading retrieves the instance's value and assigns it to a value type variable:
   ```feng
   class Complex {
      var real, imag float;
   }
   func test(a &int, b *Complex) {
      var x int = *a;
      var y Complex = *b;
   }
   ```
2. Writing directly modifies the instance; immutable references cannot be written to:
   ```feng
   class Complex {
      var real, imag float;
   }
   func test(a &int, b *Complex, c &#Complex) {
      *a = 1;
      *b = {real=1.0, imag=-1.0};
      // *c = {}; // ✖ Immutable
   }
   ```

##### Reference Types

###### Strong Reference Type

Strong references are denoted by `*` followed by a type symbol, e.g., `var aDev *Device;` declares a strong reference
variable `aDev`.
It can point to an instance of class `Device` or an instance of a [subclass](#polymorphism) of `Device`:

```feng
func test() {
    var b *Device = new(Device);    // Initialized to point to a new Device instance
    var a *Device = b;              // Pass the instance referenced by b to a
    a.speed = 10;                   // Modifications through a and b affect the same instance
    printf("speed=%d", b.speed);  // Prints: speed=10
}
```

Constant references declared with `const` must be initialized to point to an instance (or `nil`) and cannot change their
target:

```feng
const a *Bus = new(Bus);
// a = new(Bus); // ✖
// a = nil; // ✖
```

[Variable-length arrays](#variable-length-array) are also reference type variables; they can reference array instances
of the same element type but any length.

Strong references indicate to the automatic memory manager whether an instance is in use:

* Instances referenced by strong references cannot be reclaimed.
* When an instance has no strong references, it should be reclaimed.

###### Phantom Reference Type

Phantom references do not affect memory deallocation.
They can reference dynamically created instances or instances of value type variables, but only under certain
conditions.

Phantom reference variables are constants and can only be declared with `const`:

```feng
func test() {
    var gh Host;
    const h1 &Host = gh;
}
```

Phantom references can only be local variables or parameters. They can be passed in the following scenarios:

1. Value type variables within scope can be referenced by phantom references.
2. Constant reference variables within scope can pass their instances to phantom references.
3. Phantom references can pass instances to new phantom references.
4. Local variables of strong reference type, after being passed to a phantom reference, cannot be modified within the
   phantom reference's scope.
5. Within a class instance's phantom-reachable scope:
    1. Its value type fields can be phantom-referenced.
    2. Instances referenced by its constant fields can be phantom-referenced.

Global variables can be referenced from any code:

```feng
var gDrv Driver;
const rDrv *Driver = new(Driver, {});
func use() {
    const d1 &Driver = gDrv;
    const d2 &Driver = rDrv;
}
```

Local variables must be used within scope for phantom references:

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

Fields of instances can be phantom-referenced:

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

**Phantom references are restricted to the above scenarios.** For example, function return values cannot be of phantom
reference type.

#### Enumeration Variables

Refer to [Enumerations](#enumerations) for details.

#### Function Prototype Variables

Such a variable is either null or points to a function. Refer to [Function Prototype](#function-prototype) for details.

### Constants

Constants' immutability means the variable's value cannot change:

1. For value type constants, all content is immutable:
    1. Each element of a constant array is constant.
    2. Fields of constant classes and structs cannot be modified.
2. Reference type constants, once declared and initialized, can only point to a single instance until they go out of
   scope.

```feng
class Vector { var x,y,z int; }
class Data { var ve Vector; }
func test() {
   const vec Vector = {x=1.0,y=2.0,z=3.0};
   // vec.x = 4.0; // Error
   const vecs [4]Vector = [{x=1.0,y=2.0,z=3.0}];
   // vecs[1].x = 4.0; // Error
   const data Data = {v={x=1.0,y=2.0,z=3.0}}; 
   // data.ve.x = 4.0; // Error
}
```

### Variable Scope

Scope is the region where a variable is valid: a variable's lifetime begins at its declaration and ends when it leaves
its scope.
Scope is generally either local or global.

#### Local Variables

Local variables are declared within functions or methods:

1. Their scope is the code block where they are declared and any nested inner blocks.
2. Variables cannot be redeclared with the same name at the same level.
3. When an inner block declares a variable with the same name (type may differ), the outer variable is shadowed and
   cannot be used.

```feng
func test() {
   var v = "Hello"; // v's lifetime is within the current function
   {
      var s = "Fèng!"; // s's lifetime is within this block
      printf("%s %s\n", v, s);   // Can access outer variable v
   }
   // printf("%s %s\n", v, s);  // Error: cannot access s from inner block
   {
      // printf("%s %s\n", v, s);  // Error: cannot access s from another block
   }
   {
      var v = "Dear Fèng"; // Inner redeclaration shadows outer v
      printf("%s\n", v); // Prints: Dear Fèng
   }
   // var v = "Fèng"; // Error: cannot redeclare
}
```

#### Global Variables

Global variables must be placed at the topmost level of code, outside function and type definitions.
Both variables and constants must be initialized.
Their scope is global, and their lifetime is the entire runtime.

```feng
var count int = 0;
var qps int = 0;
var avg float64 = 0.0;
func doCount() int {
   count+=1;
   return count;
}
```

`export` can be used to make them available to other modules:

```feng
export const PI float64 = 3.1415926;
export var delay int = 0;
```

*Here, the lifetime is defined as runtime.*

## Literals

### Integer Literals

### Real Number Literals

### Boolean Literals

`bool` literals are only `true` or `false`.

### Null Literal _[Incomplete]_

The null value is `nil`, representing the initial value of variables or fields—not pointing to any instance.
Applicable to [reference type variables](#reference-type-variables)
and [function prototype variables](#function-prototype-variables).

### String Literals

Strings are not basic types; the compiler encodes string literals.
String literals are string constants and cannot be modified, so they can only be referenced by immutable variables.
String constants are not allocated on the function stack but are placed in a constant region:

```feng
func moduleName() [*#]byte {
    var r [*#]byte = "test-module";
    return r; // Still usable after leaving function moduleName
}
func test() {
    printf("module: %s\n", moduleName());
}
```

### Array Literals

List array elements within square brackets: `[1,2,3]`, `["Hello", "Good"]`, etc.
The array element type must be compatible with all elements; if no compatible type exists, it is disallowed.

## Tuples _[Incomplete]_

Tuples are special internal language types consisting of a group of elements. Variables of tuple type cannot be
explicitly declared.

### Usage

For functions or methods with multiple return values, if they return an array, it cannot be automatically destructured
into multiple variables; tuples handle this.

```feng
func getValue(key int) (int, bool) {
   var node = get(key);
   if (node == nil) return 0, false;
   return node.value, true;
}
```

Simultaneously assign to multiple operands:

```feng
func test() {
   u.id, ok = 1, true;
}
```

Also usable in variable declarations:

```feng
func test() {
   var id, ok = 1, true;
}
```

### Special Tuples

The result of calling a function or method is a tuple, which can be used directly as a tuple.

```feng
func result(e int, r *Res) (int, *Res) {
   return e, r;
}
func success(r *Res) (int, *Res) {
   return result(0, r);
}
```

Functions or methods with multiple return values cannot participate in expression evaluation, but single-return
functions can (the compiler should automatically unpack):

```feng
func sin(x float64) float64 {    // Single return value can be returned as a tuple or used in expressions
   // TODO:……
}
func cos(x float64) float64 {
   return sqrt(1 - sin(x)^2); // Needs to automatically unpack tuple to single value
}
```

`if` tuples are similar to `if` statements but return a tuple directly instead of a statement:

```feng
func getValue(key int) (int, bool) {
   var node = get(key);
   return if (node == nil) 0, false else node.value, true;
}
```

And `switch` tuples corresponding to `switch` statements:

```feng
func createIf(c int) (bool, *Object) {
   return switch(c) {
   case 0: true, new(Res);
   default: false, nil;
   };
}
```

Tuples of different types can be nested and combined:

```feng
func createIf(c int) (bool, *Object) {
   return if (c > 0) false, nil 
      else if (c < 0) true, new(Res)
      else true, new(Res, {code=1000});
}
```

## Macros

Macros are code snippets with specific formats determined by their specific purpose.
A specific purpose refers to some language feature; for example, when a macro is used to
implement [custom operations](#custom-operations), the operation itself defines the code format.
Currently, macros are only supported within classes and interfaces.

Macros are uniformly defined with the `macro` keyword. The main types are procedural macros and class macros.

### Procedural Macros

Procedural macros resemble ordinary procedures (functions or methods), with a name, parameter list, and sequence of
statements:

- Names do not interfere with other names; they can share names with other elements.
- Parameter lists differ from function parameters; they are context variables.
- The statement sequence is an ordinary sequence, optionally ending with an expression.
- Macros cannot be called.

For general grammar examples, please refer to [Custom Operations](#Custom-Operations).

### Class Macros _[Incomplete]_

Consist of a name, field table, and procedural macros, capable of preserving intermediate state.
Example: implementation of [iteration loops](#iteration-loop) for derived types.

## Generics

Generics are general, common types, similar to C++ templates, used to implement generic classes and functions.
Unlike C++, generics here are checked before expansion, so no specific operations can be performed before expansion—only
values can be passed.

Generics are marked with backticks (\`).

Generic parameters are specified during definition:

```feng
class Box`T`{var t T;}
func save`T`(t T) {}
```

Concrete types are passed during use:

```feng
func f() {
   var ib Box`int` = {t=100};
   save`int`(100);
}
```

Multiple parameters can be specified:

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

Generic parameters can be defined for functions, interfaces, classes, and class methods.

Besides reducing boilerplate code, generics can solve self-dependency issues, e.g.:

```feng
var bb Box`Box`int``;
var bbb Box`Box`Box`int```;
```

Without generics, the `Box` class above would fall into recursive initialization and fail to compile:

```feng
class Box {
   var t Box;
}
```

### Generic Functions

Generic parameters defined on functions can be used as types within the function body:

```feng
func go`R`(r R) {
   var v R = r;
}
```

Concrete types can be passed arbitrarily:

```feng
func test(i int, b bool, a [16]byte, r *A) {
   go`int`(i);
   go`bool`(b);
   go`[16]byte`(a);
   go`*A`(r);
}
```

Within another generic function, a generic parameter can be passed:

```feng
func run`P`(p P) {
   go`P`(p);
}
```

### Generic Classes

Generic parameters defined on classes can be used in fields, methods, and within method bodies:

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

The box class defined above can hold any instance:

```feng
func use() {
   var box Box`[*]int`;
   box.set(new[15]int);
   box.get()[0] = 100;
}
```

Class methods can also have their own generic parameters, like functions:

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

Methods with generics do not support polymorphism; they cannot be overridden or override other methods.

When inheriting a generic class, you can pass concrete types or generic parameters:

```feng
class Pair`K,V` {
   var k K;
   var v V;
}
class MyPair`V` : Pair`int,V` {
   // This class actually has only one generic parameter 'V'
}
func use() {
   var p1 MyPair`*int` = {k=1,v=new(int)};
}
```

Interface implementation follows the same pattern:

```feng
class Node`T` (Inode`T`) {
}
```

### Generic Interfaces

Generic parameters for interfaces can only be defined on the type itself; methods do not support them:

```feng
interface Box`V` {
   set(V);
   get()V;
}
```

Example implementing class:

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

## Exceptions _[Incomplete]_

An exception class that can be thrown needs to define the `#error` macro `tracestack`, which tracks and collects stack
information:

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

## Attributes _[Incomplete]_

---

This completes the translation of the modified Fèng programming language syntax documentation.