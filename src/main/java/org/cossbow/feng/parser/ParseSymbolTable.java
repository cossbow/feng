package org.cossbow.feng.parser;

import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.SymbolTable;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.stmt.DeclarationStatement;

public class ParseSymbolTable {

    public final IdentifierTable<TypeDefinition> namedTypes = new IdentifierTable<>();
    public final IdentifierTable<TypeDefinition> unnamedTypes = new IdentifierTable<>();
    public final IdentifierTable<FunctionDefinition> namedFunctions = new IdentifierTable<>();
    public final IdentifierTable<GlobalVariable> variables = new IdentifierTable<>();

    public final SymbolTable<TypeDefinition> exportedTypes = new SymbolTable<>();
    public final SymbolTable<FunctionDefinition> exportedFunctions = new SymbolTable<>();
    public final SymbolTable<GlobalVariable> exportedVariables = new SymbolTable<>();


}
