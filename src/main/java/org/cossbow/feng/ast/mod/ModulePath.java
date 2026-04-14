package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModulePath extends Entity {
    private final Identifier pkg;
    private final Identifier[] values;

    private transient volatile Path path;

    public ModulePath(Position pos,
                      Identifier pkg,
                      Identifier[] values) {
        super(pos);
        this.pkg = pkg;
        this.values = values.clone();
    }

    public ModulePath(Identifier pkg, Path path) {
        super(Position.ZERO);
        this.pkg = pkg;
        if (path.getNameCount() == 1 && path.toString().isEmpty()) {
            values = new Identifier[0];
        } else {
            values = new Identifier[path.getNameCount()];
            for (int i = 0; i < values.length; i++) {
                values[i] = new Identifier(path.getName(i).toString());
            }
        }
    }

    public Path toPath() {
        var path = this.path;
        if (path == null) {
            if (values.length == 0) {
                path = Path.of("");
            } else {
                var more = new String[values.length - 1];
                for (int i = 1; i < values.length; i++) {
                    more[i] = values[i].value();
                }
                path = Path.of(values[0].value(), more);
            }
            this.path = path;
        }
        return path;
    }

    public Identifier name() {
        return values.length > 0 ?
                values[values.length - 1] : pkg;
    }

    public int size() {
        return values.length;
    }

    public Identifier get(int index) {
        return values[index];
    }

    public Stream<String> stream() {
        return Stream.concat(Stream.of(pkg),
                        Arrays.stream(values))
                .map(Identifier::value);
    }

    public String join(String delimiter) {
        return stream().collect(
                Collectors.joining(delimiter));
    }

    //

    private transient String filename;

    public String filename() {
        var fn = filename;
        if (fn == null) {
            filename = fn = join("_");
        }
        return fn;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ModulePath t)) return false;
        return pkg.equals(t.pkg) &&
                Arrays.equals(values, t.values);
    }

    @Override
    public int hashCode() {
        return pkg.hashCode() * 31 + Arrays.hashCode(values);
    }

    //
    @Override
    public String toString() {
        return join("$");
    }
}
