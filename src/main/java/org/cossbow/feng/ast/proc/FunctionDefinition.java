package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

/**
 * Function is an executable procedure.
 */
public class FunctionDefinition extends Definition {
    /**
     * The prototype of this function
     */
    private final Prototype prototype;
    /**
     * This field is non empty unless it is in the metadata
     */
    private final Optional<Procedure> procedure;

    private FunctionDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               Prototype prototype,
                               Optional<Procedure> procedure) {
        super(pos, modifier, symbol, generic);
        this.prototype = prototype;
        this.procedure = procedure;
    }

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Procedure procedure) {
        this(pos, modifier, symbol, generic,
                procedure.prototype(),
                Optional.of(procedure));
    }

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Prototype prototype) {
        this(pos, modifier, symbol, generic,
                prototype, Optional.empty());
    }

    public Procedure procedure() {
        return procedure.must();
    }

    public Prototype prototype() {
        return prototype;
    }


    //

    /**
     * func main is the entry of a executable program
     */
    public static final Identifier MAIN_ID = new Identifier("main");
    public static final Symbol MAIN_SYMBOL = new Symbol(MAIN_ID);


    //
    @Override
    public String toString() {
        return "func " + symbol() + procedure;
    }
}
