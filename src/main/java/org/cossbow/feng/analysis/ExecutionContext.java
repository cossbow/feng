package org.cossbow.feng.analysis;

public interface ExecutionContext<N, V> {

    void put(N name, V variable);

    V get(N name);

}
