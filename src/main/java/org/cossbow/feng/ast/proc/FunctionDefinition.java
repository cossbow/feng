package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.ReferKind;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.util.Optional;

import java.util.List;

import static org.cossbow.feng.ast.Position.ZERO;

/**
 * Function is an executable procedure.
 */
public class FunctionDefinition extends Definition {
    /**
     * The prototype of this function
     */
    private final Prototype prototype;
    /**
     * This field is not empty unless it is in the metadata
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
     * func main is the entry of an executable program
     */
    public static final Identifier MAIN_ID = new Identifier("main");
    public static final Symbol MAIN_SYMBOL = new Symbol(MAIN_ID);

    public static final FunctionDefinition FORMAT_FUNC = new FunctionDefinition(ZERO,
            Modifier.empty(), new Symbol(new Identifier("format")),
            TypeParameters.empty(), new Prototype(ZERO, new ParameterSet(ZERO, List.of(
            // Ignore parameter settings: Because they do not need to
            // be defined and can be called directly
    )), StringLiteral.array(ZERO, ReferKind.STRONG)));

    //
    @Override
    public String toString() {
        return "func " + symbol() + procedure;
    }
}
