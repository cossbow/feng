# Fēng Language Specification

> **Purpose**: This document is the canonical specification for the Fēng programming language.
> It is designed to be consumed by AI agents without requiring separate example code.
> Each construct follows a fixed template: **Syntax → Semantics → Constraints → Edge Cases**.

---

## 1. Lexical Structure

### 1.1 Source Files & Encoding

[MUST] Source files use UTF-8 encoding. Line endings are LF or CRLF.
[MUST] A source file consists of a sequence of import declarations, followed by global definitions.

### 1.2 Identifiers

```
Identifier = IdentifierStart { IdentifierStart | Digit }
IdentifierStart = "a".."z" | "A".."Z" | "_"
Digit = "0".."9"
```

[MUST] Identifiers are case-sensitive. Keywords are reserved and cannot be used as identifiers.

### 1.3 Keywords

Complete list of reserved keywords:

| Category | Keywords |
|----------|----------|
| Module | `import`, `export` |
| Declaration | `var`, `const` |
| Type | `struct`, `union`, `enum`, `class`, `interface`, `attribute` |
| Function | `func`, `return`, `macro` |
| Control | `if`, `else`, `for`, `switch`, `case`, `default`, `break`, `continue` |
| Exception | `throw`, `try`, `catch`, `final`, `assert` |
| OOP | `this`, `super` |
| Memory | `new`, `sizeof`, `nil` |
| Concurrency | `static` (reserved, not yet implemented) |
| Boolean | `true`, `false` |
| Language name | `Feng`, `feng`, `FENG` |

_Note: `let` is reserved in the grammar but has no defined semantics._
_Note: `final` serves both as a class modifier (`class Foo final`) and as the `try`-`catch`-`final` clause._

### 1.4 Operators & Punctuation

```
Separators:  ( ) { } [ ] ; , . .. ... : ? @ # $ ` \
Assignment:  = := &&= ||= += -= *= /= %= &= |= ~= <<= >>=
Arithmetic:  + - * / % ^
Relational:  < <= == != > >=
Logical:     ! && ||
Bitwise:     & | ~ << >>
Other:       <- ->
```

### 1.5 Literals

#### Integer Literals
```
DecimalInteger = "0" | ("1".."9" {Digit})
HexInteger     = "0" ("x"|"X") HexDigit {HexDigit}
OctalInteger   = "0" ("o"|"O") OctalDigit {OctalDigit}
BinaryInteger  = "0" ("b"|"B") ("0"|"1") {("0"|"1")}
```

[MUST] Integer literals default to type `int`. [MAY] They are compatible with any integer type if within range.

#### Float Literals
```
FloatLiteral = Digits "." [Digits] [ExponentPart]
             | Digits ExponentPart
             | Digits "."
