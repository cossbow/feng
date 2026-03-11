package org.cossbow.feng.ast;

import java.util.HashMap;
import java.util.Map;

public enum TypeDomain {
    PRIMITIVE("primitive", true),
    STRUCT("struct", false),
    UNION("union", false),
    ENUM("enum", false),
    ATTRIBUTE("attribute", false),
    INTERFACE("interface", false),
    CLASS("class", false),
    FUNC("func", false),
    GENERIC("generic", false),
    ;

    public final String name;
    public final boolean builtin;

    TypeDomain(String name, boolean builtin) {
        this.name = name;
        this.builtin = builtin;
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
