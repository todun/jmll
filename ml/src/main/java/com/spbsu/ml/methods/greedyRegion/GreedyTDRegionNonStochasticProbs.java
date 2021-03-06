package com.spbsu.ml.methods.greedyRegion;

import com.spbsu.commons.func.AdditiveStatistics;
import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.random.FastRandom;
import com.spbsu.commons.util.ArrayTools;
import com.spbsu.commons.util.Pair;
import com.spbsu.ml.BFGrid;
import com.spbsu.ml.Binarize;
import com.spbsu.ml.data.Aggregate;
import com.spbsu.ml.data.impl.BinarizedDataSet;
import com.spbsu.ml.data.set.VecDataSet;
import com.spbsu.ml.loss.L2;
import com.spbsu.ml.loss.StatBasedLoss;
import com.spbsu.ml.loss.WeightedLoss;
import com.spbsu.ml.methods.VecOptimization;
import com.spbsu.ml.methods.trees.BFOptimizationSubset;
import com.spbsu.ml.models.Region;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 15.11.12
 * Time: 15:19
 */
public class GreedyTDRegionNonStochasticProbs<Loss extends StatBasedLoss> extends VecOptimization.Stub<Loss> {
  protected final BFGrid grid;
  int maxFailed = 1;
  private double alpha = 0.08;
  private double beta = 1.1;
  private final FastRandom rand = new FastRandom();

  public GreedyTDRegionNonStochasticProbs(final BFGrid grid) {
    this.grid = grid;
  }

  public GreedyTDRegionNonStochasticProbs(final BFGrid grid, final double alpha, final double beta) {
    this(grid);
    this.alpha = alpha;
    this.beta = beta;
  }

  Pair<BFGrid.BinaryFeature[], boolean[]> initFit(final VecDataSet learn, final Loss loss) {
    final BFOptimizationSubset current;
    final BinarizedDataSet bds = learn.cache().cache(Binarize.class, VecDataSet.class).binarize(grid);
    current = new BFOptimizationSubset(bds, loss, ArrayTools.sequence(0, learn.length()));
    final double[] bestRowScores = new double[grid.rows()];
    for (int i = 0; i < bestRowScores.length; ++i) {
      bestRowScores[i] = Double.POSITIVE_INFINITY;
    }
    final BFGrid.BinaryFeature[] bestRowFeatures = new BFGrid.BinaryFeature[grid.rows()];
    final boolean[] masks = new boolean[grid.rows()];

    current.visitAllSplits(new Aggregate.SplitVisitor<AdditiveStatistics>() {
      @Override
      public void accept(final BFGrid.BinaryFeature bf, final AdditiveStatistics left, final AdditiveStatistics right) {
        final double leftScore = logScore(left);
        final double rightScore = logScore(right);
        final double bestScore = leftScore > rightScore ? rightScore : leftScore;

        if (bestScore < bestRowScores[bf.findex]) {
          bestRowScores[bf.findex] = bestScore;
          masks[bf.findex] = leftScore > rightScore;
          bestRowFeatures[bf.findex] = bf;
        }
      }
    });


    final boolean[] resultMasks = new boolean[maxFailed];
    final BFGrid.BinaryFeature[] resultFeatures = new BFGrid.BinaryFeature[maxFailed];

    for (int i = 0; i < maxFailed; ) {
      final boolean[] used = new boolean[bestRowScores.length];
      final int index = rand.nextInt(bestRowScores.length);
      if (bestRowScores[index] < Double.POSITIVE_INFINITY && !used[index]) {
        used[index] = true;
        final BFGrid.BinaryFeature feature = bestRowFeatures[index];
        final boolean mask = masks[index];
        resultFeatures[i] = feature;
        resultMasks[i] = mask;
        ++i;
      }
    }
    return new Pair<>(resultFeatures, resultMasks);
  }

