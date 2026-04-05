package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModulePath extends Entity
        implements Iterable<Identifier> {
    private final Identifier[] values;

    private transient volatile Path path;

    public ModulePath(Position pos, List<Identifier> values) {
        super(pos);
        this.values = values.toArray(new Identifier[0]);
    }

    public ModulePath(Path path) {
        super(Position.ZERO);
        values = new Identifier[path.getNameCount()];
        for (int i = 0; i < values.length; i++) {
            values[i] = new Identifier(path.getName(i).toString());
        }
    }

    public Path toPath() {
        var path = this.path;
        if (path == null) {
            var more = new String[values.length - 1];
            for (int i = 1; i < values.length; i++) {
                more[i] = values[i].value();
            }
            path = Path.of(values[0].value(), more);
            this.path = path;
        }
        return path;
    }

    public Identifier name() {
        return values[values.length - 1];
    }

    public int size() {
        return values.length;
    }

    public Identifier get(int index) {
        return values[index];
    }

    public Iterator<Identifier> iterator() {
        return Arrays.asList(values).iterator();
    }

    public Stream<String> stream() {
        return Arrays.stream(values).map(Identifier::value);
    }

    //

    private transient String filename;

    public String filename() {
        var fn = filename;
        if (fn == null) {
            fn = stream().collect(Collectors.joining("_"));
            filename = fn;
        }
        return fn;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModulePath t)) return false;
        return Arrays.equals(values, t.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    //
    @Override
    public String toString() {
        return stream().collect(Collectors.joining("."));
    }
}
