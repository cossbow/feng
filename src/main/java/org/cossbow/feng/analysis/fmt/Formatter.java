package org.cossbow.feng.analysis.fmt;

import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.ReferKind;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.oop.InterfaceDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.cossbow.feng.ast.Position.ZERO;

public class Formatter {
    /**
     * original format string
     */
    private final String fmt;
    /**
     * All segments
     */
    private final List<FormatSegment> segments;
    private final int argNum;

    private Formatter(String fmt,
                      List<FormatSegment> segments,
                      int argNum) {
        this.fmt = fmt;
        this.segments = segments;
        this.argNum = argNum;
    }

    public String fmt() {
        return fmt;
    }

    public List<FormatSegment> segments() {
        return segments;
    }

    public int argNum() {
        return argNum;
    }

    //

    // 解析格式字符串，返回片段列表
    public static Formatter parse(StringLiteral fmt) {
        var segments = new ArrayList<FormatSegment>();
        var str = fmt.string();
        int i = 0;
        int len = str.length();
        var text = new StringBuilder();
        int index = 0;

        while (i < len) {
            char c = str.charAt(i);
            if (c == '{') {
                // 可能是占位符，检查下一个字符
                if (i + 1 < len && str.charAt(i + 1) == '}') {
                    // 遇到 {}，保存当前文本段，添加一个占位符段
                    if (!text.isEmpty()) {
                        segments.add(new TextSegment(
                                i - text.length(),
                                makeText(fmt, text)));
                        text.setLength(0);
                    }
                    var arg = new ArgumentSegment(i, index++);
                    segments.add(arg);
                    i += 2; // 跳过 {}
                } else {
                    // 普通左花括号，视为文本（或者可以报错）
                    text.append('{');
                    i++;
                }
            } else if (c == '}') {
                // 单独的右花括号可以报错或视为文本，此处简单处理
                text.append('}');
                i++;
            } else {
                text.append(c);
                i++;
            }
        }

        if (!text.isEmpty()) {
            segments.add(new TextSegment(len - text.length(),
                    makeText(fmt, text)));
        }
        return new Formatter(str, segments, index);
    }

    private static StringLiteral makeText(
            StringLiteral fmt, StringBuilder text) {
        return new StringLiteral(fmt.pos(),
                fmt.charset(),
                text.toString().getBytes(fmt.charset()));
    }

    //

    public static final DerivedTypeDeclarer FORMAT_OUT =
            new DerivedTypeDeclarer(ZERO, InterfaceDefinition.WriterType.link(),
                    new Refer(ZERO, ReferKind.PHANTOM, true, false));
    public static final DerivedTypeDeclarer FORMAT_DATA =
            new DerivedTypeDeclarer(ZERO, InterfaceDefinition.WritableType.link(),
                    new Refer(ZERO, ReferKind.PHANTOM, true, true));

    //
    @Override
    public String toString() {
        return fmt;
    }
}
