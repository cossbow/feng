package org.cossbow.feng.ast;

import java.util.HashMap;
import java.util.Map;

public enum TypeDomain {
    PRIMITIVE("primitive", false),
    MEM("mem", false),
    STRUCT("struct", true),
    UNION("union", true),
    ENUM("enum", true),
    ATTRIBUTE("attribute", true),
    INTERFACE("interface", true),
    CLASS("class", true),
    FUNC("func", false),
    ARRAY("array", false),
    ;

    public final String name;
    public final boolean derived;

    TypeDomain(String name, boolean derived) {
        this.name = name;
        this.derived = derived;
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
