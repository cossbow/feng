package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.util.Optional;

public class MemberOfExpression extends PrimaryExpression {
    private PrimaryExpression subject;
    private Identifier member;
    private TypeArguments generic;

    private Optional<? extends Field> field;

    public MemberOfExpression(Position pos,
                              PrimaryExpression subject,
                              Identifier member,
                              TypeArguments generic,
                              Optional<? extends Field> field) {
        super(pos);
        this.subject = subject;
        this.member = member;
        this.generic = generic;
        this.field = field;
    }

    public MemberOfExpression(Position pos,
                              PrimaryExpression subject,
                              Identifier member,
                              TypeArguments generic,
                              Field field) {
        this(pos, subject, member, generic, Optional.of(field));
    }

    public MemberOfExpression(Position pos,
                              PrimaryExpression subject,
                              Identifier member,
                              TypeArguments generic) {
        this(pos, subject, member, generic, Optional.empty());
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Identifier member() {
        return member;
    }

    public TypeArguments generic() {
        return generic;
    }


    public Optional<? extends Field> field() {
        return field;
    }

    //

    @Override
    public String toString() {
        if (generic.isEmpty())
            return subject + "." + member;
        return subject + "." + member + generic;
    }
}
