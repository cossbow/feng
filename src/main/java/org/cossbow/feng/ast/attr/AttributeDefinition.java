package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

import java.util.List;

import static org.cossbow.feng.ast.Position.ZERO;

public class AttributeDefinition extends TypeDefinition {
    private IdentifierMap<AttributeField> fields;

    public AttributeDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               IdentifierMap<AttributeField> fields) {
        super(pos, modifier, symbol, TypeParameters.empty(),
                TypeDomain.ATTRIBUTE);
        this.fields = fields;
    }

    public IdentifierMap<AttributeField> fields() {
        return fields;
    }

    //

    public static final
    AttributeDefinition InheritDef = new AttributeDefinition(ZERO,
            Modifier.empty(),
            new Symbol(new Identifier("Inherit")),
            new IdentifierMap<>());
    public static final
    AttributeDefinition InlineDef = new AttributeDefinition(ZERO,
            Modifier.empty(),
            new Symbol(new Identifier("Inline")),
            new IdentifierMap<>());

    public static final Identifier ValueField = new Identifier("value");

    // @Pack({value=n}) — 设置 struct/union 的对齐打包值
    public static final
    AttributeDefinition PackDef = new AttributeDefinition(ZERO,
            Modifier.empty(),
            new Symbol(new Identifier("Pack")),
            makeFields(ValueField, AttributeField.Type.INT));

    // @Align({value=n}) — 设置单个字段的对齐值
    public static final
    AttributeDefinition AlignDef = new AttributeDefinition(ZERO,
            Modifier.empty(),
            new Symbol(new Identifier("Align")),
            makeFields(ValueField, AttributeField.Type.INT));

    static {
        InheritDef.builtin(true);
        InlineDef.builtin(true);
        PackDef.builtin(true);
        AlignDef.builtin(true);
    }

    private static IdentifierMap<AttributeField> makeFields(
            Identifier name, AttributeField.Type type) {
        var fields = new IdentifierMap<AttributeField>(1);
        fields.add(name, new AttributeField(ZERO, name, type, false, Optional.empty()));
        return fields;
    }
}
