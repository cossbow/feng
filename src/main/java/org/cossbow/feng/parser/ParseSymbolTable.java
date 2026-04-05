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
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.util.DedupCache;
import org.cossbow.feng.util.Optional;

public class ParseSymbolTable {
    public final Optional<ModulePath> module;

    public ParseSymbolTable(Optional<ModulePath> module) {
        this.module = module;
    }

    public final IdentifierMap<TypeDefinition> types = new IdentifierMap<>();
    public final IdentifierMap<FunctionDefinition> functions = new IdentifierMap<>();
    public final IdentifierMap<GlobalVariable> variables = new IdentifierMap<>();
    public final MacroTable macros = new MacroTable();
    public final DedupCache<StringLiteral> stringCache = new DedupCache<>();

    public void merge(ParseSymbolTable ot) {
        types.addAll(ot.types);
        functions.addAll(ot.functions);
        variables.addAll(ot.variables);
        macros.addAll(ot.macros);
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


    public static final ParseSymbolTable BUILTIN = new ParseSymbolTable(Optional.empty());

    static {
        PrimitiveDefinition.types.forEach((k, v) ->
                BUILTIN.types.add(new Identifier(k.code), v));
        BUILTIN.types.add(AttributeDefinition.InheritName, AttributeDefinition.InheritDef);
        BUILTIN.types.add(ClassDefinition.ObjectName, ClassDefinition.ObjectClass);
    }
}
