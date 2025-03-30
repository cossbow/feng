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
import org.cossbow.feng.ast.mod.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.*;
import org.cossbow.feng.ast.var.AssignableOperand;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.MemberAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class SourceParseVisitor
        extends FengBaseVisitor<Entity>
        implements FengVisitor<Entity> {


    /* ************* */
    /*  declaration  */
    /* ************* */


    //
    // symbols table
    //

    private final UniqueTable<TypeDefinition> namedTypes = new UniqueTable<>();
    private final List<TypeDefinition> unnamedTypes = new ArrayList<>();
    private final UniqueTable<FunctionDefinition> namedFunctions = new UniqueTable<>();
    private final List<Procedure> lambdas = new ArrayList<>();

    public SourceParseVisitor() {
    }

    public UniqueTable<TypeDefinition> namedTypes() {
        return namedTypes;
    }

    public List<TypeDefinition> unnamedTypes() {
        return unnamedTypes;
    }

    public UniqueTable<FunctionDefinition> namedFunctions() {
        return namedFunctions;
    }

    public List<Procedure> lambdas() {
        return lambdas;
    }

    //
    // utils
    //

    @SuppressWarnings("unchecked")
    private <N extends Entity> Optional<N> visitOptional(ParseTree tree) {
        if (tree == null) return Optional.empty();
        return Optional.ofNullable((N) visit(tree));
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

    @SuppressWarnings("unchecked")
    private <E extends Entity> UniqueTable<E> parseUniques(
            List<? extends ParseTree> trees, Function<E, Identifier> identify) {
        if (trees == null) return new UniqueTable<>();
        var result = new UniqueTable<E>(trees.size());
        for (ParseTree tree : trees) {
            var n = (E) visit(tree);
            result.add(identify.apply(n), n);
        }
        return result;
    }

    Position posOf(Token t) {
        return new Position(t, null);
    }

    Position posOf(TerminalNode tn) {
        return new Position(tn.getSymbol(), null);
    }

    Position posOf(ParserRuleContext ctx) {
        return new Position(ctx.getStart(), ctx.getStop());
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

    //
    // module: start
    //

    @Override
    public Entity visitSource(FengParser.SourceContext ctx) {
        var imports = this.<Import>visitList(ctx.import_());
        var globals = this.<Global>visitList(ctx.global());
        return new Source(posOf(ctx), imports, globals);
    }

    @Override
    public Entity visitImport_(FengParser.Import_Context ctx) {
        var pkg = identifiers(ctx.module().Identifier());
        var setCtx = ctx.importSymbolSet();

        if (setCtx.all != null) {
            return new Import(posOf(ctx), pkg, List.of());
        }
        var list = this.<ImportSymbol>visitList(setCtx.importSymbol());
        return new Import(posOf(ctx), pkg, list);
    }

    @Override
    public Entity visitImportSymbol(FengParser.ImportSymbolContext ctx) {
        var name = identifier(ctx.name);
        var alias = ctx.alias != null ? identifier(ctx.alias) : null;
        return new ImportSymbol(posOf(ctx.name), name, Optional.ofNullable(alias));
    }

    @Override
    public Entity visitGlobalTypeDefinition(FengParser.GlobalTypeDefinitionContext ctx) {
        var export = isExport(ctx.exportable());
        var definition = (Definition) visit(ctx.def);
        return new GlobalDefinition(posOf(ctx), export, definition);
    }

    @Override
    public Entity visitGlobalFunctionDefinition(FengParser.GlobalFunctionDefinitionContext ctx) {
        var export = isExport(ctx.exportable());
        var definition = (Definition) visit(ctx.def);
        return new GlobalDefinition(posOf(ctx), export, definition);
    }

    @Override
    public Entity visitGlobalDeclaration(FengParser.GlobalDeclarationContext ctx) {
        var export = isExport(ctx.exportable());
        var stmt = (DeclarationStatement) visit(ctx.declaration());
        return new GlobalDeclaration(posOf(ctx), export, stmt);
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
        return new StringLiteral(posOf(tn), text.substring(1, text.length() - 1)); // 去掉引号
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

    private Optional<Expression> parseArraySizeExpr(FengParser.ArrayDeclarerContext ctx) {
        return visitOptional(ctx.expression());
    }

    @Override
    public Entity visitArrayTypeDeclarer(FengParser.ArrayTypeDeclarerContext ctx) {
        var size = parseArraySizeExpr(ctx.arrayDeclarer());
        var typeDcl = (TypeDeclarer) visit(ctx.typeDeclarer());
        return new ArrayTypeDeclarer(posOf(ctx), typeDcl, size);
    }

    @Override
    public Entity visitDefinedTypeDeclarer(FengParser.DefinedTypeDeclarerContext ctx) {
        var ptr = ctx.pointerType();
        var isPointer = ptr != null;
        var isPhantom = false;
        var isOptional = false;
        if (isPointer) {
            isPhantom = ptr.kind.getType() == FengParser.BITAND;
            isOptional = ptr.optional != null;
        }
        var dt = (DefinedType) visit(ctx.definedType());
        return new DefinedTypeDeclarer(posOf(ctx), dt, isPointer, isPhantom, isOptional);
    }

    @Override
    public Entity visitFuncTypeDeclarer(FengParser.FuncTypeDeclarerContext ctx) {
        var prototype = (Prototype) visit(ctx.prototype());
        return new FuncTypeDeclarer(posOf(ctx), prototype);
    }

    public List<TypeDeclarer> parseTypeDeclarerList(
            FengParser.TypeDeclarerListContext ctx) {
        return visitList(ctx.typeDeclarer());
    }

    @Override
    public Entity visitNewDefinedType(FengParser.NewDefinedTypeContext ctx) {
        var dt = (DefinedType) visit(ctx.definedType());
        return new NewDefinedType(posOf(ctx), dt);
    }

    @Override
    public Entity visitNewArrayType(FengParser.NewArrayTypeContext ctx) {
        var element = (TypeDeclarer) visit(ctx.typeDeclarer());
        var length = (Expression) visit(ctx.expression());
        return new NewArrayType(posOf(ctx), element, length);
    }

    //
    // declare: end
    //

    //
    // generic: start
    //


    private TypeArguments typeArguments(FengParser.TypeArgumentsContext ctx) {
        if (ctx == null) return TypeArguments.EMPTY;
        var arguments = parseTypeDeclarerList(ctx.typeDeclarerList());
        return new TypeArguments(posOf(ctx), arguments);
    }

    @Override
    public Entity visitDefinedType(FengParser.DefinedTypeContext ctx) {
        var name = identifier(ctx.name);
        var args = typeArguments(ctx.typeArguments());
        return new DefinedType(posOf(ctx), name, args);
    }


    @Override
    public Entity visitPrimaryTypeExpression(FengParser.PrimaryTypeExpressionContext ctx) {
        var definedType = (DefinedType) visit(ctx.definedType());
        return new PrimaryTypeExpression(posOf(ctx), definedType);
    }

    @Override
    public Entity visitBinaryTypeExpression(FengParser.BinaryTypeExpressionContext ctx) {
        var op = switch (ctx.op.getType()) {
            case FengParser.BITAND -> TypeOperator.AND;
            case FengParser.BITOR -> TypeOperator.OR;
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
        var left = (TypeExpression) visit(ctx.l);
        var right = (TypeExpression) visit(ctx.r);
        return new BinaryTypeExpression(posOf(ctx), op, left, right);
    }

    @Override
    public Entity visitTypeParameter(FengParser.TypeParameterContext ctx) {
        var name = identifier(ctx.name);
        var constraint = this.<TypeExpression>visitOptional(ctx.typeExpression());
        return new TypeParameter(posOf(ctx), name, constraint);
    }

    private TypeParameters typeParameters(FengParser.TypeParametersContext ctx) {
        if (ctx == null) return TypeParameters.empty();
        var pList = this.<TypeParameter>visitList(ctx.typeParameter());
        var params = new UniqueTable<TypeParameter>();
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
        var name = identifier(ctx.name);
        var fields = this.parseUniques(ctx.attributeMember(), AttributeField::name);
        var def = new AttributeDefinition(posOf(ctx), modifier, name, fields);
        namedTypes.add(name, def);
        return def;
    }

    @Override
    public Entity visitAttributeMemberField(FengParser.AttributeMemberFieldContext ctx) {
        var name = identifier(ctx.name);
        var type = identifier(ctx.type);
        var value = this.<Expression>visitOptional(ctx.value);
        return new AttributeField(posOf(ctx), name, type, false, value);
    }

    @Override
    public Entity visitAttributeMemberArray(FengParser.AttributeMemberArrayContext ctx) {
        var name = identifier(ctx.name);
        var type = identifier(ctx.type);
        var value = this.<Expression>visitOptional(ctx.values);
        return new AttributeField(posOf(ctx), name, type, true, value);
    }

    @Override
    public Entity visitAttribute(FengParser.AttributeContext ctx) {
        var type = identifier(ctx.type);
        var init = this.<Expression>visitOptional(ctx.init);
        return new Attribute(posOf(ctx), type, init);
    }

    private UniqueTable<Attribute> parseAttributes(FengParser.AttributesContext ctx) {
        var list = this.<Attribute>visitList(ctx.attribute());
        var table = new UniqueTable<Attribute>();
        for (var attr : list) table.add(attr.type(), attr);
        return table;
    }

    private Modifier parseModifier(FengParser.ModifierContext ctx) {
        var attributes = parseAttributes(ctx.attributes());
        return new Modifier(posOf(ctx), attributes);
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
        var union = isUnion(ctx.domain);
        var name = identifier(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        var fields = parseStructureMembers(ctx.structureFieldsDef());
        var def = new StructureDefinition(posOf(ctx), modifier,
                Optional.of(name), generic, union, fields);
        namedTypes.add(name, def);
        return def;
    }

    @Override
    public Entity visitUnnamedStructureDefinition(
            FengParser.UnnamedStructureDefinitionContext ctx) {
        var pos = posOf(ctx);
        var union = isUnion(ctx.domain);
        var fields = parseStructureMembers(ctx.structureFieldsDef());
        var def = new StructureDefinition(pos, Modifier.empty(), Optional.empty(),
                TypeParameters.empty(), union, fields);
        unnamedTypes.add(def);
        return def;
    }

    private boolean isUnion(Token domain) {
        return domain.getType() == FengParser.UNION;
    }

    private UniqueTable<StructureField> parseStructureMembers(
            List<FengParser.StructureFieldsDefContext> membersCtx) {
        var fields = new UniqueTable<StructureField>();
        for (var ctx : membersCtx) {
            var type = (StructureType) visit(ctx.type);
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
        return new DefinedStructureType(posOf(ctx), type);
    }

    @Override
    public Entity visitArrayStructureFieldType(
            FengParser.ArrayStructureFieldTypeContext ctx) {
        var elementType = (StructureType) visit(ctx.elementType);
        var length = parseArraySizeExpr(ctx.arrayDeclarer());
        return new ArrayStructureType(posOf(ctx), elementType, length);
    }

    @Override
    public Entity visitUnnamedStructureFieldType(
            FengParser.UnnamedStructureFieldTypeContext ctx) {
        var definition = (StructureDefinition) visit(ctx.unnamedStructureDefinition());
        return new UnnamedStructureType(posOf(ctx), definition);
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
        var name = identifier(ctx.name);
        var values = new UniqueTable<EnumDefinition.Value>();
        for (var vc : ctx.enumValue()) {
            var v = new EnumDefinition.Value(
                    identifier(vc.name), visitOptional(vc.value));
            values.add(v.name(), v);
        }
        var def = new EnumDefinition(posOf(ctx), modifier, name, values);
        namedTypes.add(name, def);
        return def;
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
        var name = identifier(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        var methods = new UniqueTable<InterfaceMethod>();
        var parts = new UniqueTable<DefinedType>();
        var macros = new MultiTable<Macro>();
        for (var imc : ctx.interfaceMember()) {
            if (imc.part != null) {
                var part = (DefinedType) visit(imc.part.definedType());
                parts.add(part.name(), part);
            } else if (imc.method != null) {
                var method = (InterfaceMethod) visit(imc.method);
                methods.add(method.name(), method);
            } else {
                var macro = (Macro) visit(imc.macro());
                macros.add(macro.type(), macro.name(), macro);
            }
        }
        var def = new InterfaceDefinition(posOf(ctx),
                modifier, name, generic, methods, parts, macros);
        namedTypes.add(name, def);
        return def;
    }

    @Override
    public Entity visitInterfaceMemberMethod(
            FengParser.InterfaceMemberMethodContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var name = identifier(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        var prototype = (Prototype) visit(ctx.prototype());
        return new InterfaceMethod(posOf(ctx), modifier, name, generic, prototype);
    }


    //
    // interface: end
    //

    //
    // class: start
    //


    private UniqueTable<DefinedType> parseClassImpl(FengParser.ClassImplContext ctx) {
        var tab = new UniqueTable<DefinedType>();
        if (ctx == null) return tab;
        var list = this.<DefinedType>visitList(ctx.definedType());
        for (var t : list) tab.add(t.name(), t);
        return tab;
    }

    @Override
    public Entity visitClassDefinition(FengParser.ClassDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var name = identifier(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        var parent = this.<DefinedType>visitOptional(ctx.classInherit());
        var impl = parseClassImpl(ctx.classImpl());

        var methods = new UniqueTable<ClassMethod>();
        var fields = new UniqueTable<ClassField>();
        var macros = new MultiTable<Macro>();
        for (var cmc : ctx.classMember()) {
            var mModifier = parseModifier(cmc.modifier());
            var mExport = isExport(cmc.exportable());
            var mi = cmc.classMemberImpl();

            if (mi.method != null) {
                var mName = identifier(mi.method.name);
                var mGeneric = typeParameters(mi.method.typeParameters());
                var procedure = (Procedure) visit(mi.method.procedure());
                var method = new ClassMethod(posOf(mi), mModifier, mName,
                        mGeneric, mExport, procedure);
                methods.add(mName, method);
            } else if (mi.fields != null) {
                var dcl = parseDeclare(mi.fields.declare);
                var td = (TypeDeclarer) visit(mi.fields.typeDeclarer());
                var fNames = identifiers(mi.fields.identifierList());
                for (var fn : fNames) {
                    var field = new ClassField(fn.pos(), mModifier,
                            mExport, dcl, fn, td);
                    fields.add(fn, field);
                }
            } else {
                var macro = (Macro) visit(mi.macro());
                macros.add(macro.type(), macro.name(), macro);
            }
        }

        var def = new ClassDefinition(posOf(ctx), modifier,
                Optional.of(name), generic,
                parent, impl, fields, methods, macros);
        namedTypes.add(name, def);
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
    public Entity visitNewExpression(FengParser.NewExpressionContext ctx) {
        var new_ = ctx.new_();
        var type = (NewType) visit(new_.newType());
        var init = this.<Expression>visitOptional(new_.expression());
        return new NewExpression(posOf(ctx), type, init);
    }

    @Override
    public Entity visitAssertExpression(FengParser.AssertExpressionContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var type = (TypeDeclarer) visit(ctx.assert_().typeDeclarer());
        return new AssertExpression(posOf(ctx), subject, type);
    }

    @Override
    public Entity visitLambdaExpression(FengParser.LambdaExpressionContext ctx) {
        var procedure = (Procedure) visit(ctx.procedure());
        lambdas.add(procedure);
        return new LambdaExpression(posOf(ctx), procedure);
    }

    @Override
    public Entity visitObjectExpr(FengParser.ObjectExprContext ctx) {
        var entries = new UniqueTable<Expression>();
        for (var oec : ctx.objectEntry()) {
            var key = identifier(oec.name);
            var value = (Expression) visit(oec.value);
            entries.add(key, value);
        }
        return new ObjectExpression(posOf(ctx), entries);
    }

    @Override
    public Entity visitArrayExpr(FengParser.ArrayExprContext ctx) {
        var elements = parseExpressions(ctx.elements);
        return new ArrayExpression(posOf(ctx), elements);
    }

    @Override
    public Entity visitPairsExpr(FengParser.PairsExprContext ctx) {
        var pairs = new ArrayList<PairsExpression.Pair>();
        for (var pc : ctx.pair()) {
            var key = (Expression) visit(pc.key);
            var value = (Expression) visit(pc.value);
            pairs.add(new PairsExpression.Pair(key, value));
        }
        return new PairsExpression(posOf(ctx), pairs);
    }

    @Override
    public Entity visitReferExpr(FengParser.ReferExprContext ctx) {
        var name = identifier(ctx.name);
        var generic = typeArguments(ctx.typeArguments());
        return new ReferExpression(posOf(ctx), name, generic);
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
        var handler = (PrimaryExpression) visit(ctx.primaryExpr());
        var argSet = parseExpressions(ctx.argumentSet().args);
        return new CallExpression(posOf(ctx), handler, argSet);
    }

    @Override
    public Entity visitUnaryExpression(FengParser.UnaryExpressionContext ctx) {
        var tt = ctx.op.getType();
        var rhs = (Expression) visit(ctx.rightAssocExpr());
        var op = switch (tt) {
            case FengParser.ADD -> UnaryOperator.POSITIVE;
            case FengParser.SUB -> UnaryOperator.NEGATIVE;
            case FengParser.NOT -> UnaryOperator.INVERT;
            default -> throw new UnsupportedOperationException("unreachable branch");
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
        var lhs = (Expression) visit(ctx.primaryExpr());
        var rhs = (Expression) visit(ctx.rightAssocExpr());
        return new BinaryExpression(posOf(ctx), bin, lhs, rhs);
    }

    @Override
    public Entity visitBinaryExpression(FengParser.BinaryExpressionContext ctx) {
        var bin = parseBinaryOperator(ctx.op);
        var lhs = (Expression) visit(ctx.lhs);
        var rhs = (Expression) visit(ctx.rhs);
        return new BinaryExpression(posOf(ctx), bin, lhs, rhs);
    }

    @Override
    public Entity visitParenExpression(FengParser.ParenExpressionContext ctx) {
        return new ParenExpression(posOf(ctx),
                (Expression) visit(ctx.expression()));
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
            case FengParser.LET -> Declare.LET;
            case FengParser.CONST -> Declare.CONST;
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
    }

    private List<Variable> parseVariables(
            FengParser.DeclaredNamesContext dnCtx) {
        var modifier = parseModifier(dnCtx.modifier());
        var dcl = parseDeclare(dnCtx.declare);
        var names = identifiers(dnCtx.identifierList());
        var vars = new ArrayList<Variable>(names.size());
        var unique = new UniqueTable<Identifier>(names.size());
        for (var name : names) {
            unique.add(name, name);
            vars.add(new Variable(name.pos(), modifier, dcl, name));
        }
        return vars;
    }

    @Override
    public Entity visitOnlyDeclaration(FengParser.OnlyDeclarationContext ctx) {
        var variables = parseVariables(ctx.declaredNames());
        var typeDcl = (TypeDeclarer) visit(ctx.typeDeclarer());
        for (var v : variables) v.type().set(typeDcl);
        return new DeclarationStatement(posOf(ctx), variables,
                Optional.empty());
    }

    @Override
    public Entity visitAssignedDeclaration(
            FengParser.AssignedDeclarationContext ctx) {
        var variables = parseVariables(ctx.declaredNames());
        var typeDcl = this.<TypeDeclarer>visitOptional(ctx.typeDeclarer());
        for (var v : variables) v.type().set(typeDcl.orElse(null));
        var init = (Tuple) visit(ctx.tuple());
        return new DeclarationStatement(posOf(ctx), variables, Optional.of(init));
    }

    @Override
    public Entity visitDeclarationStatement(
            FengParser.DeclarationStatementContext ctx) {
        return visit(ctx.declaration());
    }

    // statement: assignment

    @Override
    public Entity visitVariableAssignableOperand(
            FengParser.VariableAssignableOperandContext ctx) {
        var name = identifier(ctx.name);
        return new VariableAssignableOperand(posOf(ctx), name);
    }

    @Override
    public Entity visitIndexAssignableOperand(
            FengParser.IndexAssignableOperandContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var index = (Expression) visit(ctx.indexOf().expression());
        return new IndexAssignableOperand(posOf(ctx), subject, index);
    }

    @Override
    public Entity visitMemberAssignableOperand(
            FengParser.MemberAssignableOperandContext ctx) {
        var subject = (PrimaryExpression) visit(ctx.primaryExpr());
        var member = identifier(ctx.memberOf().member);
        return new MemberAssignableOperand(posOf(ctx), subject, member);
    }

    @Override
    public Entity visitAssignments(FengParser.AssignmentsContext ctx) {
        var operands = this.<AssignableOperand>visitList(ctx.operands.assignableOperand());
        var values = (Tuple) visit(ctx.tuple());
        var copy = ctx.op.getType() == FengParser.COPY;
        return new AssignmentsStatement(posOf(ctx), operands, values, copy);
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
        var binOp = switch (opCtx.getType()) {
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
            default -> throw new UnsupportedOperationException("unreachable branch");
        };
        var operand = (AssignableOperand) visit(ctx.assignableOperand());
        var value = (Expression) visit(ctx.expression());
        return new AssignmentOperateStatement(posOf(ctx), operand, binOp, value);
    }

    @Override
    public Entity visitAssignmentOperateStatement(
            FengParser.AssignmentOperateStatementContext ctx) {
        return visit(ctx.assignmentOperation());
    }

    // statement: tuple

    @Override
    public Entity visitIfTuple(FengParser.IfTupleContext ctx) {
        var condition = (Expression) visit(ctx.condition);
        var yes = (Tuple) visit(ctx.yes);
        var not = (Tuple) visit(ctx.not);
        return new IfTuple(posOf(ctx), condition, yes, not);
    }

    @Override
    public Entity visitSwitchTuple(FengParser.SwitchTupleContext ctx) {
        var value = (Expression) visit(ctx.expression());
        var rules = ctx.switchRule().stream().map(rc -> {
            var constants = parseExpressions(rc.constants);
            var values = (Tuple) visit(rc.values);
            return new SwitchTuple.Rule(constants, values);
        }).toList();
        var defaultRule = (Tuple) visit(ctx.switchRuleDefault().tuple());
        return new SwitchTuple(posOf(ctx), value, rules, defaultRule);
    }

    @Override
    public Entity visitArrayTuple(FengParser.ArrayTupleContext ctx) {
        var values = parseExpressions(ctx.values);
        return new ArrayTuple(posOf(ctx), values);
    }

    // statement: commons

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

    @Override
    public Entity visitIfStatement(FengParser.IfStatementContext ctx) {
        var init = this.<Statement>visitOptional(ctx.init);
        var condition = (Expression) visit(ctx.expression());
        var yes = (Statement) visit(ctx.yes);
        var not = this.<Statement>visitOptional(ctx.not);
        return new IfStatement(posOf(ctx), init, condition, yes, not);
    }

    // for statement

    @Override
    public Entity visitPredicateForStatement(
            FengParser.PredicateForStatementContext ctx) {
        var condition = (Expression) visit(ctx.expression());
        var body = (Statement) visit(ctx.statement());
        return new BaseForStatement(posOf(ctx), body, Optional.empty(), condition, Optional.empty());
    }

    @Override
    public Entity visitIterableForStatement(
            FengParser.IterableForStatementContext ctx) {
        var forIterator = ctx.forIterator();
        var initializer = (Statement) visit(forIterator.init);
        var condition = (Expression) visit(forIterator.expression());
        var updater = (Statement) visit(forIterator.next);
        var body = (Statement) visit(ctx.statement());
        return new BaseForStatement(posOf(ctx), body,
                Optional.of(initializer), condition, Optional.of(updater));
    }

    @Override
    public Entity visitForEachStatement(FengParser.ForEachStatementContext ctx) {
        var forEach = ctx.forEach();
        var arguments = identifiers(forEach.identifierList());
        var source = (Expression) visit(forEach.expression());
        var body = (Statement) visit(ctx.statement());
        return new EachForStatement(posOf(ctx), body, arguments, source);
    }

    @Override
    public Entity visitSwitchBranch(FengParser.SwitchBranchContext ctx) {
        var statements = parseStatements(ctx.statementList());
        var expressions = parseExpressions(ctx.expressionList());
        var fallthrough = ctx.FALLTHROUGH() != null;
        return new SwitchBranch(posOf(ctx), expressions, statements, fallthrough);
    }

    @Override
    public Entity visitSwitchStatement(FengParser.SwitchStatementContext ctx) {
        var assignmentCtx = ctx.embedAssignment();
        var assign = this.<Statement>visitOptional(assignmentCtx);
        var value = (Expression) visit(ctx.expression());
        var branches = this.<SwitchBranch>visitList(ctx.switchBranch());
        var defBr = List.<Statement>of();
        var defCtx = ctx.switchBranchDefault();
        if (defCtx != null) {
            defBr = parseStatements(defCtx.statementList());
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
        var variable = new Variable(name.pos(), modifier, Declare.CONST, name);
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
        return new TryStatement(posOf(ctx), tryBody, catchClause, Optional.empty());
    }

    @Override
    public Entity visitTryWithFinallyStatement(
            FengParser.TryWithFinallyStatementContext ctx) {
        var preCtx = ctx.tryPrefix();
        var tryBody = (BlockStatement) visit(preCtx.blockStatement());
        var catchClause = this.<CatchClause>visitList(preCtx.catchClause());
        var finalBody = this.<BlockStatement>visitOptional(ctx.finallyClause().blockStatement());
        return new TryStatement(posOf(ctx), tryBody, catchClause, finalBody);
    }

    @Override
    public Entity visitLocalDefineStatement(
            FengParser.LocalDefineStatementContext ctx) {
        var definition = (Definition) visit(ctx.localDefinition());
        return new LocalDefineStatement(posOf(ctx), definition);
    }

    @Override
    public Entity visitReturnStatement(FengParser.ReturnStatementContext ctx) {
        var result = this.<Tuple>visitOptional(ctx.result);
        return new ReturnStatement(posOf(ctx), result);
    }

    @Override
    public Entity visitContinueStatement(FengParser.ContinueStatementContext ctx) {
        return new ContinueStatement(posOf(ctx), identifierOptional(ctx.label));
    }

    @Override
    public Entity visitBreakStatement(FengParser.BreakStatementContext ctx) {
        return new BreakStatement(posOf(ctx), identifierOptional(ctx.label));
    }

    @Override
    public Entity visitGotoStatement(FengParser.GotoStatementContext ctx) {
        return new GotoStatement(posOf(ctx), identifier(ctx.label));
    }

    @Override
    public Entity visitLabeledStatement(FengParser.LabeledStatementContext ctx) {
        var label = identifier(ctx.label);
        var stmt = (Statement) visit(ctx.statement());
        return new LabeledStatement(posOf(ctx), label, stmt);
    }


    //
    // statement: end
    //

    //
    // procedure: start
    //

    private ParameterSet parseParameters(FengParser.ParametersSetContext ctx) {
        if (ctx == null) return new ParameterSet();

        var ps = ctx.parameters();
        if (ps == null) {
            var types = parseTypeDeclarerList(ctx.typeDeclarerList());
            return new UnnamedParameterSet(types);
        }

        var params = new UniqueTable<Variable>();
        for (var pc : ps.parameter()) {
            var modifier = parseModifier(pc.modifier());
            var type = (TypeDeclarer) visit(pc.typeDeclarer());
            var names = identifiers(pc.identifierList());
            for (var name : names) {
                var v = new Variable(name.pos(), modifier, Declare.CONST, name, Lazy.of(type));
                params.add(name, v);
            }
        }
        return new VariableParameterSet(params);
    }

    private List<TypeDeclarer> parseReturnSet(FengParser.ReturnSetContext ctx) {
        if (ctx == null) return List.of();
        var tdCtx = ctx.typeDeclarer();
        if (tdCtx != null) {
            var type = (TypeDeclarer) visit(tdCtx);
            return List.of(type);
        }
        return parseTypeDeclarerList(ctx.typeDeclarerList());
    }

    @Override
    public Entity visitPrototype(FengParser.PrototypeContext ctx) {
        var parameters = parseParameters(ctx.parametersSet());
        var returnSet = parseReturnSet(ctx.returnSet());
        return new Prototype(posOf(ctx), parameters, returnSet);
    }

    @Override
    public Entity visitProcedure(FengParser.ProcedureContext ctx) {
        var prototype = (Prototype) visit(ctx.prototype());
        var body = (BlockStatement) visit(ctx.blockStatement());
        return new Procedure(posOf(ctx), prototype, body);
    }


    //
    // procedure: end
    //

    //
    // macro: start
    //

    @Override
    public MacroClass visitMacroClass(FengParser.MacroClassContext ctx) {
        var type = identifier(ctx.type);
        var typeCtx = ctx.macroType();
        var name = identifier(typeCtx.name);
        var fields = parseMacroVariables(typeCtx.fields);

        var mList = this.<MacroProcedure>visitList(typeCtx.macroProcedure());
        var methods = new UniqueTable<MacroProcedure>();
        for (var mp : mList) methods.add(mp.name(), mp);

        return new MacroClass(posOf(ctx), type, name, fields, methods);
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
        var type = identifier(ctx.type);
        var proc = (MacroProcedure) visit(ctx.macroProcedure());
        return new MacroFunc(posOf(ctx), type, proc);
    }

    private UniqueTable<MacroVariable> parseMacroVariables(
            FengParser.MacroVariablesContext ctx) {
        var vars = new UniqueTable<MacroVariable>();
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
        var name = identifier(ctx.name);
        var prototype = (Prototype) visit(ctx.prototype());
        var generic = typeParameters(ctx.typeParameters());
        var def = new PrototypeDefinition(posOf(ctx), modifier,
                Optional.of(name), generic, prototype);
        namedTypes.add(name, def);
        return def;
    }

    @Override
    public Entity visitFunctionDefinition(
            FengParser.FunctionDefinitionContext ctx) {
        var modifier = parseModifier(ctx.modifier());
        var name = identifier(ctx.name);
        var generic = typeParameters(ctx.typeParameters());
        var procedure = (Procedure) visit(ctx.procedure());
        var def = new FunctionDefinition(posOf(ctx), modifier,
                Optional.of(name), generic, procedure);
        namedFunctions.add(name, def);
        return def;
    }


    //
    // function: end
    //

    //
    // global: end
    //


    //
    // global: end
    //


}
