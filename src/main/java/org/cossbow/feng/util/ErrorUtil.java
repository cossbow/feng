package org.cossbow.feng.util;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.err.SemanticException;
import org.cossbow.feng.err.SyntaxException;

import java.io.IOException;
import java.io.UncheckedIOException;

final
public class ErrorUtil {
    private ErrorUtil() {
    }

    public static <T>T argument(String fmt, Object... args) {
        throw new IllegalArgumentException(String.format(fmt, args));
    }

    public static <T> T unsupported(String fmt, Object... args)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException(fmt.formatted(args));
    }

    public static <T> T unreachable() throws UnreachableException {
        throw new UnreachableException();
    }

    public static <T> T io(IOException e) throws UncheckedIOException {
        throw new UncheckedIOException(e);
    }

    public static <T> T syntax(String fmt, Object... args) throws SyntaxException {
        throw new SyntaxException(fmt.formatted(args));
    }

    public static <T> T semantic(String fmt, Object... arg) throws SemanticException {
        throw new SemanticException(fmt.formatted(arg));
    }

    public static <T> T modFail(String fmt, Object... arg) {
        throw new ModuleException(fmt.formatted(arg));
    }

    public static <T> T binExpr(Position pos) {
        return semantic("operands of binary-expression must same type: %s", pos);
    }

    public static <T> T duplicate(Entity entity, Entity old) throws SemanticException {
        throw new SemanticException("duplicate '%s' at %s, prev at %s"
                .formatted(entity, entity.pos(), old.pos()));
    }

    public static <T> T align(Position a, Position b) throws SemanticException {
        throw new SemanticException("The number required same: @%s <- -> @%s"
                .formatted(a, b));
    }

    public static class UnreachableException extends RuntimeException {
    }

    public static class ModuleException extends RuntimeException {
        public ModuleException(String message) {
            super(message);
        }
    }
}
