package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.cossbow.feng.util.ErrorUtil.semantic;

public class GlobalSymbolContext implements SymbolContext {
    private final Map<ModulePath, ParseSymbolTable> imports;
    private final ParseSymbolTable gst;

    public GlobalSymbolContext(Map<ModulePath, ParseSymbolTable> imports,
                               ParseSymbolTable gst) {
        this.imports = imports;
        this.gst = gst;
    }

    public GlobalSymbolContext(ParseSymbolTable gst) {
        this(Map.of(), gst);
    }

    private ParseSymbolTable tableOf(ModulePath path) {
        var ctx = imports.get(path);
        if (ctx != null) return ctx;
        return semantic("import '%s' fail: %s", path, path.pos());
    }

    public boolean isLocal(Symbol s) {
        return gst.module.none() ||
                s.module().none() ||
                gst.module.equals(s.module());
    }

    private <E extends Exportable> Optional<E>
    checkExport(Symbol s, Optional<E> oe) {
        if (!oe.match(e -> !e.export()))
            return oe;

        return semantic("Cannot use the unexported '%s' here: %s",
                s, s.pos());
    }

    @Override
    public Optional<TypeDefinition> findType(Symbol s) {
        if (isLocal(s)) return gst.findType(s.name());

        var o = tableOf(s.module().get())
                .findType(s.name());
        return checkExport(s, o);
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol s) {
        if (isLocal(s)) return gst.findFunc(s.name());
        var o = tableOf(s.module().get())
                .findFunc(s.name());
        return checkExport(s, o);
    }

    @Override
    public Optional<Variable> findVar(Symbol s) {
        if (isLocal(s)) return gst.findVar(s.name());
        var o = tableOf(s.module().get())
                .findVar(s.name());
        return checkExport(s, o);
    }

    @Override
    public void putVar(Variable variable) {
        ErrorUtil.unreachable();
    }

    @Override
    public List<Variable> scope() {
        return ErrorUtil.unreachable();
    }

    @Override
    public Stream<Variable> local() {
        return Stream.empty();
    }

    public boolean lockVar(Variable v) {
        return false;
    }

    public boolean isVarLocked(Variable v) {
        return false;
    }

}
