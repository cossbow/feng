package org.cossbow.feng.visit;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.AttributeField;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.micro.*;
import org.cossbow.feng.ast.mod.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.*;
import org.cossbow.feng.ast.var.AssignableOperand;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.MemberAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.cossbow.feng.util.ErrorUtil;

public interface EntityVisitor extends ExpressionVisitor {

	default void visit(Entity e) {
		switch (e) {
			case Definition ee:
				visit(ee);
				break;
			case Identifier ee:
				visit(ee);
				break;
			case Source ee:
				visit(ee);
				break;
			case Symbol ee:
				visit(ee);
				break;
			case Attribute ee:
				visit(ee);
				break;
			case AttributeField ee:
				visit(ee);
				break;
			case Modifier ee:
				visit(ee);
				break;
			case NewType ee:
				visit(ee);
				break;
			case Reference ee:
				visit(ee);
				break;
			case TypeDeclarer ee:
				visit(ee);
				break;
			case Variable ee:
				visit(ee);
				break;
			case Expression ee:
				visit(ee);
				break;
			case DefinedType ee:
				visit(ee);
				break;
			case TypeArguments ee:
				visit(ee);
				break;
			case TypeConstraint ee:
				visit(ee);
				break;
			case TypeParameter ee:
				visit(ee);
				break;
			case TypeParameters ee:
				visit(ee);
				break;
			case Literal ee:
				visit(ee);
				break;
			case Macro ee:
				visit(ee);
				break;
			case MacroProcedure ee:
				visit(ee);
				break;
			case MacroVariable ee:
				visit(ee);
				break;
			case Global ee:
				visit(ee);
				break;
			case Import ee:
				visit(ee);
				break;
			case Module_ ee:
				visit(ee);
				break;
			case ClassField ee:
				visit(ee);
				break;
			case Procedure ee:
				visit(ee);
				break;
			case Prototype ee:
				visit(ee);
				break;
			case CatchClause ee:
				visit(ee);
				break;
			case Statement ee:
				visit(ee);
				break;
			case SwitchBranch ee:
				visit(ee);
				break;
			case Tuple ee:
				visit(ee);
				break;
			case StructureField ee:
				visit(ee);
				break;
			case StructureType ee:
				visit(ee);
				break;
			case AssignableOperand ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(Definition e) {
		switch (e) {
			case TypeDefinition ee:
				visit(ee);
				break;
			case FunctionDefinition ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(TypeDefinition e) {
		switch (e) {
			case AttributeDefinition ee:
				visit(ee);
				break;
			case ClassDefinition ee:
				visit(ee);
				break;
			case EnumDefinition ee:
				visit(ee);
				break;
			case InterfaceDefinition ee:
				visit(ee);
				break;
			case PrototypeDefinition ee:
				visit(ee);
				break;
			case StructureDefinition ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(AttributeDefinition e) {}

	default void visit(ClassDefinition e) {}

	default void visit(EnumDefinition e) {}

	default void visit(InterfaceDefinition e) {}

	default void visit(PrototypeDefinition e) {}

	default void visit(InterfaceMethod e) {}

	default void visit(StructureDefinition e) {}

	default void visit(FunctionDefinition e) {}

	default void visit(ClassMethod e) {}

	default void visit(Identifier e) {}

	default void visit(Source e) {}

	default void visit(Symbol e) {}

	default void visit(Attribute e) {}

	default void visit(AttributeField e) {}

	default void visit(Modifier e) {}

	default void visit(NewType e) {
		switch (e) {
			case NewArrayType ee:
				visit(ee);
				break;
			case NewDefinedType ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(NewArrayType e) {}

	default void visit(NewDefinedType e) {}

	default void visit(Reference e) {}

	default void visit(TypeDeclarer e) {
		switch (e) {
			case ArrayTypeDeclarer ee:
				visit(ee);
				break;
			case DefinedTypeDeclarer ee:
				visit(ee);
				break;
			case FuncTypeDeclarer ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(ArrayTypeDeclarer e) {}

	default void visit(DefinedTypeDeclarer e) {}

	default void visit(FuncTypeDeclarer e) {}

	default void visit(Variable e) {}

	default void visit(DefinedType e) {}

	default void visit(TypeArguments e) {}

	default void visit(TypeConstraint e) {
		switch (e) {
			case BinaryTypeConstraint ee:
				visit(ee);
				break;
			case DefinedTypeConstraint ee:
				visit(ee);
				break;
			case DomainTypeConstraint ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(BinaryTypeConstraint e) {}

	default void visit(DefinedTypeConstraint e) {}

	default void visit(DomainTypeConstraint e) {}

	default void visit(TypeParameter e) {}

	default void visit(TypeParameters e) {}

	default void visit(Literal e) {
		switch (e) {
			case BoolLiteral ee:
				visit(ee);
				break;
			case FloatLiteral ee:
				visit(ee);
				break;
			case IntegerLiteral ee:
				visit(ee);
				break;
			case NilLiteral ee:
				visit(ee);
				break;
			case StringLiteral ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(BoolLiteral e) {}

	default void visit(FloatLiteral e) {}

	default void visit(IntegerLiteral e) {}

	default void visit(NilLiteral e) {}

	default void visit(StringLiteral e) {}

	default void visit(Macro e) {
		switch (e) {
			case MacroClass ee:
				visit(ee);
				break;
			case MacroFunc ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(MacroClass e) {}

	default void visit(MacroFunc e) {}

	default void visit(MacroProcedure e) {}

	default void visit(MacroVariable e) {}

	default void visit(Global e) {
		switch (e) {
			case GlobalDeclaration ee:
				visit(ee);
				break;
			case GlobalDefinition ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(GlobalDeclaration e) {}

	default void visit(GlobalDefinition e) {}

	default void visit(Import e) {}

	default void visit(Module_ e) {}

	default void visit(ClassField e) {}

	default void visit(Procedure e) {}

	default void visit(Prototype e) {}

	default void visit(CatchClause e) {}

	default void visit(Statement e) {
		switch (e) {
			case AssignmentOperateStatement ee:
				visit(ee);
				break;
			case AssignmentsStatement ee:
				visit(ee);
				break;
			case BlockStatement ee:
				visit(ee);
				break;
			case BreakStatement ee:
				visit(ee);
				break;
			case CallStatement ee:
				visit(ee);
				break;
			case ContinueStatement ee:
				visit(ee);
				break;
			case DeclarationStatement ee:
				visit(ee);
				break;
			case ForStatement ee:
				visit(ee);
				break;
			case GotoStatement ee:
				visit(ee);
				break;
			case IfStatement ee:
				visit(ee);
				break;
			case LabeledStatement ee:
				visit(ee);
				break;
			case LocalDefineStatement ee:
				visit(ee);
				break;
			case ReturnStatement ee:
				visit(ee);
				break;
			case SwitchStatement ee:
				visit(ee);
				break;
			case ThrowStatement ee:
				visit(ee);
				break;
			case TryStatement ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(AssignmentOperateStatement e) {}

	default void visit(AssignmentsStatement e) {}

	default void visit(BlockStatement e) {}

	default void visit(BreakStatement e) {}

	default void visit(CallStatement e) {}

	default void visit(ContinueStatement e) {}

	default void visit(DeclarationStatement e) {}

	default void visit(ForStatement e) {
		switch (e) {
			case ConditionalForStatement ee:
				visit(ee);
				break;
			case IterableForStatement ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(ConditionalForStatement e) {}

	default void visit(IterableForStatement e) {}

	default void visit(GotoStatement e) {}

	default void visit(IfStatement e) {}

	default void visit(LabeledStatement e) {}

	default void visit(LocalDefineStatement e) {}

	default void visit(ReturnStatement e) {}

	default void visit(SwitchStatement e) {}

	default void visit(ThrowStatement e) {}

	default void visit(TryStatement e) {}

	default void visit(SwitchBranch e) {}

	default void visit(Tuple e) {
		switch (e) {
			case ArrayTuple ee:
				visit(ee);
				break;
			case IfTuple ee:
				visit(ee);
				break;
			case SwitchTuple ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(ArrayTuple e) {}

	default void visit(IfTuple e) {}

	default void visit(SwitchTuple e) {}

	default void visit(StructureField e) {}

	default void visit(StructureType e) {
		switch (e) {
			case ArrayStructureType ee:
				visit(ee);
				break;
			case DefinedStructureType ee:
				visit(ee);
				break;
			case UnnamedStructureType ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(ArrayStructureType e) {}

	default void visit(DefinedStructureType e) {}

	default void visit(UnnamedStructureType e) {}

	default void visit(AssignableOperand e) {
		switch (e) {
			case IndexAssignableOperand ee:
				visit(ee);
				break;
			case MemberAssignableOperand ee:
				visit(ee);
				break;
			case VariableAssignableOperand ee:
				visit(ee);
				break;
			case null, default:
				ErrorUtil.unreachable();
		}
	}

	default void visit(IndexAssignableOperand e) {}

	default void visit(MemberAssignableOperand e) {}

	default void visit(VariableAssignableOperand e) {}

}

