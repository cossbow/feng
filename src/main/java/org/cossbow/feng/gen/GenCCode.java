package org.cossbow.feng.gen;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.*;

import java.io.IOException;

public class GenCCode {

    public void callBiFun(Appendable a, BinaryExpression e, String fun) throws IOException {
        a.append(fun).append('(');
        writeExpression(a, e.left());
        a.append(',');
        writeExpression(a, e.right());
        a.append(')');
    }

    public void writeExpression(Appendable a, BinaryExpression e) throws IOException {
        var eop = e.operator();
        if (eop == BinaryOperator.POW) {
            callBiFun(a, e, "pow");
            return;
        }
        var op = switch (eop) {
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "%";
            case ADD -> "+";
            case SUB -> "-";
            case LSHIFT -> "<<";
            case RSHIFT -> ">>";
            case BITAND -> "&";
            case BITXOR -> "^";
            case BITOR -> "|";
            case EQ -> "==";
            case NE -> "!=";
            case GT -> ">";
            case LT -> "<";
            case GE -> ">=";
            case LE -> "<=";
            case AND -> "&&";
            case OR -> "||";
            default -> throw new UnsupportedOperationException();
        };
        writeExpression(a, e.left());
        a.append(op);
        writeExpression(a, e.right());
    }

    public void writeExpression(Appendable a, UnaryExpression e) throws IOException {
        var op = switch (e.operator()) {
            case INVERT -> ('~');
            case POSITIVE -> ('+');
            case NEGATIVE -> ('-');
        };
        a.append(op);
        a.append('(');
        writeExpression(a, e.operand());
        a.append(')');
    }

    public void writeLiteral(Appendable a, Literal lit) throws IOException {
        switch (lit) {
            case IntegerLiteral il:
                a.append(il.value().toString());
                break;
            case FloatLiteral fl:
                a.append(fl.value().toString());
                break;
            case BoolLiteral fl:
                a.append(String.valueOf(fl.value()));
                break;
            case StringLiteral sl:
                a.append('"').append(sl.value()).append('"');
                break;
            case NilLiteral ignored:
                a.append("NULL");
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public void writeExpression(Appendable a, Expression e) throws IOException {
        switch (e) {
            case ReferExpression re:
                if (!re.generic().isEmpty()) {
                    throw new UnsupportedOperationException();
                }
                var s = re.symbol();
                if (s.module().has())
                    a.append(s.module().get().value()).append('$');
                a.append(s.name().value());
                break;
            case ParenExpression pe:
                a.append('(');
                writeExpression(a, pe.child());
                a.append(')');
                break;
            case BinaryExpression be:
                writeExpression(a, be);
                break;
            case UnaryExpression ue:
                writeExpression(a, ue);
                break;
            case LiteralExpression le:
                writeLiteral(a, le.literal());
                break;
            case null, default:
                throw new UnsupportedOperationException();
        }
    }

}
