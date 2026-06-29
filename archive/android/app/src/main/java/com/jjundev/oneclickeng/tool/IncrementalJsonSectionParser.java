package com.jjundev.oneclickeng.tool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IncrementalJsonSectionParser {
  private static final String[] SECTION_KEYS = {
    "writingScore", "grammar", "conceptualBridge",
    "naturalness", "toneStyle", "paraphrasing"
  };

  private final StringBuilder buffer = new StringBuilder();
  private final Set<String> emittedSections = new HashSet<>();

  // 청크 추가 후 새로 완성된 섹션 목록 반환
  public List<SectionResult> addChunk(String chunk) {
    buffer.append(chunk);
    List<SectionResult> results = new ArrayList<>();
    String currentJson = buffer.toString();

    for (String sectionKey : SECTION_KEYS) {
      if (emittedSections.contains(sectionKey)) {
        continue;
      }

      // 섹션 키 검색 (예: "writingScore":)
      String keyPattern = "\"" + sectionKey + "\"";
      int keyIndex = currentJson.indexOf(keyPattern);
      if (keyIndex == -1) {
        // 키가 아직 없으면 더 이상 진행 불가 (순서대로 온다고 가정하거나, 아직 도착 안 함)
        // 순서가 보장된다면 여기서 break 해도 됨. 보장 안 된다면 continue.
        // Gemini는 보통 순서대로 생성하므로 break가 효율적일 수 있으나 안전하게 continue.
        continue;
      }

      // 값의 시작 찾기 (: 뒤의 첫 { 또는 [)
      int valueStartIndex = -1;
      int searchStart = keyIndex + keyPattern.length();
      for (int i = searchStart; i < currentJson.length(); i++) {
        char c = currentJson.charAt(i);
        if (c == '{' || c == '[') {
          valueStartIndex = i;
          break;
        } else if (!Character.isWhitespace(c) && c != ':') {
          // unexpected character before value start
          break;
        }
      }

      if (valueStartIndex == -1) {
        continue; // 값이 아직 시작되지 않음
      }

      // 괄호 짝 찾기
      int valueEndIndex = findMatchingBrace(currentJson, valueStartIndex);
      if (valueEndIndex != -1) {
        // 섹션 완성!
        String jsonValue = currentJson.substring(valueStartIndex, valueEndIndex + 1);
        results.add(new SectionResult(sectionKey, jsonValue));
        emittedSections.add(sectionKey);
      }
    }

    return results;
  }

  private int findMatchingBrace(String json, int startIndex) {
    char startChar = json.charAt(startIndex);
    char endChar = (startChar == '{') ? '}' : ']';
    int depth = 0;
    boolean inString = false;

    for (int i = startIndex; i < json.length(); i++) {
      char c = json.charAt(i);

      if (inString) {
        if (c == '"' && json.charAt(i - 1) != '\\') {
          inString = false;
        }
        continue;
      }

      if (c == '"') {
        inString = true;
        continue;
      }

      if (c == startChar) {
        depth++;
      } else if (c == endChar) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1; // 아직 닫히지 않음
  }

  // 섹션 결과: 키 + JSON 값 문자열
  public static class SectionResult {
    public final String key;
    public final String jsonValue; // 예: {"score":85,"encouragementMessage":"잘했어요!"}

    public SectionResult(String key, String jsonValue) {
      this.key = key;
      this.jsonValue = jsonValue;
    }
  }
}
