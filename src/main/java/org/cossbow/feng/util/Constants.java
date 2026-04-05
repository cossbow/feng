package org.cossbow.feng.util;

import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

final
public class Constants {
    private Constants() {
    }


    public static final String SRC_EXT = ".feng";
    public static final String META = "feng.meta";

    public static boolean isSource(Path path) {
        return path.getFileName().toString().endsWith(SRC_EXT);
    }

    public static boolean isSource(String name) {
        return name.endsWith(SRC_EXT);
    }

    public static FileFilter srcFilter() {
        return f -> f.isFile() && isSource(f.getName());
    }

    public static Predicate<Path> srcTest() {
        return p -> Files.isRegularFile(p) &&
                isSource(p.getFileName().toString());
    }

}
