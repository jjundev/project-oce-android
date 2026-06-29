package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserNicknameCloudRepositoryLogicTest {

  @Test
  public void normalize_null_returnsEmpty() {
    assertEquals("", UserNicknameCloudRepository.normalize(null));
  }

  @Test
  public void normalize_trimmedValue_returnsTrimmed() {
    assertEquals("닉네임", UserNicknameCloudRepository.normalize("  닉네임  "));
  }

  @Test
  public void resolveBootstrapNickname_withDisplayName_returnsDisplayName() {
    assertEquals("SkyLearner", UserNicknameCloudRepository.resolveBootstrapNickname("SkyLearner"));
  }

  @Test
  public void resolveBootstrapNickname_withBlankDisplayName_returnsDefault() {
    assertEquals("학습자", UserNicknameCloudRepository.resolveBootstrapNickname("   "));
  }

  @Test
  public void resolveNicknameToPersist_withRequestedNickname_returnsRequestedNickname() {
    assertEquals(
        "로컬닉",
        UserNicknameCloudRepository.resolveNicknameToPersist("  로컬닉  ", "CloudDisplay"));
  }

  @Test
  public void resolveNicknameToPersist_withBlankRequestedNickname_usesDisplayName() {
    assertEquals(
        "CloudDisplay",
        UserNicknameCloudRepository.resolveNicknameToPersist("  ", "CloudDisplay"));
  }

  @Test
  public void resolveNicknameToPersist_withBlankRequestedAndDisplayName_returnsDefault() {
    assertEquals("학습자", UserNicknameCloudRepository.resolveNicknameToPersist(" ", " "));
  }
}
