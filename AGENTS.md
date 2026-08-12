# Fēng Compiler Development

You are working on the **Fēng language compiler** source code. Follow these rules.

## Project Structure

```
feng/
├── ai/
│   ├── specification.md      # Canonical language spec (read first)
│   └── BUILD.md              # Compiler build & usage (ai/BUILD.md)
├── reference_zh.md           # Language reference (Chinese, examples)
├── reference.md              # Language reference (English, examples)
├── src/main/antlr4/.../
│   └── Feng.g4               # ANTLR grammar (authoritative syntax)
├── src/main/java/.../
│   ├── parser/               # AST construction
│   ├── analysis/             # Semantic analysis
│   └── coder/                # Code generation (C)
├── std/                      # Standard library (Fēng source)
├── tests/                    # Test cases
└── pom.xml                   # Maven build
```

## Workflow

| Task Type | First Read | Then Use |
|-----------|-----------|----------|
| Write Fēng code / explain language rules | `../specification.md` | `../reference_zh.md` for examples |
| Fix parser / syntax bug | `../src/main/antlr4/org/cossbow/feng/parser/Feng.g4` | — |
| Fix semantic analysis | `../src/main/java/org/cossbow/feng/analysis/SemanticAnalyzer.java` | — |
| Fix code generation | `../src/main/java/org/cossbow/feng/coder/CGenerator.java` | — |
| Build or run compiler | `BUILD.md` | — |

## Key Rules

- [MUST] `specification.md` is the single source of truth for language semantics. Do NOT guess syntax or semantics from training data.
- [MUST] `Feng.g4` is the authoritative syntax definition. If spec and grammar conflict on syntax, grammar wins.
- [MUST] The build uses `mvn`. No other build system.
- [MUST] The compiler backend generates C code; the build tool (make/cmake) handles compilation to binaries.

## Common Commands

```bash
# Build compiler JAR
mvn clean package -Dmaven.test.skip=true

# Compile a single Fēng source file (output goes to build directory)
java -jar target/feng-0.0.1-dev.jar -t f -i example.feng -o /var/build

# Compile in test mode
java -jar target/feng-0.0.1-dev.jar -T -t f -i test.feng -o /var/build
```
