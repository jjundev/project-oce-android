package com.jjundev.oneclickeng.fragment.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjundev.oneclickeng.dialog.QuizGenerateDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LearningHistoryViewModel extends ViewModel {

  private final MutableLiveData<List<HistoryItemWrapper>> historyItems =
      new MutableLiveData<>(new ArrayList<>());

  public LiveData<List<HistoryItemWrapper>> getHistoryItems() {
    return historyItems;
  }

  public void loadSavedCards() {
    com.google.firebase.auth.FirebaseUser user =
        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      historyItems.setValue(new ArrayList<>());
      return;
    }

    com.google.firebase.firestore.FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .collection("saved_cards")
        .addSnapshotListener(
            (value, error) -> {
              if (error != null) {
                return;
              }
              if (value != null) {
                List<HistoryItemWrapper> items = new ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                  com.jjundev.oneclickeng.fragment.history.model.SavedCard card =
                      doc.toObject(com.jjundev.oneclickeng.fragment.history.model.SavedCard.class);
                  if (card != null) {
                    if ("WORD".equals(card.getCardType())) {
                      SummaryData.WordItem w =
                          new SummaryData.WordItem(
                              card.getEnglish(),
                              card.getKorean(),
                              card.getExampleEnglish(),
                              card.getExampleKorean());
                      items.add(
                          new HistoryItemWrapper(
                              HistoryItemWrapper.TYPE_WORD, w, card.getTimestamp()));
                    } else if ("EXPRESSION".equals(card.getCardType())) {
                      SummaryData.ExpressionItem e =
                          new SummaryData.ExpressionItem(
                              card.getType(),
                              card.getKoreanPrompt(),
                              card.getBefore(),
                              card.getAfter(),
                              card.getExplanation(),
                              card.getAfterHighlights());
                      items.add(
                          new HistoryItemWrapper(
                              HistoryItemWrapper.TYPE_EXPRESSION, e, card.getTimestamp()));
                    } else if ("SENTENCE".equals(card.getCardType())) {
                      SummaryData.SentenceItem s =
                          new SummaryData.SentenceItem(card.getEnglish(), card.getKorean());
                      items.add(
                          new HistoryItemWrapper(
                              HistoryItemWrapper.TYPE_SENTENCE, s, card.getTimestamp()));
                    }
                  }
                }
                items.sort(
                    (a, b) -> Long.compare(b.getLearnedAtEpochMs(), a.getLearnedAtEpochMs()));
                historyItems.setValue(items);
              }
            });
  }

  public void removeCard(HistoryItemWrapper itemWrapper) {
    com.google.firebase.auth.FirebaseUser user =
        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) return;

    Object itemData = itemWrapper.getData();
    String cardId = generateDeterministicId(itemData);

    com.google.firebase.firestore.FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .collection("saved_cards")
        .document(cardId)
        .delete();
  }

  private String generateDeterministicId(Object itemData) {
    String uniqueString = "";
    if (itemData instanceof SummaryData.WordItem) {
      SummaryData.WordItem w = (SummaryData.WordItem) itemData;
      uniqueString = w.getEnglish() + "_" + w.getKorean();
    } else if (itemData instanceof SummaryData.SentenceItem) {
      SummaryData.SentenceItem s = (SummaryData.SentenceItem) itemData;
      uniqueString = s.getEnglish() + "_" + s.getKorean();
    } else if (itemData instanceof SummaryData.ExpressionItem) {
      SummaryData.ExpressionItem e = (SummaryData.ExpressionItem) itemData;
      uniqueString = e.getBefore() + "_" + e.getAfter();
    } else {
      uniqueString = UUID.randomUUID().toString();
    }
    return UUID.nameUUIDFromBytes(uniqueString.getBytes()).toString();
  }

  public SummaryData generateQuizSeed(int periodBucket, int currentTab) {
    List<HistoryItemWrapper> allItems = historyItems.getValue();
    if (allItems == null || allItems.isEmpty()) {
      return null;
    }

    long now = System.currentTimeMillis();
    long oneWeek = 7L * 24 * 60 * 60 * 1000;
    long startTime = 0;
    long endTime = now;

    if (periodBucket == QuizGenerateDialog.PERIOD_1W) {
      startTime = now - oneWeek;
    } else if (periodBucket == QuizGenerateDialog.PERIOD_2W) {
      startTime = now - 2 * oneWeek;
      endTime = now - oneWeek;
    } else if (periodBucket == QuizGenerateDialog.PERIOD_3W) {
      startTime = now - 3 * oneWeek;
      endTime = now - 2 * oneWeek;
    } else if (periodBucket == QuizGenerateDialog.PERIOD_OLDER) {
      endTime = now - 3 * oneWeek;
    }

    List<SummaryData.WordItem> words = new ArrayList<>();
    List<SummaryData.ExpressionItem> expressions = new ArrayList<>();

    for (HistoryItemWrapper item : allItems) {
      long epochMs = item.getLearnedAtEpochMs();
      if (epochMs >= startTime && epochMs <= endTime) {
        if (currentTab == 1 && item.getType() == HistoryItemWrapper.TYPE_WORD) {
          words.add((SummaryData.WordItem) item.getData());
        } else if (currentTab == 0 && item.getType() == HistoryItemWrapper.TYPE_EXPRESSION) {
          expressions.add((SummaryData.ExpressionItem) item.getData());
        } else if (currentTab == 2 && item.getType() == HistoryItemWrapper.TYPE_SENTENCE) {
          SummaryData.SentenceItem sentence = (SummaryData.SentenceItem) item.getData();
          expressions.add(
              new SummaryData.ExpressionItem(
                  "sentence", sentence.getKorean(), "", sentence.getEnglish(), ""));
        }
      }
    }

    if (words.isEmpty() && expressions.isEmpty()) {
      return null;
    }

    SummaryData result = new SummaryData();
    result.setWords(words);
    result.setExpressions(expressions);
    return result;
  }
}
