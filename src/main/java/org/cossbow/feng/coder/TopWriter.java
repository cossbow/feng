package org.cossbow.feng.coder;

import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.PrimitiveTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.proc.FixedParameter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TopWriter extends CWriter<TopWriter> {

    protected TopWriter(WriterContext context) {
        super(context);
    }

    // ---- 主编排 ----

    public void write() {
        definePre();
        includeHeaders();
        newLine();
        var header = context.header;

        // [C] 生成C模块相关类型
        if (header) writeCModule();

        // [H] 顶层类型前向 typedef（struct/interface/class）
        context.types.declareType();
        // [S] 具体 typedef 之前的 struct tag 前向（类 + 结构体 + 泛型具体化类）
        context.types.declareConcreteStructForwards();
        // [S] 无锚点的 mono 类型（依赖全 primitive，最先生成）
        context.types.headDefinitions();
        // [S] 字符串池（enum 表引用 Feng$constString_<id>，必须先于 enum 发射）
        context.types.literalStringCache();
        // [S] enum 值表（source only）
        context.types.enumDefinition();
        // [S/H] struct/union 定义（module 模式仅 header）
        context.types.structureDefinition();
        // [S/H] 命名原型 typedef（dagPrototypes；module 模式仅 header）
        context.types.declareProtoTypedefs();
        // [S/H] 类扁平 struct + Feng$Meta_<X> 类型 + 接口 meta 类型（module 模式仅 header）
        context.types.classesDefinition();
        context.types.interfaceDefinitions();

        // [H] 函数原型 + 方法原型（module 模式 source 通过 include .h 可见；单模块 source 补发）
        context.funcs.declareFunction();
        context.funcs.declareMethods();
        // [S] 全局常量定义（constVars 声明序）；[H] 仅 export 变量的 extern 声明
        //（跨模块引用需要——format-1 引 std$os$stdout 但 std_os.h 缺 extern）
        context.releaser.globalConsts();
        // [S] 全局变量定义（dagVars 依赖序）；[H] 同上仅 export extern
        context.releaser.globalVars();
        // [S] cleanup 前置声明（FENG$DEC 引用需要先声明后定义）
        if (!header) context.releaser.declareCleanups();
        // [S] copy 前置声明（declareVar/writeAssign 引用 Feng$copy_<key>）
        if (!header) context.releaser.declareCopies();
        // [H/S] destroy 前置声明——header 也要发（导入模块经 include .h 引用跨模块
        // Feng$destroy_X；旧 CGenerator 5611–5619 无 header 守卫同款）
        context.releaser.declareDestroys();
        // [S] 函数定义（functionList + monoFuncs）+ 类方法定义（source only）
        context.funcs.functionDefinition();
        context.funcs.methodDefinition();
        // [S] meta 常量定义（vtables → Feng$meta_* 常量；[H] extern 已由 TypeWriter 发）
        if (!header) context.metas.metaDefinitions();
        // [S] cleanup 函数体（source only，FENG$DEC 引用的清理函数）
        if (!header) context.releaser.writeCleanups();
        // [S] copy 函数体（source only）
        if (!header) context.releaser.writeCopies();
        // [S] destroy 函数体（cleanup/字段释放引用 Feng$destroy_X）
        if (!header) context.releaser.writeDestroys();
        // [S] 运行时全局初始化（constructor）
        if (!header) context.releaser.writeGlobalInits();
        // [S] test runner（-T）或 main（implFunc + FENG_MAIN_HAS_ARGS + Main.c 拼接）
        if (!header) context.releaser.entry();

        endFile();
    }

    // ---- 辅助 ----

    private void definePre() {
        if (context.header) {
            var name = context.table.module.has() ?
                    context.table.module.must()
                            .path().filename().toUpperCase() : "builtin";
            write("#ifndef __HEADER_").write(name).newLine();
            write("#define __HEADER_").write(name).newLine();
            return;
        }
        if (context.debug) write("#define FENG_DEBUG").newLine();
        if (context.memchk) write("#define FENG_DEBUG_MEMORY").newLine();
    }

    private void includeHeaders() {
        writeComment("base header");
        context.table.module.use(fm -> {
            write("#include \"Header.h\"").newLine();
            write("#include \"builtin.h\"").newLine();
            if (!context.header) {
                write("#include \"").write(fm.path().filename())
                        .write(".h\"").newLine();
                return;
            }
            // header file: 内置合成模块头（FENG 自身除外），再 include 导入模块头
            if (!fm.path().equals(Mangle.FENG)) {
                write("#include \"Feng.h\"").newLine();
            }
            if (!fm.imports().isEmpty()) {
                writeComment("import headers");
                for (var i : fm.imports()) {
                    write("#include \"").write(i.filename())
                            .write(".h\"").newLine();
                }
            }
        });
    }

    private void endFile() {
        if (!context.header) return;
        write("#endif").newLine();
    }

    private void writeCModule() {
        bridgeTypes();
        bridgeFunctions();
    }

    private void bridgeTypes() {
        writeComment("generated bridge for C type");
        var fm = context.table.module.must();
        for (var h : fm.headerFiles()) {
            write("#include ").write('"');
            write(h.getFileName().toString());
            write('"').write('\n');
        }

        for (var sd : context.table.dagStructures) {
            if (!sd.cType() || sd.anonymous()) continue;
            // only named C-imported structs
            write("typedef ");
            write(sd.domain().name).write(' ');
            write(sd.symbol().name().value()).write(' ');
            write(fm.path().toString()).write('$');
            write(sd.symbol().name().value());
            write(";\n");
        }
    }

    private void bridgeFunctions() {
        writeComment("generated bridge for C functions");
        var fm = context.table.module.must();
        for (var fd : context.table.functionList) {
            if (fd.builtin() || fd.procedure().has()) continue;
            var cName = fd.symbol().name().value();
            // Skip C implementation-reserved identifiers (names starting
            // with '_') that pollute system headers, except for explicitly
            // needed functions like __acrt_iob_func.
            if (cName.startsWith("_") && !"__acrt_iob_func".equals(cName)) continue;
            var prefix = fm.path().toString() + "$";
            var retType = cTypeOf(fd.prototype().returnType());
            var inlineKw = "static inline ";
            write(inlineKw + retType + " ");
            write(prefix);
            write(fd.symbol().name().value());
            write("(");
            boolean first = true;
            int paramIdx = 0;
            for (var p : fd.prototype().parameterSet()) {
                if (!first) write(", ");
                first = false;
                var fp = (FixedParameter) p;
                write(cTypeOf(fp.type()));
                write(' ');
                var pName = fp.name().get().value();
                if (pName.isEmpty()) pName = "_" + paramIdx;
                write(pName);
                paramIdx++;
            }
            // C: cast function pointer directly
            write(") {\n\treturn ((");
            write(retType);
            write("(*)(");
            first = true;
            for (var p : fd.prototype().parameterSet()) {
                if (!first) write(", ");
                first = false;
                var fp = (FixedParameter) p;
                write(cTypeOf(fp.type()));
            }
            write("))");
            write(fd.symbol().name().value());
            write(")(");
            first = true;
            paramIdx = 0;
            for (var p : fd.prototype().parameterSet()) {
                if (!first) write(", ");
                first = false;
                var fp = (FixedParameter) p;
                var pName = fp.name().get().value();
                if (pName.isEmpty()) pName = "_" + paramIdx;
                write(pName);
                paramIdx++;
            }
            write(");\n}\n");
        }
    }

    String cTypeOf(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            return PrimitiveName.get(ptd.primitive());
        }
        return "Uint64";
    }

    /**
     * Primitive type name mapping (e.g. INT → "Int", FLOAT64 → "Float64").
     */
    static final Map<Primitive, String> PrimitiveName =
            Arrays.stream(Primitive.values())
                    .collect(Collectors.toMap(Function.identity(),
                            p -> {
                                var s = p.code;
                                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
                            }));
}
