package org.cossbow.feng.coder;

import org.cossbow.feng.Compiler;

import java.util.Map;

public class CGeneratorTest extends GeneratorTest {
    protected Compiler compiler(String pkg) {
        var c = new Compiler(CGenerator.FACTORY);
        c.pkg(pkg);
        c.debug(true);
        c.sanitizer("address");
        c.buildSystem(Compiler.Build.MAKE);
        c.lib(Map.of("std", "std"));
        System.setProperty("feng.memchk", "1");
        return c;
    }
}
