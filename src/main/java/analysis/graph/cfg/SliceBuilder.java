package pascal.taie.analysis.graph.cfg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.blackcat.SensAPIHandler;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.SetQueue;

import java.io.File;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class SliceBuilder extends MethodAnalysis<CFG<Stmt>> {

    public static final String ID = "slice";

    private static final Logger logger = LogManager.getLogger(PDGBuilder.class);

    private static final String SLICE_DIR = "slice";

    private final boolean isDump;

    private final File dumpDir;

    private static Map<Stmt, Boolean> vis;

    private SensAPIHandler sensAPIHandler;

    public SliceBuilder(AnalysisConfig config) {
        super(config);
        isDump = getOptions().getBoolean("dump");
        if (isDump) {
            dumpDir = new File(World.get().getOptions().getOutputDir(), SLICE_DIR);
            if (!dumpDir.exists()) {
                dumpDir.mkdirs();
            }
            logger.info("Dumping Slices in {}", dumpDir.getAbsolutePath());
        } else {
            dumpDir = null;
        }
    }

    @Override
    public CFG<Stmt> analyze(IR ir) {
        StmtCFG slice = new StmtCFG(ir);
        CFG<Stmt> pdg = ir.getResult(PDGBuilder.ID);
        sensAPIHandler = new SensAPIHandler();
        Set<String> sensSig = sensAPIHandler.getSensitiveMethods();
        vis = Maps.newMap(pdg.getNodes().size());
        for (String target : sensSig) {
            Stmt startNode = findInvokeInPDG(pdg, target);
            slicing(startNode, pdg, slice);
        }
        if (isDump) {
            CFGDumper.dumpDotFile(slice, dumpDir);
        }
        return slice;
    }

    private static void slicing(Stmt begin, CFG<Stmt> pdg, StmtCFG slice) {
        processSlice(begin, pdg, slice, true);
        processSlice(begin, pdg, slice, false);
    }

    private static void processSlice(Stmt begin, CFG<Stmt> pdg, StmtCFG slice, boolean isForward) {
        Queue<Stmt> workList = new SetQueue<>();

        workList.add(begin);
        vis.put(begin, false);

        while (!workList.isEmpty()) {
            Stmt curr = workList.poll();
            slice.addNode(curr);
            vis.put(curr, true);
            for (CFGEdge<Stmt> edge : (isForward ? pdg.getOutEdgesOf(curr) : pdg.getInEdgesOf(curr))) {
                Stmt neighbor = isForward ? edge.target() : edge.source();
                if (!slice.hasEdge(edge)) {
                    slice.addEdge(new CFGEdge<>(edge.getKind(), isForward ? curr : neighbor, isForward ? neighbor : curr));
                }
                if (!vis.getOrDefault(neighbor, false)) {
                    if (workList.contains(neighbor)) continue;
                    workList.add(neighbor);
                    vis.put(neighbor, false);
                }
            }
        }
    }

    private static Stmt findInvokeInPDG(CFG<Stmt> pdg, String signature) {
        for(Stmt node : pdg.getNodes()){
            if(node instanceof Invoke invoke){
                InvokeExp exp = invoke.getInvokeExp();
                MethodRef ref = exp instanceof InvokeDynamic ?
                        ((InvokeDynamic) exp).getBootstrapMethodRef():
                        exp.getMethodRef();
                if(ref.toString().equals(signature)){
                    return invoke;
                }
            }
        }
        return null;
    }
}
