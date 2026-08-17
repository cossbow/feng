package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.util.Counter;

final
public class WriterContext {
    public final AnalyseSymbolTable table;
    public final Appendable out;
    public final boolean header;   // true = header file, false = source file
    public final boolean debug;
    public final boolean memchk;   // leak checker: -Dfeng.memchk → FENG_DEBUG_MEMORY
    public final Counter dent;
    public final TypeWriter types;
    public final ExprWriter exprs;
    public final StmtWriter stmts;
    public final FuncWriter funcs;
    public final MetaWriter metas;
    public final ReleaserWriter releaser;

    public WriterContext(AnalyseSymbolTable table,
                         Appendable out,
                         boolean header,
                         boolean debug) {
        this.table = table;
        this.out = out;
        this.header = header;
        this.debug = debug;
        this.memchk = System.getProperties().containsKey("feng.memchk");
        this.dent = new Counter();
        this.types = new TypeWriter(this);
        this.exprs = new ExprWriter(this);
        this.stmts = new StmtWriter(this);
        this.funcs = new FuncWriter(this);
        this.metas = new MetaWriter(this);
        this.releaser = new ReleaserWriter(this);
    }

    /**
     * 当前正在发射的函数体（FuncWriter.write(Procedure) 设置）——
     * StmtWriter 发射 return 时取返回类型用。函数/方法/destroy 共用。
     */
    public Procedure enterProc;
}
