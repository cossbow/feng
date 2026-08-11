# Fēng Compiler Build & Usage

## Prerequisites

**To build the compiler:**
- JDK 8+
- Maven (dependencies auto-downloaded: antlr4-runtime, jcommander, 3 Maven plugins)

**To run the compiler:**
- Java runtime
- Clang (for C header parsing)

## Build

```bash
cd feng/
mvn clean package -Dmaven.test.skip=true
```

Output: `target/feng-<version>.jar` (e.g., `feng-0.0.1-dev.jar`)

## Usage

```bash
java -jar feng-<version>.jar [options]
```

### Options

| Flag | Argument | Description |
|------|----------|-------------|
| `-t` | `f` / `m` / `p` | Source type: **f**=single file, **m**=module, **p**=project |
| `-i` | path | Source path: file path for `f`, directory for `m`/`p` |
| `-o` | dir | Output directory (default: same as source) |
| `-p` | name | Package name (default: source filename or dirname) |
| `-L` | `name=path` | Add dependency package (repeatable) |
| `-b` | `make` / `cmake` | Backend build tool |
| `-T` | _(none)_ | Test mode: compile only `@Test` functions, skip `main` |
| `--test-name` | name | Filter test cases by name (repeatable, test mode only) |

### Examples

```bash
# Single file
java -jar feng-0.0.1-dev.jar -t f -i hello.feng -o /var/build

# Single file (test mode)
java -jar feng-0.0.1-dev.jar -T -t f -i test_math.feng -o /var/build

# Module
java -jar feng-0.0.1-dev.jar -t m -i ./my_module -o ./out

# Project with dependency
java -jar feng-0.0.1-dev.jar -t p -i ./my_project -L foo=/path/to/foo -o ./out
```

## Generated Output

The compiler generates C source files in the output directory. The selected backend build tool
(`make` or `cmake`) then compiles these into `.o` object files and links them. If a module
contains a `main` function, the build tool also produces an executable.

## LSP Server

```bash
java -cp feng-0.0.1-dev.jar org.cossbow.feng.lsp.FengLspMain
```

Supports: diagnostics, document symbols, hover, go-to-definition, completion.

## Output Model

| Input | Output |
|-------|--------|
| Single file (`-t f`) | Build artifacts (.o / executable) |
| Module (`-t m`) | Build artifacts per module |
| Project (`-t p`) | Build artifacts per module |

Modules with `main()` produce an executable; modules without `main()` produce object files only.
