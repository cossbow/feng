package org.cossbow.feng.ast.mod;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.Lazy;

import java.nio.file.Path;
import java.util.List;

public class FModule {
    private final ModulePath path;
    private final List<ModulePath> imports;
    private final ParseSymbolTable table;
    private List<Path> cSources = List.of();
    private List<Path> headerFiles = List.of();

    public FModule(ModulePath path,
                   List<ModulePath> imports,
                   ParseSymbolTable table) {
        this.path = path;
        this.imports = imports;
        this.table = table;
    }

    public ModulePath path() {
        return path;
    }

    public List<ModulePath> imports() {
        return imports;
    }

    public ParseSymbolTable table() {
        return table;
    }

    public List<Path> cSources() {
        return cSources;
    }

    public void cSources(List<Path> files) {
        this.cSources = List.copyOf(files);
    }

    public List<Path> headerFiles() {
        return headerFiles;
    }

    public void headerFiles(List<Path> files) {
        this.headerFiles = List.copyOf(files);
    }

    public final Lazy<AnalyseSymbolTable> result = Lazy.nil();

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof FModule m && path.equals(m.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    //
    @Override
    public String toString() {
        return path.toString();
    }
}
