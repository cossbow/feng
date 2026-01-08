package org.cossbow.feng.ast;

import java.util.List;

public class FModule  {
    private List<Source> sources;

    public FModule(List<Source> sources) {
        this.sources = sources;
    }

    public List<Source> sources() {
        return sources;
    }

}
