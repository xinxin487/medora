package com.medora.knowledge.retriever;

import java.util.*;

public class ReciprocalRankFusion {
    public List<RankedCandidate> fuse(List<RankedCandidate> keyword, List<RankedCandidate> vector, int k) {
        int constant = Math.max(1, k); Map<Long, double[]> scores = new LinkedHashMap<>();
        add(scores, keyword, constant, true); add(scores, vector, constant, false);
        return scores.entrySet().stream().map(e -> new RankedCandidate(e.getKey(), (int)e.getValue()[0], (int)e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingDouble(RankedCandidate::rrfScore).reversed().thenComparing(RankedCandidate::chunkId)).toList();
    }
    private void add(Map<Long,double[]> scores, List<RankedCandidate> list, int k, boolean keyword) {
        if (list == null) return; for (int i=0;i<list.size();i++) { long id=list.get(i).chunkId(); int rank=i+1; double[] value=scores.computeIfAbsent(id, ignored -> new double[]{0,0,0}); value[keyword?0:1]=rank; value[2]+=1.0/(k+rank); }
    }
}
