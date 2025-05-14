package pascal.taie.analysis.graph.cfg;

import pascal.taie.World;
import pascal.taie.analysis.blackcat.SensAPIHandler;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.Indexer;
import pascal.taie.util.SimpleIndexer;
import pascal.taie.util.graph.DotAttributes;
import pascal.taie.util.graph.DotDumper;

import java.io.File;
import java.util.stream.Collectors;

public class SliceDumper {

    private static final int FILENAME_LIMIT = 200;

    static <N> void dumpDotFile(CFG<N> cfg, File dumpDir, SensAPIHandler handler) {
        Indexer<N> indexer = new SimpleIndexer<>();
        new DotDumper<N>()
                .setNodeToString(n -> Integer.toString(indexer.getIndex(n)))
                .setNodeLabeler(n -> toLabel(n, cfg, handler))
                .setGlobalNodeAttributes(DotAttributes.of("shape", "box",
                        "style", "filled", "color", "\".3 .2 1.0\""))
                .setEdgeLabeler(e -> {
                    CFGEdge<N> edge = (CFGEdge<N>) e;
                    if (edge.isSwitchCase()) {
                        return edge.getKind() +
                                "\n[case " + edge.getCaseValue() + "]";
                    } else if (edge.isExceptional()) {
                        return edge.getKind() + "\n" +
                                edge.getExceptions()
                                        .stream()
                                        .map(t -> t.getJClass().getSimpleName())
                                        .toList();
                    } else {
                        return edge.getKind().toString();
                    }
                })
                .setEdgeAttributer(e -> {
                    if (((CFGEdge<N>) e).isExceptional()) {
                        return DotAttributes.of("color", "red");
                    } else {
                        return DotAttributes.of();
                    }
                })
                .dump(cfg, new File(dumpDir, toDotFileName(cfg)));
    }

    /**
     * 在导出dot文件时增加PDG节点的相关信息，例如语句类型、是否为敏感API、敏感API类型等
     * 方便后续特征提取
     */
    public static <N> String toLabel(N node, CFG<N> cfg, SensAPIHandler handler) {
        if (cfg.isEntry(node)) {
            return "Entry" + cfg.getMethod();
        } else if (cfg.isExit(node)) {
            return "Exit" + cfg.getMethod();
        } else {
            // 增加语句类型，例如invoke, AssignLiteral
            String subCategory = "no";
            if (node instanceof Invoke invoke) {
                if (handler.isSensitive(invoke)) {
                    subCategory = handler.getAPIShortCode(
                            handler.getMethodRef(invoke).toString()
                    );
                }
            }
            String prefix = node.getClass().getSimpleName() + "-StmtType-"
                    + subCategory +"-SensType-"
                    +((Stmt) node).getIndex() + ": ";
            return node instanceof Stmt ?
                    prefix + node.toString().replace("\"", "\\\"") :
                    node.toString();
        }
    }

    private static String toDotFileName(CFG<?> cfg) {
        JMethod m = cfg.getMethod();
        String[] acp = World.get().getOptions().getAppClassPath().get(0).split("\\\\|/");
        String prefix = acp[acp.length-1].replace(".jar", "-");
        String fileName = prefix +
                String.valueOf(m.getDeclaringClass()) + '.' +
                m.getName() + '(' +
                m.getParamTypes()
                        .stream()
                        .map(Type::getName)
                        .collect(Collectors.joining(",")) +
                ')';
        if (fileName.length() > FILENAME_LIMIT) {
            fileName = fileName.substring(0, FILENAME_LIMIT) + "...";
        }
        // escape invalid characters in file name
        fileName = fileName.replaceAll("[\\[\\]<>]", "_") + ".dot";
        return fileName;
    }
}
