package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

/**
 * Builtin TypeConstraint: for check syncable
 */
public class SyncableTypeConstraint extends TypeConstraint {
    public SyncableTypeConstraint() {
        super(Position.ZERO);
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        return TypeTool.checkSyncable(t);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        // tc ⊆ Syncable ⟺ tc 必含 Syncable（sound，允许不完全）
        return tc.has(Concept.Syncable) == Tri.YES;
    }

    @Override
    public Tri referenced() {
        return Tri.BOTH;
    }

    @Override
    public MemberSet members() {
        return MemberSet.empty();
    }

    @Override
    public Tri newable() {
        return Tri.BOTH;
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return allDomains();
    }

    @Override
    public Tri hasAttr(Symbol attr) {
        return Tri.BOTH;
    }

    @Override
    public Tri has(Concept c) {
        return Concept.Syncable.equals(c) ?
                Tri.YES : Tri.BOTH;
    }

    @Override
    public boolean equals(Object obj) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    //
    @Override
    public String toString() {
        return "Syncable";
    }
}
