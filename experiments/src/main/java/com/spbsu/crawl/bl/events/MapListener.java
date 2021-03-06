package com.spbsu.crawl.bl.events;

import com.spbsu.crawl.bl.map.Position;
import com.spbsu.crawl.bl.map.TerrainType;

/**
 * Experts League
 * Created by solar on 05/05/16.
 */
public interface MapListener extends SystemViewListener {
  void tile(Position position, TerrainType type);

  void changeLevel(String id);

  void resetPosition();
}
