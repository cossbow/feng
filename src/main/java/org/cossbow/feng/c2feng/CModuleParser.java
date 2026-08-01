package org.cossbow.feng.c2feng;

import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.c2feng.convert.C2FengConverter;
import org.cossbow.feng.c2feng.parse.CHeaderParser;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.ErrorUtil;

import java.nio.file.Path;

public class CModuleParser {

    public static ParseSymbolTable parse(
            ModulePath mod, Path header) {
        var converter = new C2FengConverter(mod);
        var parser = new CHeaderParser(header);
        try {
            parser.runInto(converter);
            return converter.table();
        } catch (Exception e) {
            throw ErrorUtil.sneaky(e);
        }
    }

}
