package org.cossbow.feng.layout;

import org.junit.jupiter.api.Test;

import static org.cossbow.feng.layout.StructLayoutCalculator.*;

public class StructLayoutTest {

    @Test
    public void testSample1() {
        var calc = new AlignmentCalculator();

        calc.addMember(new StructMember("a", CType.CHAR));
        calc.addMember(new StructMember("b", CType.INT));
        calc.addMember(new StructMember("c", CType.SHORT));
        calc.addMember(new StructMember("d", CType.DOUBLE));

        var layout = calc.getLayout();
        layout.printLayout();
    }

    @Test
    public void testSample2() {
        var calc = new AlignmentCalculator();

        calc.addMember(new StructMember("header", CType.CHAR));

        // 添加位域
        calc.addBitField(new BitFieldMember(
                "flag1", CType.INT, 1, false));
        calc.addBitField(new BitFieldMember(
                "flag2", CType.INT, 1, false));
        calc.addBitField(new BitFieldMember(
                "value", CType.INT, 10, true));

        // 无名位域填充
        calc.addAnonymousBitField(CType.INT, 4);

        calc.addBitField(new BitFieldMember(
                "mode", CType.INT, 4, false));

        calc.addMember(new StructMember("data", CType.SHORT));

        var layout = calc.getLayout();
        layout.printLayout();
    }
}