ExponentPart = ("e"|"E") ["+"|"-"] Digits
```

[MUST] Float literals default to type `float64`.

#### String Literals
```
StringLiteral = '"' {StringCharacter} '"'
StringCharacter = any character except '"', '\', or newline | EscapeSequence
```

[MUST] String literals reside in the constant data section. They have type `[*#]byte` (variable-length, unmodifiable byte array) and never become invalid after leaving scope.

#### Bool Literals
[MUST] `true` and `false` are the only values of type `bool`.

#### Nil Literal
[MUST] `nil` represents the null value for reference types and function prototype types.

### 1.6 Comments

```
LineComment  = "//" {any character except newline}
BlockComment = "/*" {any character} "*/"
```

[MAY] Comments are ignored by the compiler.

---

## 2. Module System

### 2.1 Module = Directory

[MUST] A module corresponds to a directory. The module name is the directory name. No explicit module declaration exists in source files.
[MUST] Directory names follow the same rules as identifiers.
[MUST] Module path separator is `$`. The module `com$jjj$base$util` maps to the relative path `com/jjj/base/util`.

### 2.2 Import

**Syntax:**
```
import ModulePath [Alias] ;
ModulePath = Identifier {"$" Identifier}
```

**Semantics:**
1. [MUST] Imports all exported symbols from the specified module.
2. [MUST] To access an imported symbol, prefix it with the module's last segment and `$`: e.g., `fmt$println(...)`.
3. [MAY] An alias can be specified: `import std$math m;` allows `m$sin(...)`.
4. [MUST] Circular imports are forbidden (dependency graph must be a DAG).

### 2.3 Export

**Syntax:**
```
export Declaration
```

**Semantics:**
1. [MUST] Only exported symbols are visible to other modules.
2. [MUST] For classes: members default to NOT exported. Each member must be individually exported.
3. [MUST] For interfaces: all methods are automatically exported with the interface.
4. [MUST] For struct/union: all fields are automatically exported with the struct.
5. [MUST] For enums: all values are automatically exported with the enum.

### 2.4 main Function

[MUST] A module containing a `func main()` is compiled into an executable.
[MUST] A module with `main()` cannot be imported by other modules.
[MUST] Modules without `main()` are libraries and can be imported.

---

## 3. Type System

### 3.1 Primitive Types

#### Integer Types

| Type | Width | Signed |
|------|-------|--------|
| `int8` | 8 | Yes |
| `int16` | 16 | Yes |
| `int32` | 32 | Yes |
| `int64` | 64 | Yes |
| `int` | platform-dependent | Yes |
| `uint8` | 8 | No |
| `uint16` | 16 | No |
| `uint32` | 32 | No |
| `uint64` | 64 | No |
| `uint` | platform-dependent | No |

[MUST] Conversions between integer types must be explicit: `int32(a)`.
[MUST] Signed-to-unsigned conversion copies the sign bit into the corresponding data bit, which may change the numeric value.
[MUST] Narrowing conversions (larger width to smaller) cause truncation.
[MAY] Overflow is not checked by the compiler; it is the programmer's responsibility.

#### Float Types

| Type | Width |
|------|-------|
| `float32` | 32-bit IEEE 754 |
| `float64` | 64-bit IEEE 754 |

[MUST] Float types support arithmetic and relational operations. [MUST] They do not support bitwise operations.

#### Bool Type

| Type | Values | Size |
|------|--------|------|
| `bool` | `true`, `false` | 1 byte |

[MUST] Only the least significant bit is used (0 = false, 1 = true); other bits are ignored.
[MUST] `bool` does NOT convert to/from integer or float types.
[MUST] `bool` supports logical operations (`!`, `&&`, `||`) and bitwise operations (`&`, `~`, `|`) when both operands are `bool`.
[MUST] `&&` and `||` are short-circuiting: the right operand is not evaluated if the left operand determines the result.

### 3.2 Type Declarer Syntax

```
TypeDeclarer      = PrimaryTypeDeclarer | ArrayTypeDeclarer
ArrayTypeDeclarer = "[" ArrayType "]" TypeDeclarer
ArrayType         = expression | refer
PrimaryTypeDeclarer = DefinedTypeDeclarer | FuncTypeDeclarer | TupleTypeDeclarer
DefinedTypeDeclarer = [refer] DefinedType
refer             = ("*" | "&") ["?"] ["#"]
FuncTypeDeclarer  = ["?"] "func" prototype | ["?"] DefinedType
TupleTypeDeclarer = "(" TypeDeclarer {"," TypeDeclarer}+ ")"
DefinedType       = symbol [typeArguments]
typeArguments     = "`" TypeDeclarer {"," TypeDeclarer} "`"
```

### 3.3 Reference Markers

There are two kinds of references, both default to **non-null**:

1. **Single-instance reference** — `*T`, points to one instance.
2. **Array reference** — `[*]T`, points to a variable-length array (multiple instances laid out side by side).

Both can be made nullable by placing `?` directly after the `*`: `*?T` and `[*?]T`.

| Marker | Meaning | Syntax Example |
|--------|---------|---------------|
| `*` | Strong reference (affects memory management) | `*int` |
| `&` | Phantom reference (does not affect lifetime) | `&int` |
| `?` | Nullable (default is non-null) | `*?int` |
| `#` | Unmodifiable (const through this reference) | `*#int` |

#### General Reference Rules

1. [MUST] Non-null → Nullable is allowed. Nullable → Non-null requires explicit `!= nil` check.
2. [MUST] Modifiable → Unmodifiable is allowed. Reverse is forbidden.

#### Strong References (`*T`)

1. [MUST] Strong references can only reference instances created via `new`.
2. [MUST] An instance referenced by at least one strong reference variable is kept alive by the memory manager.
3. [MUST] When an instance has no strong references pointing to it, it is eligible for reclamation.

#### Phantom References (`&T`)

Phantom references do NOT affect memory management. They can reference both
`new`-allocated instances and value-type instances.

A phantom reference can only point to an **"immobile" instance** — one that can be
proven not to be freed or moved during the phantom reference's lifetime. The following
rules enumerate the cases where immobility is guaranteed.

**Declaration and scope:**
1. [MUST] Phantom reference variables are always `const` — declared with `const` keyword.
2. [MUST] Phantom references can only be local variables or parameters. They cannot be fields or global variables.
3. [MUST] `@Sync` cannot be applied to phantom references.

**What can be assigned to a phantom reference:**
4. [MUST] Value-type variables that are currently in scope.
5. [MUST] Const reference variables (both strong and phantom) that are currently in scope.
6. [MUST] An existing phantom reference can be passed to a new phantom reference of a compatible type.
7. [MUST] When a local strong reference is aliased by a phantom reference, the strong reference becomes immutable for the phantom's lifetime.
8. [MUST] Within the scope where a class instance is accessible via phantom reference:
   - Its value-type fields can be phantom-referenced.
   - Instances referenced by its const fields can be phantom-referenced.
9. [MUST] Phantom reference **parameters** have a unique capability: they may also reference temporary instances — literals, initialization expressions, `new`-created instances, and return values — that would otherwise be destroyed after the call.

**Examples:**

*Global variables — always in scope:*
```feng
var gDrv Driver;
const rDrv *Driver = new(Driver);
func use() {
    const d1 &Driver = gDrv;   // value-type global → phantom
    const d2 &Driver = rDrv;   // strong-ref global → phantom
}
```

*Local variables — must be in scope:*
```feng
func sample1() {
    var drv Driver;
    const d1 &Driver = drv;           // value-type local → phantom
}
func sample2() {
    const drv *Driver = new(Driver);
    const d1 &Driver = drv;           // strong-ref local → phantom
}
func sample3() {
    var drv *Driver = new(Driver);
    {
        const d1 &Driver = drv;       // aliased — drv is immutable here
        // drv = nil;                 // ✖ forbidden while d1 is in scope
    }
    drv = nil;                        // ✔ allowed after d1 out of scope
}
```

*Class fields — via phantom reference chain:*
```feng
class Device {
    const driver *Driver;
    var disk Disk;
}
func sample1(dev Device) {            // value-type parameter
    const drv &Driver = dev.driver;   // const field → phantom
    const dk  &Disk   = dev.disk;     // value-type field → phantom
}
func sample2(dev *Device) {           // strong-ref parameter
    const drv &Driver = dev.driver;
    const dk  &Disk   = dev.disk;
}
```

*Phantom reference parameters — accept temporaries:*
```feng
func use1(a &int)    { /* ... */ }
func use2(a &Device) { /* ... */ }
func use3(a [&]int)  { /* ... */ }

func sample() {
    use1(0);                         // literal
    use1(new(int));                  // new-created
    use1(get());                     // return value
    use2(Device{});                  // initialization expression
    use2(new(Device));               // new-created
    use3([]int[1,2]);               // array literal
    use3(new([2]int));              // new-created array
}
func get() int { return 1; }
```

### 3.4 Function Prototype Types

**Syntax:**
```
FuncTypeDeclarer = ["?"] "func" prototype
prototype = "(" [parametersSet] ")" [returnSet]
```

**Semantics:**
1. [MUST] A function prototype variable is either `nil` or points to a function with a compatible signature.
2. [MAY] Nullable marker `?` prefix allows `nil` assignment.
3. [MUST] A nullable prototype cannot be called; must check `!= nil` first.
4. [MUST] Prototype types support covariant return types (subclass or implementation in return position).

### 3.5 Generics

**Definition Syntax:**
```
typeParameters = "`" TypeParameter {"," TypeParameter} "`"
TypeParameter  = Identifier [TypeConstraint]
```

**Rules:**
1. [MUST] Generics are checked before expansion; no concrete operations can be performed on generic parameters.
2. [MUST] Generics can be defined on: functions, classes, interfaces, and class methods.
3. [MUST] Interface generics can only be on the type, not on individual methods.
4. [MUST] Generic methods do NOT support polymorphism (cannot override or be overridden).
5. [MUST] Type inference is supported: type parameters are inferred from argument types, return type context, or the left-hand side of assignments.

---

## 4. Declarations & Definitions

### 4.1 Variables

**Syntax:**
```
declaration = VarSpec {"," VarSpec} [":" TypeDeclarer] ["=" ExpressionList]
VarSpec     = Identifier
```

[MUST] `var` declares a mutable variable. [MAY] Initialize with `=`. Without initialization, the variable is zeroed (references get `nil`) — except non-null references, which [MUST] be explicitly initialized with a non-null value.
[MUST] `const` declares an immutable variable. [MUST] Must be initialized at declaration. Cannot be reassigned.
[MUST] Type inference: if type is omitted and initializers are present, types are inferred from expressions.

**Scope rules:**
1. [MUST] Local variables are scoped to the enclosing block.
2. [MUST] Redeclaration of the same name in the same block is forbidden.
3. [MUST] Inner blocks may shadow outer names by redeclaring.
4. [MUST] Global variables must be at the top level and must be initialized.
5. [MUST] Global variable lifetime equals runtime lifetime.

### 4.2 Functions

**Full Syntax:**
```
functionDefinition = [modifier] "func" Identifier [typeParameters] ["*"] ["#"] prototype block
prototype          = "(" [parametersSet] ")" [returnSet]
parametersSet      = parameters ["," "..."] | TypeDeclarerList
parameters         = Parameter {"," Parameter}
Parameter          = IdentifierList TypeDeclarer
returnSet          = TypeDeclarer | "this"
```

**Modifier markers on function name:**
| Marker | Before parentheses | Meaning |
|--------|-------------------|---------|
| `*` | Escape marker | `this` can be used as strong reference inside the method; can only be called via strong reference |
| `#` | Unmodifiable marker | `this` is unmodifiable; cannot modify fields; can only call other `#` methods |

**Parameter rules:**
1. [MUST] Parameters are implicitly `const`; they cannot be reassigned.
2. [MUST] Adjacent parameters of the same type may share a type declaration: `func add(a, b int)`.

**Return rules:**
1. [MUST] If a return type is declared, ALL reachable exit paths must end with a `return` statement carrying a value of a compatible type.
2. [MUST] All `return` statements must carry a value. An empty `return;` is only valid in void functions.
3. [MUST] An infinite loop (condition known at compile time to always be `true`) counts as a terminating statement.

**Variadic functions:**
1. [MUST] `...` at the end of the parameter list declares variadic parameters.
2. [MUST] Currently variadic parameters can only be forwarded to another variadic parameter (used for wrapping `format`).
3. [MUST] Only one variadic group is allowed, and it must be at the end.

### 4.3 Classes

**Syntax:**
```
classDefinition = [modifier] "class" Identifier [typeParameters] classExtension "{" {classMember} "}"
classExtension  = ["final"] [classInherit] [classImpl]
classInherit    = ":" DefinedType
classImpl       = "(" DefinedType {"," DefinedType} ")"
classMember     = classMemberFields | classMemberMethod | macro
classMemberFields = ("var" | "const") identifierList typeDeclarer ";"
classMemberMethod  = functionDefinition
```

**Core rules:**
1. [MUST] Can be instantiated as a value type or via `new` for strong references.
2. [MUST] Fields: all variable types are allowed except phantom references (`&`).
3. [MUST] Field order does NOT correspond to memory layout order.
4. [MUST] `const` fields must be initialized at instantiation.
5. [MUST] A class with unexported `const` fields cannot be instantiated from other modules.
6. [MUST] `this` refers to the current instance inside methods.
7. [MUST] `super` refers to the direct parent class.

#### Inheritance
1. [MUST] Single inheritance only. For non-final classes, the root class is `Object` (built-in, no members). Final classes do NOT inherit from `Object`.
2. [MUST] Child classes cannot declare fields with the same name as parent fields.
3. [MUST] Methods can be overridden if they have the same prototype (name + parameters + return type variants for covariance).
4. [MUST] Method name uniqueness: a method name in a class must be unique across inherited and own methods; overriding is the exception.
5. [MUST] A final class can only inherit from a final class; a non-final class can only inherit from a non-final class.

#### Polymorphism
1. [MUST] A parent-class reference can point to a child-class instance (covariance). This applies only to non-final classes.
2. [MUST] Method dispatch is dynamic: calling through a parent reference invokes the child's override.
3. [MUST] Covariant return types: a child method's return type can be a subclass of the parent method's return type.
4. [MUST] Parameters and escape/unmodifiable markers must match exactly.
5. [MUST] Covariance (polymorphism and abstraction) does NOT apply to final classes; reference types cannot be converted to or from a final class.

#### final Class

[MUST] A class declared with the `final` keyword is a final class. Classes without `final` are non-final classes.

1. [MUST] `Object` is a non-final class.
2. [MUST] A final class can only inherit from a final class; a non-final class can only inherit from a non-final class. Consequently, a final class cannot inherit from a non-final class, and a non-final class cannot inherit from a final class.
3. [MUST] A final class does NOT inherit from `Object` (because `Object` is non-final and final classes can only inherit final classes).
4. [MUST] A final class can implement any interface.
5. [MUST] A final class does not support covariance (polymorphism and abstraction); reference types cannot be converted to or from it.
6. [MUST] Since covariance is unsupported, `is` expressions involving a final class are not allowed.

#### Resource Class
[MAY] A class with a `macro resource free()` method is a resource class. The macro is called when the instance is released.
[MUST] Resource classes can only be instantiated via `new` (to avoid double-free from value-type copies).

### 4.4 Interfaces

**Syntax:**
```
interfaceDefinition = [modifier] "interface" Identifier [typeParameters] "{" {interfaceMember} "}"
interfaceMember     = interfaceMemberMethod | interfaceMemberPart | macro
interfaceMemberMethod = name "(" [parametersSet] ")" [returnSet] ";"
```

**Rules:**
1. [MUST] Interfaces cannot be instantiated. Interface variables must be references.
2. [MUST] An interface is a set of method prototypes. Methods omit the `func` keyword.
3. [MUST] Interface composition: including another interface name in the body merges its methods.
4. [MUST] Method name conflicts: if composed interfaces have methods with the same name, they are treated as one; prototypes must be compatible.
5. [MUST] A composed interface can be passed where a component interface is expected.
6. [MUST] A class implementing an interface must provide all methods.
7. [MUST] Both final and non-final classes can implement interfaces. However, since covariance applies only to non-final classes, an interface reference can only point to a non-final implementing class instance.

### 4.5 Enums

**Syntax:**
```
enumDefinition = [modifier] "enum" Identifier "{" enumValue {"," enumValue} [","] "}"
enumValue      = Identifier ["=" expression]
```

**Rules:**
1. [MUST] Enum values are finite and defined at compile time.
2. [MUST] Trailing comma is OPTIONAL (but common in examples).
3. [MUST] Default value for enum variables/array elements is the first enum value (id=0).

**Built-in properties:**

| Property | Type | Description |
|----------|------|-------------|
| `.id` | `int` | Auto-incrementing integer, starts at 0, follows definition order |
| `.name` | `[*#]byte` | The literal name of the enum value as a string |
| `.value` | `int` | Custom integer value; defaults to id if not explicitly set |

**Rules for enum values:**
1. [MAY] Enum values can be used with or without type prefix when the type is clear from context.
2. [MUST] `switch` on an enum: if not all values are covered by `case` branches, a `default` branch is required. If all are covered, `default` is forbidden.
3. [MUST] Enum types support iteration: `for (v : EnumType)`.
4. [MUST] Enum types support indexing: `EnumType[0]` returns the first value; `EnumType[n]` returns a tuple `(value, ok)`.

### 4.6 Structures (struct & union)

**Syntax:**
```
structureDefinition = "struct" | "union" Identifier "{" {structureFieldsDef} "}"
structureFieldsDef  = fields type ";" | "[" expression "]" elementType ";"
```

**Rules:**
1. [MUST] Fields can only be integer types, float types, and other struct/union types, plus fixed-size arrays of these.
2. [MUST] Fields cannot be references.
3. [MUST] Struct: fields are laid out sequentially in memory.
4. [MUST] Union: all fields share the same memory (overlapping).
5. [MUST] A union can only initialize one field at a time.
6. [MUST] Field bit-width can be specified: `fieldName(width) type`. Width must be a compile-time constant between 1 and the type's bit width.
7. [MAY] Adjacent same-type fields can share a type declaration: `type, code int;`.
8. [MAY] Anonymous nested struct/union definitions are supported.
9. [MUST] Struct/union memory layout is identical to C for interop.

### 4.7 Arrays

#### Fixed-Size Array
**Syntax:** `[expression] Type`

[MUST] The size expression must be a compile-time constant (integer literal or const expression).
[MUST] Fixed-size arrays are value types. Assignment copies all elements.
[MUST] Initialization: `[4]int[1, 2, 3, 4]`. Fewer elements than capacity → remaining elements are zeroed (not allowed for non-null reference elements, which must all be explicitly initialized).

#### Variable-Length Array (Array Reference)
**Syntax:** `[*]Type`

[MUST] Variable-length arrays are reference types. They are declared as `[*]T` (array reference) but point to instances created via `new`.
[MUST] An array reference defaults to non-null; the nullable form is `[*?]T` (`?` follows the `*`).
[MUST] Creation: `new([size]Type)` where `size` can be runtime expression.
[MUST] The `values` built-in field of a `[*]T` array provides a `uint64` pointer for C interop.

### 4.8 Tuples

**Syntax:** `(Type1, Type2, ...)`

[MUST] At least 2 elements required.
[MUST] All types allowed except phantom references.
[MUST] Tuples are value types.
[MUST] Access elements by index: `tuple.0`, `tuple.1`, etc. (using `.` followed by decimal integer).
[MUST] Initialization with tuple expression: `(false:bool, 0:int)` — each element may have an explicit type annotation.

### 4.9 Macros

**Syntax:**
```
macro = "macro" ("operator" | "helper" | "resource") Identifier macroType
macroType = name "{" fields ";" {macroProcedure} "}"
macroProcedure = name "(" [params] ")" [type] "{" statementList [expression] "}"
```

**Rules:**
1. [MUST] Macros cannot be called like regular functions.
2. [MUST] Macro names do not conflict with other names (functions, methods, fields).
3. [SHOULD] Macro parameters are treated as context variables, not formal parameters.
4. [MUST] Currently only supported in classes and interfaces.

**Known macro types:**
- `macro operator` — custom operators (see §5.12)
- `macro helper Iterator` — custom iteration (see §6.6.3)
- `macro resource free()` — resource class destructor (see §4.3)

---

## 5. Expressions

### 5.1 Operator Precedence

[HIGHEST to LOWEST]:

| Precedence | Operators | Associativity |
|------------|-----------|---------------|
| 1 | `new()`, `(expr)`, literals, primary | — |
| 2 | `is`, indexing `[]`, member `.`, call `()`, block `{}` | Left |
| 3 | `+` (unary), `-` (unary), `!` | Right |
| 4 | `^` (power) | Right |
| 5 | `*`, `/`, `%` | Left |
| 6 | `+`, `-` | Left |
| 7 | `<<`, `>>` | Left |
| 8 | `&` | Left |
| 9 | `~` | Left |
| 10 | `\|` | Left |
| 11 | `<`, `<=`, `==`, `!=`, `>`, `>=` | Left |
| 12 | `&&` | Left |
| 13 | `\|\|` | Left |

[NOTE] `^` (power) is right-associative and binds tighter on the left (`-a^b` = `-(a^b)`) but looser on the right (`a^-b` = `a^(-b)`).

### 5.2 Arithmetic Operators

| Operator | Symbol | Operands |
|----------|--------|----------|
| Power | `^` | Integer, Float |
| Multiply | `*` | Integer, Float |
| Divide | `/` | Integer, Float |
| Modulo | `%` | Integer |
| Add | `+` | Integer, Float |
| Subtract | `-` | Integer, Float |

### 5.3 Bitwise Operators

| Operator | Symbol | Operands |
|----------|--------|----------|
| Bitwise NOT | `!` | Integer (also `bool`) |
| Left shift | `<<` | Integer |
| Right shift | `>>` | Integer |
| Bitwise AND | `&` | Integer (also `bool`) |
| Bitwise XOR | `~` | Integer (also `bool`) |
| Bitwise OR | `\|` | Integer (also `bool`) |

### 5.4 Relational Operators

| Operator | Symbol | Result |
|----------|--------|--------|
| Less than | `<` | `bool` |
| Less or equal | `<=` | `bool` |
| Equal | `==` | `bool` |
| Not equal | `!=` | `bool` |
| Greater than | `>` | `bool` |
| Greater or equal | `>=` | `bool` |

### 5.5 Logical Operators

| Operator | Symbol | Operands | Note |
|----------|--------|----------|------|
| Logical NOT | `!` | `bool` | Same symbol as bitwise NOT |
| Logical AND | `&&` | `bool` | Short-circuit |
| Logical OR | `\|\|` | `bool` | Short-circuit |

[MUST] `&&` and `\|\|` short-circuit: the right operand is not evaluated if the left operand determines the result.

### 5.6 Primary Expressions

| Expression | Syntax | Description |
|------------|--------|-------------|
| Symbol | `identifier` or `mod$identifier` | Variable or function reference |
| `this` / `super` | `this`, `super` | Current instance / parent class |
| Paren | `(expr)` | Grouping |
| Block expression | `{ stmts expr }` | Block with final expression as value |

### 5.7 `new` Expression

**Syntax:** `new(Type [, initExpression])`

**Rules:**
1. [MUST] `Type` is a `DefinedType` or an array type `[size]ElementType`.
2. [MAY] Optional second argument for initialization: field expression for classes/structs, array expression for arrays.
3. [MUST] Returns a strong reference to the newly allocated instance.
4. [MUST] Without init expression, the instance is default-initialized (zeroed) — unless the type contains a non-null reference, in which case an init expression is required.

### 5.8 `is` Expression

**Syntax:** `expression ? (TypeDeclarer)`

**Semantics:**
1. [MUST] Checks if the expression's runtime type is compatible with the target type.
2. [MUST] Returns a nullable reference of the target type; the target type must be a reference type.
3. [MUST] If the conversion fails, returns `nil`. The result must be checked against `nil` before use.
4. [MAY] Used for downcasting in class hierarchies and interface checks.
5. [MUST] The `is` expression cannot be used on final classes (source or target), since final classes do not support covariance.

### 5.9 `sizeof` Expression

**Syntax:** `sizeof(TypeDeclarer)`

**Semantics:**
1. [MUST] Evaluated at compile time; returns size in bytes.
2. [MUST] Allowed types: integer types, float types, struct/union types, fixed-size arrays of these.
3. [MUST] Disallowed: variable-length arrays (`[*]T`), classes, enums, tuples, `bool`.

### 5.10 Conditional Expression (Ternary)

**Syntax:** `condition ? trueValue : falseValue`

**Rules:**
1. [MUST] `condition` must be of type `bool`.
2. [MUST] `trueValue` and `falseValue` must have compatible types.
3. [SHOULD] The result type is the common type of both branches.

### 5.11 Assignment Operations

**Syntax:** `operand op= expression`

**Rules:**
1. [MUST] Equivalent to `operand = operand op expression`.
2. [MUST] Only valid as a statement, not in expressions (no return value).
3. [MUST] Supported operators: `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `~=`, `<<=`, `>>=`, `&&=`, `||=`.

### 5.12 Custom Operators

**Syntax:** Defined via `macro operator Name(params) { body }`

| Operator | Macro Name | Right Operand Type | Result Type |
|----------|------------|-------------------|-------------|
| `*` | `mul` | Same as left | Same as left |
| `/` | `div` | Same as left | Same as left |
| `%` | `mod` | Same as left | Same as left |
| `+` | `add` | Same as left | Same as left |
| `-` | `sub` | Same as left | Same as left |
| `<` | `lt` | Same as left | `bool` |
| `<=` | `le` | Same as left | `bool` |
| `==` | `eq` | Same as left | `bool` |
| `!=` | `ne` | Same as left | `bool` |
| `>` | `gt` | Same as left | `bool` |
| `>=` | `ge` | Same as left | `bool` |
| `[]` (read) | `index get` | index | element type |
| `[]` (write) | `index set` | index, value | — |

[MUST] Custom operators can only be defined in classes.

### 5.13 Object Expression (Field Initialization)

**Syntax:** `[TypePrefix] "{" field "=" expr {"," field "=" expr} "}"`

**Rules:**
1. [MUST] Used to initialize classes, structs, and unions.
2. [MUST] Field order does not need to match definition order.
3. [MUST] Duplicate field initialization is forbidden.
4. [MAY] Type prefix is required when the type cannot be inferred from context.

### 5.14 Array Expression

**Syntax:** `[ [size] ElementType ] "[" [ expr {"," expr} ] "]"`
**[MUST] Used to initialize fixed-size arrays.**

**Rules:**
1. [MUST] Elements are listed in order inside `[...]`.
2. [MUST] If element count is less than array size, remaining elements are zeroed — except non-null reference elements, which must all be explicitly provided.
3. [MUST] If array size and type are omitted from the prefix, element count determines the length: `[]int[1,2,3]` → `[3]int`.
4. [MUST] Literal-only arrays without type prefix cannot infer type: `[1,2,3]` alone is invalid.
5. [MUST] Elements must be type-compatible with the declared element type.
6. [MUST] If no explicit type and first-element inference is used, all elements must be compatible with the inferred type.

### 5.15 Tuple Expression

**Syntax:** `"(" expr [":" TypeDeclarer] {"," expr [":" TypeDeclarer]}+ ")"`

**Rules:**
1. [MUST] At least 2 elements.
2. [MUST] Each element may have an explicit type annotation after `:`.
3. [MUST] For literal values (except `true`/`false` which are unambiguously `bool`), type annotations CANNOT be omitted.
4. [MAY] For variables with known types, the annotation can be omitted.

### 5.16 Block Expression

**Syntax:** `"{" statementList expression "}"`

**Rules:**
1. [MUST] The final expression is the value of the block.
2. [MUST] Enters a new scope; local variables are discarded on exit.
3. [MUST] Usable anywhere an expression is expected.

---

## 6. Statements

### 6.1 Statement Taxonomy

```
statement = blockStatement
          | assignmentOperateStatement
          | assignmentsStatement
          | declarationStatement
          | callStatement
          | ifStatement
          | switchStatement
          | forStatement
          | throwStatement
          | tryStatement
          | assertStatement
          | returnStatement
          | continueStatement
          | breakStatement
          | labeledStatement
```

### 6.2 Block Statement

**Syntax:** `"{" {statement} "}"`
**[MUST] Creates a nested scope.** Variables declared inside are not accessible outside.

### 6.3 Variable Declaration Statement

**Syntax:** `("var" | "const") Identifier {"," Identifier} [TypeDeclarer] ["=" ExpressionList] ";"`
See §4.1 for full variable semantics.

### 6.4 Assignment Statement

**Syntax:** `OperandList "=" ExpressionList ";"`

**Rules:**
1. [MUST] The number of left-hand operands must equal the number of right-hand expressions.
2. [MUST] Left-hand operands must be assignable: variables, fields, indexed elements, dereferenced references, tuple elements.
3. [MUST] Each right-hand expression must be type-compatible with its corresponding left-hand operand.
4. [MUST] `const` variables cannot appear on the left-hand side after initialization.

### 6.5 Assignment Operation Statement

**Syntax:** `Operand op= Expression ";"`
**[MUST] Equivalent to `Operand = Operand op Expression`.** No return value; only valid as a statement.

### 6.6 Call Statement

**Syntax:** `PrimaryExpression "(" [ExpressionList ["," "..."]] ")" ";"`
**[MUST] A call expression used as a statement.** The return value (if any) is discarded.

### 6.7 if Statement

**Syntax:**
```
ifStatement = "if" "(" [embedAssignment ";"] expression ")" statement ["else" statement]
```

**Rules:**
1. [MUST] The condition expression must be of type `bool`.
2. [MAY] An initialization statement can precede the condition, separated by `;`. Variables declared there are scoped to the if-else block.
3. [MAY] The `else` branch is optional.
4. [MUST] In a function with a return type: if the `if` has no `else`, the `if` body must end with a terminating statement.
5. [MUST] If `else` is present, both branches must end with terminating statements (when return is required).

### 6.8 switch Statement

**Syntax:**
```
switchStatement = "switch" "(" [embedAssignment ";"] expression ")"
                  "{" {switchBranch} [switchBranchDefault] "}"
switchBranch    = "case" ExpressionList blockStatement
switchBranchDefault = "default" blockStatement
```

**Rules:**
1. [MUST] The switch expression is evaluated once and matched against `case` values.
2. [MUST] `case` values must be compile-time constants, compatible with the switch expression type.
3. [MUST] Multiple values in one `case` are separated by commas.
4. [MUST] For enum switches: if all enum values are covered by `case` branches, `default` is FORBIDDEN; otherwise `default` is REQUIRED.
5. [MUST] Each `case` body is a block statement (no fall-through).

### 6.9 for Statement

Three forms:

**Form 1 — Bare condition:**
```
"for" "(" expression ")" statement
```
[MUST] The condition expression must be `bool`. Equivalent to `while (condition)`.

**Form 2 — Full clause:**
```
"for" "(" embedAssignment ";" expression ";" embedAssignment ")" statement
```
Execution order:
1. [MUST] First `embedAssignment`: executed once before the loop.
2. [MUST] `expression` (must be `bool`): checked before each iteration. Exit on `false`.
3. [MUST] Second `embedAssignment`: executed after each iteration.
4. [MUST] `statement`: the loop body.

**Form 3 — Iteration:**
```
"for" "(" IdentifierList ":" expression ")" statement
```

**Rules for iteration:**
1. [MUST] The expression must be iterable: arrays (default), enums, or custom types with `macro helper Iterator`.
2. [MUST] Single identifier: receives the element value. Two identifiers: receives `(index, value)`.
3. [MUST] Custom iterators define `initializer`, `condition`, `updater`, and `get` (multiple overloads by parameter count).

**Loop control:**
1. [MUST] `continue;` skips to the next iteration.
2. [MUST] `break;` exits the loop immediately.
3. [MAY] `continue label;` and `break label;` target a labeled outer statement (labels are reserved, not yet implemented).

### 6.10 return Statement

**Syntax:** `"return" [expression] ";"`
**[MUST] Unconditionally terminates the current function/method.**

**Rules:**
1. [MUST] If the function has a return type, `return` MUST carry a value; if void, MUST NOT.
2. [MUST] The returned value must be type-compatible with the declared return type.
3. [MUST] A function with a return type must have a terminating statement on every reachable path.
4. [MUST] An infinite loop (condition provably always `true` at compile time) counts as a terminating statement.

### 6.11 throw Statement

**Syntax:** `"throw" expression ";"`

**Rules:**
1. [MUST] The thrown expression must be a `new`-created instance of `Exception` or a subclass.
2. [MUST] Immediately terminates the current function and propagates up the call stack until caught.
3. [MUST] A `throw` is a terminating statement.

### 6.12 try-catch-final Statement

**Syntax:**
```
tryStatement = "try" blockStatement {catchClause} finalClause?
             | "try" blockStatement finalClause
catchClause  = "catch" "(" [modifier] [Identifier] catchTypeSet ")" blockStatement
catchTypeSet = TypeDeclarer {"|" TypeDeclarer}
finalClause  = "final" blockStatement
```

**Rules:**
1. [MUST] At least one `catch` or `final` clause is required.
2. [MUST] `catch` clauses are matched in order. The first matching type catches the exception.
3. [MUST] The catch parameter must be a non-null, unmodifiable strong reference.
4. [MUST] `catch` can match multiple exception types with `|` separator.
5. [MUST] `final` clause is ALWAYS executed:
   - If `try` has a `return`: expression is evaluated, then `final` runs, then the return completes.
   - If an uncaught exception is thrown: `final` runs first, then the exception continues propagating.
6. [MUST] The catch parameter is implicitly `const`.

### 6.13 assert Statement

**Syntax:** `"assert" "(" expression ")" ";"`
**[MUST] Only active in debug mode.** In release mode, assert statements are ignored at compile time.

**Rules:**
1. [MUST] The expression must be `bool`.
2. [MUST] If `false` at runtime (debug mode), throws `AssertException`.
3. [MUST] Do not put side-effect operations inside assert expressions — they may not execute in release mode.

### 6.14 Terminating Statement Rules

A **terminating statement** is a `return` or `throw` (collectively). An infinite loop also qualifies.

**Rule:** In a function with a return type, every reachable exit path must end with a terminating statement. The compiler performs control-flow analysis to verify this.

---

## 7. Variable Semantics (Detail)

### 7.1 Value Type Variables

[MUST] Assignment copies the entire value (all fields/elements). Modifications to the copy do not affect the original.
Applies to: primitives, structs, unions, fixed-size arrays, tuples, and class value types.

### 7.2 Reference Type Variables

[MUST] Assignment changes the reference target, not the instance data.

#### Strong Reference (`*`)
[MUST] Only references instances created via `new`.
[MUST] Affects memory management (reference counting): as long as a strong reference exists, the instance is not released.

#### Phantom Reference (`&`)
[MUST] Does not affect memory management.
[MUST] Must be declared `const`. Only allowed as local variables or parameters.
[MUST] Can reference: value type variables in scope, constant fields, temporary instances (literals, new, return values).
[MUST] Cannot be fields, global variables, or synchronized (`@Sync`).

#### Nullable Reference (`?`)
[MUST] Non-null → Nullable: always allowed.
[MUST] Nullable → Non-null: only allowed after explicit `!= nil` check (flow analysis).

**Prohibited operations on nullable references:**
1. [MUST] Cannot dereference (`*`).
2. [MUST] Cannot access fields or methods.
3. [MUST] Cannot index (for array references).
4. [MUST] Cannot call (for function prototypes).

#### Non-null Initialization

[MUST] A non-null reference cannot hold `nil`; therefore every non-null reference must be explicitly initialized with a non-null value.

1. [MUST] A variable of non-null reference type must be initialized at declaration.
2. [MUST] If a class, tuple, or fixed-size array contains a non-null reference (directly or transitively through a nested value type), the whole value must be explicitly initialized — every such non-null member must receive a non-null value.
3. [MUST] The element type of a variable-length array (`[*]T`) cannot be a non-null reference type, because a variable-length array cannot guarantee that every element is explicitly initialized.

#### Unmodifiable Reference (`#`)
[MUST] Cannot modify the instance through this reference (cannot assign to fields, cannot dereference-assign, cannot call non-`#` methods).
[MUST] Modifiable → Unmodifiable: allowed. Reverse: forbidden.

#### Escape Method Marker (`*` before `(`)
[MUST] In a method marked with `*` (escape method), `this` can be used as a `const` strong reference.
[MUST] Escape methods can only be called through a strong reference.

### 7.3 Scope

| Scope | Declaration Location | Lifetime |
|-------|---------------------|----------|
| Local | Inside function/method | Until end of enclosing block |
| Global | Top-level | Entire runtime |

[MUST] The same name cannot be redeclared in the same block.
[MAY] Inner blocks can shadow outer variables by redeclaring the same name.

---

## 8. Concurrency Model

### 8.1 Core Mechanism

The concurrency model is based on `@Sync`/`@Async` attribute-based compile-time checking, NOT runtime locks.

**Fundamental rule:** Only **syncable instances** can cross a **concurrency boundary** (call to `@Async` function/method).

### 8.2 Concurrency Boundary

**Definition:** A function or method annotated with `@Async` is a concurrency boundary.

**Rules:**
1. [MUST] All parameters of an `@Async` function are implicitly `@Sync`.
2. [MUST] `@Async` functions/methods cannot have a return value.
3. [MUST] An `@Async` method can only be called on a syncable instance.
4. [MUST] Inside an `@Async` method, `this` is syncable.
5. [MUST] A non-`@Async` method cannot call an `@Async` method on `this` (syncability unknown).

### 8.3 Sync Assignment Rules

[MUST] Sync and non-sync references cannot be assigned to each other:
- `new`-created instances are initially unowned and can be assigned to either.
- Once assigned to a sync reference, cannot be assigned to non-sync.
- Once assigned to a non-sync reference, cannot be assigned to sync.

### 8.4 Syncable Types

**Automatically syncable:** Primitives, enums, structs/unions, function prototypes.
(Because they contain no references to other instances.)

**Conditionally syncable — Classes:**
1. [MUST] A class with NO reference-type fields is automatically syncable.
2. [MUST] If ALL reference-type fields are individually marked `@Sync`, the class is syncable.
3. [MUST] If the class itself is marked `@Sync`, all reference fields are implicitly syncable.
4. [MUST] Syncability does NOT inherit: a subclass must independently satisfy the rules.
5. [MUST] If a value-type field is a class, that nested class must also be syncable (transitive).

**Conditionally syncable — Interfaces:**
[MUST] Marked `@Sync` on the interface means it can only reference syncable implementing classes.

**Conditionally syncable — Arrays:**
[MUST] Variable-length arrays (`[*]T`) are NEVER syncable.
[MUST] Fixed-size arrays are syncable only if every nesting level is fixed-size and the innermost element type is syncable.

**Conditionally syncable — Tuples:**
[MUST] Each tuple element branch must independently satisfy the nesting check (no references at any nesting level).

### 8.5 Restrictions

[MUST] Value types cannot be individually marked `@Sync` (they are copied; marking is meaningless).
[MUST] Phantom references (`&`) cannot be marked `@Sync`.
[MUST] Generics do not currently support `@Sync`/`@Async`.

### 8.6 Override Rules

[MUST] If a parent class method is `@Async`, the overriding child method must also be `@Async`.
[MUST] If a parent class method is NOT `@Async`, the overriding child method must NOT be `@Async`.
[MUST] Same rules apply for interface implementations.

### 8.7 Compiler Backend Handling

The concurrency check is purely a compile-time check. The compiler backend treats `@Sync`
differently depending on where it is applied:

1. [MUST] `@Sync` on a field or variable: the compiler generates the concurrent
   operations — atomic reference counting, and (for fields) spinlock-protected
   load/store.
2. [MUST] `@Sync` on a class: the compiler only marks the type as syncable and
   generates NO concurrent operations. Concurrency correctness for the class's
   internal state is the programmer's responsibility (e.g., via mutex, cond, or
   atomic operations).

