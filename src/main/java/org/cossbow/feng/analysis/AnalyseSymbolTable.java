package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.Lazy;

import java.util.*;

/**
 * The analysis symbol table is generated after syntax analysis
 * and is used as input for the generation of target code.
 */
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

    // ---- Monomorphization results (populated by Monomorphization pass) ----

    /**
     * Concrete generic type instantiations (class/interface).
     * Deduplicated by DerivedType.equals/hashCode.
     * @deprecated Use {@link #concreteTypeInsts} instead; will be removed after CGenerator refactor.
     */
    @Deprecated
    public Set<DerivedType> concreteInstantiations = new LinkedHashSet<>();

    /**
     * All concrete type instantiations in DAG topological order.
     * Populated by the Monomorphization pass.
     */
    public DAGGraph<ConcreteTypeInst> concreteTypeInsts = DAGGraph.empty();

    /**
     * Mapping from resolved TypeDeclarer → ConcreteTypeInst.
     * Used by CGenerator to look up type definitions and type parameter mappings.
     * Populated by the Monomorphization pass.
     */
    public Map<String, ConcreteTypeInst> typeToInst = new LinkedHashMap<>();

    /**
     * Concrete generic function instantiations: (FunctionDefinition, TypeArguments).
     * Deduplicated by FuncInstantiation.equals/hashCode.
     */
    public Set<FuncInstantiation> concreteFuncInsts = new LinkedHashSet<>();

    /**
     * Concrete method-level generic instantiations: (classOwner, method, methodArgs).
     * Deduplicated by MethodInstantiation.equals/hashCode.
     */
    public Set<MethodInstantiation> concreteMethodInsts = new LinkedHashSet<>();

    /**
     * Imported (cross-module) generic function instantiations:
     * mangled name → FuncInstantiation (fd + concrete type arguments).
     * Used by CGenerator to emit correct extern declarations with
     * resolved types (not raw generic type variables like $T).
     */
    public Map<String, FuncInstantiation> externFuncInsts = new LinkedHashMap<>();

    /**
     * Pending anonymous prototype typedefs registered by emitProtoType
     * during code generation. Flushed once by {@code declareProtoTypedefs()}.
     */
    public final Set<org.cossbow.feng.ast.proc.Prototype> pendingProtoTypedefs =
            new LinkedHashSet<>();

}
