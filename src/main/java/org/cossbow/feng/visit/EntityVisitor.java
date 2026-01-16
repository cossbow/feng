package org.cossbow.feng.visit;

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
import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.mod.GlobalDeclaration;
import org.cossbow.feng.ast.mod.GlobalDefinition;
import org.cossbow.feng.ast.mod.Import;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.AssignableOperand;
import org.cossbow.feng.ast.var.FieldAssignableOperand;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.cossbow.feng.util.Optional;

import java.util.List;

import static org.cossbow.feng.util.ErrorUtil.unreachable;

public interface EntityVisitor<R> {

    default <T extends Entity> Optional<R> visit(Optional<T> e) {
        if (e.none()) return Optional.empty();
        return Optional.of(visit(e.get()));
    }

	default <T extends Entity> List<R> visit(List<T> e) {
		return e.stream().map(this::visit).toList();
	}

	default R visit(Entity e) {
		return switch (e) {
			case Definition ee -> visit(ee);
			case Identifier ee -> visit(ee);
			case Source ee -> visit(ee);
			case Symbol ee -> visit(ee);
			case Attribute ee -> visit(ee);
			case AttributeField ee -> visit(ee);
			case Modifier ee -> visit(ee);
			case Declaration ee -> visit(ee);
			case NewType ee -> visit(ee);
			case Refer ee -> visit(ee);
			case TypeDeclarer ee -> visit(ee);
			case Variable ee -> visit(ee);
			case Expression ee -> visit(ee);
			case DefinedType ee -> visit(ee);
			case TypeArguments ee -> visit(ee);
			case TypeConstraint ee -> visit(ee);
			case TypeParameter ee -> visit(ee);
			case TypeParameters ee -> visit(ee);
			case Literal ee -> visit(ee);
			case Macro ee -> visit(ee);
			case MacroProcedure ee -> visit(ee);
			case MacroVariable ee -> visit(ee);
			case Global ee -> visit(ee);
			case Import ee -> visit(ee);
			case ClassField ee -> visit(ee);
			case Procedure ee -> visit(ee);
			case Prototype ee -> visit(ee);
			case CatchClause ee -> visit(ee);
			case Statement ee -> visit(ee);
			case SwitchBranch ee -> visit(ee);
			case Tuple ee -> visit(ee);
			case StructureField ee -> visit(ee);
			case AssignableOperand ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(Definition e) {
		return switch (e) {
			case TypeDefinition ee -> visit(ee);
			case FunctionDefinition ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(TypeDefinition e) {
		return switch (e) {
			case AttributeDefinition ee -> visit(ee);
			case ClassDefinition ee -> visit(ee);
			case EnumDefinition ee -> visit(ee);
			case InterfaceDefinition ee -> visit(ee);
			case PrototypeDefinition ee -> visit(ee);
			case StructureDefinition ee -> visit(ee);
			case PrimitiveDefinition ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(AttributeDefinition e) { return unreachable(); }

	default R visit(ClassDefinition e) { return unreachable(); }

	default R visit(EnumDefinition e) { return unreachable(); }

	default R visit(InterfaceDefinition e) { return unreachable(); }

	default R visit(PrototypeDefinition e) { return unreachable(); }

	default R visit(InterfaceMethod e) { return unreachable(); }

	default R visit(StructureDefinition e) { return unreachable(); }

	default R visit(PrimitiveDefinition e) { return unreachable(); }

	default R visit(FunctionDefinition e) { return unreachable(); }

	default R visit(ClassMethod e) { return unreachable(); }

	default R visit(Identifier e) { return unreachable(); }

	default R visit(Source e) { return unreachable(); }

	default R visit(Symbol e) { return unreachable(); }

	default R visit(Attribute e) { return unreachable(); }

	default R visit(AttributeField e) { return unreachable(); }

	default R visit(Modifier e) { return unreachable(); }

	default R visit(Declaration e) { return unreachable(); }

	default R visit(NewType e) {
		return switch (e) {
			case NewArrayType ee -> visit(ee);
			case NewDefinedType ee -> visit(ee);
			case NewMemType ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(NewArrayType e) { return unreachable(); }

	default R visit(NewDefinedType e) { return unreachable(); }

	default R visit(NewMemType e) { return unreachable(); }

	default R visit(Refer e) { return unreachable(); }

	default R visit(TypeDeclarer e) {
		return switch (e) {
			case ArrayTypeDeclarer ee -> visit(ee);
			case DefinedTypeDeclarer ee -> visit(ee);
			case FuncTypeDeclarer ee -> visit(ee);
			case MemTypeDeclarer ee -> visit(ee);
			case PrimitiveTypeDeclarer ee -> visit(ee);
			case LiteralTypeDeclarer ee -> visit(ee);
			case ThisTypeDeclarer ee -> visit(ee);
			case TupleTypeDeclarer ee -> visit(ee);
			case VoidTypeDeclarer ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(ArrayTypeDeclarer e) { return unreachable(); }

	default R visit(DefinedTypeDeclarer e) { return unreachable(); }

	default R visit(FuncTypeDeclarer e) { return unreachable(); }

	default R visit(MemTypeDeclarer e) { return unreachable(); }

	default R visit(PrimitiveTypeDeclarer e) { return unreachable(); }

	default R visit(LiteralTypeDeclarer e) { return unreachable(); }

	default R visit(ThisTypeDeclarer e) { return unreachable(); }

	default R visit(TupleTypeDeclarer e) { return unreachable(); }

	default R visit(VoidTypeDeclarer e) { return unreachable(); }

	default R visit(GlobalVariable e) { return unreachable(); }

	default R visit(Variable e) { return unreachable(); }

	default R visit(Expression e) {
		return switch (e) {
			case BinaryExpression ee -> visit(ee);
			case PrimaryExpression ee -> visit(ee);
			case UnaryExpression ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(BinaryExpression e) { return unreachable(); }

	default R visit(PrimaryExpression e) {
		return switch (e) {
			case ArrayExpression ee -> visit(ee);
			case AssertExpression ee -> visit(ee);
			case CallExpression ee -> visit(ee);
			case CurrentExpression ee -> visit(ee);
			case IndexOfExpression ee -> visit(ee);
			case LambdaExpression ee -> visit(ee);
			case LiteralExpression ee -> visit(ee);
			case MemberOfExpression ee -> visit(ee);
			case NewExpression ee -> visit(ee);
			case ObjectExpression ee -> visit(ee);
			case PairsExpression ee -> visit(ee);
			case ParenExpression ee -> visit(ee);
			case ReferExpression ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(ArrayExpression e) { return unreachable(); }

	default R visit(AssertExpression e) { return unreachable(); }

	default R visit(CallExpression e) { return unreachable(); }

	default R visit(CurrentExpression e) { return unreachable(); }

	default R visit(IndexOfExpression e) { return unreachable(); }

	default R visit(LambdaExpression e) { return unreachable(); }

	default R visit(LiteralExpression e) { return unreachable(); }

	default R visit(MemberOfExpression e) { return unreachable(); }

	default R visit(NewExpression e) { return unreachable(); }

	default R visit(ObjectExpression e) { return unreachable(); }

	default R visit(PairsExpression e) { return unreachable(); }

	default R visit(ParenExpression e) { return unreachable(); }

	default R visit(ReferExpression e) { return unreachable(); }

	default R visit(UnaryExpression e) { return unreachable(); }

	default R visit(DefinedType e) { return unreachable(); }

	default R visit(TypeArguments e) { return unreachable(); }

	default R visit(TypeConstraint e) {
		return switch (e) {
			case BinaryTypeConstraint ee -> visit(ee);
			case DefinedTypeConstraint ee -> visit(ee);
			case DomainTypeConstraint ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(BinaryTypeConstraint e) { return unreachable(); }

	default R visit(DefinedTypeConstraint e) { return unreachable(); }

	default R visit(DomainTypeConstraint e) { return unreachable(); }

	default R visit(TypeParameter e) { return unreachable(); }

	default R visit(TypeParameters e) { return unreachable(); }

	default R visit(Literal e) {
		return switch (e) {
			case BoolLiteral ee -> visit(ee);
			case FloatLiteral ee -> visit(ee);
			case IntegerLiteral ee -> visit(ee);
			case NilLiteral ee -> visit(ee);
			case StringLiteral ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(BoolLiteral e) { return unreachable(); }

	default R visit(FloatLiteral e) { return unreachable(); }

	default R visit(IntegerLiteral e) { return unreachable(); }

	default R visit(NilLiteral e) { return unreachable(); }

	default R visit(StringLiteral e) { return unreachable(); }

	default R visit(Macro e) {
		return switch (e) {
			case MacroClass ee -> visit(ee);
			case MacroFunc ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(MacroClass e) { return unreachable(); }

	default R visit(MacroFunc e) { return unreachable(); }

	default R visit(MacroProcedure e) { return unreachable(); }

	default R visit(MacroVariable e) { return unreachable(); }

	default R visit(Global e) {
		return switch (e) {
			case GlobalDeclaration ee -> visit(ee);
			case GlobalDefinition ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(GlobalDeclaration e) { return unreachable(); }

	default R visit(GlobalDefinition e) { return unreachable(); }

	default R visit(Import e) { return unreachable(); }

	default R visit(ClassField e) { return unreachable(); }

	default R visit(Procedure e) { return unreachable(); }

	default R visit(Prototype e) { return unreachable(); }

	default R visit(CatchClause e) { return unreachable(); }

	default R visit(Statement e) {
		return switch (e) {
			case AssignmentsStatement ee -> visit(ee);
			case BlockStatement ee -> visit(ee);
			case BreakStatement ee -> visit(ee);
			case CallStatement ee -> visit(ee);
			case ContinueStatement ee -> visit(ee);
			case DeclarationStatement ee -> visit(ee);
			case ForStatement ee -> visit(ee);
			case GotoStatement ee -> visit(ee);
			case IfStatement ee -> visit(ee);
			case LabeledStatement ee -> visit(ee);
			case ReturnStatement ee -> visit(ee);
			case SwitchStatement ee -> visit(ee);
			case ThrowStatement ee -> visit(ee);
			case TryStatement ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(AssignmentsStatement e) { return unreachable(); }

	default R visit(BlockStatement e) { return unreachable(); }

	default R visit(BreakStatement e) { return unreachable(); }

	default R visit(CallStatement e) { return unreachable(); }

	default R visit(ContinueStatement e) { return unreachable(); }

	default R visit(DeclarationStatement e) { return unreachable(); }

	default R visit(ForStatement e) {
		return switch (e) {
			case ConditionalForStatement ee -> visit(ee);
			case IterableForStatement ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(ConditionalForStatement e) { return unreachable(); }

	default R visit(IterableForStatement e) { return unreachable(); }

	default R visit(GotoStatement e) { return unreachable(); }

	default R visit(IfStatement e) { return unreachable(); }

	default R visit(LabeledStatement e) { return unreachable(); }

	default R visit(ReturnStatement e) { return unreachable(); }

	default R visit(SwitchStatement e) { return unreachable(); }

	default R visit(ThrowStatement e) { return unreachable(); }

	default R visit(TryStatement e) { return unreachable(); }

	default R visit(SwitchBranch e) { return unreachable(); }

	default R visit(Tuple e) {
		return switch (e) {
			case ArrayTuple ee -> visit(ee);
			case IfTuple ee -> visit(ee);
			case ReturnTuple ee -> visit(ee);
			case SwitchTuple ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(ArrayTuple e) { return unreachable(); }

	default R visit(IfTuple e) { return unreachable(); }

	default R visit(ReturnTuple e) { return unreachable(); }

	default R visit(SwitchTuple e) { return unreachable(); }

	default R visit(StructureField e) { return unreachable(); }

	default R visit(AssignableOperand e) {
		return switch (e) {
			case IndexAssignableOperand ee -> visit(ee);
			case FieldAssignableOperand ee -> visit(ee);
			case VariableAssignableOperand ee -> visit(ee);
			case null, default -> unreachable();
		};
	}

	default R visit(IndexAssignableOperand e) { return unreachable(); }

	default R visit(FieldAssignableOperand e) { return unreachable(); }

	default R visit(VariableAssignableOperand e) { return unreachable(); }

}

