package org.cossbow.feng.parser;

import org.cossbow.feng.ast.Source;

import java.util.List;

public record ParseResult(Source root, List<SyntaxErrorMsg> errors) {
}
