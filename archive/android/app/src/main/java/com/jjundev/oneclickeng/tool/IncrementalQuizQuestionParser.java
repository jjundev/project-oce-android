package com.jjundev.oneclickeng.tool;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Incrementally extracts completed question objects from the "questions" JSON array.
 *
 * <p>The parser accepts streamed text chunks and returns only newly completed JSON object slices.
 */
public final class IncrementalQuizQuestionParser {
  private static final String QUESTIONS_KEY = "\"questions\"";

  @NonNull private final StringBuilder buffer = new StringBuilder();
  private int emittedQuestionCount = 0;

  @NonNull
  public List<String> addChunk(@NonNull String chunk) {
    List<String> newlyCompleted = new ArrayList<>();
    if (chunk.isEmpty()) {
      return newlyCompleted;
    }

    buffer.append(chunk);
    List<String> allCompleted = extractCompletedQuestionObjects(buffer.toString());
    if (allCompleted.size() <= emittedQuestionCount) {
      return newlyCompleted;
    }

    for (int i = emittedQuestionCount; i < allCompleted.size(); i++) {
      newlyCompleted.add(allCompleted.get(i));
    }
    emittedQuestionCount = allCompleted.size();
    return newlyCompleted;
  }

  @NonNull
  private static List<String> extractCompletedQuestionObjects(@NonNull String source) {
    List<String> result = new ArrayList<>();

    int arrayStartIndex = resolveQuestionsArrayStart(source);
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

  private static int resolveQuestionsArrayStart(@NonNull String source) {
    int searchFrom = 0;
    while (true) {
      int keyIndex = source.indexOf(QUESTIONS_KEY, searchFrom);
      if (keyIndex < 0) {
        return -1;
      }

      int arrayStart = findArrayStart(source, keyIndex + QUESTIONS_KEY.length());
      if (arrayStart >= 0) {
        return arrayStart;
      }

      searchFrom = keyIndex + QUESTIONS_KEY.length();
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
}
