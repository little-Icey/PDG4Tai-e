package pascal.taie.analysis.graph.icfg;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.graph.callgraph.CallGraph;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGEdge;
import pascal.taie.analysis.blackcat.SensAPIHandler;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Return;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.util.collection.*;

import java.util.*;
import java.util.stream.Collectors;

import static pascal.taie.analysis.graph.icfg.ChoppedIPDGBuilder.getPDGOf;

class ChoppedDefaultIPDG extends AbstractICFG<JMethod, Stmt>{

    private static final Logger logger = LogManager.getLogger(ChoppedDefaultIPDG.class);

    private final MultiMap<Stmt, ICFGEdge<Stmt>> inEdges = Maps.newMultiMap();

    private final MultiMap<Stmt, ICFGEdge<Stmt>> outEdges = Maps.newMultiMap();

    private final Map<Stmt, CFG<Stmt>> stmtToPDG = Maps.newLinkedHashMap();

    private final MultiMap<Stmt, ICFGEdge<Stmt>> sliceInEdges = Maps.newMultiMap();

    private final MultiMap<Stmt, ICFGEdge<Stmt>> sliceOutEdges = Maps.newMultiMap();

    private static Map<Stmt, Boolean> vis;


    ChoppedDefaultIPDG(CallGraph<Stmt, JMethod> callGraph, int sliceIter) {
        super(callGraph);
        build(callGraph);
        Set<String> sensSig = new SensAPIHandler().getSensitiveMethods();
        vis = Maps.newMap(getNodes().size());
        for (String target : sensSig) {
            Stmt startNode = findInvokeInPDG(target);
            if (startNode != null) {
                slicing(startNode, sliceIter);
            }
        }
        inEdges.clear();
        outEdges.clear();
        sliceInEdges.forEach(inEdges::put);
        sliceOutEdges.forEach(outEdges::put);
        Map<Stmt, CFG<Stmt>> tmp = stmtToPDG.entrySet().stream()
                .filter(entry -> vis.containsKey(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        stmtToPDG.clear();
        stmtToPDG.putAll(tmp);
    }

    /**
     * 构建这个调用图子图的ICFG
     * @param callGraph 调用图子图
     */
    private void build(CallGraph<Stmt, JMethod> callGraph) {
        callGraph.forEach(method -> {
            CFG<Stmt> pdg = getPDGOf(method);
            if (pdg == null) {
//                logger.warn("PDG of {} is absent, try to fix this" +
//                        " by adding option: -scope REACHABLE", method);
                return;
            }
            pdg.forEach(stmt -> {
                stmtToPDG.put(stmt, pdg);
                pdg.getOutEdgesOf(stmt).forEach(edge -> {
                    ICFGEdge<Stmt> local = new NormalEdge<>(edge); // intra control dependence
                    outEdges.put(stmt, local);
                    inEdges.put(edge.target(), local);
                });
                if (isCallSite(stmt)) {
                    getCalleesOf(stmt).forEach(callee -> {
                        if (getPDGOf(callee) == null) {
//                            logger.warn("PDG of {} is missing", callee);
                            return;
                        }
                        // Add inter control dependence
                        Stmt entry = getEntryOf(callee);
                        CallEdge<Stmt> call = new CallEdge<>(stmt, entry, callee); // call edge表示函数入口依赖于调用该函数的语句
                        outEdges.put(stmt, call);
                        inEdges.put(entry, call);
                        // 处理调用后返回的情况
                        Stmt exit = getExitOf(callee);
                        Set<Var> retVars = Sets.newHybridSet();
                        Set<ClassType> exceptions = Sets.newHybridSet();
                        // The exit node of CFG is mock, thus it is not
                        // a real return or excepting Stmt. We need to
                        // collect return and exception information from
                        // the real return and excepting Stmts, and attach
                        // them to the ReturnEdge.
                        getPDGOf(callee).getInEdgesOf(exit).forEach(retEdge -> {
                            if (retEdge.getKind() == CFGEdge.Kind.RETURN) {
                                Return ret = (Return) retEdge.source();
                                if (ret.getValue() != null) {
                                    retVars.add(ret.getValue());
                                }
                            }
                            if (retEdge.isExceptional()) {
                                exceptions.addAll(retEdge.getExceptions());
                            }
                        });
                        getReturnSitesOf(stmt).forEach(retSite -> {
                            ReturnEdge<Stmt> ret = new ReturnEdge<>(
                                    exit, retSite, stmt, retVars, exceptions);
                            outEdges.put(exit, ret);
                            inEdges.put(retSite, ret);
                        });
                    });
                }
            });
        });
    }

    private void slicing(Stmt begin, int sliceIter) {
        processSlice(begin, true, sliceIter);
        processSlice(begin, false, sliceIter);
    }

    private void processSlice(Stmt begin, boolean isForward, int sliceIter) {
        Queue<Stmt> workList = new ArrayDeque<>();
        workList.add(begin);
        vis.put(begin, false);

        int currLevel = 0;
        while (!workList.isEmpty() && currLevel <= sliceIter) {
            Stmt curr = workList.poll();
            vis.put(curr, true);
            for (ICFGEdge<Stmt> edge : (isForward ? getOutEdgesOf(curr) : getInEdgesOf(curr))) {
                Stmt neighbor = isForward ? edge.target() : edge.source();
                if (isForward) { // curr --> neighbor
                    if (!sliceInEdges.get(neighbor).contains(edge) && !sliceOutEdges.get(curr).contains(edge)) {
                        sliceInEdges.put(neighbor, edge);
                        sliceOutEdges.put(curr, edge);
                    }
                } else { // curr <-- neighbor
                    if (!sliceInEdges.get(curr).contains(edge) && !sliceOutEdges.get(neighbor).contains(edge)) {
                        sliceInEdges.put(curr, edge);
                        sliceOutEdges.put(neighbor, edge);
                    }
                }
                if (!vis.getOrDefault(neighbor, false)) {
                    if (workList.contains(neighbor)) continue;
                    workList.add(neighbor);
                    vis.put(neighbor, false);
                }
            }
            if (sliceIter != 114514) { // 切片上下文不为无穷
                currLevel++;
            }
        }
    }

    @Override
    public Set<ICFGEdge<Stmt>> getInEdgesOf(Stmt stmt) {
        return inEdges.get(stmt);
    }

    @Override
    public Set<ICFGEdge<Stmt>> getOutEdgesOf(Stmt stmt) {
        return outEdges.get(stmt);
    }

    @Override
    public Stmt getEntryOf(JMethod method) {
        return getPDGOf(method).getExit();
    }

    @Override
    public Stmt getExitOf(JMethod method) {
        return getPDGOf(method).getExit();
    }

    @Override
    public Set<Stmt> getReturnSitesOf(Stmt callSite) {
        assert isCallSite(callSite);
        return stmtToPDG.get(callSite).getSuccsOf(callSite);
    }

    @Override
    public JMethod getContainingMethodOf(Stmt stmt) {
        return stmtToPDG.get(stmt).getMethod();
    }

    @Override
    public boolean isCallSite(Stmt stmt) {
        return stmt instanceof Invoke;
    }

    @Override
    public boolean hasEdge(Stmt source, Stmt target) {
        return getOutEdgesOf(source)
                .stream()
                .anyMatch(edge -> edge.target().equals(target));
    }

    @Override
    public Set<Stmt> getPredsOf(Stmt stmt) {
        return Views.toMappedSet(getInEdgesOf(stmt), ICFGEdge::source);
    }

    @Override
    public Set<Stmt> getSuccsOf(Stmt stmt) {
        return Views.toMappedSet(getOutEdgesOf(stmt), ICFGEdge::target);
    }

    @Override
    public Set<Stmt> getNodes() {
        return Collections.unmodifiableSet(stmtToPDG.keySet());
    }

    public Stmt findInvokeInPDG(String signature) {
        for (Stmt node : getNodes()) {
            if (node instanceof Invoke invoke) {
                InvokeExp exp = invoke.getInvokeExp();
                MethodRef ref = exp instanceof InvokeDynamic ?
                        ((InvokeDynamic) exp).getBootstrapMethodRef():
                        exp.getMethodRef();
                if (ref.toString().equals(signature)) {
                    return invoke;
                }
            }
        }
        return null;
    }
}
