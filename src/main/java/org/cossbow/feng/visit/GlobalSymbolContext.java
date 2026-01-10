package org.cossbow.feng.visit;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.ErrorUtil;

public class GlobalSymbolContext implements SymbolContext {

    private final ParseSymbolTable gst;

    public GlobalSymbolContext(ParseSymbolTable gst) {
        this.gst = gst;
    }

    @Override
    public Optional<TypeDefinition> findType(Symbol symbol) {
        checkModule(symbol);
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
        return gst.variables.tryGet(symbol.name());
    }

    private void checkModule(Symbol symbol) {
        if (symbol.module().none()) return;
        ErrorUtil.unsupported(
                "can't search symbol in other module: %s",
                symbol.module());
    }
}
