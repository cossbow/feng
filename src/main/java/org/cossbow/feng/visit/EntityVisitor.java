package org.cossbow.feng.visit;

import org.cossbow.feng.ast.micro.*;
import org.cossbow.feng.ast.mod.*;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.struct.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.attr.*;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.util.ErrorUtil;

public interface EntityVisitor<R> {

	default R visit(Entity e) {
		return switch (e) {
			case Definition ee -> visit(ee);
			case Identifier ee -> visit(ee);
			case Source ee -> visit(ee);
			case Symbol ee -> visit(ee);
			case Attribute ee -> visit(ee);
			case AttributeField ee -> visit(ee);
			case Modifier ee -> visit(ee);
			case NewType ee -> visit(ee);
			case Reference ee -> visit(ee);
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
			case StructureType ee -> visit(ee);
			case AssignableOperand ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(Definition e) {
		return switch (e) {
			case TypeDefinition ee -> visit(ee);
			case FunctionDefinition ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
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
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(AttributeDefinition e) { return null; }

	default R visit(ClassDefinition e) { return null; }

	default R visit(EnumDefinition e) { return null; }

	default R visit(InterfaceDefinition e) { return null; }

	default R visit(PrototypeDefinition e) { return null; }

	default R visit(InterfaceMethod e) { return null; }

	default R visit(StructureDefinition e) { return null; }

	default R visit(FunctionDefinition e) { return null; }

	default R visit(ClassMethod e) { return null; }

	default R visit(Identifier e) { return null; }

	default R visit(Source e) { return null; }

	default R visit(Symbol e) { return null; }

	default R visit(Attribute e) { return null; }

	default R visit(AttributeField e) { return null; }

	default R visit(Modifier e) { return null; }

	default R visit(NewType e) {
		return switch (e) {
			case NewArrayType ee -> visit(ee);
			case NewDefinedType ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(NewArrayType e) { return null; }

	default R visit(NewDefinedType e) { return null; }

	default R visit(Reference e) { return null; }

	default R visit(TypeDeclarer e) {
		return switch (e) {
			case ArrayTypeDeclarer ee -> visit(ee);
			case DefinedTypeDeclarer ee -> visit(ee);
			case FuncTypeDeclarer ee -> visit(ee);
			case MemTypeDeclarer ee -> visit(ee);
			case PrimitiveTypeDeclarer ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(ArrayTypeDeclarer e) { return null; }

	default R visit(DefinedTypeDeclarer e) { return null; }

	default R visit(FuncTypeDeclarer e) { return null; }

	default R visit(MemTypeDeclarer e) { return null; }

	default R visit(PrimitiveTypeDeclarer e) { return null; }

	default R visit(Variable e) { return null; }

	default R visit(Expression e) {
		return switch (e) {
			case BinaryExpression ee -> visit(ee);
			case PrimaryExpression ee -> visit(ee);
			case UnaryExpression ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(BinaryExpression e) { return null; }

	default R visit(PrimaryExpression e) {
		return switch (e) {
			case ArrayExpression ee -> visit(ee);
			case AssertExpression ee -> visit(ee);
			case CallExpression ee -> visit(ee);
			case IndexOfExpression ee -> visit(ee);
			case LambdaExpression ee -> visit(ee);
			case LiteralExpression ee -> visit(ee);
			case MemberOfExpression ee -> visit(ee);
			case NewExpression ee -> visit(ee);
			case ObjectExpression ee -> visit(ee);
			case PairsExpression ee -> visit(ee);
			case ParenExpression ee -> visit(ee);
			case ReferExpression ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(ArrayExpression e) { return null; }

	default R visit(AssertExpression e) { return null; }

	default R visit(CallExpression e) { return null; }

	default R visit(IndexOfExpression e) { return null; }

	default R visit(LambdaExpression e) { return null; }

	default R visit(LiteralExpression e) { return null; }

	default R visit(MemberOfExpression e) { return null; }

	default R visit(NewExpression e) { return null; }

	default R visit(ObjectExpression e) { return null; }

	default R visit(PairsExpression e) { return null; }

	default R visit(ParenExpression e) { return null; }

	default R visit(ReferExpression e) { return null; }

	default R visit(UnaryExpression e) { return null; }

	default R visit(DefinedType e) { return null; }

	default R visit(TypeArguments e) { return null; }

	default R visit(TypeConstraint e) {
		return switch (e) {
			case BinaryTypeConstraint ee -> visit(ee);
			case DefinedTypeConstraint ee -> visit(ee);
			case DomainTypeConstraint ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(BinaryTypeConstraint e) { return null; }

	default R visit(DefinedTypeConstraint e) { return null; }

	default R visit(DomainTypeConstraint e) { return null; }

	default R visit(TypeParameter e) { return null; }

	default R visit(TypeParameters e) { return null; }

	default R visit(Literal e) {
		return switch (e) {
			case BoolLiteral ee -> visit(ee);
			case FloatLiteral ee -> visit(ee);
			case IntegerLiteral ee -> visit(ee);
			case NilLiteral ee -> visit(ee);
			case StringLiteral ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(BoolLiteral e) { return null; }

	default R visit(FloatLiteral e) { return null; }

	default R visit(IntegerLiteral e) { return null; }

	default R visit(NilLiteral e) { return null; }

	default R visit(StringLiteral e) { return null; }

	default R visit(Macro e) {
		return switch (e) {
			case MacroClass ee -> visit(ee);
			case MacroFunc ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(MacroClass e) { return null; }

	default R visit(MacroFunc e) { return null; }

	default R visit(MacroProcedure e) { return null; }

	default R visit(MacroVariable e) { return null; }

	default R visit(Global e) {
		return switch (e) {
			case GlobalDeclaration ee -> visit(ee);
			case GlobalDefinition ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(GlobalDeclaration e) { return null; }

	default R visit(GlobalDefinition e) { return null; }

	default R visit(Import e) { return null; }

	default R visit(ClassField e) { return null; }

	default R visit(Procedure e) { return null; }

	default R visit(Prototype e) { return null; }

	default R visit(CatchClause e) { return null; }

	default R visit(Statement e) {
		return switch (e) {
			case AssignmentOperateStatement ee -> visit(ee);
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
			case LocalDefineStatement ee -> visit(ee);
			case ReturnStatement ee -> visit(ee);
			case SwitchStatement ee -> visit(ee);
			case ThrowStatement ee -> visit(ee);
			case TryStatement ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(AssignmentOperateStatement e) { return null; }

	default R visit(AssignmentsStatement e) { return null; }

	default R visit(BlockStatement e) { return null; }

	default R visit(BreakStatement e) { return null; }

	default R visit(CallStatement e) { return null; }

	default R visit(ContinueStatement e) { return null; }

	default R visit(DeclarationStatement e) { return null; }

	default R visit(ForStatement e) {
		return switch (e) {
			case ConditionalForStatement ee -> visit(ee);
			case IterableForStatement ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(ConditionalForStatement e) { return null; }

	default R visit(IterableForStatement e) { return null; }

	default R visit(GotoStatement e) { return null; }

	default R visit(IfStatement e) { return null; }

	default R visit(LabeledStatement e) { return null; }

	default R visit(LocalDefineStatement e) { return null; }

	default R visit(ReturnStatement e) { return null; }

	default R visit(SwitchStatement e) { return null; }

	default R visit(ThrowStatement e) { return null; }

	default R visit(TryStatement e) { return null; }

	default R visit(SwitchBranch e) { return null; }

	default R visit(Tuple e) {
		return switch (e) {
			case ArrayTuple ee -> visit(ee);
			case IfTuple ee -> visit(ee);
			case SwitchTuple ee -> visit(ee);
			case ReturnTuple ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(ArrayTuple e) { return null; }

	default R visit(IfTuple e) { return null; }

	default R visit(SwitchTuple e) { return null; }

	default R visit(ReturnTuple e) { return null; }

	default R visit(StructureField e) { return null; }

	default R visit(StructureType e) {
		return switch (e) {
			case ArrayStructureType ee -> visit(ee);
			case DefinedStructureType ee -> visit(ee);
			case UnnamedStructureType ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(ArrayStructureType e) { return null; }

	default R visit(DefinedStructureType e) { return null; }

	default R visit(UnnamedStructureType e) { return null; }

	default R visit(AssignableOperand e) {
		return switch (e) {
			case IndexAssignableOperand ee -> visit(ee);
			case MemberAssignableOperand ee -> visit(ee);
			case VariableAssignableOperand ee -> visit(ee);
			case null, default -> ErrorUtil.unreachable();
		};
	}

	default R visit(IndexAssignableOperand e) { return null; }

	default R visit(MemberAssignableOperand e) { return null; }

	default R visit(VariableAssignableOperand e) { return null; }

}

