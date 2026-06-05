package org.cossbow.feng.parser;

import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.dcl.PrimitiveDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.DedupCache;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

/**
 * The parsed symbol table is generated after syntax parsing
 * and is used as input for semantic analysis.
 */
public class ParseSymbolTable {
    public final Optional<ModulePath> module;
    public final DedupCache<StringLiteral> stringCache;

    public ParseSymbolTable(Optional<ModulePath> module,
                            DedupCache<StringLiteral> stringCache) {
        this.module = module;
        this.stringCache = stringCache;
    }

    /**
     * Collection of custom types
     */
    public final IdentifierMap<TypeDefinition> types = new IdentifierMap<>();
    /**
     * Collection of custom functions
     */
    public final IdentifierMap<FunctionDefinition> functions = new IdentifierMap<>();
    /**
     * Collection of global variables
     */
    public final IdentifierMap<GlobalVariable> variables = new IdentifierMap<>();
    /**
     * Collection of global macros
     */
    public final MacroTable macros = new MacroTable();
    /**
     * main function: the entry function of an executable program,
     * if empty, indicates that the current module is a library.
     */
    public final Lazy<FunctionDefinition> main = Lazy.nil();

    public void merge(ParseSymbolTable ot) {
        types.addAll(ot.types);
        functions.addAll(ot.functions);
        variables.addAll(ot.variables);
        macros.addAll(ot.macros);
        stringCache.addAll(ot.stringCache);
        if (main.none()) {
            main.set(ot.main);
            return;
        }
        if (ot.main.has()) {
            ErrorUtil.semantic(
                    "duplicate func main defined: %s <--> %s",
                    main.must().pos(), ot.main.must().pos());
        }
    }

    public void add(TypeDefinition def) {
        types.add(def.symbol().name(), def);
    }

    public void add(FunctionDefinition def) {
        functions.add(def.symbol().name(), def);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> find(Identifier name, IdentifierMap<T>... maps) {
        for (IdentifierMap<T> map : maps) {
            var o = map.tryGet(name);
            if (o.has()) return o;
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Optional<TypeDefinition> findType(Identifier name) {
        return find(name, BUILTIN.types, types);
    }

    @SuppressWarnings("unchecked")
    public Optional<FunctionDefinition> findFunc(Identifier name) {
        return find(name, BUILTIN.functions, functions);
    }

    @SuppressWarnings("unchecked")
    public Optional<Variable> findVar(Identifier name) {
        return find(name, BUILTIN.variables, variables).map(v -> v);
    }

    //

    public static boolean isBuiltin(Identifier name) {
        return BUILTIN.types.exists(name) ||
                BUILTIN.functions.exists(name) ||
                BUILTIN.variables.exists(name);
    }

    /**
     * built-in symbol table
     */
    public static final ParseSymbolTable BUILTIN = new ParseSymbolTable(
            Optional.empty(), new DedupCache<>());

    static {
        PrimitiveDefinition.types.forEach((k, v) ->
                BUILTIN.types.add(new Identifier(k.code), v));
        BUILTIN.add(AttributeDefinition.InheritDef);
        BUILTIN.add(ClassDefinition.ObjectClass);
        BUILTIN.add(InterfaceDefinition.WriterType);
        BUILTIN.add(InterfaceDefinition.WritableType);
        BUILTIN.add(FunctionDefinition.FORMAT_FUNC);
    }
}
