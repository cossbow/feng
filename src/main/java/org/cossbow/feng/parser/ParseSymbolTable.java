package org.cossbow.feng.parser;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;

import java.util.ArrayList;
import java.util.List;

public class ParseSymbolTable {
    public final IdentifierTable<TypeDefinition> namedTypes = new IdentifierTable<>();
    public final List<TypeDefinition> unnamedTypes = new ArrayList<>();
    public final IdentifierTable<FunctionDefinition> namedFunctions = new IdentifierTable<>();
    public final List<Procedure> lambdas = new ArrayList<>();
    public final IdentifierTable<TypeDefinition> exportedTypes = new IdentifierTable<>();
    public final IdentifierTable<FunctionDefinition> exportedFunctions = new IdentifierTable<>();
    public final IdentifierTable<Variable> variables = new IdentifierTable<>();
    public final IdentifierTable<Variable> exportedVariables = new IdentifierTable<>();


}
