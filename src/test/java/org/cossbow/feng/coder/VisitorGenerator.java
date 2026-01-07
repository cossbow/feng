package org.cossbow.feng.coder;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
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

    static void genVisitor(ClassInfo root) throws IOException {
        var pkgs = new HashSet<String>();
        visitClass(root, ci -> pkgs.add(ci.getPackageName()));
        var sb = new StringBuilder("package org.cossbow.feng.visit;\n\n");
        for (var p : pkgs) sb.append("import ").append(p).append(".*;\n");
        sb.append("import org.cossbow.feng.util.ErrorUtil;\n");
        var rootName = root.getSimpleName();
        sb.append("\npublic interface ").append(rootName).append("Parser<R> {\n\n");
        visitClass(root, ci -> {
            var name = ci.getSimpleName();
            if (!ci.isAbstract()) {
                sb.append("\tdefault R visit(").append(name).append(" e) { return null; }\n\n");
                return;
            }
            sb.append("\tdefault R visit(").append(name).append(" e) {\n");

            sb.append("\t\treturn switch (e) {\n");

            for (var ch : ci.getSubclasses().directOnly()) {
                sb.append("\t\t\tcase ").append(ch.getSimpleName()).append(" ee -> ");
                sb.append("visit(ee);\n");
            }
            sb.append("\t\t\tcase null, default -> ");
            sb.append("ErrorUtil.unreachable();\n");

            sb.append("\t\t};\n");

            sb.append("\t}\n\n");
        });
        sb.append("}\n");

        Files.write(Path.of(rootName + "Parser.java"), List.of(sb),
                StandardCharsets.UTF_8);
    }

    static <T> void genForType(Class<T> type) throws IOException {
        try (var cg = new ClassGraph().enableAllInfo()
                .acceptPackages(Entity.class.getPackageName()).scan()) {
            var root = cg.getAllClasses().get(type.getName());
            genVisitor(root);
        }
    }

    public static void main(String[] args) throws IOException {
        genForType(Entity.class);
    }

}