---

## 9. Exception System

### 9.1 Exception Hierarchy

[MUST] `Exception` is the built-in base class:
```
class Exception {
   var fn uint64;
   var line uint32;
   func trace(fnAddr uint64, lineNum uint32) {
      fn = fnAddr;
      line = lineNum;
   }
}
```

**Built-in exception subclasses:**
| Exception | Thrown When |
|-----------|-------------|
| `NilException` | Dereferencing `nil` reference |
| `OutOfBoundsException` | Array index out of bounds |
| `AssertException` | `assert` fails (debug mode) |

### 9.2 Custom Exceptions

[MUST] Custom exceptions must extend `Exception`:
```
class MyException : Exception { }
```
[MUST] Only instances created via `new` can be thrown: `throw new(MyException);`

---

## 10. Testing

### 10.1 Test Functions

[MUST] Annotated with `@Test`. Syntax:
```
@Test
func testName() { }
```

**Rules:**
1. [MUST] No parameters, no return value.
2. [MUST] Cannot be called by other functions/methods/test cases.
3. [MUST] Can be placed anywhere in the module; the compiler separates them automatically.
4. [MUST] In test mode, only `@Test` functions are compiled; `main` is ignored.
5. [MUST] In normal mode, `@Test` functions are ignored.

---

## 11. C Language Interoperability

