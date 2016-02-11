package com.spbsu.ml.models.nn.nfa;

import com.spbsu.commons.math.vectors.Mx;
import com.spbsu.commons.math.vectors.Vec;
import com.spbsu.commons.math.vectors.VecTools;
import com.spbsu.commons.math.vectors.impl.mx.VecBasedMx;
import com.spbsu.commons.random.FastRandom;
import com.spbsu.commons.seq.CharSeqTools;
import com.spbsu.commons.seq.Seq;
import com.spbsu.commons.seq.SeqTools;
import com.spbsu.commons.util.ArrayTools;
import com.spbsu.ml.FuncC1;
import com.spbsu.ml.func.generic.Const;
import com.spbsu.ml.func.generic.SubVecFuncC1;
import com.spbsu.ml.func.generic.WSum;
import com.spbsu.ml.models.nn.NeuralSpider;
import org.jetbrains.annotations.NotNull;

/**
 * User: solar
 * Date: 25.05.15
 * Time: 13:25
 */
public class NFANetwork<T> extends NeuralSpider<T, Seq<T>> {
  private final FastRandom rng;
  private final double dropout;
  final int statesCount;
  private final Seq<T> alpha;
  private final int dim;
  private final int transitionMxDim;
  private final int finalStates;

  public NFANetwork(FastRandom rng, double dropout, int statesCount, int finalStates, Seq<T> alpha) {
    super();
    this.rng = rng;
    this.dropout = dropout;
    this.statesCount = statesCount;
    this.finalStates = finalStates;
    this.alpha = alpha;
    transitionMxDim = (statesCount - finalStates) * (statesCount - 1);
    dim = transitionMxDim * alpha.length();
  }

  final ThreadLocal<WeightsCalculator[]> calculators = new ThreadLocal<WeightsCalculator[]>() {
    @Override
    protected WeightsCalculator[] initialValue() {
      final WeightsCalculator[] calculators = new WeightsCalculator[alpha.length()];
      for(int i = 0; i < calculators.length; i++) {
        calculators[i] = new WeightsCalculator(statesCount, finalStates, i * transitionMxDim, transitionMxDim);
      }
      return calculators;
    }
  };

  @Override
  protected Topology topology(Seq<T> seq, final boolean dropout) {
    final Node[] nodes = new Node[(seq.length() + 1) * statesCount + finalStates + 1];
    for (int i = 0; i < statesCount; i++) {
      final Const aConst = new Const(i == 0 ? 1 : 0);
      nodes[i] = new InputNode(aConst);
    }
    final boolean[] dropoutArr = new boolean[statesCount];

    if (dropout && rng.nextDouble() < this.dropout)
      dropoutArr[rng.nextInt(statesCount - finalStates - 1) + 1] = true;

    final int[][] outputNodesConnections = new int[finalStates][seq.length()];
    for (int d = 0; d < seq.length(); d++) {
      final int elementIndex = ArrayTools.indexOf(seq.at(d), alpha);
      final int prevLayerStart = d * statesCount;
      final WeightsCalculator calcer = calculators.get()[elementIndex];
      calcer.setDropOut(dropoutArr);
      for (int i = 0; i < statesCount; i++) {
        final int nodeIndex = (d + 1) * statesCount + i;
        nodes[nodeIndex] = new MyNode(i, elementIndex * transitionMxDim, transitionMxDim, dim, prevLayerStart, statesCount, nodes.length, calcer);
      }
      for (int i = 0; i < finalStates; i++) {
        outputNodesConnections[i][d] = (d + 2) * statesCount - finalStates + i;
      }
    }
    final int lastLayerStart = seq.length() * statesCount;
    nodes[nodes.length - finalStates - 1] = new NonDeterminedNode(statesCount - finalStates, lastLayerStart, nodes.length);
    for (int i = 0; i < finalStates; i++) {
      nodes[nodes.length - finalStates + i] = new OutputNode(outputNodesConnections[i], nodes);
    }

    return new NFATopology<>(statesCount, finalStates, dropout, nodes, dropoutArr);
  }

  @Override
  public int dim() {
    return dim;
  }

  public String ppSolution(Vec x) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < alpha.length(); i++) {
      builder.append(alpha.at(i)).append(":").append("\n");
      builder.append(new VecBasedMx(statesCount - 1, x.sub(i * transitionMxDim, transitionMxDim))).append("\n");
    }
    return builder.toString();
  }

  public String ppState(Vec state, Seq<T> seq) {
    return getString(state, seq, statesCount);
  }

  public String ppSolution(Vec x, T s) {
    final int i = SeqTools.indexOf(alpha, s);
    return String.valueOf(s) + ":\n" + new VecBasedMx(statesCount - 1, x.sub(i * transitionMxDim, transitionMxDim));
  }

  public static class MyNode implements NeuralSpider.Node {
    private final int index;
    private final int wStart;
    private final int wLen;
    private final int wDim;
    private final int pStart;
    private final int pLen;
    private final int nodesCount;
    private final WeightsCalculator calcer;

    public MyNode(int index, int wStart, int wLen, int wDim, int pStart, int pLen, int nodesCount, WeightsCalculator calcer) {
      this.index = index;
      this.wStart = wStart;
      this.wLen = wLen;
      this.wDim = wDim;
      this.pStart = pStart;
      this.pLen = pLen;
      this.nodesCount = nodesCount;
      this.calcer = calcer;
    }

    public FuncC1 transByParents(final Vec parents) {
      return new FuncC1.Stub() {
        @Override
        public Vec gradientTo(Vec betta, Vec to) {
          final Mx weights = calcer.compute(betta);
          final int bettaDim = pLen - 1;
          final int indexLocal = index;
          final int pStartLocal = pStart;
          final VecBasedMx grad = new VecBasedMx(bettaDim, to.sub(wStart, wLen));
          for (int i = 0; i < grad.rows(); i++) {
            final double selectedProbab = weights.get(indexLocal, i);
            for (int j = 0; j < bettaDim; j++) {
              double currentProbab = weights.get(j, i);
              if (j == indexLocal)
                grad.set(i, j, parents.get(pStartLocal + i) * selectedProbab * (1 - selectedProbab));
              else
                grad.set(i, j, -parents.get(pStartLocal + i) * selectedProbab * currentProbab);
            }
          }
          return to;
        }

        @Override
        public double value(Vec betta) {
          final Mx weights = calcer.compute(betta);
          return VecTools.multiply(weights.row(index), parents.sub(pStart, pLen));
        }

        @Override
        public int dim() {
          return wDim;
        }
      };
    }

    public FuncC1 transByParameters(Vec betta) {
      final Mx weights = calcer.compute(betta);
      return new SubVecFuncC1(new WSum(weights.row(index)), pStart, pLen, nodesCount);
    }
  }
}
