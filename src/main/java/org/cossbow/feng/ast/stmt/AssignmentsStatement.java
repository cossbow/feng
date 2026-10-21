package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.var.Assignment;
import org.cossbow.feng.ast.var.Operand;

import java.util.ArrayList;
import java.util.List;

public class AssignmentsStatement extends Statement {
    private List<Assignment> list;

    public AssignmentsStatement(Position pos,
                                List<Assignment> list) {
        super(pos);
        this.list = list;
    }

    public List<Assignment> list() {
        return list;
    }

    public void list(List<Assignment> list) {
        this.list = list;
    }

    public Assignment get(int i) {
        return list.get(i);
    }

    public Operand operand(int i) {
        return list.get(i).operand();
    }

    public Expression value(int i) {
        return list.get(i).value();
    }

    @Override
    public AssignmentsStatement mirror() {
        var l = new ArrayList<Assignment>(list.size());
        for (var a : list) l.add(a.mirror());
        return new AssignmentsStatement(pos(), l);
    }

    @Override
    public AssignmentsStatement mono(GenericMap gm) {
        var l = new ArrayList<Assignment>(list.size());
        for (var a : list) {
            var na = new Assignment(a.pos(), a.operand().mono(gm), a.value().mono(gm));
            // 保留语义分析挂上的 replacer（index-set / sync 字段改写），并经 gm 重建；
            // 否则单态化后 StmtWriter 仍按数组访问发射（CallStatement.mono 同理保留 replace）。
            a.replacer().use(r -> na.replacer().set(r.mono(gm)));
            l.add(na);
        }
        return new AssignmentsStatement(pos(), l);
    }

}
