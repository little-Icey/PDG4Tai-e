package pascal.taie.analysis.graph.icfg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.ProgramAnalysis;
import pascal.taie.analysis.blackcat.SensAPIHandler;
import pascal.taie.analysis.graph.callgraph.CallGraph;
import pascal.taie.analysis.graph.callgraph.CallGraphPartitioning;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGEdge;
import pascal.taie.analysis.graph.cfg.SinglePDGBuilder;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.Indexer;
import pascal.taie.util.SimpleIndexer;
import pascal.taie.util.graph.DotAttributes;
import pascal.taie.util.graph.DotDumper;

import java.io.File;
import java.util.Set;

public class ChoppedIPDGBuilder extends ProgramAnalysis<ICFG<JMethod, Stmt>> {

    public static final String ID = "ipdg";

    private static final Logger logger = LogManager.getLogger(ChoppedIPDGBuilder.class);

    private static final String FILE_DIR = "chopped-ipdg";

    private static final String TEST_DIR = "chopped-ipdg-test";

    private final boolean isTest;

    private final boolean isDump;

    private final int sliceIter;

    private final File dumpDir;

    private final String[] acp;

    private final SensAPIHandler handler;

    public ChoppedIPDGBuilder(AnalysisConfig config) {
        super(config);
        isDump = getOptions().getBoolean("dump");
        isTest = getOptions().getBoolean("test");
        sliceIter = getOptions().getInt("slice-iteration");
        acp = getAppClassPath();
        handler = new SensAPIHandler();
        if (isDump) {
            String fileDir = isTest ? TEST_DIR : FILE_DIR;
            File dir = new File(World.get().getOptions().getOutputDir(), fileDir);
            dumpDir = new File(dir, acp[acp.length - 2]);
            if (!dumpDir.exists()) {
                dumpDir.mkdirs();
            }
        } else {
            dumpDir = null;
        }
    }

    /**
     * 对每一个调用图的子图进行过程间控制流图的生成
     * @return
     */
    @Override
    public ICFG<JMethod, Stmt> analyze() {
        Set<CallGraph<Stmt, JMethod>> subgraphSet = World.get().getResult(CallGraphPartitioning.ID);
        if (!subgraphSet.isEmpty()) {
            int idx = 0;
            for (CallGraph<Stmt, JMethod> subgraph : subgraphSet) {
                ICFG<JMethod, Stmt> slicedIPDG = new ChoppedDefaultIPDG(subgraph, sliceIter);
                if (isDump) {
                    dumpIPDG(slicedIPDG, idx);
                }
                idx++;
            }
        } else {
            logger.info("No sensitive subgraph in this jar, skip it");
        }
        return null;
    }

    private void dumpIPDG(ICFG<JMethod, Stmt> ipdg, int idx) {
        String fileName = acp[acp.length - 1] + "-{" + idx + "}-" + "slice.dot";
        File dotFile = new File(dumpDir, fileName);
        logger.info("Dumping program slice to {}", dotFile.getAbsolutePath());
        Indexer<Stmt> indexer = new SimpleIndexer<>();
        new DotDumper<Stmt>()
                .setNodeToString(n -> Integer.toString(indexer.getIndex(n)))
                .setNodeLabeler(n -> toLabel(n, ipdg))
                .setGlobalNodeAttributes(DotAttributes.of("shape", "box",
                        "style", "filled", "color", "\".3 .2 1.0\""))
                .setEdgeLabeler(e -> {
                    if (e instanceof CallEdge) {
                        return "CALL";
                    } else if (e instanceof ReturnEdge) {
                        return "RETURN";
                    } else if (e instanceof CallToReturnEdge) {
                        return "CALL2RET";
                    } else {
                        NormalEdge<Stmt> normalEdge = (NormalEdge<Stmt>) e;
                        CFGEdge<Stmt> edge = normalEdge.getCFGEdge();
                        return edge.getKind().toString();
                    }
                })
                .setEdgeAttributer(e -> {
                    if (e instanceof CallEdge) {
                        return DotAttributes.of("style", "dashed", "color", "blue");
                    } else if (e instanceof ReturnEdge) {
                        return DotAttributes.of("style", "dashed", "color", "red");
                    } else {
                        return DotAttributes.of();
                    }
                })
                .dump(ipdg, dotFile);
    }

    private String toLabel(Stmt stmt, ICFG<JMethod, Stmt> ipdg) {
        JMethod method = ipdg.getContainingMethodOf(stmt);
        CFG<Stmt> pdg = getPDGOf(method);
        return toLabel0(stmt, pdg, handler);
    }

    /**
     * 增加节点的相关信息，例如语句类型、是否为敏感API、敏感API类型等信息
     * @param node 节点
     * @param pdg 程序依赖图
     * @param handler 敏感API处理器
     * @return
     * @param <N>
     */
    private <N> String toLabel0(N node, CFG<N> pdg, SensAPIHandler handler) {
        if (pdg.isEntry(node)) {
            return "Entry" + pdg.getMethod();
        } else if (pdg.isExit(node)) {
            return "Exit" + pdg.getMethod();
        } else {
            // 增加语句类型
            String subCategory = "no";
            if (node instanceof Invoke invoke) {
                if (handler.isSensitive(invoke)) {
                    subCategory = handler.getAPIShortCode(
                            handler.getMethodRef(invoke).toString()
                    );
                }
            }
            String prefix = node.getClass().getSimpleName() + "-StmtType-"
                    + subCategory + "-SensType-"
                    + ((Stmt) node).getIndex() + ": ";
            return node instanceof Stmt ?
                    prefix + node.toString().replace("\"", "\\\"") :
                    node.toString();
//            return node instanceof Stmt ?
//                    node.getClass().getSimpleName() + "-ST-" + ((Stmt) node).getIndex() + ": " + node.toString().replace("\"", "\\\""):
//                    node.toString();
        }
    }

    static CFG<Stmt> getPDGOf(JMethod method) {
        // not user-defined method
        if (method.getDeclaringClass().isApplication()) {
            try {
                return SinglePDGBuilder.analyze(method.getIR());
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.error("Index error, may be in ModifierDisjointSetUnion, skip this method " + e);
                return null;
            }
        } else {
            return null;
        }
    }

    private static String[] getAppClassPath() {
        return World.get().getOptions().getAppClassPath().get(0).split("\\\\|/");
    }
}
