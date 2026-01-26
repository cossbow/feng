package org.cossbow.feng.coder;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.ast.micro.Macro;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.ast.stmt.Tuple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class VisitorGenerator {

    static void visitClass(ClassInfo ci, Consumer<ClassInfo> consumer) {
        consumer.accept(ci);
        for (var sc : ci.getSubclasses().directOnly()) {
            visitClass(sc, consumer);
        }
    }

    static void genVisitor(ClassInfo root, String rootName, String returnType, boolean makeDef)
            throws IOException {
        var pkgs = new HashSet<String>();
        visitClass(root, ci -> pkgs.add(ci.getPackageName()));
        var sb = new StringBuilder("package org.cossbow.feng.visit;\n\n");
        for (var p : pkgs) sb.append("import ").append(p).append(".*;\n");
        sb.append("import static org.cossbow.feng.util.ErrorUtil.*;\n");
        sb.append("\npublic interface ").append(rootName).append(" {\n\n");
        visitClass(root, ci -> {
            var name = ci.getSimpleName();
            if (!ci.isAbstract()) {
                if (makeDef)
                    sb.append("\tdefault ").append(returnType).append(" visit(")
                            .append(name).append(" e) { return unreachable(); }\n\n");
                else
                    sb.append("\tdefault ").append(returnType).append(" visit(")
                            .append(name).append(" e);\n\n");
                return;
            }

            sb.append("\tdefault ").append(returnType).append(" visit(").
                    append(name).append(" e) {\n");
            sb.append("\t\treturn switch (e) {\n");
            for (var ch : ci.getSubclasses().directOnly()) {
                sb.append("\t\t\tcase ").append(ch.getSimpleName())
                        .append(" ee -> visit(ee);\n");
            }
            sb.append("\t\t\tcase null, default -> ");
            sb.append("unreachable();\n");

            sb.append("\t\t};\n");

            sb.append("\t}\n\n");
        });
        sb.append("}\n");

        Files.write(Path.of(rootName + ".java"), List.of(sb),
                StandardCharsets.UTF_8);
    }

    static <T> void genForType(Class<T> type, String rootName,
                               String returnType, boolean makeDef)
            throws IOException {
        try (var cg = new ClassGraph().enableAllInfo()
                .acceptPackages(Entity.class.getPackageName()).scan()) {
            var root = cg.getAllClasses().get(type.getName());
            genVisitor(root, rootName, returnType, makeDef);
        }
    }

    public static void main(String[] args) throws IOException {
        genForType(Statement.class, "StatementVisitor", "R", true);
    }

}
