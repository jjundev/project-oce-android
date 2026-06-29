package com.jjundev.oneclickeng.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Incrementally extracts dialogue metadata and completed turn objects from streamed JSON text. */
public final class IncrementalDialogueScriptParser {
  private static final String SCRIPT_KEY = "\"script\"";
  private static final String DEFAULT_OPPONENT_NAME = "AI Coach";
  private static final String DEFAULT_OPPONENT_GENDER = "female";
  private static final String[] TOPIC_KEYS = {"topic", "topicTitle", "conversationTopic"};
  private static final String[] OPPONENT_NAME_KEYS = {"opponent_name", "opponentName"};
  private static final String[] OPPONENT_GENDER_KEYS = {"opponent_gender", "opponentGender"};

  @NonNull private final StringBuilder buffer = new StringBuilder();
  private int emittedTurnCount = 0;
  @Nullable private Metadata lastEmittedMetadata;

  public static final class Metadata {
    @NonNull private final String topic;
    @NonNull private final String opponentName;
    @NonNull private final String opponentGender;

    public Metadata(
        @NonNull String topic, @NonNull String opponentName, @NonNull String opponentGender) {
      this.topic = topic;
      this.opponentName = opponentName;
      this.opponentGender = opponentGender;
    }

    @NonNull
    public String getTopic() {
      return topic;
    }

    @NonNull
    public String getOpponentName() {
      return opponentName;
    }

    @NonNull
    public String getOpponentGender() {
      return opponentGender;
    }
  }

  public static final class ParseUpdate {
    @Nullable private final Metadata metadata;
    @NonNull private final List<String> completedTurnObjects;

    public ParseUpdate(@Nullable Metadata metadata, @NonNull List<String> completedTurnObjects) {
      this.metadata = metadata;
      this.completedTurnObjects = completedTurnObjects;
    }

    @Nullable
    public Metadata getMetadata() {
      return metadata;
    }

    @NonNull
    public List<String> getCompletedTurnObjects() {
      return completedTurnObjects;
    }
  }

  @NonNull
  public ParseUpdate addChunk(@NonNull String chunk) {
    if (!chunk.isEmpty()) {
      buffer.append(chunk);
    }

    Metadata metadata = tryExtractMetadata(buffer.toString(), lastEmittedMetadata);
    if (metadata != null && isSameMetadata(metadata, lastEmittedMetadata)) {
      metadata = null;
    } else if (metadata != null) {
      lastEmittedMetadata = metadata;
    }

    List<String> allCompleted = extractCompletedTurnObjects(buffer.toString());
    List<String> newlyCompleted = new ArrayList<>();
    if (allCompleted.size() > emittedTurnCount) {
      for (int i = emittedTurnCount; i < allCompleted.size(); i++) {
        newlyCompleted.add(allCompleted.get(i));
      }
      emittedTurnCount = allCompleted.size();
    }
    return new ParseUpdate(metadata, newlyCompleted);
  }

  @Nullable
  private static Metadata tryExtractMetadata(
      @NonNull String source, @Nullable Metadata previousMetadata) {
    String topic =
        firstNonBlank(
            readJsonStringValue(source, TOPIC_KEYS),
            previousMetadata == null ? null : previousMetadata.getTopic());
    if (isBlank(topic)) {
      return null;
    }

    String opponentName =
        firstNonBlank(
            readJsonStringValue(source, OPPONENT_NAME_KEYS),
            previousMetadata == null ? null : previousMetadata.getOpponentName(),
            DEFAULT_OPPONENT_NAME);

    String previousGender =
        firstNonBlank(
            previousMetadata == null ? null : previousMetadata.getOpponentGender(),
            DEFAULT_OPPONENT_GENDER);
    String opponentGender =
        normalizeGender(
            firstNonBlank(readJsonStringValue(source, OPPONENT_GENDER_KEYS), previousGender),
            previousGender);

    return new Metadata(topic.trim(), opponentName.trim(), opponentGender);
  }

  private static boolean isSameMetadata(
      @NonNull Metadata current, @Nullable Metadata previousMetadata) {
    if (previousMetadata == null) {
      return false;
    }
    return current.getTopic().equals(previousMetadata.getTopic())
        && current.getOpponentName().equals(previousMetadata.getOpponentName())
        && current.getOpponentGender().equals(previousMetadata.getOpponentGender());
  }

