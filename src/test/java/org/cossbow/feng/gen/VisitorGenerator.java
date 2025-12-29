package org.cossbow.feng.gen;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import org.cossbow.feng.ast.Entity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class VisitorGenerator {

    void visitClass(ClassInfo ci, Consumer<ClassInfo> consumer) {
        consumer.accept(ci);
        for (var sc : ci.getSubclasses().directOnly()) {
            visitClass(sc, consumer);
        }
    }

    void genVisitor(ClassInfo entity) throws IOException {
        var pkgs = new HashSet<String>();
        visitClass(entity, ci -> {
            pkgs.add(ci.getPackageName());
        });
        var sb = new StringBuilder("package org.cossbow.feng.gen;\n\n");
        for (var p : pkgs) sb.append("import ").append(p).append(".*;\n");

        sb.append("\npublic interface EntityVisitor {\n\n");
        visitClass(entity, ci -> {
            var name = ci.getSimpleName();
            if (!ci.isAbstract()) {
                sb.append("\tvoid visit(").append(name).append(" e);\n\n");
                return;
            }
            sb.append("\tdefault void visit(").append(name).append(" e) {\n");

            sb.append("\t\tswitch (e) {\n");

            for (var ch : ci.getSubclasses().directOnly()) {
                sb.append("\t\t\tcase ").append(ch.getSimpleName()).append(" ee:\n");
                sb.append("\t\t\t\tvisit(ee);\n");
                sb.append("\t\t\t\tbreak;\n");
            }
            sb.append("\t\t\tcase null, default:\n");
            sb.append("\t\t\t\tthrow new UnsupportedOperationException();\n");

            sb.append("\t\t}\n");

            sb.append("\t}\n\n");
        });
        sb.append("}\n");

        Files.write(Path.of("EntityVisitor.java"), List.of(sb), StandardCharsets.UTF_8);
    }

    @Test
    void genVisitor() throws Exception {
        try (var cg = new ClassGraph().enableAllInfo()
                .acceptPackages(Entity.class.getPackageName()).scan()) {
            var entity = cg.getAllClasses().get(Entity.class.getName());
            genVisitor(entity);
        }
    }

}
