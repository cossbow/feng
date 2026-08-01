package org.cossbow.feng.c2feng.parse;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.c2feng.convert.C2FengConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link CHeaderParser}.
 * <p>
 * Requires clang to be available on the system PATH.
 * The test is skipped gracefully if clang is not found.
 */
public class CHeaderParserTest {

    private static final String RESOURCE = "src/test/resources/c2feng/test_header.h";

    private Path parse(Path headerPath, Path outputDir)
            throws IOException, InterruptedException {
        if (!Files.isRegularFile(headerPath)) {
            throw new IOException("header file not found: " + headerPath);
        }
        Files.createDirectories(outputDir);

        var mp = new ModulePath(new Identifier("c_test"), Path.of(""));
        var converter = new C2FengConverter(mp);
        new CHeaderParser(headerPath).runInto(converter);

        var outputFile = outputDir.resolve(mp + ".feng");
        try (var w = Files.newBufferedWriter(outputFile)) {
            converter.write(w);
        }
        return outputFile;
    }

    @Test
    public void testParseHeader(@TempDir Path tempDir) throws Exception {
        var header = Path.of(RESOURCE).toAbsolutePath();
        if (!header.toFile().exists()) {
            System.out.println("SKIP: test header not found at " + header);
            return;
        }

        // Check clang availability
        try {
            var proc = new ProcessBuilder("clang", "--version")
                    .start();
            int ec = proc.waitFor();
            if (ec != 0) {
                System.out.println("SKIP: clang not available (exit " + ec + ")");
                return;
            }
        } catch (Exception e) {
            System.out.println("SKIP: clang not available — " + e.getMessage());
            return;
        }

        var outputFile = parse(header, tempDir);

        System.out.println("=== testParseHeader ===");
        System.out.println("Output: " + outputFile);

        assertTrue(outputFile.toFile().exists());
        var content = java.nio.file.Files.readString(outputFile);
        System.out.println(content);

        // Verify key constructs are present
        assertTrue(content.contains("struct Point"));
        assertTrue(content.contains("x int"));
        assertTrue(content.contains("y int"));
        assertTrue(content.contains("const Color$RED int = 0;"));
        assertTrue(content.contains("const Color$GREEN int = 1;"));
        assertTrue(content.contains("Color$BLUE")); // exact value depends on clang
        assertTrue(content.contains("func open("));
        assertTrue(content.contains("uint64")); // const char* → uint64
        assertTrue(content.contains("func close(fd int32)"));
    }
}