### 11.1 Type Mapping

| Feng Type | C Type |
|-----------|--------|
| `int8`..`int64`, `uint8`..`uint64` | Corresponding `intN_t`/`uintN_t` |
| `int`, `uint` | Platform-dependent (`int`/`unsigned int`) |
| `float32` | `float` |
| `float64` | `double` |
| `bool` | `bool` (`stdbool.h`) |
| `struct`/`union` | Identical layout |

### 11.2 Pointer Handling

C pointers are represented as `uint64` in Feng.

**Converting Feng references to C pointers:**
1. [MUST] Strong references (`*T`) and phantom references (`&T`) can both be cast to `uint64` for passing to C pointer parameters.
2. [MUST] The resulting `uint64` is a valid C pointer to the referenced value.
3. [MUST] Reverse cast (`uint64` → reference) is FORBIDDEN.
4. [MUST] Array references (`[*]T`) expose their pointer via the built-in field `values` (type `uint64`).

**Example** — passing a class field's address to C:
```feng
class Buffer {
    var len int32;
    func writeData() {
        const r &int32 = len;      // phantom reference to the field
        var ptr uint64 = uint64(r); // C pointer to len
        cFunction(ptr, ...);        // pass to C
    }
}
```

### 11.3 Calling C Library Functions

To call C functions (from libc or a third-party library), place a `.h` header file
directly in the Feng module directory. The header includes the target library's
headers and declares any symbols the Feng code needs. Feng code then calls those
functions directly — no `import` is required.

