package org.cossbow.feng.ast.proc;

import org.cossbow.feng.analysis.fmt.Formatter;
import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.ReferKind;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.StringLiteral;
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

    public Optional<Procedure> procedure() {
        return procedure;
    }

    public Prototype prototype() {
        return prototype;
    }

    public boolean variadic() {
        return prototype.variadic();
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
            new FixedParameter(ZERO, new Identifier("out"),
                    Formatter.FORMAT_OUT),
            new FixedParameter(ZERO, new Identifier("fmt"),
                    StringLiteral.array(ZERO, ReferKind.PHANTOM))
    ))));

    /**
     * func intToStr(n Int, buf [*!]byte) Int
     */
    public static final FunctionDefinition INT_TO_STR_FUNC = new FunctionDefinition(ZERO,
            Modifier.empty(), new Symbol(new Identifier("intToStr")),
            TypeParameters.empty(), new Prototype(ZERO, new ParameterSet(ZERO, List.of(
            FixedParameter.create("n", Primitive.INT),
            new FixedParameter(ZERO, new Identifier("buf"),
                    StringLiteral.array(ZERO, ReferKind.PHANTOM))
    )), Primitive.INT.declarer()));

    /**
     * func floatToStr(n Float, buf [*!]byte) Int
     */
    public static final FunctionDefinition FLOAT_TO_STR_FUNC = new FunctionDefinition(ZERO,
            Modifier.empty(), new Symbol(new Identifier("floatToStr")),
            TypeParameters.empty(), new Prototype(ZERO, new ParameterSet(ZERO, List.of(
            FixedParameter.create("n", Primitive.INT),
            new FixedParameter(ZERO, new Identifier("buf"),
                    StringLiteral.array(ZERO, ReferKind.PHANTOM))
    )), Primitive.INT.declarer()));

    //
    @Override
    public String toString() {
        return "func " + symbol() + procedure;
    }
}
