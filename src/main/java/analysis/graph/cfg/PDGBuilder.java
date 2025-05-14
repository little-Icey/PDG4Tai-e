package pascal.taie.analysis.graph.cfg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.defuse.DefUse;
import pascal.taie.analysis.defuse.DefUseAnalysis;
import pascal.taie.analysis.blackcat.PDGCalculator;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.stmt.Nop;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;

import java.io.File;
import java.util.List;
import java.util.Set;

public class PDGBuilder extends MethodAnalysis<CFG<Stmt>> {

    public static final String ID = "pdg";

    private static final Logger logger = LogManager.getLogger(PDGBuilder.class);

    private static final String PDG_DIR = "pdg";

    private final boolean isDump;

    private final File dumpDir;

    public PDGBuilder(AnalysisConfig config) {
        super(config);
        isDump = getOptions().getBoolean("dump");
        if (isDump) {
            dumpDir = new File(World.get().getOptions().getOutputDir(), PDG_DIR);
            if (!dumpDir.exists()) {
                dumpDir.mkdirs();
            }
            logger.info("Dumping PDGs in {}", dumpDir.getAbsolutePath());
        } else {
            dumpDir = null;
        }
    }

    @Override
    public CFG<Stmt> analyze(IR ir) {
        StmtCFG pdg = new StmtCFG(ir);
        pdg.setEntry(new Nop());
        pdg.setExit(new Nop());
        buildControlDependenceEdge(pdg);
        buildDataDependenceEdge(pdg);
        if (isDump) {
            CFGDumper.dumpDotFile(pdg, dumpDir);
        }
        return pdg;
    }

    private void buildControlDependenceEdge(StmtCFG pdg) {
        IR ir = pdg.getIR();
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        PDGCalculator calculator = new PDGCalculator(cfg);
        calculator.analyse();
        List<Integer>[] matrix = calculator.getCDG();

        for (int u = 0; u < matrix.length - 1; u++) {
            // 0: entry, length-1: exit
            Stmt curr = cfg.getNode(u);
            addNodeIfAbsent(pdg, curr);
            if (u == 0) {
                matrix[u].forEach(v -> {
                    Stmt target = cfg.getNode(v);
                    addNodeIfAbsent(pdg, target);
                    pdg.addEdge(new CFGEdge<>(CFGEdge.Kind.CONTROL_DEPENDENCE, pdg.getEntry(), target));
                });
            } else {
                for (int v : matrix[u]) {
                    Stmt target = cfg.getNode(v);
                    addNodeIfAbsent(pdg, target);
                    pdg.addEdge(new CFGEdge<>(CFGEdge.Kind.CONTROL_DEPENDENCE, curr, target));
                }
            }
        }

        for (int i = 0; i < ir.getStmts().size(); i++) {
            Stmt stmt = ir.getStmt(i);
            if (stmt instanceof Return) {
                pdg.addEdge(new CFGEdge<>(CFGEdge.Kind.RETURN, stmt, pdg.getExit()));
            }
        }
    }

    private void addNodeIfAbsent(StmtCFG pdg, Stmt node) {
        if (!pdg.hasNode(node)) {
            pdg.addNode(node);
        }
    }

    private void buildDataDependenceEdge(StmtCFG pdg) {
        IR ir = pdg.getIR();
        DefUse defUseResult = ir.getResult(DefUseAnalysis.ID);
        for (int i = 0; i < ir.getStmts().size(); i++) {
            Stmt curr = ir.getStmt(i);
            Set<Stmt> users = defUseResult.getUses(curr);
            // def-use
            if (!users.isEmpty()) {
                for (Stmt user : users) {
                    pdg.addEdge(new CFGEdge<>(CFGEdge.Kind.DEF_USE,
                            curr, user));
                }
            }
        }
    }
}