**Rules:**
1. [MUST] The `.h` file resides in the Feng module directory (alongside `.feng` files).
2. [MUST] `#include` the relevant system/library headers so the compiler can resolve types and recognize the functions.
3. [SHOULD] Additionally declare any functions not covered by the `#include`d headers (e.g. POSIX extensions).
4. [MUST] Call the function from Feng by its C name, with no module prefix.

**Example** — calling libc from `std/os/file.feng`:

```c
// file.h — placed in std/os/, alongside file.feng
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Additional POSIX functions not in standard headers
int mkdir(const char *path);
int access(const char *path, int mode);
char *getcwd(char *buf, int size);
```

```feng
// file.feng — calls C functions directly, no import needed
func open(path [&#]byte, mode OpenMode) *File {
    var fp = fopen(cpath.values, cmode.values);  // from <stdio.h>
    return file(fp);
}
func makeDir(path [&#]byte) bool {
    return mkdir(cpath.values) == 0;             // declared in file.h
}
```

To pass the address of a value-type field (rather than an array), take a **phantom reference**
to the field and cast it to `uint64`:

```feng
// Pass a class field's address to a C function
class Counter {
    var n int32;
    func reset() {
        const r &int32 = n;
        c_reset(uint64(r));   // pass &n as C pointer
    }
}
```

