# Fēng for VS Code

VS Code extension for the [Fēng](https://github.com/cossbow/feng) language.

## Features

- Syntax highlighting (TextMate grammar)
- Diagnostics (syntax + semantic)
- Code completion
- Hover / type info
- Go-to-definition
- Document symbols
- Semantic tokens (keywords / types / functions / variables / strings / numbers / comments)

## Requirements

- A Java runtime (the language server runs as `java -cp feng-x.y.z.jar org.cossbow.feng.lsp.FengLspMain`).
- On first activation the extension downloads the latest compiler JAR from GitHub Releases. To use a local build instead, set `feng.lsp.jarPath`.

## Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `feng.lsp.javaPath` | `java` | Path to the Java executable. |
| `feng.lsp.jarPath` | `""` | Path to the compiler JAR; empty = auto-download from GitHub Releases. |
| `feng.lsp.trace.server` | `off` | LSP trace verbosity (`off` / `messages` / `verbose`). |

## Develop

```bash
cd ide/vscode
npm install
npm run compile   # type-check and emit out/
npm run package   # build a .vsix with vsce
```

Then open this folder in VS Code and press F5 to launch an Extension Development Host.