  @Override
  public Region fit(final VecDataSet learn, final Loss loss) {
//    maxFailed = rand.nextInt(4);
    final List<BFGrid.BinaryFeature> conditions = new ArrayList<>(32);
    final boolean[] usedBF = new boolean[grid.size()];
    final List<Boolean> mask = new ArrayList<>();
    final TDoubleArrayList conditionSum = new TDoubleArrayList(32);
    final TDoubleArrayList conditionTotal = new TDoubleArrayList(32);
    final Pair<BFGrid.BinaryFeature[], boolean[]> init = initFit(learn, loss);
    for (int i = 0; i < init.first.length; ++i) {
      conditions.add(init.first[i]);
      usedBF[init.first[i].bfIndex] = true;
      mask.add(init.second[i]);
    }
    final BinarizedDataSet bds = learn.cache().cache(Binarize.class, VecDataSet.class).binarize(grid);
    double currentScore = Double.POSITIVE_INFINITY;
    final BFWeakConditionsStochasticOptimizationRegion current =
            new BFWeakConditionsStochasticOptimizationRegion(bds, loss, ArrayTools.sequence(0, learn.length()), init.first, init.second, maxFailed);
    current.alpha = alpha;
    current.beta = beta;
    final BFWeakConditionsOptimizationRegion determenisticCurrent =
            new BFWeakConditionsOptimizationRegion(bds, loss, ArrayTools.sequence(0, learn.length()), init.first, init.second, maxFailed);

    final double[] scores = new double[grid.size()];
    final boolean[] isRight = new boolean[grid.size()];
    final double[] weights = new double[grid.size() * 2];


    while (true) {
      final double total = weight(determenisticCurrent.total());
      final double[] csum = new double[conditionSum.size() + 1];
      final double[] ctotal = new double[conditionSum.size() + 1];
      for (int i = 0; i < csum.length - 1; ++i) {
        csum[i] = conditionSum.get(i);
        ctotal[i] = conditionTotal.get(i);
      }

      determenisticCurrent.visitAllSplits(new Aggregate.SplitVisitor<AdditiveStatistics>() {
        @Override
        public void accept(final BFGrid.BinaryFeature bf, final AdditiveStatistics left, final AdditiveStatistics right) {
          final AdditiveStatistics leftIn = (AdditiveStatistics) loss.statsFactory().create();
          final AdditiveStatistics rightIn = (AdditiveStatistics) loss.statsFactory().create();
          leftIn.append(left);
          leftIn.append(current.nonCriticalTotal);
          rightIn.append(right);
          rightIn.append(current.nonCriticalTotal);
          final double leftWeight = weight(leftIn);
          final double rightWeight = weight(rightIn);
          weights[bf.bfIndex * 2] = leftWeight;
          weights[bf.bfIndex * 2 + 1] = rightWeight;
        }
      });


      current.visitAllSplits(new Aggregate.SplitVisitor<AdditiveStatistics>() {
        @Override
        public void accept(final BFGrid.BinaryFeature bf, final AdditiveStatistics left, final AdditiveStatistics right) {
          if (usedBF[bf.bfIndex]) {
            scores[bf.bfIndex] = Double.POSITIVE_INFINITY;
          } else {
            double leftScore = Double.POSITIVE_INFINITY;
            {
              csum[csum.length - 1] = weights[bf.bfIndex * 2];
              ctotal[csum.length - 1] = total;
              final double prob = estimate(csum, ctotal);
              final AdditiveStatistics out = (AdditiveStatistics) loss.statsFactory().create();
              final AdditiveStatistics in = (AdditiveStatistics) loss.statsFactory().create();
              out.append(right);
              out.append(current.excluded);
              in.append(current.nonCriticalTotal);
              in.append(left);
              leftScore = (1 - prob) * outScore(out) + prob * inScore(in);// + 2 * ((1-prob) * Math.log(1-prob) + prob * Math.log(prob));
//              leftScore = score(out) +  score(in);// + 2 * ((1-prob) * Math.log(1-prob) + prob * Math.log(prob));
//              leftScore =inScore(in);
            }

            double rightScore = Double.POSITIVE_INFINITY;
            {
              csum[csum.length - 1] = weights[bf.bfIndex * 2 + 1];
              ctotal[csum.length - 1] = total;
              final double prob = estimate(csum, ctotal);
              final AdditiveStatistics out = (AdditiveStatistics) loss.statsFactory().create();
              final AdditiveStatistics in = (AdditiveStatistics) loss.statsFactory().create();
              out.append(left);
              out.append(current.excluded);
              in.append(current.nonCriticalTotal);
              in.append(right);
              rightScore = (1 - prob) * outScore(out) + prob * inScore(in);// + 2 * ((1-prob) * Math.log(1-prob) + prob * Math.log(prob));
//              rightScore = score(out) +  score(in);
//              rightScore = inScore(in);
            }
            scores[bf.bfIndex] = leftScore > rightScore ? rightScore : leftScore;
            isRight[bf.bfIndex] = leftScore > rightScore;
          }
        }
      });

//

      final int bestSplit = ArrayTools.min(scores);
      if (bestSplit < 0)
        break;


      if ((scores[bestSplit] + 1e-9 >= currentScore))
        break;

      final BFGrid.BinaryFeature bestSplitBF = grid.bf(bestSplit);
      final boolean bestSplitMask = isRight[bestSplitBF.bfIndex];

      BFOptimizationSubset outRegion = current.split(bestSplitBF, bestSplitMask);
      if (outRegion == null) {
        break;
      }
      outRegion = determenisticCurrent.split(bestSplitBF, bestSplitMask);
      if (outRegion == null) {
        break;
      }

      conditions.add(bestSplitBF);
      conditionSum.add(isRight[bestSplitBF.bfIndex] ? weights[bestSplitBF.bfIndex * 2 + 1] : weights[bestSplitBF.bfIndex * 2]);
      conditionTotal.add(total);
      usedBF[bestSplitBF.bfIndex] = true;
      mask.add(bestSplitMask);
      currentScore = scores[bestSplit];
    }


    final boolean[] masks = new boolean[conditions.size()];
    for (int i = 0; i < masks.length; i++) {
      masks[i] = mask.get(i);
    }

//
    final Region region = new Region(conditions, masks, 1, 0, -1, currentScore, conditions.size() > maxFailed ? maxFailed : 0);
    final Vec target = loss.target();
    double sum = 0;
    double outSum = 0;
    double weight = 0;
    double outWeight = 0;

    for (int i = 0; i < bds.original().length(); ++i) {
      if (region.value(bds, i) == 1) {
        final double samplWeight = 1.0;// current.size() > 10 ? rand.nextPoisson(1.0) : 1.0;
        weight += samplWeight;
        sum += target.get(i) * samplWeight;
      } else {
        outSum += target.get(i);
        outWeight++;
      }
    }

    final double value = weight > 1 ? sum / weight : loss.bestIncrement(current.total());//loss.bestIncrement(inside);
//    double value = loss.bestIncrement(current.total());//loss.bestIncrement(inside);
    return new Region(conditions, masks, value, 0, -1, currentScore, conditions.size() > 1 ? maxFailed : 0);

  }

