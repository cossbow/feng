package org.cossbow.feng.ast;

import java.util.HashMap;
import java.util.Map;

/**
 * Each type definition is associated with a unique TypeDomain value.
 */
public enum TypeDomain {
    PRIMITIVE("primitive", true),
    STRUCT("struct", true),
    UNION("union", true),
    ENUM("enum", true),
    ATTRIBUTE("attribute", false),
    INTERFACE("interface", true),
    CLASS("class", true),
    FUNC("func", true),
    ;

    public final String name;
    public final boolean instantiable;

    TypeDomain(String name, boolean instantiable) {
        this.name = name;
        this.instantiable = instantiable;
    }

    @Override
    public String toString() {
        return name;
    }

    //

    private static final Map<String, TypeDomain> nameMap;

    static {
        var m = new HashMap<String, TypeDomain>();
        for (var d : values()) {
            m.put(d.name, d);
        }
        nameMap = Map.copyOf(m);
    }

    public static TypeDomain parse(String name) {
        return nameMap.get(name);
    }
}
