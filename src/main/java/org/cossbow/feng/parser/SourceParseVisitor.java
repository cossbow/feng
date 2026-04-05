package org.cossbow.feng.parser;


import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.AttributeField;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.micro.*;
import org.cossbow.feng.ast.mod.Import;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.util.*;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.ErrorUtil.*;

final class SourceParseVisitor
        extends FengBaseVisitor<Entity>
        implements FengVisitor<Entity> {


    /* ************* */
    /*  declaration  */
    /* ************* */


    //
    // symbols table
    //

    final String file;
    final Optional<ModulePath> module;
    final Charset charset;
    final ParseSymbolTable table;
    final boolean metadata;

    public SourceParseVisitor(String file,
                              Optional<ModulePath> module,
                              Charset charset,
                              ParseSymbolTable table,
                              boolean metadata) {
        this.file = file;
        this.module = module;
        this.charset = charset;
        this.table = table;
        this.metadata = metadata;
    }

    //
    // utils
    //

    @SuppressWarnings("unchecked")
    private <N extends Entity> Optional<N> visitOptional(ParseTree tree) {
        if (tree == null) return Optional.empty();
        return Optional.of((N) visit(tree));
    }

    @SuppressWarnings("unchecked")
    private <E extends Entity> List<E> visitList(List<? extends ParseTree> trees) {
        if (trees == null) return List.of();
        var result = new ArrayList<E>(trees.size());
        for (ParseTree tree : trees) {
            var n = (E) visit(tree);
            result.add(n);
        }
        return result;
    }

    private <K extends Entity, E extends Entity, T extends OrderlyMap<K, E>>
    T parseUniques(T tab, List<? extends ParseTree> trees, Function<E, K> identify) {
        var list = this.<E>visitList(trees);
        for (var e : list) {
            tab.add(identify.apply(e), e);
        }
        return tab;
    }


    Position posOf(Token t) {
        return new Position(file, t, null);
    }

    Position posOf(TerminalNode tn) {
        return new Position(file, tn.getSymbol(), null);
    }

    Position posOf(ParserRuleContext ctx) {
        return new Position(file, ctx.getStart(), ctx.getStop());
    }

    //
    // token
    //


    Identifier identifier(TerminalNode tn) {
        return identifier(tn.getSymbol());
    }

    Identifier identifier(Token tn) {
        return new Identifier(posOf(tn), tn.getText());
    }

    Optional<Identifier> identifierOptional(Token tn) {
        if (tn == null) return Optional.empty();
        return Optional.of(identifier(tn));
    }

    List<Identifier> identifiers(List<? extends TerminalNode> nodes) {
        return nodes.stream().map(this::identifier).toList();
    }

    List<Identifier> identifiers(FengParser.IdentifierListContext listCtx) {
        return identifiers(listCtx.Identifier());
    }

    Symbol defineSymbol(Identifier id) {
        return new Symbol(id.pos(), module, id);
    }

    Symbol defineSymbol(Token tn) {
        return defineSymbol(identifier(tn));
    }

    //
    // module: start
    //

    public final Map<Identifier, Entity> globalNames = new HashMap<>();

    private void checkGlobalName(Identifier name, Entity def) {
        var old = globalNames.putIfAbsent(name, def);
        if (old == null) return;
        var m = switch (old) {
            case TypeDefinition d -> d.domain();
            case FunctionDefinition d -> "function";
            case GlobalVariable v -> "variable";
            default -> unreachable();
        };
        semantic("name '%s' @%s has been used by %s: %s",
                name, def.pos(), m, old.pos());
    }

    private Map<Identifier, ModulePath> importMap = new HashMap<>();

    @Override
    public Entity visitSource(FengParser.SourceContext ctx) {
        var imports = this.<Import>visitList(ctx.import_());
        var cache = new DedupCache<ModulePath>();
        for (var i : imports) {
            importMap.put(i.name(), cache.dedup(i.path()));
        }
        // 语法格式是import在最前面，所以先缓存起来，下面解析symbol时直接替换成路径
        visitList(ctx.global());
        return new Source(posOf(ctx), cache.toList(), table);
    }

    @Override
    public Entity visitImport_(FengParser.Import_Context ctx) {
        var pos = posOf(ctx);
        var mod = identifiers(ctx.module().Identifier());
        var path = new ModulePath(pos, mod);
        var alias = identifierOptional(ctx.alias);
        return new Import(pos, path, alias);
    }

    @Override
    public Entity visitGlobalTypeDefinition(FengParser.GlobalTypeDefinitionContext ctx) {
        var def = (TypeDefinition) visit(ctx.def);
        checkGlobalName(def.symbol().name(), def);
        table.types.add(def.symbol().name(), def);
        return def;
    }

    @Override
    public Entity visitGlobalFunctionDefinition(
            FengParser.GlobalFunctionDefinitionContext ctx) {
        var def = (FunctionDefinition) visit(ctx.def);
        checkGlobalName(def.symbol().name(), def);
        table.functions.add(def.symbol().name(), def);
        return def;
    }

    private void globalVarCheck(Variable v) {
        if (v.type().none()) return;
        var r = v.type().must().maybeRefer();
        if (r.none()) return;
        if (!r.must().isKind(PHANTOM)) return;
        semantic("global variable can't be phantom reference: %s", v.pos());
    }

    @Override
    public Entity visitGlobalDeclaration(FengParser.GlobalDeclarationContext ctx) {
        var export = isExport(ctx.exportable());
        var stmt = (DeclarationStatement) visit(ctx.declaration());
        for (var v : stmt.variables()) {
            globalVarCheck(v);
            var gv = new GlobalVariable(export, v, defineSymbol(v.name()));
            checkGlobalName(gv.name(), gv);
            table.variables.add(gv.name(), gv);
        }
        return stmt;
    }

    @Override
    public Entity visitGlobalMacro(FengParser.GlobalMacroContext ctx) {
        var macro = (Macro) visit(ctx.macro());
        table.macros.add(macro);
        return macro;
    }

    private boolean isExport(FengParser.ExportableContext ctx) {
        return ctx.EXPORT() != null;
    }

    //
    // module: end
    //

    //
    // literal: start
    //

    @Override
    public Entity visitLiteral(FengParser.LiteralContext ctx) {
        Literal literal = parseInteger(ctx.integerLiteral());
        if (literal != null) return literal;

        literal = parseFloat(ctx.FloatLiteral());
        if (literal != null) return literal;

        literal = parseString(ctx.StringLiteral());
        if (literal != null) return literal;

        literal = parseBool(ctx.BoolLiteral());
        if (literal != null) return literal;

        return parseNil(ctx.NilLiteral());
    }

    private IntegerLiteral parseInteger(FengParser.IntegerLiteralContext ctx) {
        if (ctx == null) return null;

        var tn = (TerminalNode) ctx.getChild(0);
        var sym = tn.getSymbol();
        var radix = switch (sym.getType()) {
            case FengParser.DecimalInteger -> 10;
            case FengParser.HexInteger -> 16;
            case FengParser.OctalInteger -> 8;
            case FengParser.BinaryInteger -> 2;
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
        var txt = tn.getText();
        if (radix != 10) txt = txt.substring(2);
        return new IntegerLiteral(posOf(ctx), new BigInteger(txt, radix), radix);
    }

    private FloatLiteral parseFloat(TerminalNode tn) {
        if (tn == null) return null;
        return new FloatLiteral(posOf(tn), new BigDecimal(tn.getText()));
    }

    private StringLiteral parseString(TerminalNode tn) {
        if (tn == null) return null;
        var text = tn.getText();
        text = text.substring(1, text.length() - 1);
        var sl = new StringLiteral(posOf(tn), charset, text.getBytes(charset)); // 去掉引号
        return table.stringCache.dedup(sl);
    }

    private BoolLiteral parseBool(TerminalNode tn) {
        if (tn == null) return null;
        return new BoolLiteral(posOf(tn), Boolean.parseBoolean(tn.getText()));
    }

    private NilLiteral parseNil(TerminalNode tn) {
        return tn == null ? null : new NilLiteral(posOf(tn));
    }


    //
    // literal: end
    //

    //
    // declare: start
    //


    private Optional<Refer> parseRefer(
            FengParser.ReferContext ctx) {
        if (ctx == null) return Optional.empty();
        ReferKind type = switch (ctx.kind.getType()) {
            case FengParser.MUL -> STRONG;
            case FengParser.BITAND -> PHANTOM;
            default -> unreachable();
        };
        var required = ctx.required != null;
        var immutable = ctx.immutable != null;
        return Optional.of(new Refer(posOf(ctx),
                type, required, immutable));
    }

    @Override
    public Entity visitArrayTypeDeclarer(
            FengParser.ArrayTypeDeclarerContext ctx) {
        var pos = posOf(ctx);
        var typeDcl = (TypeDeclarer) visit(ctx.typeDeclarer());
        var at = ctx.arrayType();
        var len = this.<Expression>visitOptional(at.len);
        var refer = parseRefer(at.refer());
        return new ArrayTypeDeclarer(pos,
                typeDcl, len, refer);
    }

    @Override
    public Entity visitDefinedTypeDeclarer(
            FengParser.DefinedTypeDeclarerContext ctx) {
        var refer = parseRefer(ctx.refer());
        var dt = (DefinedType) visit(ctx.definedType());
        if (dt instanceof PrimitiveType pt)
            return pt.primitive().declarer(pt.pos(), refer);
        if (dt instanceof GenericType gt)
            return new GenericTypeDeclarer(posOf(ctx), gt);
        return new DerivedTypeDeclarer(posOf(ctx),
                (DerivedType) dt, refer);
    }

    @Override
    public Entity visitFuncTypeDeclarer(FengParser.FuncTypeDeclarerContext ctx) {
        var required = ctx.required != null;
        var pt = this.<Prototype>visitOptional(ctx.prototype());
        if (pt.has())
            return new AnonFuncTypeDeclarer(posOf(ctx), required, pt.get());

        var dt = (DerivedType) visit(ctx.definedType());
        Objects.requireNonNull(dt);
        return new NamedFuncTypeDeclarer(posOf(ctx), required, dt);
    }

    public List<TypeDeclarer> parseTypeDeclarerList(
            FengParser.TypeDeclarerListContext ctx) {
        return visitList(ctx.typeDeclarer());
    }

    //
    // declare: end
    //

    //
    // generic: start
    //


    private final Stack<TypeParameters> genericStack = new Stack<>();

    private Optional<TypeParameter> findGeneric(Identifier name) {
        for (var tp : genericStack) {
            var o = tp.params().tryGet(name);
            if (o.has()) return o;
        }
        return Optional.empty();
    }

    private TypeArguments typeArguments(FengParser.TypeArgumentsContext ctx) {
        if (ctx == null) return TypeArguments.EMPTY;
        var arguments = parseTypeDeclarerList(ctx.typeDeclarerList());
        return new TypeArguments(posOf(ctx), arguments);
    }

    @Override
    public Entity visitDefinedType(FengParser.DefinedTypeContext ctx) {
        var symbol = parseSymbol(ctx.symbol());
        if (symbol.module().none()) {
            var name = symbol.name();
            // 优先解析泛型参数
            var gt = findGeneric(name);
            if (gt.has()) {
                var ta = ctx.typeArguments();
                if (ta != null)
                    return semantic("%s have no generic: %s", name, posOf(ta));
                return new GenericType(posOf(ctx), gt.get());
            }

            // 然后解析基本类型
            var pd = Primitive.ofCode(name.value());
            if (pd.has()) {
                var ta = ctx.typeArguments();
                if (ta != null)
                    return semantic("%s have no generic: %s",
                            name, posOf(ta));

                return new PrimitiveType(posOf(ctx), name, pd.get());
            }

            symbol = defineSymbol(name);
        }
        var args = typeArguments(ctx.typeArguments());
        return new DerivedType(posOf(ctx), symbol, args);
    }


    @Override
    public Entity visitDomainTypeConstraint(FengParser.DomainTypeConstraintContext ctx) {
        var domain = TypeDomain.parse(ctx.typeDomain().getText());
        return new DomainTypeConstraint(posOf(ctx), domain);
    }

    @Override
    public Entity visitDefinedTypeConstraint(FengParser.DefinedTypeConstraintContext ctx) {
        var definedType = (DefinedType) visit(ctx.definedType());
        return new DefinedTypeConstraint(posOf(ctx), definedType);
    }

    @Override
    public Entity visitBinaryTypeConstraint(FengParser.BinaryTypeConstraintContext ctx) {
        var op = switch (ctx.op.getType()) {
            case FengParser.BITAND -> TypeOperator.AND;
            case FengParser.BITOR -> TypeOperator.OR;
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
        var left = (TypeConstraint) visit(ctx.l);
        var right = (TypeConstraint) visit(ctx.r);
        return new BinaryTypeConstraint(posOf(ctx), op, left, right);
    }

    @Override
    public Entity visitTypeParameter(FengParser.TypeParameterContext ctx) {
        var name = identifier(ctx.name);
        findGeneric(name).use(tp -> {
            semantic("duplicate type parameter '%s'%s, before at %s",
                    name, name.pos(), tp.pos());
        });
        var constraint = this.<TypeConstraint>visitOptional(ctx.typeConstraint());
        return new TypeParameter(posOf(ctx), name, constraint);
    }

    private TypeParameters typeParameters(FengParser.TypeParametersContext ctx) {
        if (ctx == null) return TypeParameters.empty();
        var pList = this.<TypeParameter>visitList(ctx.typeParameter());
        var params = new IdentifierMap<TypeParameter>();
        for (var p : pList) params.add(p.name(), p);
        return new TypeParameters(posOf(ctx), params);
    }


    //
    // generic: end
    //

    //
    // attribute: start
    //

    @Override
    public Entity visitAttributeDefinition(FengParser.AttributeDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var name = defineSymbol(ctx.name);
        var fields = this.parseUniques(new IdentifierMap<>(),
                ctx.attributeMember(), AttributeField::name);
        return new AttributeDefinition(posOf(ctx), modifier, name, fields);
    }

    @Override
    public Entity visitAttributeMemberField(FengParser.AttributeMemberFieldContext ctx) {
        var name = identifier(ctx.name);
        var type = identifier(ctx.type);
        var ft = AttributeField.TYPE_MAP.get(type.value());
        if (ft == null) return semantic("invalid type '%s': %s", type, type.pos());
        var value = this.<Expression>visitOptional(ctx.value);
        return new AttributeField(posOf(ctx), name, ft, false, value);
    }

    @Override
    public Entity visitAttributeMemberArray(FengParser.AttributeMemberArrayContext ctx) {
        var name = identifier(ctx.name);
        var type = identifier(ctx.type);
        var ft = AttributeField.TYPE_MAP.get(type.value());
        if (ft == null) return semantic("invalid type '%s': %s", type, type.pos());
        var value = this.<Expression>visitOptional(ctx.values);
        return new AttributeField(posOf(ctx), name, ft, true, value);
    }

    @Override
    public Entity visitAttribute(FengParser.AttributeContext ctx) {
        var symbol = parseSymbol(ctx.symbol());
        var init = this.<ObjectExpression>visitOptional(ctx.init);
        return new Attribute(posOf(ctx), symbol, init);
    }

    private SymbolMap<Attribute> parseAttributes(FengParser.AttributesContext ctx) {
        var list = this.<Attribute>visitList(ctx.attribute());
        var table = new SymbolMap<Attribute>();
        for (var attr : list) table.add(attr.type(), attr);
        return table;
    }

    private Modifier parseModifier(FengParser.ModifierContext ctx) {
        var export = isExport(ctx.exportable());
        var attributes = parseAttributes(ctx.attributes());
        return new Modifier(posOf(ctx), export, attributes);
    }

    private Symbol parseSymbol(FengParser.SymbolContext ctx) {
        var mod = identifierOptional(ctx.mod)
                .map(importMap::get);
        var name = identifier(ctx.name);
        return new Symbol(posOf(ctx), mod, name);
    }

    //
    // attribute: end
    //


    //
    // structure: start
    //

    @Override
    public Entity visitStructureDefinition(FengParser.StructureDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var domain = parseDomain(ctx.domain);
        var symbol = defineSymbol(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        genericStack.push(generic);
        var fields = parseStructureMembers(ctx.structureFieldsDef());
        var def = new StructureDefinition(posOf(ctx), modifier, symbol,
                generic, domain, fields);
        for (var f : fields) f.master().set(def);
        genericStack.pop();
        return def;
    }

    @Override
    public Entity visitUnnamedStructureDefinition(
            FengParser.UnnamedStructureDefinitionContext ctx) {
        var pos = posOf(ctx);
        var domain = parseDomain(ctx.domain);
        var fields = parseStructureMembers(ctx.structureFieldsDef());
        var symbol = defineSymbol(CommonUtil.rand(domain.name));
        var def = new StructureDefinition(pos, Modifier.empty(),
                symbol, TypeParameters.empty(), domain, fields);
        table.types.add(symbol.name(), def);
        for (var f : fields) f.master().set(def);
        return def;
    }

    private TypeDomain parseDomain(Token domain) {
        return switch (domain.getType()) {
            case FengParser.STRUCT -> TypeDomain.STRUCT;
            case FengParser.UNION -> TypeDomain.UNION;
            default -> unreachable();
        };
    }

    private IdentifierMap<StructureField> parseStructureMembers(
            List<FengParser.StructureFieldsDefContext> membersCtx) {
        var fields = new IdentifierMap<StructureField>();
        for (var ctx : membersCtx) {
            var type = (TypeDeclarer) visit(ctx.type);
            for (var fc : ctx.fields.structureField()) {
                var field = new StructureField(posOf(fc),
                        identifier(fc.Identifier()),
                        visitOptional(fc.expression()),
                        type);
                fields.add(field.name(), field);
            }
        }
        return fields;
    }


    @Override
    public Entity visitDefinedStructureFieldType(
            FengParser.DefinedStructureFieldTypeContext ctx) {
        var type = (DefinedType) visit(ctx.definedType());
        if (type instanceof PrimitiveType pt)
            return pt.primitive().declarer(posOf(ctx));
        if (type instanceof GenericType gt)
            return new GenericTypeDeclarer(posOf(ctx), gt);
        return new DerivedTypeDeclarer(posOf(ctx), (DerivedType) type,
                Optional.empty());
    }

    @Override
    public Entity visitArrayStructureFieldType(
            FengParser.ArrayStructureFieldTypeContext ctx) {
        var et = (TypeDeclarer) visit(ctx.element);
        var len = this.<Expression>visitOptional(ctx.len);
        return new ArrayTypeDeclarer(posOf(ctx), et, len, Optional.empty());
    }

    @Override
    public Entity visitUnnamedStructureFieldType(
            FengParser.UnnamedStructureFieldTypeContext ctx) {
        var def = (StructureDefinition) visit(ctx.unnamedStructureDefinition());
        var dt = new DerivedType(def.pos(), def.symbol(), TypeArguments.EMPTY);
        return new DerivedTypeDeclarer(posOf(ctx), dt, Optional.empty());
    }


    //
    // structure: end
    //


    //
    // enumeration: start
    //

    @Override
    public Entity visitEnumDefinition(FengParser.EnumDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var symbol = defineSymbol(ctx.name);
        var values = new IdentifierMap<EnumDefinition.Value>();
        var id = 0;
        for (var vc : ctx.enumValue()) {
            var name = identifier(vc.name);
            var sl = new StringLiteral(name.pos(), charset,
                    name.value().getBytes(charset));
            sl = table.stringCache.dedup(sl);
            var value = this.<Expression>visitOptional(vc.value);
            var ev = new EnumDefinition.Value(posOf(vc), id++,
                    name, value, sl);
            values.add(ev.name(), ev);
        }
        return new EnumDefinition(posOf(ctx), modifier, symbol, values);
    }

    //
    // enumeration: end
    //

    //
    // interface: start
    //

    @Override
    public Entity visitInterfaceDefinition(
            FengParser.InterfaceDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var symbol = defineSymbol(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        genericStack.push(generic);
        var methods = new IdentifierMap<InterfaceMethod>();
        var parts = new SymbolMap<DerivedType>();
        var macros = new MacroTable();
        for (var imc : ctx.interfaceMember()) {
            if (imc.part != null) {
                var part = (DefinedType) visit(imc.part.definedType());
                if (!(part instanceof DerivedType dt))
                    return semantic("require derived type: %s", part.pos());

                parts.add(dt.symbol(), dt);
            } else if (imc.method != null) {
                var method = (InterfaceMethod) visit(imc.method);
                methods.add(method.name(), method);
            } else {
                var macro = (Macro) visit(imc.macro());
                macros.add(macro);
            }
        }
        var def = new InterfaceDefinition(posOf(ctx),
                modifier, symbol, generic, methods, parts, macros);
        for (var m : methods) m.master.set(def);
        genericStack.pop();
        return def;
    }

    @Override
    public Entity visitInterfaceMemberMethod(
            FengParser.InterfaceMemberMethodContext ctx) {
        var pos = posOf(ctx);
        var modifier = parseModifier(ctx.modifier());
        var name = identifier(ctx.name);
        enterMethodName = name;
        methodReturnThis = false;
        var generic = typeParameters(ctx.typeParameters());
        genericStack.push(generic);
        var prototype = (Prototype) visit(ctx.prototype());
        var method = new InterfaceMethod(pos, modifier, name, generic,
                prototype, methodReturnThis);
        genericStack.pop();
        methodReturnThis = false;
        enterMethodName = null;
        return method;
    }


    //
    // interface: end
    //

    //
    // class: start
    //


    private SymbolMap<DerivedType> parseClassImpl(
            FengParser.ClassImplContext ctx) {
        var tab = new SymbolMap<DerivedType>();
        if (ctx == null) return tab;
        var dts = this.<DefinedType>visitList(ctx.definedType());
        for (var dt : dts) {
            if (dt instanceof DerivedType der) {
                tab.add(der.symbol(), der);
                continue;
            }
            return semantic("require derived type: %s", dt.pos());
        }
        return tab;
    }

    private Optional<DerivedType> parseClassInherit(
            FengParser.ClassInheritContext ctx) {
        if (ctx == null) return Optional.empty();

        var dt = (DefinedType) visit(ctx);
        if (dt instanceof DerivedType der)
            return Optional.of(der);

        return semantic("require derived type: %s",
                dt.pos());
    }

    private volatile Symbol enterClassSymbol;
    private volatile Identifier enterMethodName;
    private volatile boolean methodReturnThis;

    private void mustInMethod(Position pos) {
        if (enterMethodName == null)
            syntax("must use in method: %s", pos);
    }

    @Override
    public Entity visitClassDefinition(FengParser.ClassDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var symbol = defineSymbol(ctx.name);
        if (enterClassSymbol != null) syntax("nested define class: %s", symbol);
        enterClassSymbol = symbol;
        var generic = typeParameters(ctx.typeParameters());
        genericStack.push(generic);
        var ext = ctx.classExtension();
        var isFinal = ext.FINAL() != null;
        var inherit = parseClassInherit(ext.classInherit());
        if (!isFinal && inherit.none())
            inherit = Optional.of(ClassDefinition.ObjectType);
        var impl = parseClassImpl(ext.classImpl());

        var methods = new IdentifierMap<ClassMethod>();
        var fields = new IdentifierMap<ClassField>();
        var macros = new MacroTable();
        for (var cmc : ctx.classMember()) {
            var mModifier = parseModifier(cmc.modifier());
            var mi = cmc.classMemberImpl();

            if (mi.method != null) {
                var def = mi.method.def;
                var mName = identifier(def.name);
                enterMethodName = mName;
                methodReturnThis = false;
                var mGeneric = typeParameters(def.typeParameters());
                genericStack.push(mGeneric);
                var proc = this.<Procedure>visitOptional(def.procedure());
                var pt = this.<Prototype>visitOptional(def.prototype());
                methodReturnThis = false;
                genericStack.pop();
                enterMethodName = null;
                ClassMethod method;
                if (proc.has()) {
                    if (metadata) {
                        return semantic("method implementation can't be in metadata: %s",
                                posOf(def));
                    }
                    method = new ClassMethod(posOf(mi), mModifier, mName,
                            mGeneric, proc.get(), methodReturnThis);
                } else {
                    if (!metadata) {
                        return semantic("method declaration can't be in source: %s",
                                posOf(def));
                    }
                    method = new ClassMethod(posOf(mi), mModifier, mName,
                            mGeneric, pt.must(), methodReturnThis);
                }
                methods.add(mName, method);
            } else if (mi.fields != null) {
                var dcl = parseDeclare(mi.fields.declare);
                var td = (TypeDeclarer) visit(mi.fields.typeDeclarer());
                var fNames = identifiers(mi.fields.identifierList());
                for (var fName : fNames) {
                    var field = new ClassField(fName.pos(), mModifier,
                            dcl, fName, td);
                    fields.add(fName, field);
                }
            } else {
                var macro = (Macro) visit(mi.macro());
                macros.add(macro);
            }
        }

        var def = new ClassDefinition(posOf(ctx), modifier, symbol,
                generic, isFinal, inherit, impl, fields, methods, macros);
        for (var f : fields) f.master().set(def);
        for (var m : methods) m.master(def);

        genericStack.pop();
        enterClassSymbol = null;
        return def;
    }


    //
    // class: end
    //


    /* ************* */
    /* procedure code  */
    /* ************* */

    //
    // expression: start
    //

    private List<Expression> parseExpressions(FengParser.ExpressionListContext ctx) {
        if (ctx == null) return List.of();
        return visitList(ctx.expression());
    }

    @Override
    public Entity visitLiteralExpression(FengParser.LiteralExpressionContext ctx) {
        var literal = (Literal) visit(ctx.literal());
        return new LiteralExpression(posOf(ctx), literal);
    }

    @Override
    public Entity visitNewType(FengParser.NewTypeContext ctx) {
        var pos = posOf(ctx);
        var defTp = ctx.definedType();

        if (defTp != null) {
            var t = (DefinedType) visit(defTp);
            return new NewDefinedType(pos, t);
        }

        var at = ctx.newArrayType();
        var element = (TypeDeclarer) visit(at.typeDeclarer());
        var length = (Expression) visit(at.expression());
        return new NewArrayType(pos, element, length);
    }

    @Override
    public Entity visitNewExpression(FengParser.NewExpressionContext ctx) {
        var new_ = ctx.new_();
        var type = (NewType) visit(new_.newType());
        var arg = this.<Expression>visitOptional(new_.expression());
        return new NewExpression(posOf(ctx), type, arg);
    }

    @Override
    public Entity visitAssertExpression(FengParser.AssertExpressionContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var type = (TypeDeclarer) visit(ctx.assert_().typeDeclarer());
        if (type instanceof DerivedTypeDeclarer ptd)
            return new AssertExpression(posOf(ctx), subject, ptd);
        return semantic("assert require a derived type, not %s: %s",
                type, type.pos());
    }

    @Override
    public Entity visitSizeofExpression(FengParser.SizeofExpressionContext ctx) {
        var td = (TypeDeclarer) visit(ctx.sizeof().typeDeclarer());
        return new SizeofExpression(posOf(ctx), td);
    }

    @Override
    public Entity visitConditionalExpression(
            FengParser.ConditionalExpressionContext ctx) {
        var c = (Expression) visit(ctx.c);
        var y = (Expression) visit(ctx.y);
        var n = (Expression) visit(ctx.n);
        return new ConditionalExpression(posOf(ctx), c, y, n);
    }

    @Override
    public Entity visitBlockExpr(FengParser.BlockExprContext ctx) {
        var list = parseStatements(ctx.statementList());
        var result = (Expression) visit(ctx.expression());
        return new BlockExpression(posOf(ctx), list, result);
    }

    @Override
    public Entity visitLambdaExpression(FengParser.LambdaExpressionContext ctx) {
        unsupported("lambda");
        var procedure = (Procedure) visit(ctx.procedure());
        return new LambdaExpression(posOf(ctx), procedure);
    }

    @Override
    public Entity visitObjectExpr(FengParser.ObjectExprContext ctx) {
        var o = this.<DefinedType>visitOptional(ctx.definedType());
        var type = o.map(dt -> {
            if (dt instanceof DerivedType t)
                return new DerivedTypeDeclarer(dt.pos(), t, Optional.empty());
            return semantic("can't init %s with object-init: %s",
                    dt, dt.pos());
        });

        var entries = new IdentifierMap<Expression>();
        for (var oec : ctx.objectEntry()) {
            var key = identifier(oec.name);
            var value = (Expression) visit(oec.value);
            entries.add(key, value);
        }
        return new ObjectExpression(posOf(ctx), entries, type);
    }

    @Override
    public Entity visitArrayExpr(FengParser.ArrayExprContext ctx) {
        var pos = posOf(ctx);
        var elements = parseExpressions(ctx.elements);
        var otd = Optional.<ArrayTypeDeclarer>empty();
        if (ctx.et != null) {
            var et = (TypeDeclarer) visit(ctx.et);
            var len = this.<Expression>visitOptional(ctx.len);
            if (len.none()) {
                len = Optional.of(new IntegerLiteral(pos, elements.size()).expr());
            }
            var td = new ArrayTypeDeclarer(pos, et, len, Optional.empty(), true);
            if (len.none()) td.len(elements.size());
            otd = Optional.of(td);
        }
        return new ArrayExpression(pos, elements, otd);
    }

    @Override
    public Entity visitSymbolExpression(
            FengParser.SymbolExpressionContext ctx) {
        var pos = posOf(ctx);
        var symbol = parseSymbol(ctx.symbol());
        var generic = typeArguments(ctx.typeArguments());
        return new SymbolExpression(pos, symbol, generic);
    }

    @Override
    public Entity visitCurrentExpression(
            FengParser.CurrentExpressionContext ctx) {
        var pos = posOf(ctx.current);
        var cn = enterClassSymbol;
        var mn = enterMethodName;
        mustInMethod(pos);
        var type = ctx.current.getType();
        var isSelf = type == FengParser.THIS;
        return new CurrentExpression(pos, cn, mn, isSelf);
    }

    @Override
    public Entity visitMemberOfExpression(FengParser.MemberOfExpressionContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var member = identifier(ctx.memberOf().member);
        var generic = typeArguments(ctx.typeArguments());
        return new MemberOfExpression(posOf(ctx), subject, member, generic);
    }

    @Override
    public Entity visitIndexOfExpression(FengParser.IndexOfExpressionContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var index = (Expression) visit(ctx.indexOf().expression());
        return new IndexOfExpression(posOf(ctx), subject, index);
    }


    @Override
    public Entity visitCallExpression(FengParser.CallExpressionContext ctx) {
        var callee = (PrimaryExpression) visit(ctx.primaryExpr());
        var args = parseExpressions(ctx.argumentSet().args);
        if (callee instanceof SymbolExpression re) {
            if (re.generic().isEmpty() && re.symbol().module().none()) {
                var op = Primitive.ofCode(re.symbol().name());
                if (op.has()) {
                    if (args.size() > 1) {
                        return semantic("can't convert multi values: %s",
                                args.get(1).pos());
                    }
                    return new ConvertExpression(posOf(ctx), op.get(), args.getFirst());
                }
            }
        }
        return new CallExpression(posOf(ctx), callee, args);
    }

    @Override
    public Entity visitUnaryExpression(FengParser.UnaryExpressionContext ctx) {
        var tt = ctx.op.getType();
        var rhs = (Expression) visit(ctx.rightAssocExpr());
        if (tt == FengParser.MUL) {
            if (rhs instanceof PrimaryExpression pe)
                return new DereferExpression(posOf(ctx), pe);
            return semantic("derefer must use for reference: %s", rhs.pos());
        }
        UnaryOperator op = switch (tt) {
            case FengParser.ADD -> UnaryOperator.POSITIVE;
            case FengParser.SUB -> UnaryOperator.NEGATIVE;
            case FengParser.NOT -> UnaryOperator.INVERT;
            default -> unreachable();
        };
        return new UnaryExpression(posOf(ctx), op, rhs);
    }

    private BinaryOperator parseBinaryOperator(Token op) {
        return switch (op.getType()) {
            case FengParser.ADD -> BinaryOperator.ADD;
            case FengParser.SUB -> BinaryOperator.SUB;
            case FengParser.MUL -> BinaryOperator.MUL;
            case FengParser.DIV -> BinaryOperator.DIV;
            case FengParser.MOD -> BinaryOperator.MOD;
            case FengParser.POW -> BinaryOperator.POW;
            case FengParser.BITAND -> BinaryOperator.BITAND;
            case FengParser.BITOR -> BinaryOperator.BITOR;
            case FengParser.BITXOR -> BinaryOperator.BITXOR;
            case FengParser.LSHIFT -> BinaryOperator.LSHIFT;
            case FengParser.RSHIFT -> BinaryOperator.RSHIFT;
            case FengParser.AND -> BinaryOperator.AND;
            case FengParser.OR -> BinaryOperator.OR;
            case FengParser.GT -> BinaryOperator.GT;
            case FengParser.LT -> BinaryOperator.LT;
            case FengParser.EQ -> BinaryOperator.EQ;
            case FengParser.NE -> BinaryOperator.NE;
            case FengParser.LE -> BinaryOperator.LE;
            case FengParser.GE -> BinaryOperator.GE;
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
    }

    @Override
    public Entity visitPowerExpression(FengParser.PowerExpressionContext ctx) {
        var bin = parseBinaryOperator(ctx.op);
        var lhs = (Expression) visit(ctx.dereferExpr());
        var rhs = (Expression) visit(ctx.rightAssocExpr());
        return new BinaryExpression(posOf(ctx.op), bin, lhs, rhs);
    }

    @Override
    public Entity visitBinaryExpression(FengParser.BinaryExpressionContext ctx) {
        var bin = parseBinaryOperator(ctx.op);
        var lhs = (Expression) visit(ctx.lhs);
        var rhs = (Expression) visit(ctx.rhs);
        return new BinaryExpression(posOf(ctx.op), bin, lhs, rhs);
    }

    @Override
    public Entity visitDereferExpression(FengParser.DereferExpressionContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        return new DereferExpression(posOf(ctx), subject);
    }

    @Override
    public Entity visitParenExpression(FengParser.ParenExpressionContext ctx) {
        return new ParenExpression(posOf(ctx),
                (Expression) visit(ctx.expression()));
    }

    @Override
    public Entity visitPairsExpr(FengParser.PairsExprContext ctx) {
        var e = new PairsExpression(posOf(ctx), new ArrayList<>());
        for (var pc : ctx.pair()) {
            var k = (Expression) visit(pc.key);
            var v = (Expression) visit(pc.value);
            e.pairs().add(new PairsExpression.Pair(k, v));
        }
        return e;
    }

    //
    // expression: end
    //

    //
    // statement: start
    //


    private List<Statement> parseStatements(FengParser.StatementListContext ctx) {
        if (ctx == null) return List.of();
        return visitList(ctx.statement());
    }

    // statement: declaration

    private Declare parseDeclare(Token declare) {
        return switch (declare.getType()) {
            case FengParser.VAR -> Declare.VAR;
            case FengParser.CONST -> Declare.CONST;
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
    }

    private List<Variable> parseVariables(
            FengParser.DeclaredNamesContext dnCtx,
            Optional<TypeDeclarer> type) {
        var attrs = parseAttributes(dnCtx.attributes());
        var modifier = new Modifier(posOf(dnCtx), false, attrs);
        var dcl = parseDeclare(dnCtx.declare);
        type.use(td -> {
            var r = td.maybeRefer();
            if (r.none() || r.get().kind() != PHANTOM) return;
            if (dcl == Declare.CONST) return;
            semantic("phantom refer must declare const: %s", td.pos());
        });
        var names = identifiers(dnCtx.identifierList());
        var vars = new ArrayList<Variable>(names.size());
        var unique = new OrderlyMap<Identifier, Identifier>(names.size());
        for (var name : names) {
            unique.add(name, name);
            vars.add(new Variable(name.pos(), modifier, dcl, name,
                    Lazy.of(type), Lazy.nil()));
        }
        return vars;
    }

    @Override
    public Entity visitOnlyDeclaration(FengParser.OnlyDeclarationContext ctx) {
        var typeDcl = (TypeDeclarer) visit(ctx.typeDeclarer());
        var variables = parseVariables(ctx.declaredNames(), Optional.of(typeDcl));
        return new DeclarationStatement(posOf(ctx), variables);
    }

    @Override
    public Entity visitAssignedDeclaration(
            FengParser.AssignedDeclarationContext ctx) {
        var typeDcl = this.<TypeDeclarer>visitOptional(ctx.typeDeclarer());
        var variables = parseVariables(ctx.declaredNames(), typeDcl);
        var values = parseExpressions(ctx.values);
        if (variables.size() != values.size()) {
            return semantic("number of var and value not match: %s", posOf(ctx));
        }
        int i = 0;
        for (var v : variables) {
            v.value().set(values.get(i++));
        }
        return new DeclarationStatement(posOf(ctx), variables);
    }

    @Override
    public Entity visitDeclarationStatement(
            FengParser.DeclarationStatementContext ctx) {
        return visit(ctx.declaration());
    }

    // statement: assignment

    @Override
    public Entity visitVariableOperand(
            FengParser.VariableOperandContext ctx) {
        var name = parseSymbol(ctx.symbol());
        return new VariableOperand(posOf(ctx), name);
    }

    @Override
    public Entity visitIndexOperand(
            FengParser.IndexOperandContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var index = (Expression) visit(ctx.indexOf().expression());
        return new IndexOperand(posOf(ctx), subject, index);
    }

    @Override
    public Entity visitFieldOperand(
            FengParser.FieldOperandContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var field = identifier(ctx.memberOf().member);
        return new FieldOperand(posOf(ctx), subject, field);
    }

    @Override
    public Entity visitDereferOperand(
            FengParser.DereferOperandContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        return new DereferOperand(posOf(ctx), subject);
    }

    @Override
    public Entity visitAssignments(FengParser.AssignmentsContext ctx) {
        var pos = posOf(ctx);
        var operands = this.<Operand>visitList(ctx.operands().operand());
        var values = parseExpressions(ctx.values);
        if (operands.size() != values.size()) {
            return semantic("operands and values not aligned: %s", pos);
        }

        var list = new ArrayList<Assignment>(operands.size());
        for (int i = 0; i < operands.size(); i++) {
            var o = operands.get(i);
            list.add(new Assignment(o.pos(), o, values.get(i)));
        }
        return new AssignmentsStatement(pos, list);
    }

    @Override
    public Entity visitAssignmentsStatement(
            FengParser.AssignmentsStatementContext ctx) {
        return visit(ctx.assignments());
    }

    @Override
    public Entity visitAssignmentOperation(
            FengParser.AssignmentOperationContext ctx) {
        var opCtx = ctx.assignmentOperator().op;
        BinaryOperator binOp = switch (opCtx.getType()) {
            case FengParser.ASSIGN_ADD -> BinaryOperator.ADD;
            case FengParser.ASSIGN_SUB -> BinaryOperator.SUB;
            case FengParser.ASSIGN_MUL -> BinaryOperator.MUL;
            case FengParser.ASSIGN_DIV -> BinaryOperator.DIV;
            case FengParser.ASSIGN_MOD -> BinaryOperator.MOD;
            case FengParser.ASSIGN_BITAND -> BinaryOperator.BITAND;
            case FengParser.ASSIGN_BITOR -> BinaryOperator.BITOR;
            case FengParser.ASSIGN_BITXOR -> BinaryOperator.BITXOR;
            case FengParser.ASSIGN_LSHIFT -> BinaryOperator.LSHIFT;
            case FengParser.ASSIGN_RSHIFT -> BinaryOperator.RSHIFT;
            case FengParser.ASSIGN_AND -> BinaryOperator.AND;
            case FengParser.ASSIGN_OR -> BinaryOperator.OR;
            default -> unreachable();
        };
        var operand = (Operand) visit(ctx.operand());
        var value = (Expression) visit(ctx.expression());
        var rhs = new BinaryExpression(posOf(opCtx), binOp, operand.rhs(), value);
        var assign = new Assignment(operand.pos(), operand, rhs);
        return new AssignmentsStatement(posOf(ctx), List.of(assign));
    }

    @Override
    public Entity visitAssignmentOperateStatement(
            FengParser.AssignmentOperateStatementContext ctx) {
        return visit(ctx.assignmentOperation());
    }

    // statement: commons

    private BlockStatement noScope(BlockStatement s) {
        return new BlockStatement(s.pos(), s.list(), false);
    }

    @Override
    public Entity visitBlockStatement(FengParser.BlockStatementContext ctx) {
        var list = parseStatements(ctx.statementList());
        return new BlockStatement(posOf(ctx), list);
    }

    @Override
    public Entity visitCallStatement(FengParser.CallStatementContext ctx) {
        var callee = (PrimaryExpression) visit(ctx.primaryExpr());
        var argSet = parseExpressions(ctx.argumentSet().args);
        var call = new CallExpression(posOf(ctx), callee, argSet);
        return new CallStatement(call.pos(), call);
    }

    private BlockStatement wrapInScope(Statement yes) {
        if (yes instanceof BlockStatement bs)
            return bs;
        return new BlockStatement(yes.pos(), List.of(yes));
    }

    @Override
    public Entity visitIfStatement(FengParser.IfStatementContext ctx) {
        var init = this.<Statement>visitOptional(ctx.init);
        var condition = (Expression) visit(ctx.expression());
        var yes = wrapInScope((Statement) visit(ctx.yes));
        var not = this.<Statement>visitOptional(ctx.not);
        not = not.map(s -> {
            if (s instanceof BlockStatement || s instanceof IfStatement)
                return s;
            return new BlockStatement(s.pos(), List.of(s));
        });
        return new IfStatement(posOf(ctx), init, condition, yes, not);
    }

    // for statement

    @Override
    public Entity visitUnaryForStatement(
            FengParser.UnaryForStatementContext ctx) {
        var condition = (Expression) visit(ctx.expression());
        var body = (Statement) visit(ctx.statement());
        return new ConditionalForStatement(posOf(ctx), wrapInScope(body),
                Optional.empty(), condition, Optional.empty());
    }

    @Override
    public Entity visitTernaryForStatement(
            FengParser.TernaryForStatementContext ctx) {
        var clause = ctx.forClause();
        var initializer = (Statement) visit(clause.init);
        var condition = (Expression) visit(clause.expression());
        var updater = (Statement) visit(clause.next);
        if (updater instanceof DeclarationStatement) {
            return syntax("can't declare variable here: %s", updater.pos());
        }
        var body = (Statement) visit(ctx.statement());
        return new ConditionalForStatement(posOf(ctx), wrapInScope(body),
                Optional.of(initializer), condition,
                Optional.of(updater));
    }

    @Override
    public Entity visitIterableForStatement(
            FengParser.IterableForStatementContext ctx) {
        var iterator = ctx.forIterator();
        var arguments = identifiers(iterator.identifierList());
        var source = (Expression) visit(iterator.expression());
        if (!(source instanceof PrimaryExpression)) {
            return syntax("%s is not iterable: %s", source, source.pos());
        }
        var body = (Statement) visit(ctx.statement());
        return new IterableForStatement(posOf(ctx), wrapInScope(body),
                arguments, (PrimaryExpression) source);
    }

    @Override
    public Entity visitSwitchBranch(FengParser.SwitchBranchContext ctx) {
        var body = (BlockStatement) visit(ctx.body);
        var expressions = parseExpressions(ctx.expressionList());
        return new SwitchBranch(posOf(ctx), expressions, body);
    }

    @Override
    public Entity visitSwitchStatement(FengParser.SwitchStatementContext ctx) {
        var assign = this.<Statement>visitOptional(ctx.init);
        var value = (Expression) visit(ctx.expression());
        var branches = this.<SwitchBranch>visitList(ctx.switchBranch());
        var defBr = Optional.<Branch>empty();
        if (ctx.def != null) {
            var list = (BlockStatement) visit(ctx.def.body);
            defBr = Optional.of(new Branch(posOf(ctx.def), list));
        }
        return new SwitchStatement(posOf(ctx), assign, value, branches, defBr);
    }

    @Override
    public Entity visitThrowStatement(FengParser.ThrowStatementContext ctx) {
        return new ThrowStatement(posOf(ctx), (Expression) visit(ctx.expression()));
    }

    @Override
    public Entity visitCatchClause(FengParser.CatchClauseContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var name = identifier(ctx.name);
        var variable = new Variable(name.pos(), modifier,
                Declare.CONST, name, Lazy.nil(), Lazy.nil());
        var typeSet = this.<TypeDeclarer>visitList(ctx.catchTypeSet().typeDeclarer());
        var body = (BlockStatement) visit(ctx.blockStatement());
        return new CatchClause(posOf(ctx), variable, typeSet, body);
    }

    @Override
    public Entity visitTryWithCatchStatement(
            FengParser.TryWithCatchStatementContext ctx) {
        var preCtx = ctx.tryPrefix();
        var tryBody = (BlockStatement) visit(preCtx.blockStatement());
        var catchClause = this.<CatchClause>visitList(preCtx.catchClause());
        catchClause.add((CatchClause) visit(ctx.catchClause()));
        return new TryStatement(posOf(ctx), tryBody,
                catchClause, Optional.empty());
    }

    @Override
    public Entity visitTryWithFinalStatement(
            FengParser.TryWithFinalStatementContext ctx) {
        var preCtx = ctx.tryPrefix();
        var tryBody = (BlockStatement) visit(preCtx.blockStatement());
        var catchClause = this.<CatchClause>visitList(preCtx.catchClause());
        var finalBody = this.<BlockStatement>visitOptional(ctx.finalClause().blockStatement());
        return new TryStatement(posOf(ctx), tryBody, catchClause, finalBody);
    }

    @Override
    public Entity visitReturnStatement(FengParser.ReturnStatementContext ctx) {
        var result = this.<Expression>visitOptional(ctx.result);
        return new ReturnStatement(posOf(ctx), result);
    }

    @Override
    public Entity visitContinueStatement(FengParser.ContinueStatementContext ctx) {
        var label = identifierOptional(ctx.label);
        return new ContinueStatement(posOf(ctx), label);
    }

    @Override
    public Entity visitBreakStatement(FengParser.BreakStatementContext ctx) {
        var label = identifierOptional(ctx.label);
        return new BreakStatement(posOf(ctx), label);
    }

    @Override
    public Entity visitGotoStatement(FengParser.GotoStatementContext ctx) {
        return new GotoStatement(posOf(ctx), identifier(ctx.label));
    }

    @Override
    public Entity visitLabeledStatement(FengParser.LabeledStatementContext ctx) {
        var label = new Label(identifier(ctx.label));

        var stmt = (Statement) visit(ctx.statement());
        if (stmt instanceof LabeledStatement)
            return semantic("can't use multi label: %s", label.pos());
        if (stmt instanceof DeclarationStatement)
            return semantic("can't use for declaration: %s", label.pos());
        var ls = new LabeledStatement(posOf(ctx), label, stmt);
        var ol = labels.put(label.name(), ls);
        if (ol != null)
            return semantic("label %s duplicate: %s <==> %s",
                    label, label.pos(), ol.pos());
        return ls;
    }


    //
    // statement: end
    //

    //
    // procedure: start
    //

    private ParameterSet parseParameters(FengParser.ParametersSetContext ctx) {
        var params = new IdentifierMap<Variable>();

        if (ctx == null) return new ParameterSet(params);

        var ps = ctx.parameters();
        if (ps == null) {
            var types = parseTypeDeclarerList(ctx.typeDeclarerList());
            for (int i = 0, typesSize = types.size(); i < typesSize; i++) {
                var td = types.get(i);
                var name = new Identifier(td.pos(), "feng$arg" + i, true);
                var v = new Variable(td.pos(), Modifier.empty(),
                        Declare.CONST, name, Lazy.of(td), Lazy.nil());
                params.add(name, v);
            }
            return new ParameterSet(params);
        }

        for (var pc : ps.parameter()) {
            var modifier = parseModifier(pc.modifier());
            var type = (TypeDeclarer) visit(pc.typeDeclarer());
            var names = identifiers(pc.identifierList());
            for (var name : names) {
                var v = new Variable(name.pos(), modifier,
                        Declare.CONST, name, Lazy.of(type), Lazy.nil());
                params.add(name, v);
            }
        }
        return new ParameterSet(params);
    }

    private Optional<TypeDeclarer> parseReturnSet(FengParser.ReturnSetContext ctx) {
        if (ctx == null) return Optional.empty();
        if (ctx.current != null) {
            mustInMethod(posOf(ctx.current));
            methodReturnThis = true;
            return Optional.empty();
        }

        var type = (TypeDeclarer) visit(ctx.typeDeclarer());
        return Optional.of(type);
    }

    @Override
    public Entity visitPrototype(FengParser.PrototypeContext ctx) {
        var parameters = parseParameters(ctx.parametersSet());
        var returnSet = parseReturnSet(ctx.returnSet());
        return new Prototype(posOf(ctx), parameters, returnSet);
    }

    private volatile Map<Identifier, LabeledStatement> labels;

    @Override
    public Entity visitProcedure(FengParser.ProcedureContext ctx) {
        var prototype = (Prototype) visit(ctx.prototype());
        assert labels == null;
        labels = new HashMap<>();
        var body = (BlockStatement) visit(ctx.blockStatement());
        var l = Map.copyOf(labels);
        labels = null;
        return new Procedure(posOf(ctx), prototype,
                noScope(body), l);
    }


    //
    // procedure: end
    //

    //
    // macro: start
    //

    @Override
    public MacroClass visitMacroClass(FengParser.MacroClassContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var type = identifier(ctx.type);
        var typeCtx = ctx.macroType();
        var name = identifier(typeCtx.name);
        var fields = parseMacroVariables(typeCtx.fields);

        var mList = this.<MacroProcedure>visitList(typeCtx.macroProcedure());
        var methods = new IdentifierMap<MacroProcedure>();
        for (var mp : mList) methods.add(mp.name(), mp);

        return new MacroClass(posOf(ctx), modifier, type, name, fields, methods);
    }

    @Override
    public Entity visitMacroProcedure(FengParser.MacroProcedureContext ctx) {
        var name = identifier(ctx.name);
        var params = parseMacroVariables(ctx.params);
        var body = parseStatements(ctx.statementList());
        var result = this.<Expression>visitOptional(ctx.expression());
        return new MacroProcedure(posOf(ctx), name, params, body, result);
    }

    @Override
    public Entity visitMacroFunc(FengParser.MacroFuncContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var type = identifier(ctx.type);
        var proc = (MacroProcedure) visit(ctx.macroProcedure());
        return new MacroFunc(posOf(ctx), modifier, type, proc);
    }

    private IdentifierMap<MacroVariable> parseMacroVariables(
            FengParser.MacroVariablesContext ctx) {
        var vars = new IdentifierMap<MacroVariable>();
        if (ctx == null) return vars;
        var li = this.<MacroVariable>visitList(ctx.macroVariable());
        for (var v : li) vars.add(v.name(), v);
        return vars;
    }

    @Override
    public Entity visitMacroVariable(FengParser.MacroVariableContext ctx) {
        var name = identifier(ctx.name);
        var type = this.<TypeDeclarer>visitOptional(ctx.type);
        return new MacroVariable(posOf(ctx), name, Lazy.of(type));
    }


    //
    // macro: end
    //

    //
    // function: start
    //

    @Override
    public Entity visitPrototypeDefinition(
            FengParser.PrototypeDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var symbol = defineSymbol(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        genericStack.push(generic);
        var prototype = (Prototype) visit(ctx.prototype());
        var def = new PrototypeDefinition(posOf(ctx), modifier,
                symbol, generic, prototype);
        genericStack.pop();
        return def;
    }

    @Override
    public Entity visitFunctionDefinition(
            FengParser.FunctionDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var name = defineSymbol(ctx.def.name);
        var generic = typeParameters(ctx.def.typeParameters());
        genericStack.push(generic);
        var proc = this.<Procedure>visitOptional(ctx.def.procedure());
        var pt = this.<Prototype>visitOptional(ctx.def.prototype());
        genericStack.pop();

        if (proc.has()) {
            if (metadata) return semantic(
                    "function implementation can't be in metadata: %s",
                    posOf(ctx));

            return new FunctionDefinition(posOf(ctx), modifier,
                    name, generic, proc.get());
        }

        if (!metadata) {
            return semantic("function declaration can't be in source: %s",
                    posOf(ctx));
        }
        return new FunctionDefinition(posOf(ctx), modifier, name, generic, pt.must());
    }

    //
    // function: end
    //

}
