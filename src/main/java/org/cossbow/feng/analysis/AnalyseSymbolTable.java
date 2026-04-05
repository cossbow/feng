package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.Lazy;

import java.util.List;
import java.util.Map;

public class AnalyseSymbolTable {
    public final Lazy<FModule> module = Lazy.nil();

    public List<TypeDefinition> typeList;
    public List<FunctionDefinition> functionList;

    public List<GlobalVariable> constVars;
    public DAGGraph<GlobalVariable> dagVars;
    public List<EnumDefinition> enumList;
    public DAGGraph<PrototypeDefinition> dagPrototypes;
    public DAGGraph<StructureDefinition> dagStructures;
    public DAGGraph<InterfaceDefinition> dagInterfaces;
    public DAGGraph<ClassDefinition> dagClasses;

    public Map<StringLiteral, StringLiteral> stringCache;
    public final Lazy<FunctionDefinition> main = Lazy.nil();

}
