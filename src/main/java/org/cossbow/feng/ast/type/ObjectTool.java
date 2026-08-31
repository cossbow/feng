package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.oop.ObjectDefinition;
import org.cossbow.feng.util.Optional;

final
public class ObjectTool {
    private ObjectTool() {
    }

    static Optional<TypeArguments> checkInherited(
            DerivedType lt,
            ObjectDefinition rd, GenericMap next) {
        for (var st : rd.supers()) {
            var sd = (ObjectDefinition) st.def();
            var gm = st.gm().overlay(next);
            var tas = gm.mapAll(sd.generic());
            if (lt.symbol().equals(sd.symbol()))
                return Optional.of(tas);
            var o = checkInherited(lt, sd, gm);
            if (o.has())
                return o;
        }
        return Optional.empty();
    }

    public static Optional<TypeArguments> checkInherited(
            DerivedType lt, DerivedType rt) {

        if (!(lt.def() instanceof ObjectDefinition))
            return Optional.empty();

        if (!(rt.def() instanceof ObjectDefinition rd))
            return Optional.empty();

        return checkInherited(lt, rd, rt.gm());
    }

}
