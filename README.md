**【Fèng】Programming Language**

C++ initially extended C by adding object-oriented features, but it lacks memory safety semantics.
Memory safety (safety) and system security are different concepts. Memory safety specifically refers to hard-to-predict issues caused by developer mistakes in memory usage.
These mistakes can be partially checked with ASAN, but not completely detected. Additionally, the system can identify some memory safety issues, such as null pointers and page faults.
However, these detectable issues do not cause actual harm. The real harm comes from issues like modifying in-use memory through dangling pointers.
Neither the system nor ASAN can detect such issues. Yet these are the truly harmful problems—they can even accidentally modify other processes' data without the system noticing.

The Fèng language also extends C with object-oriented features, but goes one step further than C++ by introducing memory safety design.
The goal is to replace most of C's use cases, improve system development efficiency, and make developers' work easier.

# Feature Overview

Main designed features:

1. Memory safety mechanisms:
    1. Automatic memory management: Pointers must point to an object or be null. Objects will be freed when no longer referenced. Freed objects can have their space reclaimed immediately or deferred.
    2. Safe pointer usage: Pointers cannot be generated through arithmetic operations, nor can arbitrary integers be converted to pointer values. This also includes pointer increment and decrement.
    3. Mandatory bounds checking: For arrays and buffer space operations.
    4. Mandatory type checking: Type conversions must be checked for permissibility. Even when compile-time analysis is impossible, runtime checks must be performed.
    5. Phantom references: Replace C's address-of operator (&) with phantom references, restricting usage to within the instance's lifetime.
2. Basic object-oriented elements, including inheritance, polymorphism, and interface abstraction.
3. Resource classes: Designed with reference to C++ destructors, used to reclaim resources allocated by underlying libraries and handle circular references.
4. Allow free conversion for certain types, mainly struct and union.
5. Value type variables: Variables are the instances themselves, requiring no additional memory allocation.
6. Modular code management: Symbols require export and import between modules.

Plus several minor features:

7. Exception handling mechanism. _[*Incomplete*]_
8. Generics: Only basic generic mechanisms are implemented.
9. Operator overloading: _[*Incomplete*]_

Designed syntax details are listed in the [reference manual](reference_zh.md).

# Development Progress

Currently under development. Although simple projects can be compiled, the lack of system call libraries and utility libraries still prevents normal usage.
Contributions from interested friends are welcome!

## Syntax Parsing

The parser is generated using ANTLR4. The language specification can be found in [grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4).
The parse results are traversed and the AST is built using [SourceParseVisitor](src/main/java/org/cossbow/feng/parser/SourceParseVisitor.java).

During build, parser classes are automatically generated via Maven plugins, so simply building with `mvn` allows debugging in IDEA.

## Semantic Analysis

The main analysis class is [SemanticAnalysis](src/main/java/org/cossbow/feng/analysis/SemanticAnalysis.java).

Completed semantic analyses include:

1. Symbol checking: Check whether types and functions are defined, and whether variables are declared.
2. Constant folding: Evaluate constants directly.
3. Type checking: Check variable assignments, return value types, function prototype comparisons, and convertible type checks.
4. Class inheritance and interface implementation checks.
5. Check for missing return paths.
6. Variable lifetime checking.
7. Check for anonymous objects in expressions.
8. Required checks for reference and function type variables.
9. Immutable checks for references.
10. Statement context checking.
11. Generic type parameter checking.
12. Checking of symbols exported from other modules.

## Target Code Generation

Only C++ code generation is currently implemented, with the tool class [CppGenerator](src/main/java/org/cossbow/feng/coder/CppGenerator.java).

Completed code features:

1. Derived class definitions: Classes, interfaces, struct types, function types completed; properties _[*Incomplete*]_
2. Expressions: Power operation _[*Incomplete*]_
3. Statements: Exceptions _[*Incomplete*]_
4. Variables: Completed
5. Types: Completed
6. Polymorphic calls for classes: Completed
7. Runtime type checking: Completed
8. Variable cleanup and reference instance management: Completed
9. Literals and initialization: Completed
10. Generics: Completed
11. String formatting: _[*Incomplete*]_
12. Modules: One cpp file generated per module.

# Tool Building

The current build tool supports compiling a single source file, a single module, or multi-module joint builds.

The tool is developed in Java, requiring JDK and Maven to be installed first. For details, consult [deepseek](https://chat.deepseek.com/).
The project dependencies include only antlr4-runtime, jcommander, and 3 Maven plugins, which are automatically downloaded during build. Recommended build command:

```shell
mvn clean package -Dmaven.test.skip=true
```

The packaged JAR will be in the target directory: `feng-${version}.jar`
For example, with the current version "0.0.1-dev", the built package is `feng-0.0.1-dev.jar`.

Tool usage:
```shell
java -jar feng-0.0.1-dev.jar -t [type] -i [source] -o [output directory]
```

Parameter descriptions:
1. -t Source type: f/file - single file, m/module - single module, p/project - simple project with multi-module organization
2. -i Source path: For a single file, points to the full file path; for a module or project, points to the corresponding directory.
3. -o Output directory: For a single file, outputs one C++ file; for a module or project, each module corresponds to one C++ file. If not specified, defaults to the source directory.
4. -p Current package name: Defaults to the filename or directory name.
5. -L Add dependency packages: Multiple packages can be specified as key-value pairs (package name = path), for example: `-Lfoo=D:\dev\libs\foo`

Compile a single source file:

```shell
java -jar feng-0.0.1-dev.jar -t f -i jjj.feng -o jjj.cpp
```

The generated C++ requires a C/C++ compilation environment, and the C++20 standard must be specified during compilation:

```shell
c++ --std=c++20 -c jjj.cpp -o jjj.o
```

If the Fèng code contains a `main` function, a `main` function will be created in the corresponding C++ file, allowing it to be compiled into an executable:

```shell
c++ --std=c++20 jjj.cpp -o jjj.o
```