  private double estimate(final double[] sum, final double[] counts) {
    double p = 1;
    for (int i = 0; i < sum.length; ++i) {
      p *= sum[i] / counts[i];
    }
    return p;
//    double[] probs = MTA.bernoulliStein(sum, counts);
//    double p = 0;
//    for (int i = 0; i < sum.length; ++i) {
//      p += Math.log(probs[i]);
//    }
//    return Math.exp(p);
  }


  public static double weight(final AdditiveStatistics stats) {
    if (stats instanceof WeightedLoss.Stat) {
      return ((L2.MSEStats) ((WeightedLoss.Stat) stats).inside).weight;
    }
    if (stats instanceof L2.MSEStats) {
      return ((L2.MSEStats) stats).weight;
    }
    return 0;
  }

  public static double sum(final AdditiveStatistics stats) {
    if (stats instanceof WeightedLoss.Stat) {
      return ((L2.MSEStats) ((WeightedLoss.Stat) stats).inside).sum;
    }
    if (stats instanceof L2.MSEStats) {
      return ((L2.MSEStats) stats).sum;
    }
    return 0;
  }

  public static double sum2(final AdditiveStatistics stats) {
    if (stats instanceof WeightedLoss.Stat) {
      return ((L2.MSEStats) ((WeightedLoss.Stat) stats).inside).sum2;
    }
    if (stats instanceof L2.MSEStats) {
      return ((L2.MSEStats) stats).sum2;
    }
    return 0;
  }

