package org.cossbow.feng.coder;

import org.cossbow.feng.Compiler;

import java.util.Map;

public class CppGeneratorTest {
    protected Compiler compiler(String pkg) {
        var c = new Compiler(CppGenerator.FACTORY);
        c.pkg(pkg);
        c.debug(true);
        c.buildSystem(Compiler.Build.MAKE);
        c.lib(Map.of("std", "std"));
        return c;
    }
}
