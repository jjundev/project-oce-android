package com.jjundev.oneclickeng.learning.dialoguelearning.parser;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.DialogueScript;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DialogueScriptParser {

  private static final String DEFAULT_TOPIC = "영어 연습";
  private static final String DEFAULT_OPPONENT_NAME = "English Coach";
  private static final String DEFAULT_OPPONENT_ROLE = "Partner";

  private final Random random;

  public DialogueScriptParser() {
    this(new Random());
  }

  public DialogueScriptParser(@NonNull Random random) {
    this.random = random;
  }

  @NonNull
  public DialogueScript parse(@NonNull String jsonString) throws Exception {
    String trimmedJson = jsonString.trim();
    JsonArray scriptArray;

    String topic;
    String opponentName;
    String opponentRole;
    String opponentGender;

    JsonElement root = JsonParser.parseString(trimmedJson);
    if (root.isJsonObject()) {
      JsonObject object = root.getAsJsonObject();
      topic = getStringOrDefault(object, "topic", DEFAULT_TOPIC);
      opponentName = getStringOrDefault(object, "opponent_name", "AI Coach");
      opponentRole = getStringOrDefault(object, "opponent_role", DEFAULT_OPPONENT_ROLE);
      opponentGender = getStringOrDefault(object, "opponent_gender", randomGender());
      scriptArray = object.getAsJsonArray("script");
      if (scriptArray == null) {
        throw new IllegalArgumentException("Missing script array");
      }
    } else {
      scriptArray = root.getAsJsonArray();
      topic = DEFAULT_TOPIC;
      opponentName = DEFAULT_OPPONENT_NAME;
      opponentRole = DEFAULT_OPPONENT_ROLE;
      opponentGender = randomGender();
    }

    List<ScriptTurn> turns = new ArrayList<>();
    for (int i = 0; i < scriptArray.size(); i++) {
      JsonObject item = scriptArray.get(i).getAsJsonObject();
      String ko = getStringOrDefault(item, "ko", "");
      String en = getStringOrDefault(item, "en", "");
      String role = getStringOrDefault(item, "role", "");
      if (role.isEmpty()) {
        role = (i % 2 == 0) ? "model" : "user";
      }
      turns.add(new ScriptTurn(ko, en, role));
    }

    return new DialogueScript(topic, opponentName, opponentRole, opponentGender, turns);
  }

  @NonNull
  private String randomGender() {
    return random.nextBoolean() ? "male" : "female";
  }

  @NonNull
  private String getStringOrDefault(
      @NonNull JsonObject object, @NonNull String key, @NonNull String fallback) {
    if (!object.has(key) || object.get(key).isJsonNull()) {
      return fallback;
    }
    try {
      String value = object.get(key).getAsString();
      return value == null || value.isEmpty() ? fallback : value;
    } catch (Exception ignored) {
      return fallback;
    }
  }
}
