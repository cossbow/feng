package org.cossbow.feng.parser;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.stmt.DeclarationStatement;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.Map;

public class ParseSymbolTable {

    public final IdentifierTable<TypeDefinition> namedTypes = new IdentifierTable<>();
    public final IdentifierTable<FunctionDefinition> namedFunctions = new IdentifierTable<>();
    public final IdentifierTable<GlobalVariable> variables = new IdentifierTable<>();

    public final SymbolTable<TypeDefinition> exportedTypes = new SymbolTable<>();
    public final SymbolTable<FunctionDefinition> exportedFunctions = new SymbolTable<>();
    public final SymbolTable<GlobalVariable> exportedVariables = new SymbolTable<>();

    //

    public final IdentifierTable<TypeDefinition> builtinTypes = new IdentifierTable<>();
    public final IdentifierTable<FunctionDefinition> builtinFunctions = new IdentifierTable<>();

    //

    public volatile Optional<DAGGraph<GlobalVariable>> dagConst;
    public volatile Optional<DAGGraph<GlobalVariable>> dagVars;
    public volatile List<EnumDefinition> enumList;
    public volatile Optional<DAGGraph<PrototypeDefinition>> dagPrototypes;
    public volatile Optional<DAGGraph<StructureDefinition>> dagStructures;
    public volatile Optional<DAGGraph<InterfaceDefinition>> dagInterfaces;
    public volatile Optional<DAGGraph<ClassDefinition>> dagClasses;

    //

    public ParseSymbolTable() {
        namedTypes.add(ClassDefinition.ObjectName, ClassDefinition.ObjectClass);
        builtinTypes.add(ClassDefinition.ObjectName, ClassDefinition.ObjectClass);
    }
}
