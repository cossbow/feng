package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Symbol;

import java.util.Set;

import static org.cossbow.feng.ast.Position.ZERO;

final
public class BuiltinAttributes {
    public static final Set<AttributeDefinition> All = Set.of(
            new AttributeDefinition(ZERO, Modifier.empty(),
                    new Symbol(new Identifier("Inherit")),
                    new IdentifierMap<>())
    );

    static {
        for (var ad : All) ad.builtin(true);
    }
}
