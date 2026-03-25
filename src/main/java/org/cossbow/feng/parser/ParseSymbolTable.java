package org.cossbow.feng.parser;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.Optional;

import java.util.*;

public class ParseSymbolTable {

    public final IdentifierTable<TypeDefinition> namedTypes = new IdentifierTable<>();
    public final IdentifierTable<FunctionDefinition> namedFunctions = new IdentifierTable<>();
    public final IdentifierTable<GlobalVariable> variables = new IdentifierTable<>();
    public final MacroTable macros = new MacroTable();

    public final SymbolTable<TypeDefinition> exportedTypes = new SymbolTable<>();
    public final SymbolTable<FunctionDefinition> exportedFunctions = new SymbolTable<>();
    public final SymbolTable<GlobalVariable> exportedVariables = new SymbolTable<>();
    public final MacroTable exportedMacros = new MacroTable();

    //

    public final IdentifierTable<TypeDefinition> builtinTypes = new IdentifierTable<>();
    public final IdentifierTable<FunctionDefinition> builtinFunctions = new IdentifierTable<>();

    //

    public Optional<DAGGraph<GlobalVariable>> dagConst;
    public Optional<DAGGraph<GlobalVariable>> dagVars;
    public List<EnumDefinition> enumList;
    public Optional<DAGGraph<PrototypeDefinition>> dagPrototypes;
    public Optional<DAGGraph<StructureDefinition>> dagStructures;
    public Optional<DAGGraph<InterfaceDefinition>> dagInterfaces;
    public Optional<DAGGraph<ClassDefinition>> dagClasses;

    public final Map<StringLiteral, StringLiteral> stringCache = new HashMap<>();

    //

    public ParseSymbolTable() {
        namedTypes.add(ClassDefinition.ObjectName, ClassDefinition.ObjectClass);
        builtinTypes.add(ClassDefinition.ObjectName, ClassDefinition.ObjectClass);
    }
}
