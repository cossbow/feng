package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Source;

import java.util.List;

public record ParseResult(Source root,
                          GlobalSymbolTable table,
                          List<SyntaxErrorMsg> errors) {
}
