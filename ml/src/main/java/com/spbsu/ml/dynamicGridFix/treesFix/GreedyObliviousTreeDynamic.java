package com.spbsu.ml.dynamicGridFix.treesFix;

import com.spbsu.commons.func.AdditiveStatistics;
import com.spbsu.commons.util.ArrayTools;
import com.spbsu.ml.Binarize;
import com.spbsu.ml.data.set.VecDataSet;
import com.spbsu.ml.dynamicGridFix.AggregateDynamic;
import com.spbsu.ml.dynamicGridFix.implFix.BFDynamicGrid;
import com.spbsu.ml.dynamicGridFix.implFix.BinarizedDynamicDataSet;
import com.spbsu.ml.dynamicGridFix.interfacesFix.BinaryFeature;
import com.spbsu.ml.dynamicGridFix.interfacesFix.DynamicGrid;
import com.spbsu.ml.dynamicGridFix.modelsFix.ObliviousTreeDynamicBin;
import com.spbsu.ml.loss.StatBasedLoss;
import com.spbsu.ml.methods.VecOptimization;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by noxoomo on 22/07/14.
 */
public class GreedyObliviousTreeDynamic<Loss extends StatBasedLoss> extends VecOptimization.Stub<Loss> {
  private final int depth;
  private DynamicGrid grid;
  private boolean growGrid = true;
  //  private final int minSplits;
  private final double lambda;
  private static double eps = 1e-4;


  public GreedyObliviousTreeDynamic(DynamicGrid grid, int depth) {
    this.depth = depth;
    this.grid = grid;
//    minSplits = 1;
    lambda = 1;
  }

  public GreedyObliviousTreeDynamic(VecDataSet ds, int depth) {
    this(ds, depth, 0, 1);
  }

  public GreedyObliviousTreeDynamic(VecDataSet ds, int depth, double lambda) {
    this(ds, depth, lambda, 1);
  }

  public GreedyObliviousTreeDynamic(VecDataSet ds, int depth, double lambda, int minSplits) {
//    this.minSplits = minSplits;
    this.depth = depth;
    this.lambda = lambda;
    this.grid = new BFDynamicGrid(ds, minSplits);
  }


  public void stopGrowing() {
    this.growGrid = false;
  }

  public ObliviousTreeDynamicBin fit(VecDataSet ds, final Loss loss) {
    BinarizedDynamicDataSet bds = ds.cache().cache(Binarize.class, VecDataSet.class).binarize(grid);

    List<BFDynamicOptimizationSubset> leaves = new ArrayList<>(1 << depth);
    TIntArrayList nonActiveF = new TIntArrayList(grid.rows());
    TIntArrayList nonActiveBin = new TIntArrayList(grid.rows());
    final List<BinaryFeature> conditions = new ArrayList<>(depth);
    final double[][] scores = new double[grid.rows()][];
    for (int i = 0; i < scores.length; ++i) {
      scores[i] = new double[0];
    }

    while (true) {
      boolean updated = false;
      leaves.clear();
      conditions.clear();
      leaves.add(new BFDynamicOptimizationSubset(bds, loss, ArrayTools.sequence(0, ds.length())));
      double currentScore = Double.POSITIVE_INFINITY;

      for (int level = 0; level < depth; level++) {
        for (int f = 0; f < scores.length; ++f) {
          if (scores[f].length != grid.row(f).size()) {
            scores[f] = new double[grid.row(f).size()];
          } else Arrays.fill(scores[f], 0);
        }


        for (BFDynamicOptimizationSubset leaf : leaves) {
          leaf.visitAllSplits(new AggregateDynamic.SplitVisitor<AdditiveStatistics>() {
            @Override
            public void accept(BinaryFeature bf, AdditiveStatistics left, AdditiveStatistics right) {
              final double leftScore = loss.score(left);
              final double rightScore = loss.score(right);
              scores[bf.fIndex()][bf.binNo()] += leftScore + rightScore;
            }
          });
        }

        int bestSplitF = -1;
        int bestSplitBin = -1;
        double bestSplitScore = Double.POSITIVE_INFINITY;

        int bestNonActiveSplitF = -1;
        int bestNonActiveSplitBin = -1;
        double bestNonActiveSplitScore = Double.POSITIVE_INFINITY;
        nonActiveF.clear();
        nonActiveBin.clear();
        for (int f = 0; f < scores.length; ++f) {
          for (int bin = 0; bin < scores[f].length; ++bin) {
            BinaryFeature bf = grid.bf(f, bin);
            if (bf.isActive()) {
              if (bestSplitScore > scores[f][bin]) {
                bestSplitF = f;
                bestSplitBin = bin;
                bestSplitScore = scores[f][bin];
              }
            } else {
              nonActiveF.add(f);
              nonActiveBin.add(bin);
            }
          }
        }
        if (growGrid) {
          double threshold = bestSplitScore < currentScore ? bestSplitScore : currentScore;
          for (int j = 0; j < nonActiveF.size(); ++j) {
            int feature = nonActiveF.get(j);
            int bin = nonActiveBin.get(j);
            BinaryFeature bf = grid.bf(feature, bin);
            double reg = lambda != 0 ? bf.regularization() : 0;
            final double score = threshold - scores[feature][bin] - lambda * reg;
            if (score > eps) {
              bds.queueSplit(bf);
              if (bestNonActiveSplitScore > scores[feature][bin]) {
                bestNonActiveSplitF = feature;
                bestNonActiveSplitBin = bin;
                bestNonActiveSplitScore = scores[feature][bin];
              }
            }
          }
        }

        if (bestNonActiveSplitScore <= bestSplitScore) {
          bestSplitF = bestNonActiveSplitF;
          bestSplitBin = bestNonActiveSplitBin;
        }


        //tree growing continue
        if (bestSplitF < 0 || scores[bestSplitF][bestSplitBin] >= currentScore) {
          if (growGrid) {
            if (bds.acceptQueue(leaves)) {
              updated = true;
            }
          }
          break;
        }
        final BinaryFeature bestSplitBF = grid.bf(bestSplitF, bestSplitBin);
        final List<BFDynamicOptimizationSubset> next = new ArrayList<>(leaves.size() * 2);
        final ListIterator<BFDynamicOptimizationSubset> iter = leaves.listIterator();
        while (iter.hasNext()) {
          final BFDynamicOptimizationSubset subset = iter.next();
          next.add(subset);
          next.add(subset.split(bestSplitBF));
        }
        conditions.add(bestSplitBF);
        leaves = next;
        currentScore = scores[bestSplitF][bestSplitBin];
        if (growGrid) {
          if (bds.acceptQueue(leaves)) {
            updated = true;
          }
        }
      }

//      updated = false;
      if (!updated) {
        double[] values = new double[leaves.size()];
        for (int i = 0; i < values.length; i++) {
          values[i] = loss.bestIncrement(leaves.get(i).total());
        }
//        for (BinaryFeature bf : conditions) {
//          bf.use();
//        }
        return new ObliviousTreeDynamicBin(conditions, values);
      }
    }

  }

  public int[] hist() {
    return grid.hist();
  }
}