  @Nullable
  private static String readJsonStringValue(@NonNull String source, @NonNull String[] keys) {
    for (String key : keys) {
      String value = readJsonStringValue(source, key);
      if (!isBlank(value)) {
        return value;
      }
    }
    return null;
  }

  @Nullable
  private static String readJsonStringValue(@NonNull String source, @NonNull String key) {
    String quotedKey = "\"" + key + "\"";
    int keyIndex = source.indexOf(quotedKey);
    if (keyIndex < 0) {
      return null;
    }

    int colonIndex = source.indexOf(':', keyIndex + quotedKey.length());
    if (colonIndex < 0) {
      return null;
    }

    int valueStart = -1;
    for (int i = colonIndex + 1; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (Character.isWhitespace(ch)) {
        continue;
      }
      if (ch != '"') {
        return null;
      }
      valueStart = i + 1;
      break;
    }
    if (valueStart < 0 || valueStart > source.length()) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    boolean escaping = false;
    for (int i = valueStart; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (escaping) {
        builder.append(ch);
        escaping = false;
        continue;
      }
      if (ch == '\\') {
        escaping = true;
        continue;
      }
      if (ch == '"') {
        return builder.toString();
      }
      builder.append(ch);
    }
    return null;
  }

  @NonNull
  private static List<String> extractCompletedTurnObjects(@NonNull String source) {
    List<String> result = new ArrayList<>();
    int arrayStartIndex = resolveScriptArrayStart(source);
    if (arrayStartIndex < 0) {
      return result;
    }

    boolean inString = false;
    boolean escaping = false;
    int objectStart = -1;
    int braceDepth = 0;

    for (int i = arrayStartIndex + 1; i < source.length(); i++) {
      char ch = source.charAt(i);

      if (inString) {
        if (escaping) {
          escaping = false;
          continue;
        }
        if (ch == '\\') {
          escaping = true;
          continue;
        }
        if (ch == '"') {
          inString = false;
        }
        continue;
      }

      if (ch == '"') {
        inString = true;
        continue;
      }

      if (objectStart < 0) {
        if (ch == '{') {
          objectStart = i;
          braceDepth = 1;
          continue;
        }
        if (ch == ']') {
          break;
        }
        continue;
      }

      if (ch == '{') {
        braceDepth++;
        continue;
      }
      if (ch == '}') {
        braceDepth--;
        if (braceDepth == 0) {
          result.add(source.substring(objectStart, i + 1));
          objectStart = -1;
        }
      }
    }

    return result;
  }

  private static int resolveScriptArrayStart(@NonNull String source) {
    int searchFrom = 0;
    while (true) {
      int keyIndex = source.indexOf(SCRIPT_KEY, searchFrom);
      if (keyIndex < 0) {
        return -1;
      }
      int arrayStart = findArrayStart(source, keyIndex + SCRIPT_KEY.length());
      if (arrayStart >= 0) {
        return arrayStart;
      }
      searchFrom = keyIndex + SCRIPT_KEY.length();
    }
  }

  private static int findArrayStart(@NonNull String source, int fromIndex) {
    for (int i = fromIndex; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (ch == '[') {
        return i;
      }
      if (!Character.isWhitespace(ch) && ch != ':') {
        return -1;
      }
    }
    return -1;
  }

  private static boolean isBlank(@Nullable String value) {
    return trimToNull(value) == null;
  }

  @NonNull
  private static String normalizeGender(@Nullable String raw, @NonNull String fallback) {
    String fallbackValue = trimToNull(fallback);
    if (fallbackValue == null) {
      fallbackValue = DEFAULT_OPPONENT_GENDER;
    }
    String value = trimToNull(raw);
    if (value == null) {
      return fallbackValue;
    }
    String lowered = value.toLowerCase(Locale.US);
    if ("male".equals(lowered) || "female".equals(lowered)) {
      return lowered;
    }
    return fallbackValue;
  }

  @Nullable
  private static String firstNonBlank(@Nullable String... candidates) {
    if (candidates == null) {
      return null;
    }
    for (String candidate : candidates) {
      String trimmed = trimToNull(candidate);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
