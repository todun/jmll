package com.spbsu.crawl.data.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spbsu.crawl.data.Message;


public class UpdateMapCellMessage implements Message {

  //this properties send if prop was updated
  //Almost all ints are uint32.
  @JsonProperty("x")
  private int x = Integer.MAX_VALUE;

  @JsonProperty("y")
  private int y = Integer.MAX_VALUE;

  public void setPoint(final int x, final int y) {
    this.x = x;
    this.y = y;
  }

  @JsonProperty("f")
  private int dungeonFeatureType;

  @JsonProperty("mf")
  private int mapFeature;

  @JsonProperty("g")
  private String glyph;

  @JsonProperty("col")
  private int colour;

  @JsonProperty("t")
  private PackedCellMessage packedCell;

  @JsonProperty("mon")
  private MonsterInfoMessage monsterInfoMessage;

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  public int getDungeonFeatureType() {
    return dungeonFeatureType;
  }

  public int getMapFeature() {
    return mapFeature;
  }

  public String getGlyph() {
    return glyph;
  }

  public int getColour() {
    return colour;
  }

  public MonsterInfoMessage getMonsterInfoMessage() {
    return monsterInfoMessage;
  }

  public PackedCellMessage getPackedCell() {
    return packedCell;
  }
}
