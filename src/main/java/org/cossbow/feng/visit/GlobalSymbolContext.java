package org.cossbow.feng.visit;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GlobalSymbolContext implements SymbolContext {

    private final ParseSymbolTable gst;

    public GlobalSymbolContext(ParseSymbolTable gst) {
        this.gst = gst;
    }

    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        checkModule(symbol);

        var pd = Primitive.findType(symbol.name().value());
        if (pd.has()) return Optional.of(pd.get());

        return gst.namedTypes.tryGet(symbol.name());
    }

    @Override
    public Optional<FunctionDefinition> findFunc(Symbol symbol) {
        checkModule(symbol);
        return gst.namedFunctions.tryGet(symbol.name());
    }

    @Override
    public Optional<Variable> findVar(Symbol symbol) {
        checkModule(symbol);
        return gst.variables.tryGet(symbol.name())
                .map(gv -> gv);
    }

    @Override
    public void putVar(Variable variable) {
    }

    @Override
    public List<Variable> scope() {
        return new ArrayList<>(gst.variables.values());
    }

    @Override
    public Stream<Variable> local() {
        return Stream.empty();
    }

    private void checkModule(Symbol symbol) {
        if (symbol.module().none()) return;
        ErrorUtil.unsupported(
                "can't search symbol in other module: %s",
                symbol.module());
    }
}