**How the compiler decides which functions get a module prefix**:
The compiler maintains a known list of C standard library function names. Functions
from `#include`d system headers or those explicitly recognized as libc symbols are
called without a prefix. Functions that the compiler does NOT recognize as standard
library symbols will receive the module's prefix (e.g., `module$functionName`),
making them uncallable by their bare name.

### 11.4 Third-Party C Libraries

[MUST] libc is linked automatically — no extra configuration is needed.
[MUST] Third-party libraries (e.g., `pthread`, `m`, `dl`) require a `feng.cfg`
file in the module directory:

```properties
# feng.cfg
link=pthread
```

[MUST] The `link` value is the library name without the `-l` prefix.
[MUST] Multiple libraries are comma-separated: `link=pthread,m`.

### 11.5 C Implementation Modules

When you need custom C code (not just calling an existing library), create a
**separate** subdirectory with `.h` + `.c` files. This is a C module.

[MUST] A C module is a directory containing `.h` + `.c` files. The directory name
follows Feng module naming.
[MUST] Feng and C code cannot be mixed in the same module.
[MUST] The `.h` file declares the symbols (functions, types, globals) exported from the C module.
[MUST] The `.c` file implements them.
[MUST] Feng code imports the C module with `import` and accesses its symbols
with the module's last-segment prefix:

```
import jjj$mm;
func test() {
    var c = mm$add(a, b);
}
```

**When to use §11.3 vs §11.5:**

| Scenario | Approach | Section |
|----------|----------|---------|
| Call libc functions (`fopen`, `printf`, `malloc`, …) | `.h` in Feng module, `#include` libc headers | §11.3 |
| Call third-party library functions | `.h` in Feng module + `feng.cfg` with `link=` | §11.3, §11.4 |
| Call compiler builtins (`__atomic_*`, `__builtin_*`) | C module with wrapper `.c` | §11.5 |
| Need custom C logic (algorithms, platform glue) | C module | §11.5 |

### 11.6 Compiler Builtins

Compiler builtins (e.g., GCC `__atomic_*`, Clang `__builtin_*`) are NOT part of
any C library — they are recognized and inlined by the C compiler at the call site.
Because they are not declared in any system header, the Feng compiler does NOT
recognize them as standard library functions and will prefix their names.

**[MUST] Compiler builtins require a C module wrapper** (§11.5). You cannot
call them directly from a Feng module's `.h` file.

**Example** — wrapping GCC `__atomic_*` builtins:

```c
// c/atomic.h — C module header
#include <stdint.h>

int32_t atomic_int32_load(const volatile void* ptr, int memorder);
void    atomic_int32_store(volatile void* ptr, int32_t val, int memorder);
int32_t atomic_int32_fetch_add(volatile void* ptr, int32_t val, int memorder);
```

```c
// c/atomic.c — calls the builtin internally
#include <stdint.h>

int32_t atomic_int32_load(const volatile void* ptr, int memorder) {
    return __atomic_load_4(ptr, memorder);
}
void atomic_int32_store(volatile void* ptr, int32_t val, int memorder) {
    __atomic_store_4(ptr, val, memorder);
}
int32_t atomic_int32_fetch_add(volatile void* ptr, int32_t val, int memorder) {
    return __atomic_fetch_add_4(ptr, val, memorder);
}
```

```feng
// atomic.feng — imports the C module, calls wrapped functions
import std$async$c;

class Counter final {
    var buf [*]int32;
}
func load(c &Counter) int32 {
    return c$atomic_int32_load(c.buf.values, int32(5));  // 5 = SEQ_CST
}
```

**Common builtin families:**
| Family | Provider | Example | Purpose |
|--------|----------|---------|---------|
| `__atomic_*` | GCC / Clang | `__atomic_fetch_add_4` | Atomic operations |
| `__builtin_*` | GCC / Clang | `__builtin_popcount` | Compiler intrinsics |

---

## 12. Compile-Time Constants

[MUST] The following are compile-time constants:
1. All literals (integer, float, string, bool, nil).
2. `const` variables of primitive types or string literal type.
3. Expressions composed entirely of compile-time constants.

[MUST] Compile-time constants are evaluated at compile time.

---

## 13. Attributes (Annotations)

**Syntax:** `@Identifier ["(" [objectExpr] ")"]`

[MUST] Attributes are annotations that decorate declarations.

| Attribute | Applies To | Meaning |
|-----------|-----------|---------|
| `@Test` | Functions | Marks a test case (see §10) |
| `@Async` | Functions, Methods | Marks a concurrency boundary (see §8) |
| `@Sync` | Variables, Fields, Classes, Interfaces | Marks syncable (see §8) |

---

## Appendix A: Operator Precedence (Complete)

| Precedence | Operators | Associativity | Notes |
|:----------:|-----------|:------------:|-------|
| 1 | `new(...)`, `(expr)`, literals, `this`, `super`, `{...}` | — | Primary |
| 2 | `?()`, `[expr]`, `.member`, `.index`, `(args)`, `{stmts expr}` | Left | Postfix & invocation |
| 3 | `+x`, `-x`, `!x` | Right | Unary |
| 4 | `^` | Right | Power (binds tighter on left) |
| 5 | `*`, `/`, `%` | Left | Multiplicative |
| 6 | `+`, `-` | Left | Additive |
| 7 | `<<`, `>>` | Left | Shift |
| 8 | `&` | Left | Bitwise AND |
| 9 | `~` | Left | Bitwise XOR |
| 10 | `\|` | Left | Bitwise OR |
| 11 | `<`, `<=`, `==`, `!=`, `>`, `>=` | Left | Relational |
| 12 | `&&` | Left | Logical AND (short-circuit) |
| 13 | `\|\|` | Left | Logical OR (short-circuit) |

---

## Appendix B: Type Compatibility Matrix

| From → To | Same Type | Sub→Parent (Class) | Impl→Interface | mappable | Explicit Cast |
|-----------|:---------:|:-------------------:|:--------------:|:--------:|:-------------:|
| Primitives | ✅ | — | — | — | ✅ other int types |
| Classes (ref) | ✅ | ✅ (covariant) | ✅ | — | — |
| Classes (ref, final) | ✅ | — | — | — | — |
| Interfaces (ref) | ✅ | — | ✅ (composed→component) | — | — |
| Structs/Unions | ✅ | — | — | ✅ (boundary check) | — |
| Fixed Arrays | ✅ | — | — | ✅ (element mappable) | — |
| Func Prototypes | ✅ | ✅ (covariant return) | — | — | — |
| Nullable → Non-null | — | — | — | — | ✅ after `!= nil` check |
| Modifiable → Unmodifiable | — | — | — | — | ✅ (one-way) |
| Ref → uint64 | — | — | — | — | ✅ (one-way, for C) |

---

## Appendix C: Complete EBNF Grammar

> Derived from `Feng.g4` (ANTLR grammar). Placeholder rules (unimplemented or reserved) are marked with `[PH]`.

### C.1 Module-Level

```
source       = {import_} {global} EOF
import_      = "import" module [Identifier] ";"
module       = Identifier {"$" Identifier}
symbol       = [Identifier "$"] Identifier
exportable   = ["export"]
```

### C.2 Global Definitions

```
global       = typeDefinition
             | functionDefinition
             | exportable declaration ";"
             | macro

typeDefinition = structureDefinition
               | enumDefinition
               | classDefinition
               | interfaceDefinition
               | prototypeDefinition
               | attributeDefinition
```

### C.3 Attributes

```
attributeDefinition = modifier "attribute" Identifier "{" {attributeMember} "}"
attributeMember     = Identifier Identifier ["=" expression] ";"
                    | Identifier "[" "]" Identifier ["=" arrayExpr] ";"
attribute           = "@" symbol ["(" objectExpr ")"]
modifier            = {attribute} exportable
```

### C.4 Structures

```
structureDefinition       = modifier ("struct"|"union") Identifier [typeParameters]
                            "{" {structureFieldsDef} "}"
unnamedStructureDefinition = ("struct"|"union") "{" {structureFieldsDef} "}"
structureFieldsDef        = structureFields structureFieldType ";"
structureFieldType        = definedStructureFieldType
                          | unnamedStructureFieldType
                          | "[" expression "]" structureFieldType
structureFields           = structureField {"," structureField}
structureField            = {attribute} Identifier ["(" expression ")"]
```

### C.5 Interfaces

```
interfaceDefinition   = modifier "interface" Identifier [typeParameters]
                        "{" {interfaceMember} "}"
interfaceMember       = interfaceMemberMethod | interfaceMemberPart | macro
interfaceMemberMethod = modifier Identifier [typeParameters] ["*"] ["#"]
                        prototype ";"
interfaceMemberPart   = definedType ";"
```

### C.6 Classes

