package com.spbsu.bernulli.MCMCBernoulliMixture;

import com.spbsu.commons.random.FastRandom;
import com.spbsu.commons.util.ArrayTools;
import gnu.trove.list.array.TIntArrayList;

import java.util.Arrays;


//estimates some vector parameter of distibution by sampling with metropolis-hastings
public class MCMCBernoulliEstimation {
  private final int k; //number of mixtures
  private final int n; //number of observations
  private final double invn;
  private final int[] sums;
  private final int[] stateSums;
  private final int[] componentsMap;
  private TIntArrayList[] componentsPoints;
  double[] denums;
  private final FastRandom rand;
  private final BernoulliPrior prior;
  private Estimator estimator;
  private final double[] likelihoods;
  private final double[] logSizesCache;
  private final boolean[] isLogProbCached;

  private long accepted;
  private long rejected;

  public double acceptedRate() {
    return ((double) accepted) / (accepted + rejected);
  }

  private boolean burnIn = false; // take values only after burn in
  private final int window = 100; // decorrelation, for better estimation. (we don't have infinite sample size)

  private final double logStepProb;

  public MCMCBernoulliEstimation(int k, int n, int sums[], BernoulliPrior prior, FastRandom random) {
    this.k = k;
    this.logStepProb = -Math.log(k) - Math.log(k - 1);
    ;
    this.n = n;
    this.invn = 1.0 / n;
    this.rand = new FastRandom(random.nextLong());
    this.prior = prior;
    this.stateSums = new int[k];
    this.componentsMap = new int[sums.length];
    this.likelihoods = new double[k];
    this.sums = sums;
    this.estimator = new Estimator(sums.length);
    this.componentsPoints = new TIntArrayList[k];
    this.denums = new double[k];
    for (int i = 0; i < k; ++i)
      this.componentsPoints[i] = new TIntArrayList(sums.length);
    this.randomState();
    this.logSizesCache = new double[n * sums.length + 1];
    this.isLogProbCached = new boolean[n * sums.length + 1];

  }


  private void randomState() {
    Arrays.fill(stateSums, 0);
    for (int i = 0; i < k; ++i)
      componentsPoints[i].clear();

    for (int i = 0; i < componentsMap.length; ++i) {
      final int comp = rand.nextByte(k);
      componentsMap[i] = comp;
      stateSums[comp] += sums[i];
      componentsPoints[comp].add(i);
    }
    for (int i = 0; i < k; ++i) {
      denums[i] = 1.0 / n / componentsPoints[i].size();
    }
    updateLikelihood();
  }

  private int move(int from, int entry, int to) {
    final int point = componentsPoints[from].get(entry);
    this.stateSums[from] -= sums[point];
    this.stateSums[to] += sums[point];

    final int lastInd = componentsPoints[from].size() - 1;
    componentsPoints[from].set(entry, componentsPoints[from].get(lastInd));
    componentsPoints[from].removeAt(lastInd);
    denums[from] = invn / componentsPoints[from].size();
    componentsPoints[to].add(point);
    denums[to] = invn / componentsPoints[from].size();
    componentsMap[point] = to;
    updateLikelihood(from);
    updateLikelihood(to);
    return componentsPoints[to].size() - 1;
  }


  private void updateLikelihood(int i) {
    cachedLL -= likelihoods[i];
    likelihoods[i] = prior.likelihood(stateSums[i], componentsPoints[i].size() * n);
    cachedLL += likelihoods[i];
  }

  private void updateLikelihood() {
    for (int i = 0; i < likelihoods.length; ++i)
      updateLikelihood(i);
    cachedLL = 0;
    for (int i = 0; i < stateSums.length; ++i)
      cachedLL += likelihoods[i];
  }

  private double cachedLL = 0;

  final double likelihood() {
    return cachedLL;
  }


  final double getNewLL(final int from, final int entry, final int to) {
    double ll = likelihood();
    ll -= likelihoods[from];
    ll -= likelihoods[to];
    final int point = componentsPoints[from].get(entry);
    final int sum = sums[point];
    final int s0 = stateSums[from] - sum;
    final int s1 = stateSums[to] + sum;
    ll += prior.likelihood(s0, (componentsPoints[from].size() - 1) * n);
    ll += prior.likelihood(s1, (componentsPoints[to].size() + 1) * n);
    return ll;
  }

