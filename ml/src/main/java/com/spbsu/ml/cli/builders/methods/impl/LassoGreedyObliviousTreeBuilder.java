package com.spbsu.ml.cli.builders.methods.impl;

import com.spbsu.commons.func.Factory;
import com.spbsu.ml.methods.VecOptimization;
import com.spbsu.ml.methods.trees.GreedyObliviousTree;
import com.spbsu.ml.methods.trees.LassoGreedyObliviousTree;
import com.spbsu.ml.methods.trees.RidgeGreedyObliviousTree;

/**
 * User: noxoomo
 */

public class LassoGreedyObliviousTreeBuilder implements Factory<VecOptimization> {
  private int nlambda = 50;
  private double alpha = 0.5;
  private GreedyObliviousTree weak;
  private final GreedyObliviousTreeBuilder defaultWeakBuilder = new GreedyObliviousTreeBuilder();

  public void setWeak(final GreedyObliviousTree weak) {
    this.weak = weak;
  }

  public void setnlambda(final int nlambda) {
    this.nlambda = nlambda;
  }

  public void setAlpha(final double alpha) {
    this.alpha = alpha;
  }

  @Override
  public VecOptimization create() {
    if (weak == null) {
      weak = (GreedyObliviousTree) defaultWeakBuilder.create();

    }
    //noinspection unchecked
    return new LassoGreedyObliviousTree(weak,  nlambda, alpha);
  }
}
