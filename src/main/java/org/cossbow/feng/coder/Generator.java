package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.AnalyseSymbolTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Unified code-generator backend.
 * <p>
 * Each target language (C++, C) provides a {@link Factory} instance.
 * The factory creates generator instances for source/header output,
 * copies the language-specific runtime header, and reports the target
 * file extension.
 */
public interface Generator {

    /**
     * Write the complete output for this module (source or header).
     */
    void write();

    /**
     * Creates {@link Generator} instances and provides target metadata.
     */
    interface Factory {
        Generator create(AnalyseSymbolTable ast,
                         Appendable out,
                         boolean header,
                         boolean debug);

        String extension();

        void copyBaseHeader(Path dir);

        String compiler();

        String version();
    }
}
