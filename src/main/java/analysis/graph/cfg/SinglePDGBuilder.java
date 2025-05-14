package pascal.taie.analysis.graph.cfg;

import pascal.taie.analysis.defuse.DefUse;
import pascal.taie.analysis.defuse.DefUseAnalysis;
import pascal.taie.analysis.blackcat.PDGCalculator;
import pascal.taie.ir.IR;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;

import java.util.List;
import java.util.Set;

public class SinglePDGBuilder {

    public static CFG<Stmt> analyze(IR ir) {
        StmtCFG pdg = new StmtCFG(ir);
        buildControlDependenceEdge(pdg);
        buildDataDependenceEdge(pdg);
        return pdg;
    }

    private static void buildControlDependenceEdge(StmtCFG pdg) {
        IR ir = pdg.getIR();
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        PDGCalculator calculator = new PDGCalculator(cfg);
        calculator.analyse();
        List<Integer>[] matrix = calculator.getCDG();
        cfg.getNodes().forEach(stmt -> {
            if (cfg.isEntry(stmt)) {
                pdg.setEntry(stmt);
            } else if (cfg.isExit(stmt)) {
                pdg.setExit(stmt);
            } else {
                pdg.addNode(stmt);
            }
        });

        for (int u = 1; u <= matrix.length - 1; u++) {
            // 1 and length-1 are entries and exits respectively
            // Basic block index in IR starts from 0 to size-1, where size+2=cfg node num
            Stmt source = (u == 1) ? pdg.getEntry() : cfg.getNode(u-1);
            CFGEdge.Kind kind = (source instanceof Return) ? CFGEdge.Kind.RETURN : CFGEdge.Kind.CONTROL_DEPENDENCE;
            matrix[u].forEach(v -> {
                Stmt target = cfg.getNode(v-1);
                pdg.addEdge(new CFGEdge<>(kind, source, target));
            });
        }
    }

    private static void buildDataDependenceEdge(StmtCFG pdg) {
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
