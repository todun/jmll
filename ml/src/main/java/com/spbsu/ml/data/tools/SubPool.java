package com.spbsu.ml.data.tools;


import com.spbsu.commons.seq.Seq;
import com.spbsu.commons.util.ArrayTools;
import com.spbsu.commons.util.Pair;
import com.spbsu.ml.meta.DSItem;
import com.spbsu.ml.meta.PoolFeatureMeta;

/**
 * User: solar
 * Date: 11.07.14
 * Time: 22:57
 */
public class SubPool<I extends DSItem> extends Pool<I> {
  public final int[] indices;

  public SubPool(final Pool<I> original, int[] indices) {
    super(original.meta,
        ArrayTools.cut(original.items, indices),
        cutFeatures(original.features, indices),
        Pair.create(original.target.first, ArrayTools.cut(original.target.second, indices)));
    this.indices = indices;
  }

  private static Pair<? extends PoolFeatureMeta, ? extends Seq<?>>[] cutFeatures(Pair<? extends PoolFeatureMeta, ? extends Seq<?>>[] original, int[] indices) {
    @SuppressWarnings("unchecked")
    Pair<PoolFeatureMeta, Seq<?>>[] result = new Pair[original.length];
    for (int i = 0; i < original.length; i++) {
      result[i] = Pair.<PoolFeatureMeta, Seq<?>>create(original[i].first, ArrayTools.cut(original[i].getSecond(), indices));
    }
    return result;
  }
}
