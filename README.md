**【Fèng】Programming Language**

The Fèng language is a compiled language that prioritizes simplicity, ease of use, and memory safety.

* `struct` and `union` are basically the same as in C, and support conversion between each other.
* Classes and interfaces are simplified from Java, but can also define value types like in C++, reducing unnecessary
  dynamic memory allocation.
* Designed with automatic memory; currently uses ARC, with the ability to switch to GC.
* Resource classes with destructors make it easy to manage resources from interop languages.
* Phantom references, similar to those borrowed in C++.
* Compile-time generics.
* Operator overloading.
* Definable non-null references and read-only references.
* Modular code organization.

For detailed syntax design, please refer to the [reference manual](reference_zh.md).

# Development Progress

Currently under development. Although simple projects can be compiled, the lack of system call libraries and utility
libraries still prevents normal usage.
Contributions from interested friends are welcome!

## Syntax Parsing

The parser is generated using ANTLR4. The language specification can be found
in [grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4).
The parse results are traversed and the AST is built
using [SourceParseVisitor](src/main/java/org/cossbow/feng/parser/SourceParseVisitor.java).

During build, parser classes are automatically generated via Maven plugins, so simply building with `mvn` allows
debugging in IDEA.

## Semantic Analysis

The main analysis class is [SemanticAnalysis](src/main/java/org/cossbow/feng/analysis/SemanticAnalysis.java).

Completed semantic analyses include:

1. Symbol checking: Check whether types and functions are defined, and whether variables are declared.
2. Constant folding: Evaluate constants directly.
3. Type checking: Check variable assignments, return value types, function prototype comparisons, and convertible type
   checks.
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

Only C++ code generation is currently implemented, with the tool
class [CppGenerator](src/main/java/org/cossbow/feng/coder/CppGenerator.java).

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

The tool is developed in Java, requiring JDK and Maven to be installed first. For details,
consult [deepseek](https://chat.deepseek.com/).
The project dependencies include only antlr4-runtime, jcommander, and 3 Maven plugins, which are automatically
downloaded during build. Recommended build command:

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

1. -t Source type: f/file - single file, m/module - single module, p/project - simple project with multi-module
   organization
2. -i Source path: For a single file, points to the full file path; for a module or project, points to the corresponding
   directory.
3. -o Output directory: For a single file, outputs one C++ file; for a module or project, each module corresponds to one
   C++ file. If not specified, defaults to the source directory.
4. -p Current package name: Defaults to the filename or directory name.
5. -L Add dependency packages: Multiple packages can be specified as key-value pairs (package name = path), for example:
   `-Lfoo=D:\dev\libs\foo`

Compile a single source file:

```shell
java -jar feng-0.0.1-dev.jar -t f -i jjj.feng -o jjj.cpp
```

The generated C++ requires a C/C++ compilation environment, and the C++20 standard must be specified during compilation:

```shell
c++ --std=c++20 -c jjj.cpp -o jjj.o
```

If the Fèng code contains a `main` function, a `main` function will be created in the corresponding C++ file, allowing
it to be compiled into an executable:

```shell
c++ --std=c++20 jjj.cpp -o jjj.o
```
