package org.cossbow.feng.ast;

import org.cossbow.feng.ast.mod.Import;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.parser.ParseSymbolTable;

import java.util.List;

public class Source extends Entity {
    private List<Import> imports;
    private ParseSymbolTable table;

    public Source(Position pos,
                  List<Import> imports,
                  ParseSymbolTable table) {
        super(pos);
        this.imports = imports;
        this.table = table;
    }

    public List<Import> imports() {
        return imports;
    }

    public List<TypeDefinition> types() {
        return table.namedTypes.values();
    }

    public TypeDefinition type(Identifier name) {
        return table.namedTypes.get(name);
    }

    public List<FunctionDefinition> functions() {
        return table.namedFunctions.values();
    }

    public FunctionDefinition function(Identifier name){
        return table.namedFunctions.get(name);
    }

    public List<GlobalVariable> variables() {
        return table.variables.values();
    }

    public ParseSymbolTable table() {
        return table;
    }
}
