package org.cossbow.feng.util;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

public class UnamedUtil {

    public static Identifier rand(String prefix) {
        var rand = ThreadLocalRandom.current();
        var buf = new byte[16];
        rand.nextBytes(buf);
        var h = HexFormat.of().formatHex(buf);
        return new Identifier(Position.ZERO, prefix + "_" + h);
    }

}