```
classDefinition   = modifier "class" Identifier [typeParameters] classExtension
                    "{" {classMember} "}"
classExtension    = ["final"] [classInherit] [classImpl]
classInherit      = ":" definedType
classImpl         = "(" definedType {"," definedType} ")"
classMember       = modifier (classMemberFields | classMemberMethod | macro)
classMemberFields = ("var"|"const") identifierList typeDeclarer ";"
classMemberMethod = funcDef
```

### C.7 Enums

```
enumDefinition = modifier "enum" Identifier "{" enumValue {"," enumValue} [","] "}"
enumValue      = Identifier ["=" expression]
```

### C.8 Functions

```
funcDef              = "func" Identifier [typeParameters] ["*"] ["#"]
                       (procedure | prototype ";")
functionDefinition   = modifier funcDef
prototypeDefinition  = modifier "func" Identifier "=" [typeParameters] prototype ";"
procedure            = prototype blockStatement
prototype            = "(" [parametersSet] ")" [returnSet]
parametersSet        = parameters ["," "..."] | typeDeclarerList ","
parameters           = parameter {"," parameter}
parameter            = modifier identifierList typeDeclarer
returnSet            = typeDeclarer | "this"
```

### C.9 Macros [PH]

```
macro         = modifier "macro" Identifier macroType
              | modifier "macro" Identifier macroProcedure
macroType     = Identifier "{" macroVariables ";" {macroProcedure} "}"
macroProcedure = Identifier "(" [macroVariables] ")" [typeDeclarer] "{" statementList [expression] "}"
macroVariables = macroVariable {"," macroVariable}
macroVariable  = Identifier [typeDeclarer]
```

### C.10 Statements

```
statement              = blockStatement
                       | assignmentOperateStatement
                       | assignmentsStatement
                       | declarationStatement
                       | callStatement
                       | ifStatement
                       | switchStatement
                       | forStatement
                       | throwStatement
                       | tryStatement
                       | assertStatement
                       | returnStatement
                       | continueStatement
                       | breakStatement
                       | labeledStatement           [PH]

blockStatement         = "{" {statement} "}"
callStatement          = primaryExpr argumentSet ";"

ifStatement            = "if" "(" [embedAssignment ";"] expression ")"
                         statement ["else" statement]

forStatement           = "for" "(" expression ")" statement
                       | "for" "(" forClause ")" statement
                       | "for" "(" forIterator ")" statement
forClause              = embedAssignment ";" expression ";" embedAssignment
forIterator            = identifierList ":" expression

switchStatement        = "switch" "(" [embedAssignment ";"] expression ")"
                         "{" {switchBranch} [switchBranchDefault] "}"
switchBranch           = "case" expressionList blockStatement
switchBranchDefault    = "default" blockStatement

embedAssignment        = assignments | assignedDeclaration | assignmentOperation

throwStatement         = "throw" expression ";"
assertStatement        = "assert" expression ";"

tryStatement           = "try" blockStatement {catchClause} finalClause?
                       | "try" blockStatement finalClause
catchClause            = "catch" "(" modifier [Identifier] catchTypeSet ")" blockStatement
catchTypeSet           = typeDeclarer {"|" typeDeclarer}
finalClause            = "final" blockStatement

assignmentOperation    = operand assignmentOperator expression
assignmentOperator     = "+=" | "-=" | "*=" | "/=" | "%="
                       | "&&=" | "||="
                       | "&=" | "|=" | "~=" | "<<=" | ">>="
assignmentOperateStatement = assignmentOperation ";"
assignmentsStatement   = assignments ";"
declarationStatement   = declaration ";"

returnStatement        = "return" [expression] ";"
continueStatement      = "continue" [Identifier] ";"    [PH label]
breakStatement         = "break" [Identifier] ";"       [PH label]
labeledStatement       = Identifier ":" statement       [PH]
```

### C.11 Expressions

```
expression       = rightAssocExpr
                 | expression ("*"|"/"|"%") expression
                 | expression ("+"|"-") expression
                 | expression ("<<"|">>") expression
                 | expression "&" expression
                 | expression "~" expression
                 | expression "|" expression
                 | expression ("<"|"<="|"=="|"!="|">"|">=") expression
                 | expression "&&" expression
                 | expression "||" expression
                 | expression "?" expression ":" expression

rightAssocExpr   = powerExpr
                 | ("+"|"-"|"!") rightAssocExpr
powerExpr        = dereferExpr
                 | dereferExpr "^" rightAssocExpr
dereferExpr      = primaryExpr | "*" primaryExpr

primaryExpr      = operandExpr
                 | primaryExpr "?" "(" typeDeclarer ")"
                 | primaryExpr "[" expression "]"
                 | primaryExpr "." Identifier [typeArguments]
                 | primaryExpr "." DecimalInteger
                 | primaryExpr argumentSet

operandExpr      = literal
                 | objectExpr
                 | arrayExpr
                 | tupleExpr
                 | pairsExpr          [PH]
                 | symbol [typeArguments]
                 | "this" | "super"
                 | "func" procedure                           [PH lambda]
                 | "(" expression ")"
                 | new
                 | blockExpr
                 | sizeof

argumentSet      = "(" [expressionList ["," "..."]] ")"
blockExpr        = "{" statementList expression "}"
expressionList   = expression {"," expression}

objectExpr       = [definedType] "{" [objectEntry {"," objectEntry} [","]] "}"
objectEntry      = Identifier "=" expression
arrayExpr        = (["[" [expression] "]" typeDeclarer])? "[" [expressionList [","]] "]"
tupleExpr        = "(" tupleElement {"," tupleElement}+ ")"
tupleElement     = expression [":" typeDeclarer]
pairsExpr        = "{" pair {"," pair} "}"                   [PH]
pair             = expression ":" expression

indexOf          = "[" expression "]"
memberOf         = "." Identifier
tupleIndex       = "." DecimalInteger
new              = "new" "(" newType ["," expression] ")"
newType          = definedType | "[" expression "]" typeDeclarer
sizeof           = "sizeof" "(" typeDeclarer ")"
```

### C.12 Types

```
typeDeclarer      = primaryTypeDeclarer | arrayTypeDeclarer
arrayTypeDeclarer = "[" arrayType "]" typeDeclarer
arrayType         = expression | refer
primaryTypeDeclarer = definedTypeDeclarer | funcTypeDeclarer | tupleTypeDeclarer
definedTypeDeclarer = [refer] definedType
refer             = ("*"|"&") ["?"] ["#"]
funcTypeDeclarer  = ["?"] "func" prototype | ["?"] definedType
tupleTypeDeclarer = "(" typeDeclarer {"," typeDeclarer}+ ")"
typeArguments     = "`" typeDeclarer {"," typeDeclarer} "`"
definedType       = symbol [typeArguments]
typeParameters    = "`" typeParameter {"," typeParameter} "`"
typeParameter     = Identifier [typeConstraint]
typeConstraint    = typeDomain | definedType
                  | typeConstraint "&" typeConstraint
                  | typeConstraint "|" typeConstraint
typeDomain        = "class" | "interface" | "enum" | "struct" | "union" | "attribute" | "func"
```

### C.13 Declarations & Assignments

```
declaration        = onlyDeclaration | assignedDeclaration
onlyDeclaration     = declaredNames typeDeclarer
assignedDeclaration = declaredNames [typeDeclarer] "=" expressionList
declaredNames       = {attribute} ("var"|"const") identifierList

assignments = operands "=" expressionList
operands    = operand {"," operand}
operand     = symbol
            | primaryExpr "[" expression "]"
            | primaryExpr "." Identifier
            | primaryExpr "." DecimalInteger
            | "*" primaryExpr
```

### C.14 Identifiers & Literals

```
identifierList = Identifier {"," Identifier}
literal        = integerLiteral | FloatLiteral | StringLiteral | "true" | "false" | "nil"
integerLiteral = DecimalInteger | HexInteger | OctalInteger | BinaryInteger
```

---

## Legend

| Marker | Meaning |
|--------|---------|
| `[MUST]` | Required rule — violation is a compile-time error |
| `[SHOULD]` | Recommended practice — violation may cause warnings |
| `[MAY]` | Optional feature or behavior |
| `[PH]` | Placeholder — reserved in grammar but not yet implemented |