  final boolean next() {
    final double currentLL = likelihood();
    final int moveFrom = rand.nextByte(k);
    if (componentsPoints[moveFrom].size() == 1)
      return false;
    int moveTo = rand.nextByte(k - 1);
    if (moveTo >= moveFrom)
      ++moveTo;

    final int entry = rand.nextInt(componentsPoints[moveFrom].size());
    final double prob = getProb(componentsPoints[moveFrom].size());
    final double invProb = getProb(componentsPoints[moveTo].size() + 1);
    final double newLL = getNewLL(moveFrom, entry, moveTo);
    final double accProb = Math.exp(newLL + invProb - prob - currentLL);
    if (rand.nextDouble() < accProb) {
      move(moveFrom, entry, moveTo);
      return true;
    }
    return false;
  }

//  private double getProb(int from) {
//    final int logProbCached = componentsPoints[from].size();
//    if (isLogProbCached[logProbCached]) {
//      return logSizesCache[logProbCached];
//    } else {
//      logSizesCache[logProbCached] = logStepProb - Math.log(logProbCached);
//      isLogProbCached[logProbCached] = true;
//      return logSizesCache[logProbCached];
//    }
//  }

  private double getProb(int size) {
    if (isLogProbCached[size]) {
      return logSizesCache[size];
    } else {
      logSizesCache[size] = logStepProb - Math.log(size);
      isLogProbCached[size] = true;
      return logSizesCache[size];
    }
  }


  public void run(int iters) {
    {
      int it = 0;
      double currentMeans[] = new double[componentsMap.length];
      int burnIters = 1000;
      while (!burnIn) {
        for (int i = 0; i < burnIters; ++i, ++it) {
          if (!next()) {
            ++rejected;
          } else {
            ++accepted;
          }
          if (i % window == 0)
            add();
        }
        double[] means = estimation();
        if (burned(means, currentMeans)) {
          burnIn = true;
        }
        burnIters *= 2;
        estimator.clear();
        currentMeans = means;
        System.out.println("Accepted rate after " + it + " iters is " + acceptedRate());
      }
    }

    for (int i = 0; i < iters; ++i) {
      if (!next()) {
        ++rejected;
      } else {
        ++accepted;
      }
      if (i % window == 0)
        add();
    }
    System.out.println("Accepted rate " + acceptedRate());
  }

  private boolean burned(double[] means, double[] currentMeans) {
    return dist(means, currentMeans) < 1;
  }

  private double dist(double[] first, double[] second) {
    final int len = (first.length / 4) * 4;
    double sum = 0;
    for (int i = 0; i < len; i += 4) {
      double diff0 = first[i] - second[i];
      double diff1 = first[i + 1] - second[i + 1];
      double diff2 = first[i + 2] - second[i + 2];
      double diff3 = first[i + 3] - second[i + 3];
      diff0 *= diff0;
      diff1 *= diff1;
      diff2 *= diff2;
      diff3 *= diff3;
      diff0 += diff2;
      diff1 += diff3;
      sum += diff0 + diff1;
    }
    return sum;
  }


  public final double[] estimation() {
    return estimator.get();
  }

  public void clear() {
    estimator.clear();
  }


  final void add() {
    estimator.count++;
    final int len = (componentsMap.length / 4) * 4;
    for (int i = 0; i < len; i += 4) {
      final int ind0 = componentsMap[i];
      final int ind1 = componentsMap[i + 1];
      final int ind2 = componentsMap[i + 2];
      final int ind3 = componentsMap[i + 3];
      final int m0 = stateSums[ind0];
      final int m1 = stateSums[ind1];
      final int m2 = stateSums[ind2];
      final int m3 = stateSums[ind3];
      estimator.meanSums[i] += m0 * denums[ind0];
      estimator.meanSums[i + 1] += m1 * denums[ind1];
      estimator.meanSums[i + 2] += m2 * denums[ind2];
      estimator.meanSums[i + 3] += m3 * denums[ind3];
    }
    for (int i = len; i < componentsMap.length; ++i) {
      final int ind0 = componentsMap[i];
      final int m0 = stateSums[ind0];
      estimator.meanSums[i] += m0 * denums[ind0];
    }
  }

  private class Estimator {
    final double[] meanSums;
    private int count; //normalization for estimator

    public Estimator(int len) {
      this.meanSums = new double[len];
    }

    public final double[] get() {
      double[] result = new double[meanSums.length];
      System.arraycopy(meanSums, 0, result, 0, result.length);
      for (int i = 0; i < meanSums.length; ++i)
        result[i] /= count;
      return result;
    }

    public final void clear() {
      ArrayTools.fill(meanSums, 0);
      count = 0;
    }
  }


}