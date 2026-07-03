package org.cossbow.feng.c2feng.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link CHeaderParser}.
 * <p>
 * Requires clang to be available on the system PATH.
 * The test is skipped gracefully if clang is not found.
 */
public class CHeaderParserTest {

    private static final String RESOURCE = "src/test/resources/c2feng/test_header.h";

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

        var parser = new CHeaderParser(header, "c_test", tempDir);
        var outputFile = parser.run();

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
