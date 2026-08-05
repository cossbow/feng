package org.cossbow.feng.util;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.err.SemanticException;
import org.cossbow.feng.err.SyntaxException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final
public class ErrorUtil {
    private ErrorUtil() {
    }

    static volatile boolean traceError;
    static volatile boolean printWarn;

    public static void setTraceError(boolean t) {
        traceError = t;
    }

    public static void setPrintWarn(boolean w) {
        printWarn = w;
    }

    public static void warn(String fmt, Object... args) {
        var msg = "warn: " + fmt.formatted(args);
        if (printWarn) System.err.println(msg);
    }

    public static <T> T argument(String fmt, Object... args) {
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
        throw new SyntaxException(fmt.formatted(args), traceError);
    }

    public static <T> T semantic(String fmt, Object... arg) throws SemanticException {
        throw new SemanticException(fmt.formatted(arg), traceError);
    }

    public static <T> T modFail(String fmt, Object... arg) {
        throw new ModuleException(fmt.formatted(arg));
    }

    public static void reportError(List<String> errors) {
        if (errors.isEmpty()) return;
        for (var er : errors) {
            System.err.println(er);
        }
        ErrorUtil.semantic("got %d errors, please check.",
                errors.size());
    }

    public static <T> T duplicate(Entity entity, Entity old) throws SemanticException {
        return semantic("duplicate '%s' at %s, prev at %s",
                entity, entity.pos(), old.pos());
    }

    public static <T> T backend(String fmt, Object... arg) {
        throw new BackendException(fmt.formatted(arg));
    }

    public static class UnreachableException extends RuntimeException {
    }

    public static class ModuleException extends RuntimeException {
        public ModuleException(String message) {
            super(message);
        }
    }

    public static class BackendException extends RuntimeException {
        public BackendException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable>
    T sneaky(Throwable t) throws T {
        throw (T) t;
    }
}
