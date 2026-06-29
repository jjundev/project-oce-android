package com.jjundev.oneclickeng.others;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class EnglishShortsTagFormatterTest {

  @Test
  public void buildDisplayTags_returnsEmpty_whenRawTagsIsNull() {
    List<String> result = EnglishShortsTagFormatter.buildDisplayTags(null, 3);
    assertTrue(result.isEmpty());
  }

  @Test
  public void buildDisplayTags_returnsEmpty_whenRawTagsIsEmpty() {
    List<String> result = EnglishShortsTagFormatter.buildDisplayTags(Collections.emptyList(), 3);
    assertTrue(result.isEmpty());
  }

  @Test
  public void buildDisplayTags_trimsAddsHashAndLimitsToMaxCount() {
    List<String> result =
        EnglishShortsTagFormatter.buildDisplayTags(
            Arrays.asList(" travel ", "food", "grammar", "extra"), 3);
    assertEquals(Arrays.asList("#travel", "#food", "#grammar"), result);
  }

  @Test
  public void buildDisplayTags_deduplicatesByNormalizedTag() {
    List<String> result =
        EnglishShortsTagFormatter.buildDisplayTags(Arrays.asList("travel", "#travel", "Travel"), 3);
    assertEquals(Collections.singletonList("#travel"), result);
  }

  @Test
  public void buildDisplayTags_preservesInputOrder() {
    List<String> result =
        EnglishShortsTagFormatter.buildDisplayTags(Arrays.asList("zeta", "alpha", "beta"), 3);
    assertEquals(Arrays.asList("#zeta", "#alpha", "#beta"), result);
  }
}
