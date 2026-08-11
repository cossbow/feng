**Fēng Programming Language**

The Fēng language is a statically typed, object-oriented programming language that prioritizes memory safety:

* Memory safety as a top priority, with mandatory type and bounds checking.
* Class and interface designs simplified from Java, supporting inheritance and abstraction.
* No forms of abstraction other than classes and interfaces.
* Automatic reference counting (ARC).
* Resource classes with destructors.
* `struct` and `union` like in C, supporting arbitrary conversion between them.
* Phantom reference mechanism, similar to C++ references or Rust borrowing.
* Monomorphized generics with type inference.
* References and methods support an immutable (`read-only`) marker, enabling write protection of data without
  encapsulation.
* Control-flow-based non-null checking reduces null pointer issues and improves program stability.
* Simple modular code organization.
* Supports partial operator overloading.
* Trust-boundary-based concurrency checking.

For detailed syntax design, please refer to the [reference manual](reference.md).

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

The main analysis class is [SemanticAnalyzer](src/main/java/org/cossbow/feng/analysis/SemanticAnalyzer.java).

Completed semantic analyses include:

1. Symbol checking: Check whether types and functions are defined, and whether variables are declared.
2. Constant folding: Evaluate constants directly.
3. Struct type layout calculation and bounds checking.
4. Type checking: variable assignment, return value types, function prototype comparison, and convertible type checks.
5. Class inheritance and interface implementation checks.
6. Multi-branch path termination statement checking.
7. Variable lifetime checking.
8. Check for anonymous objects in expressions.
9. Read-only constraint and checking for references.
10. Statement context checking.
11. Generic type parameter checking.
12. Checking of symbols exported from other modules.
13. Non-null checking.
14. Concurrency checking.

## Compiler Backend

The compiler first generates C code, which is then built into a binary. The C backend class
is [CGenerator.java](src/main/java/org/cossbow/feng/coder/CGenerator.java).

Completed code features:

1. Derived class definitions: Classes, interfaces, struct types, function types completed
2. Expressions: Completed
3. Statements: Completed
4. Variables: Completed
5. Types: Completed
6. Polymorphic calls for classes: Completed
7. Runtime type checking: Completed
8. Variable cleanup and reference instance management: Completed
9. Literals and initialization: Completed
10. Generics: checking and inference completed, constraints not yet
11. String formatting: Completed
12. Modules: Completed
13. Concurrency safety for references: Completed

# Compiler

The compiler supports compiling a single source file, a single module, or multi-module project builds.
Entry class: [Compiler.java](src/main/java/org/cossbow/feng/Compiler.java).

To build the compiler, JDK and Maven are required. For installation details,
consult [deepseek](https://chat.deepseek.com/).
The project dependencies include only antlr4-runtime, jcommander, and 3 Maven plugins, which are automatically
downloaded during build. Recommended build command:

```shell
mvn clean package -Dmaven.test.skip=true
```

The packaged JAR will be in the target directory: `feng-${version}.jar`
For example, with the current version "0.0.1-dev", the built package is `feng-0.0.1-dev.jar`.

To run the compiler, Java and Clang are required. Clang installation can also be consulted with deepseek.

Compiler usage:

```shell
java -jar feng-0.0.1-dev.jar -t [type] -i [source] -o [output directory]
```

Parameter descriptions:

1. -t Source type: f/file - single file, m/module - single module, p/project - simple project with multi-module
   organization
2. -i Source path: For a single file, points to the full file path; for a module or project, points to the corresponding
   directory.
3. -o Output directory: used by the build process, including intermediate and final artifacts; defaults to the source
   directory if not specified.
4. -p Current package name: Defaults to the filename or directory name.
5. -L Add dependency packages: Multiple packages can be specified as key-value pairs (package name = path), for example:
   `-Lfoo=D:\dev\libs\foo`
6. -b Backend build tool: Optionally `make` or `cmake`
7. -T [switch] Unit test mode: compiles only unit test cases into an executable; the `main` function is not compiled.
8. --test-name Specify test cases: effective in unit test mode, filters test cases to execute. Can be specified multiple times.

For example, compiling a single source file:

```shell
java -jar feng-0.0.1-dev.jar -t f -i jjj.feng -o /var/build
```

The build results under `/var/build` are:

1. One `.o` file is generated per module after compilation.
2. If one of the modules contains a `main` function, an executable is also produced.

# Editor Support

The project includes a built-in LSP Server for syntax highlighting and language services, packaged in the same JAR as the compiler. Launch command:

```shell
java -cp feng-0.0.1-dev.jar org.cossbow.feng.lsp.FengLspMain
```

Supported LSP features: diagnostics, document symbol, hover, go-to-definition, completion.

## VS Code Extension

The VS Code extension is maintained in a separate repository: [feng-vscode](https://github.com/cossbow/feng-vscode).
It auto-downloads the LSP server JAR from GitHub Releases on first activation.
