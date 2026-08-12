#!/usr/bin/env bash
# ============================================================
# init-agents.sh — Generate AGENTS.md for a Fēng user project
#
# Usage:
#   ./init-agents.sh [project-dir]
#
#   FENG_HOME must be set (environment variable or first arg).
#   If project-dir is omitted, the current directory is used.
#
#   The script creates AGENTS.md in the project root,
#   referencing specification.md and BUILD.md from $FENG_HOME/docs/.
#
#   A local .output/ directory is created (gitignore'd) for
#   compiler scratch files.
#
# Example:
#   export FENG_HOME=/opt/feng
#   ./init-agents.sh ~/my-feng-project
# ============================================================
set -euo pipefail

# ---- resolve FENG_HOME ----
if [ -z "${FENG_HOME:-}" ]; then
    echo "ERROR: FENG_HOME is not set." >&2
    echo "  Set it to the Fēng installation directory, e.g.:" >&2
    echo "  export FENG_HOME=/opt/feng" >&2
    exit 1
fi

# ---- resolve project directory ----
PROJECT_DIR="${1:-$(pwd)}"
if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: project directory does not exist: $PROJECT_DIR" >&2
    exit 1
fi
PROJECT_DIR="$(cd "$PROJECT_DIR" && pwd)"
PROJECT_NAME="$(basename "$PROJECT_DIR")"

# ---- ensure FENG_HOME has the expected docs ----
SPEC="$FENG_HOME/docs/specification.md"
BUILD="$FENG_HOME/docs/BUILD.md"
if [ ! -f "$SPEC" ]; then
    echo "WARNING: Language spec not found at $SPEC" >&2
    echo "  Copy specification.md into $FENG_HOME/docs/" >&2
fi
if [ ! -f "$BUILD" ]; then
    echo "WARNING: Build guide not found at $BUILD" >&2
    echo "  Copy BUILD.md into $FENG_HOME/docs/" >&2
fi

# ---- ensure FENG_HOME has the compiler jar ----
if ! ls "$FENG_HOME"/feng-*.jar >/dev/null 2>&1; then
    echo "WARNING: No feng-*.jar found in $FENG_HOME" >&2
    echo "  The AGENTS.md references will point to the jar but it may need updating." >&2
fi

# ---- create local directories ----
mkdir -p "$PROJECT_DIR/.output"
if [ ! -f "$PROJECT_DIR/.gitignore" ]; then
    echo ".output/" >> "$PROJECT_DIR/.gitignore"
else
    if ! grep -q ".output" "$PROJECT_DIR/.gitignore" 2>/dev/null; then
        echo ".output/" >> "$PROJECT_DIR/.gitignore"
    fi
fi

# ---- normalize FENG_HOME for config file ----
# Convert to absolute POSIX path for cross-platform readability
FENG_HOME_NORM="$(cd "$FENG_HOME" 2>/dev/null && pwd || echo "$FENG_HOME")"

# ---- generate AGENTS.md ----
AGENTS="$PROJECT_DIR/AGENTS.md"
cat > "$AGENTS" << EOF
# Fēng Project: $PROJECT_NAME

You are working on a **Fēng language** project. Follow these rules.

## Language Spec & Tools

| Resource | Path |
|----------|------|
| Language specification | \`$SPEC\` |
| Compiler usage guide | \`$BUILD\` |
| Fēng source files | \`*.feng\` in this project |
| Output directory | \`.output/\` (gitignored) |

## Workflow

1. [MUST] Read \`$SPEC\` before writing or modifying any Fēng code.
2. [MUST] Use the compiler via:

   \`\`\`bash
   java -jar $FENG_HOME_NORM/feng-<version>.jar -t f -i <source.feng> -o .output/<source>.cpp
   \`\`\`\`

3. [MUST] Compile generated C++ with \`--std=c++20\`:

   \`\`\`bash
   c++ --std=c++20 .output/<source>.cpp -o .output/<binary>
   \`\`\`

4. [MAY] Use test mode for \`@Test\` functions:

   \`\`\`bash
   java -jar $FENG_HOME_NORM/feng-<version>.jar -T -t f -i <test.feng> -o .output/<test>.cpp
   \`\`\`

## Key Conventions

- Module path separator: \`\$\` (e.g., \`std\$os\`)
- Entry point: \`func main()\` (no parameters, no return value)
- Export with \`export\` keyword; import with \`import\` keyword
- Circular imports are forbidden
- Reference markers: \`*\`=strong, \`&\`=phantom, \`?\`=nullable, \`#\`=unmodifiable

## Project Structure

\`\`\`
$PROJECT_NAME/
├── *.feng               # Fēng source files
├── AGENTS.md            # This file (AI instructions)
├── .output/           # Compiler output (gitignored)
└── .gitignore
\`\`\`
EOF

echo "✅ AGENTS.md created at $AGENTS"
echo "   FENG_HOME = $FENG_HOME_NORM"
[ ! -f "$SPEC" ]  && echo "   ⚠ specification.md not found — copy it to $FENG_HOME/docs/"
[ ! -f "$BUILD" ] && echo "   ⚠ BUILD.md not found — copy it to $FENG_HOME/docs/"
