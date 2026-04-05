package org.cossbow.feng.mod;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.ArrayExpression;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.LiteralExpression;
import org.cossbow.feng.ast.expr.ObjectExpression;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.Optional;

import java.io.IOException;
import java.util.function.Consumer;

import static org.cossbow.feng.ast.Position.ZERO;
import static org.cossbow.feng.util.ErrorUtil.io;
import static org.cossbow.feng.util.ErrorUtil.unreachable;

public class MetaDataExtractor {
    private final FModule module;
    private final ParseSymbolTable table;
    private final Appendable out;

    public MetaDataExtractor(FModule module,
                             Appendable out) {
        this.module = module;
        this.table = module.table();
        this.out = out;
    }

    private MetaDataExtractor write(long c) {
        return write(Long.toString(c));
    }

    private MetaDataExtractor write(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private MetaDataExtractor write(CharSequence cs) {
        try {
            out.append(cs);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    //

    private int indentValue;

    private MetaDataExtractor indent() {
        indentValue++;
        return this;
    }

    private MetaDataExtractor dedent() {
        indentValue--;
        return this;
    }

    private MetaDataExtractor newLine() {
        return write('\n');
    }

    private MetaDataExtractor space() {
        return write(' ');
    }

    private MetaDataExtractor comma() {
        return write(',');
    }

    private MetaDataExtractor colon() {
        return write(':');
    }

    private MetaDataExtractor semi() {
        return write(';');
    }

    private MetaDataExtractor endStmt() {
        return semi().newLine();
    }

    private MetaDataExtractor write(Identifier id) {
        if (id.unnamed()) return this;
        return write(id.value());
    }

    private MetaDataExtractor write(ModulePath mp) {
        for (var id : mp) write(id).write('$');
        return this;
    }

    private MetaDataExtractor write(Symbol s) {
        s.module().use(this::write);
        return write(s.name());
    }

    private MetaDataExtractor write(TypeDomain domain) {
        return write(domain.name);
    }

    private MetaDataExtractor export(boolean export) {
        return export ? write("export").space() : this;
    }

    private MetaDataExtractor write(Primitive p) {
        return write(p.code);
    }

    private MetaDataExtractor write(Declare d) {
        return write(d.code);
    }

    private <T> void joinByComma(Iterable<T> s, Consumer<T> w) {
        var first = true;
        for (var t : s) {
            if (first) first = false;
            else comma();
            w.accept(t);
        }
    }

    private MetaDataExtractor required(boolean required) {
        if (required) write('!');
        return this;
    }

    //

    public void write() {
        writeImports();
        table.types.forEach(this::write);
        table.functions.forEach(this::write);
        table.variables.forEach(this::write);
    }

    private void write(Modifier m) {
        export(m.export());
        for (var a : m.attributes())
            write(a);
    }

    private void writeImports() {
        for (var i : module.imports()) {
            write("import ").write(i.toString()).endStmt();
        }
    }

    private void write(GlobalVariable gv) {
        write(gv.modifier());
        write(gv.declare()).space().write(gv.symbol().name()).space();
        write(gv.type().must());
        gv.value().use(e -> write('=').write(e));
        endStmt();
    }

    private void write(FunctionDefinition fd) {
        if (fd.builtin() || fd.entry()) return;
        write(fd.modifier());
        write(TypeDomain.FUNC).space().write(fd.symbol().name());
        write(fd.generic()).write(fd.prototype()).endStmt();
    }

    private void write(TypeDefinition def) {
        if (def.builtin()) return;
        write(def.modifier());
        write(def.domain()).space().write(def.symbol().name())
                .write(def.generic());
        switch (def) {
            case StructureDefinition cd -> write(cd);
            case ClassDefinition cd -> write(cd);
            case EnumDefinition cd -> write(cd);
            case InterfaceDefinition cd -> write(cd);
            case PrototypeDefinition cd -> write(cd);
            default -> unreachable();
        }
    }

    private void write(Attribute ad) {
        write('@').write(ad.type());
        ad.init().use(e ->
                write('(').write(e).write(')'));
        newLine();
    }

    private void write(StructureDefinition sd) {
        write('{').newLine();
        for (var f : sd.fields()) {
            write(f.name()).space().write(f.type()).endStmt();
        }
        write('}').newLine();
    }

    private void write(ClassDefinition cd) {
        if (cd.isFinal()) {
            space().write("final");
        } else {
            cd.inherit().use(dt -> write(':').write(dt));
            write('(');
            joinByComma(cd.impl(), this::write);
            write(')');
        }
        write('{').newLine();

        for (var f : cd.inheritFields()) {
            write(InheritAttr);
            write(f);
        }
        for (var m : cd.inheritMethods()) {
            write(InheritAttr);
            write(m);
        }

        for (var f : cd.fields()) {
            write(f);
        }
        for (var m : cd.methods()) {
            write(m);
        }

        write('}').newLine();
    }

    private void write(ClassField f) {
        write(f.modifier());
        write(f.declare()).space().write(f.name())
                .space().write(f.type()).endStmt();
    }

    private void write(ClassMethod m) {
        write(m.modifier());
        write(TypeDomain.FUNC).space().write(m.name());
        write(m.generic()).write(m.prototype());
        if (m.returnThis()) write("this");
        endStmt();
    }

    private void write(EnumDefinition sd) {
        write('{');
        if (sd.size() > 10) newLine();
        for (var v : sd.values()) {
            write(v.name());
            v.init().use(e -> write('=').write(e));
            write(',');
            if (sd.size() > 10) newLine();
        }
        if (sd.size() > 10) newLine();
        write('}').newLine();
    }

    private void write(InterfaceDefinition sd) {
        write('{').newLine();
        for (var p : sd.parts()) {
            write(p).endStmt();
        }
        for (var m : sd.allMethods) {
            if (!sd.methods().exists(m.name())) {
                write(InheritAttr);
            }
            write(m.name()).write(m.prototype()).endStmt();
        }
        write('}').newLine();
    }

    private void write(PrototypeDefinition sd) {
        colon().write(sd.prototype()).endStmt();
    }

    private MetaDataExtractor write(TypeParameters tps) {
        joinByComma(tps, tp -> {
            write(tp.name());
            tp.constraint().use(this::write);
        });
        return this;
    }

    private MetaDataExtractor write(TypeConstraint c) {
        return switch (c) {
            case DefinedTypeConstraint tc -> write(tc);
            case BinaryTypeConstraint tc -> write(tc);
            case DomainTypeConstraint tc -> write(tc);
            case null, default -> unreachable();
        };
    }

    private MetaDataExtractor write(DefinedTypeConstraint c) {
        return write(c.definedType());
    }

    private MetaDataExtractor write(BinaryTypeConstraint c) {
        return write(c.left()).write(c.operator().symbol)
                .write(c.right());
    }

    private MetaDataExtractor write(DomainTypeConstraint c) {
        return write(c.domain());
    }

    private MetaDataExtractor write(DefinedType dt) {
        return switch (dt) {
            case PrimitiveType t -> write(t.primitive());
            case DerivedType t -> write(t);
            case GenericType t -> write(t);
            case null, default -> unreachable();
        };
    }

    private MetaDataExtractor write(Prototype p) {
        write('(');
        joinByComma(p.parameterSet(), v -> {
            write(v.name()).space().write(v.type().must());
        });
        write(')');
        p.returnSet().use(this::write);
        return this;
    }

    private MetaDataExtractor write(TypeDeclarer t) {
        return switch (t) {
            case ArrayTypeDeclarer td -> write(td);
            case PrimitiveTypeDeclarer td -> write(td);
            case DerivedTypeDeclarer td -> write(td);
            case GenericTypeDeclarer td -> write(td);
            case AnonFuncTypeDeclarer td -> write(td);
            case NamedFuncTypeDeclarer td -> write(td);
            case null, default -> unreachable();
        };
    }

    private MetaDataExtractor write(ArrayTypeDeclarer td) {
        write('[').refer(td.refer());
        if (td.length().has())
            write(td.len());
        write(']');
        return write(td.element());
    }

    private MetaDataExtractor write(PrimitiveTypeDeclarer td) {
        return write(td.primitive());
    }

    private MetaDataExtractor write(DerivedTypeDeclarer td) {
        return refer(td.refer()).write(td.derivedType());
    }

    private MetaDataExtractor write(GenericTypeDeclarer td) {
        return write(td.type());
    }

    private MetaDataExtractor write(AnonFuncTypeDeclarer td) {
        return required(td.required()).write(TypeDomain.FUNC.name)
                .space().write(td.prototype());
    }

    private MetaDataExtractor write(NamedFuncTypeDeclarer td) {
        return required(td.required()).write(td.derivedType());
    }

    private MetaDataExtractor refer(Optional<Refer> o) {
        if (o.none()) return this;
        return write(o.get().toString());
    }

    private MetaDataExtractor write(DerivedType dt) {
        return write(dt.symbol()).write(dt.generic());
    }

    private MetaDataExtractor write(GenericType gt) {
        return write(gt.name());
    }

    private MetaDataExtractor write(TypeArguments tas) {
        if (tas.isEmpty()) return this;
        write('`');
        joinByComma(tas, this::write);
        return write('`');
    }

    //

    private MetaDataExtractor write(Expression ex) {
        return switch (ex) {
            case LiteralExpression e -> write(e);
            case ArrayExpression e -> write(e);
            case ObjectExpression e -> write(e);
            case null, default -> unreachable();
        };
    }

    private MetaDataExtractor write(LiteralExpression e) {
        return write(e.literal());
    }

    private MetaDataExtractor write(Literal lit) {
        return switch (lit) {
            case NilLiteral l -> write(l.type());
            case IntegerLiteral l -> write(l.value().toString());
            case FloatLiteral l -> write(l.value().toString());
            case BoolLiteral l -> write(String.valueOf(l.value()));
            case StringLiteral l -> write(l.toString());
            case null, default -> unreachable();
        };
    }

    private MetaDataExtractor write(ArrayExpression ae) {
        write('[');
        for (var v : ae.elements()) {
            write(v);
        }
        write(']');
        return this;
    }

    private MetaDataExtractor write(ObjectExpression e) {
        write('{');
        for (var n : e.entries().nodes()) {
            write(n.key()).write('=').write(n.value());
        }
        write('}');
        return this;
    }

    //

    public static final
    Attribute InheritAttr = new Attribute(ZERO,
            AttributeDefinition.InheritDef.symbol(),
            Optional.empty());

}
