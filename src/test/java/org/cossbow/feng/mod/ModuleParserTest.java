package org.cossbow.feng.mod;

import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.parser.ModuleParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModuleParserTest {

    public static List<File> listMod() {
        var cl = Thread.currentThread().getContextClassLoader();
        var dir = cl.getResource("mod");
        assert dir != null;
        var list = new File(dir.getFile()).listFiles(File::isDirectory);
        assert list != null;
        return List.of(list);
    }

    public static FModule parseMod(Path path) {
        var base = path.getParent();
        var mod = path.getFileName();
        var mp = new ModuleParser(UTF_8);
        return mp.parse(base, mod);
    }

    @Test
    public void parseSample() {
        for (var md : listMod()) {
            parseMod(md.toPath());
        }

    }

}
