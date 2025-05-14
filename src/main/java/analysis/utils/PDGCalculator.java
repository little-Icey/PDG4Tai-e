package pascal.taie.analysis.utils;

import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.ir.stmt.Stmt;

import java.util.ArrayList;
import java.util.List;

public class PDGCalculator {

    private int n;

    private List<Integer>[] g, ig, sds, df, cdg;

    private int[] dfn, pos, fa, sdom, idom;

    private CFG<Stmt> cfg;

    public PDGCalculator(CFG<Stmt> cfg) {
        this.cfg = cfg;
        this.n = cfg.getNumberOfNodes();
        dfn = new int[n + 1];
        pos = new int[n + 1];
        fa = new int[n + 1];
        sdom = new int[n + 1];
        idom = new int[n + 1];
        g = new ArrayList[n + 1];
        ig = new ArrayList[n + 1];
        sds = new ArrayList[n + 1];
        df = new ArrayList[n + 1];
        cdg = new ArrayList[n + 1];
        for (int i = 0; i < g.length; i++) {
            g[i] = new ArrayList<>();
            ig[i] = new ArrayList<>();
            sds[i] = new ArrayList<>();
            df[i] = new ArrayList<>();
            cdg[i] = new ArrayList<>();
        }
    }

    public void analyse() {
        transform();
        calcIdom(n, n);
        calcDominanceFrontier();
        calcControlDependence();
    }

    public List<Integer>[] getCDG() {
        return this.cdg;
    }

    private void transform() {
        cfg.getNodes().forEach(node -> {
            cfg.getOutEdgesOf(node).forEach(e -> {
                // The index in cfg starts from 0, but the index of the algorithm starts from 1
                int src = cfg.getIndex(e.source())+1;
                int dst = cfg.getIndex(e.target())+1;
                g[dst].add(src);
                ig[src].add(dst);
            });
        });
        g[this.n].add(1); // Indexes in the algorithm start from 1
        ig[1].add(this.n);
    }

    private int dfnMin(int x, int y) {
        return (dfn[x] < dfn[y]) ? x : y;
    }

    private void dfs(int k) {
        dfn[k] = ++dfn[0];
        pos[dfn[0]] = k;
        for (int i = 0; i < g[k].size(); i++) {
            if (dfn[g[k].get(i)] == 0) {
                fa[g[k].get(i)] = k;
                dfs(g[k].get(i));
            }
        }
    }

    private void calcIdom(int source, int n) {
        dfs(source);
        int nodeCnt = dfn[0];
        ModifierDisjointSetUnion dsu = new ModifierDisjointSetUnion(nodeCnt);

        for (int i = nodeCnt; i > 1; i--) {
            int v = pos[i];
            for (int vpre : ig[v]) {
                if (dfn[vpre] < i) sdom[v] = dfnMin(sdom[v], vpre);
                else sdom[v] = dfnMin(sdom[v], sdom[dsu.query(vpre, dfn, sdom)]);
            }
            sds[sdom[v]].add(v);
            for (int x : sds[v]) {
                // 保存(sdom(x)=v,x] 之间sdom最小的u
                idom[x] = dsu.query(x, dfn, sdom);
            }
            dsu.update(v, fa);
        }

        for (int i = 2; i <= nodeCnt; i++) {
            int v = pos[i], u = idom[v];
            if (sdom[u] == sdom[v]) idom[v] = sdom[v];
            else idom[v] = idom[u];
        }
    }

    private void calcDominanceFrontier() {
        for (int i = 1; i <= n; i++) {
            if (idom[i] == 0) {
                idom[i] = n;
            }
        }
        for (int i = 1; i <= n; i++) {
            List<Integer> predecessors = ig[i];
            if (predecessors.size() > 1) { // node has multiple predecessors
                for (int p : predecessors) {
                    int runner = p;
                    while (runner != idom[i]) {
                        if (!df[runner].contains(i)) {
                            df[runner].add(i);
                        }
                        runner = idom[runner];
                    }
                }
            }
        }
    }

    private void calcControlDependence() {
        for (int y = 1; y <= n; y++) {
            for (int x : df[y]) {
                cdg[x].add(y);
            }
        }
    }
}
