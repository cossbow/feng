package org.cossbow.feng.ast;

import java.util.HashMap;
import java.util.Map;

public enum TypeDomain {
    STRUCT("struct"),
    UNION("union"),
    ENUM("enum"),
    ATTRIBUTE("attribute"),
    INTERFACE("interface"),
    CLASS("class"),
    FUNC("func"),
    ;

    public final String name;

    TypeDomain(String name) {
        this.name = name;
    }

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