  public double score(final AdditiveStatistics stats) {
    final double sum = sum(stats);
    final double sum2 = sum2(stats);
    final double weight = weight(stats);
    return weight > 2 ? (-sum * sum / weight) * weight * (weight - 2) / (weight * weight - 3 * weight + 1) * (1 + 2 * Math.log(weight + 1)) : 0;
//    return weight > 1 ? (sum2 / (weight - 1) - sum * sum / (weight - 1) / (weight - 1)) : sum2;
//    return weight > 2 ? (sum2 / weight - sum * sum / weight / weight) * weight * (weight - 2) / (weight * weight - 3 * weight + 1) * (1 + 2 * Math.log(weight + 1)) : 0;

//    final double n = stats.weight;
//    return n > 2 ? n*(n-2)/(n * n - 3 * n + 1) * (stats.sum2 - stats.sum * stats.sum / n) : stats.sum2;
//    return (stats.sum2 / (n-1) -stats.sum * stats.sum  / (n - 1) / (n - 1));
  }

  public double inScore(final AdditiveStatistics stats) {
    final double weight = weight(stats);
    if (weight < 5) {
      return Double.POSITIVE_INFINITY;
    }
    final double sum = sum(stats);
    final double sum2 = sum2(stats);
//    return weight > 2 ? (-sum * sum * weight / (weight-1) / (weight-1))  * (1 + 2 * Math.log(weight + 1)) : 0;
    return -sum * sum / (weight - 1) / (weight - 1);
//    return weight > 1 ? (sum2 / (weight - 1) - sum * sum / (weight - 1) / (weight - 1)) : sum2;
//    final double n = stats.weight;
//    return (weight - 2) /   (weight * weight - 3 * weight + 1) * (sum2 - sum * sum / weight);
//    return (sum2 /(weight-1) - sum * sum / weight / (weight-1));
//    return (stats.sum2 / (n-1) -stats.sum * stats.sum  / (n - 1) / (n - 1));
  }

  public double outScore(final AdditiveStatistics stats) {
    final double sum2 = sum2(stats);
    final double sum = sum(stats);
    final double weight = weight(stats);
    if (weight < 5) {
      return Double.POSITIVE_INFINITY;
    }
    return 0;
//    return weight > 1 ? (sum2 / (weight - 1) - sum * sum / (weight - 1) / (weight - 1)) : sum2;
//    return sum2 / (weight - 1) ;

//    return weight > 1 ? sum2 / (weight - 1) : sum2;
//
//    return weight > 1 ? sum2 / (weight - 1) : sum2;
//    final double n = stats.weight;
//    return n > 2 ? n*(n-2)/(n * n - 3 * n + 1) * (stats.sum2 - stats.sum * stats.sum / n) : stats.sum2;
//    return (stats.sum2 / (n-1) -stats.sum * stats.sum  / (n - 1) / (n - 1));
  }

  public double logScore(final AdditiveStatistics stats) {
    final double weight = weight(stats);
    final double sum = sum(stats);
    return weight > 2 ? (-sum * sum / weight) * weight * (weight - 2) / (weight * weight - 3 * weight + 1) * (1 + 2 * Math.log(weight + 1)) : 0;
  }
}
