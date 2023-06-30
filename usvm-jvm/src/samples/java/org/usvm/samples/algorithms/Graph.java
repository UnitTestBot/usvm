package org.usvm.samples.algorithms;

public class Graph {
    private int size;
    private int[][] adj;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int[][] getAdj() {
        return adj;
    }

    public void setAdj(int[][] adj) {
        this.adj = adj;
    }

    private boolean isCyclic(int i, boolean[] visited, boolean[] recStack) {
        if (recStack[i])
            return true;

        if (visited[i])
            return false;

        visited[i] = true;

        recStack[i] = true;
        int[] children = adj[i];

        for (Integer c : children)
            if (c == 1) {
                if (isCyclic(c, visited, recStack)) {
                    return true;
                }
            }

        recStack[i] = false;

        return false;
    }

    public void addEdge(int source, int dest) {
        adj[source][dest] = 1;
    }

    public boolean isCyclic() {
        boolean[] visited = new boolean[size];
        boolean[] recStack = new boolean[size];

        for (int i = 0; i < size; i++)
            if (isCyclic(i, visited, recStack))
                return true;

        return false;
    }
}