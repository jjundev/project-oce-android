package com.jjundev.oneclickeng.others;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.NaturalnessFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ParaphrasingLevel;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ReasonItem;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.StyledSentence;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.TextSegment;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ToneStyle;
import com.jjundev.oneclickeng.view.VennDiagramView;
import java.util.List;

/** Helper class to bind SentenceFeedback data to layout_sentence_feedback views */
public class SentenceFeedbackBinder {

  // Colors for text segments
  private static final int COLOR_INCORRECT = 0xFFF44336; // Red
  private static final int COLOR_CORRECTION = 0xFF4CAF50; // Green

  private final View rootView;
  private final int colorNormal;
  private final int colorHighlightBg;
  private final int colorHighlightText;
  private SentenceFeedback feedback;
  private TtsActionListener ttsActionListener;
  private ParaphrasingBookmarkDelegate paraphrasingBookmarkDelegate;

  public interface TtsActionListener {
    void onPlayTts(String text, ImageView speakerBtn);
  }

  public interface ParaphrasingBookmarkDelegate {
    boolean isBookmarked(String sentence);

    void onToggleBookmark(ParaphrasingLevel level, boolean targetSaved);
  }

  public void setTtsActionListener(TtsActionListener listener) {
    this.ttsActionListener = listener;
  }

  public void setParaphrasingBookmarkDelegate(ParaphrasingBookmarkDelegate delegate) {
    this.paraphrasingBookmarkDelegate = delegate;
  }

  // View references
  // View references
  private ShimmerFrameLayout skeletonWritingScore;
  private ShimmerFrameLayout skeletonGrammar;
  private ShimmerFrameLayout skeletonConceptualBridge;
  private ShimmerFrameLayout skeletonNaturalness;
  private ShimmerFrameLayout skeletonToneStyle;
  private ShimmerFrameLayout skeletonParaphrasing;

  private TextView tvWritingScoreLabel;
  private LinearLayout layoutWritingScoreContainer;
  private TextView tvWritingScore;
  private TextView tvEncouragementMessage;

  private TextView tvGrammarLabel;
  private View cardGrammar;
  private TextView tvGrammarExample;
  private TextView tvGrammarExplanation;

  private TextView tvConceptualBridgeLabel;
  private View cardConceptualBridge;
  private TextView tvConceptualBridgeExample;
  private TextView tvConceptualBridgeExplanation;
  private TextView tvConceptualBridgeVennGuide;
  private VennDiagramView viewConceptualBridgeVenn;

  private TextView tvNaturalnessLabel;
  private View cardNaturalness;
  private TextView tvNaturalSentenceExample;
  private TextView tvNaturalSentenceTranslation;
  private TextView tvNaturalnessExplanation;
  private TextView tvNaturalnessReason1;
  private TextView tvNaturalnessReason2;

  private TextView tvToneStyleLabel;
  private View cardToneStyle;
  private SeekBar seekBarToneStyle;
  private TextView tvToneSampleSentence;
  private TextView tvToneSampleTranslation;

  private TextView tvParaphrasingLabel;
  private View cardParaphrasing;
  private TextView tvParaphrasingLevel1Label;
  private TextView tvParaphrasingLevel1Content;
  private TextView tvParaphrasingLevel1Translation;
  private TextView tvSaveParaphrasingLevel1;
  private ImageButton btnSaveParaphrasingLevel1;
  private TextView tvParaphrasingLevel2Label;
  private TextView tvParaphrasingLevel2Content;
  private TextView tvParaphrasingLevel2Translation;
  private TextView tvSaveParaphrasingLevel2;
  private ImageButton btnSaveParaphrasingLevel2;
  private TextView tvParaphrasingLevel3Label;
  private TextView tvParaphrasingLevel3Content;
  private TextView tvParaphrasingLevel3Translation;
  private TextView tvSaveParaphrasingLevel3;
  private ImageButton btnSaveParaphrasingLevel3;
  private ImageView btnSpeakGrammar;
  private ImageView btnSpeakNatural;
  private ImageView btnSpeakTone;
  private ImageView btnSpeakParaphrasingLevel1;
  private ImageView btnSpeakParaphrasingLevel2;
  private ImageView btnSpeakParaphrasingLevel3;

  public SentenceFeedbackBinder(View rootView) {
    this.rootView = rootView;
    this.colorNormal = ContextCompat.getColor(rootView.getContext(), R.color.color_primary_text);
    this.colorHighlightBg =
        ContextCompat.getColor(rootView.getContext(), R.color.color_feedback_highlight_bg);
    this.colorHighlightText =
        ContextCompat.getColor(rootView.getContext(), R.color.color_feedback_highlight_text);
    bindViews();
  }

  private void bindViews() {
    skeletonWritingScore = rootView.findViewById(R.id.skeleton_writing_score);
    skeletonGrammar = rootView.findViewById(R.id.skeleton_grammar);
    skeletonConceptualBridge = rootView.findViewById(R.id.skeleton_conceptual_bridge);
    skeletonNaturalness = rootView.findViewById(R.id.skeleton_naturalness);
    skeletonToneStyle = rootView.findViewById(R.id.skeleton_tone_style);
    skeletonParaphrasing = rootView.findViewById(R.id.skeleton_paraphrasing);

    tvWritingScoreLabel = rootView.findViewById(R.id.tv_writing_score_label);
    layoutWritingScoreContainer = rootView.findViewById(R.id.layout_writing_score_container);
    tvWritingScore = rootView.findViewById(R.id.tv_writing_score);
    tvEncouragementMessage = rootView.findViewById(R.id.tv_encouragement_message_sentence);

    tvGrammarLabel = rootView.findViewById(R.id.tv_grammar_label);
    cardGrammar = rootView.findViewById(R.id.card_grammar);
    tvGrammarExample = rootView.findViewById(R.id.tv_grammar_example);
    tvGrammarExplanation = rootView.findViewById(R.id.tv_grammar_explanation);

    tvConceptualBridgeLabel = rootView.findViewById(R.id.tv_conceptual_bridge_label);
    cardConceptualBridge = rootView.findViewById(R.id.card_conceptual_bridge);
    tvConceptualBridgeExample = rootView.findViewById(R.id.tv_conceptual_bridge_example);
    tvConceptualBridgeExplanation = rootView.findViewById(R.id.tv_conceptual_bridge_explanation);
    tvConceptualBridgeVennGuide = rootView.findViewById(R.id.tv_conceptual_bridge_venn_guide);
    viewConceptualBridgeVenn = rootView.findViewById(R.id.view_conceptual_bridge_venn);

    tvNaturalnessLabel = rootView.findViewById(R.id.tv_naturalness_label);
    cardNaturalness = rootView.findViewById(R.id.card_naturalness);
    tvNaturalSentenceExample = rootView.findViewById(R.id.tv_natural_sentence_example);
    tvNaturalSentenceTranslation = rootView.findViewById(R.id.tv_natural_sentence_translation);
    tvNaturalnessExplanation = rootView.findViewById(R.id.tv_naturalness_explanation);
    tvNaturalnessReason1 = rootView.findViewById(R.id.tv_naturalness_reason_1);
    tvNaturalnessReason2 = rootView.findViewById(R.id.tv_naturalness_reason_2);

    tvToneStyleLabel = rootView.findViewById(R.id.tv_tone_style_label);
    cardToneStyle = rootView.findViewById(R.id.card_tone_style);
    seekBarToneStyle = rootView.findViewById(R.id.seek_bar_tone_style);
    tvToneSampleSentence = rootView.findViewById(R.id.tv_tone_sample_sentence);
    tvToneSampleTranslation = rootView.findViewById(R.id.tv_tone_sample_translation);

    tvParaphrasingLabel = rootView.findViewById(R.id.tv_paraphrasing_label);
    cardParaphrasing = rootView.findViewById(R.id.card_paraphrasing);
    tvParaphrasingLevel1Label = rootView.findViewById(R.id.tv_paraphrasing_level_1_label);
    tvParaphrasingLevel1Content = rootView.findViewById(R.id.tv_paraphrasing_level_1_content);
    tvParaphrasingLevel1Translation =
        rootView.findViewById(R.id.tv_paraphrasing_level_1_translation);
    tvSaveParaphrasingLevel1 = rootView.findViewById(R.id.tv_save_paraphrasing_level_1);
    btnSaveParaphrasingLevel1 = rootView.findViewById(R.id.btn_save_paraphrasing_level_1);
    tvParaphrasingLevel2Label = rootView.findViewById(R.id.tv_paraphrasing_level_2_label);
    tvParaphrasingLevel2Content = rootView.findViewById(R.id.tv_paraphrasing_level_2_content);
    tvParaphrasingLevel2Translation =
        rootView.findViewById(R.id.tv_paraphrasing_level_2_translation);
    tvSaveParaphrasingLevel2 = rootView.findViewById(R.id.tv_save_paraphrasing_level_2);
    btnSaveParaphrasingLevel2 = rootView.findViewById(R.id.btn_save_paraphrasing_level_2);
    tvParaphrasingLevel3Label = rootView.findViewById(R.id.tv_paraphrasing_level_3_label);
    tvParaphrasingLevel3Content = rootView.findViewById(R.id.tv_paraphrasing_level_3_content);
    tvParaphrasingLevel3Translation =
        rootView.findViewById(R.id.tv_paraphrasing_level_3_translation);
    tvSaveParaphrasingLevel3 = rootView.findViewById(R.id.tv_save_paraphrasing_level_3);
    btnSaveParaphrasingLevel3 = rootView.findViewById(R.id.btn_save_paraphrasing_level_3);
    btnSpeakGrammar = rootView.findViewById(R.id.btn_speak_grammar);
    btnSpeakNatural = rootView.findViewById(R.id.btn_speak_natural);
    btnSpeakTone = rootView.findViewById(R.id.btn_speak_tone);
    btnSpeakParaphrasingLevel1 = rootView.findViewById(R.id.btn_speak_paraphrasing_level_1);
    btnSpeakParaphrasingLevel2 = rootView.findViewById(R.id.btn_speak_paraphrasing_level_2);
    btnSpeakParaphrasingLevel3 = rootView.findViewById(R.id.btn_speak_paraphrasing_level_3);
  }

  /** Bind SentenceFeedback data to all views */
  public void bind(SentenceFeedback feedback) {
    this.feedback = feedback;
    if (feedback == null) {
      return;
    }

    bindWritingScore();
    bindGrammar();
    bindConceptualBridge();
    bindNaturalness();
    bindToneStyle();
    bindParaphrasing();
    setupTtsListeners();
  }

  public void showAllSkeletons() {
    if (skeletonWritingScore != null) {
      skeletonWritingScore.setVisibility(View.VISIBLE);
      skeletonWritingScore.startShimmer();
    }
    if (skeletonGrammar != null) {
      skeletonGrammar.setVisibility(View.VISIBLE);
      skeletonGrammar.startShimmer();
    }
    if (skeletonConceptualBridge != null) {
      skeletonConceptualBridge.setVisibility(View.VISIBLE);
      skeletonConceptualBridge.startShimmer();
    }
    if (skeletonNaturalness != null) {
      skeletonNaturalness.setVisibility(View.VISIBLE);
      skeletonNaturalness.startShimmer();
    }
    if (skeletonToneStyle != null) {
      skeletonToneStyle.setVisibility(View.VISIBLE);
      skeletonToneStyle.startShimmer();
    }
    if (skeletonParaphrasing != null) {
      skeletonParaphrasing.setVisibility(View.VISIBLE);
      skeletonParaphrasing.startShimmer();
    }
  }

  public void hideAllSkeletons() {
    if (skeletonWritingScore != null) {
      skeletonWritingScore.stopShimmer();
      skeletonWritingScore.setVisibility(View.GONE);
    }
    if (skeletonGrammar != null) {
      skeletonGrammar.stopShimmer();
      skeletonGrammar.setVisibility(View.GONE);
    }
    if (skeletonConceptualBridge != null) {
      skeletonConceptualBridge.stopShimmer();
      skeletonConceptualBridge.setVisibility(View.GONE);
    }
    if (skeletonNaturalness != null) {
      skeletonNaturalness.stopShimmer();
      skeletonNaturalness.setVisibility(View.GONE);
    }
    if (skeletonToneStyle != null) {
      skeletonToneStyle.stopShimmer();
      skeletonToneStyle.setVisibility(View.GONE);
    }
    if (skeletonParaphrasing != null) {
      skeletonParaphrasing.stopShimmer();
      skeletonParaphrasing.setVisibility(View.GONE);
    }
  }

  private void hideSectionSkeleton(String sectionKey) {
    ShimmerFrameLayout targetSkeleton = null;
    switch (sectionKey) {
      case "writingScore":
        targetSkeleton = skeletonWritingScore;
        break;
      case "grammar":
        targetSkeleton = skeletonGrammar;
        break;
      case "conceptualBridge":
        targetSkeleton = skeletonConceptualBridge;
        break;
      case "naturalness":
        targetSkeleton = skeletonNaturalness;
        break;
      case "toneStyle":
        targetSkeleton = skeletonToneStyle;
        break;
      case "paraphrasing":
        targetSkeleton = skeletonParaphrasing;
        break;
    }

    if (targetSkeleton != null) {
      targetSkeleton.stopShimmer();
      targetSkeleton.setVisibility(View.GONE);
    }
  }

  public void hideAllSections() {
    if (tvWritingScoreLabel != null) tvWritingScoreLabel.setVisibility(View.GONE);
    if (layoutWritingScoreContainer != null) layoutWritingScoreContainer.setVisibility(View.GONE);
    if (tvEncouragementMessage != null) tvEncouragementMessage.setVisibility(View.GONE);

    if (tvGrammarLabel != null) tvGrammarLabel.setVisibility(View.GONE);
    if (cardGrammar != null) cardGrammar.setVisibility(View.GONE);

    if (tvConceptualBridgeLabel != null) tvConceptualBridgeLabel.setVisibility(View.GONE);
    if (cardConceptualBridge != null) cardConceptualBridge.setVisibility(View.GONE);

    if (tvNaturalnessLabel != null) tvNaturalnessLabel.setVisibility(View.GONE);
    if (cardNaturalness != null) cardNaturalness.setVisibility(View.GONE);

    if (tvToneStyleLabel != null) tvToneStyleLabel.setVisibility(View.GONE);
    if (cardToneStyle != null) cardToneStyle.setVisibility(View.GONE);

    if (tvParaphrasingLabel != null) tvParaphrasingLabel.setVisibility(View.GONE);
    if (cardParaphrasing != null) cardParaphrasing.setVisibility(View.GONE);
  }

  public void bindSection(String sectionKey, SentenceFeedback partialFeedback) {
    if (this.feedback == null) {
      this.feedback = new SentenceFeedback();
    }

    hideSectionSkeleton(sectionKey);

    switch (sectionKey) {
      case "writingScore":
        if (partialFeedback.getWritingScore() != null) {
          this.feedback.setWritingScore(partialFeedback.getWritingScore());
          bindWritingScore();
        }
        break;
      case "grammar":
        if (partialFeedback.getGrammar() != null) {
          this.feedback.setGrammar(partialFeedback.getGrammar());
          bindGrammar();
        }
        break;
      case "conceptualBridge":
        if (partialFeedback.getConceptualBridge() != null) {
          this.feedback.setConceptualBridge(partialFeedback.getConceptualBridge());
          bindConceptualBridge();
        }
        break;
      case "naturalness":
        if (partialFeedback.getNaturalness() != null) {
          this.feedback.setNaturalness(partialFeedback.getNaturalness());
          bindNaturalness();
        }
        break;
      case "toneStyle":
        if (partialFeedback.getToneStyle() != null) {
          this.feedback.setToneStyle(partialFeedback.getToneStyle());
          bindToneStyle();
        }
        break;
      case "paraphrasing":
        if (partialFeedback.getParaphrasing() != null) {
          this.feedback.setParaphrasing(partialFeedback.getParaphrasing());
          bindParaphrasing();
        }
        break;
    }
  }

  private void setupTtsListeners() {
    if (btnSpeakGrammar != null && feedback != null && feedback.getGrammar() != null) {
      btnSpeakGrammar.setOnClickListener(
          v -> {
            if (ttsActionListener != null && feedback != null && feedback.getGrammar() != null) {
              String ttsText = getTtsText(feedback.getGrammar().getCorrectedSentence());
              ttsActionListener.onPlayTts(ttsText, btnSpeakGrammar);
            }
          });
    }

    if (btnSpeakNatural != null && feedback != null && feedback.getNaturalness() != null) {
      btnSpeakNatural.setOnClickListener(
          v -> {
            if (ttsActionListener != null
                && feedback != null
                && feedback.getNaturalness() != null) {
              String ttsText = getTtsText(feedback.getNaturalness().getNaturalSentence());
              ttsActionListener.onPlayTts(ttsText, btnSpeakNatural);
            }
          });
    }

    if (btnSpeakTone != null && tvToneSampleSentence != null) {
      btnSpeakTone.setOnClickListener(
          v -> {
            if (ttsActionListener != null) {
              ttsActionListener.onPlayTts(tvToneSampleSentence.getText().toString(), btnSpeakTone);
            }
          });
    }

    if (btnSpeakParaphrasingLevel1 != null && tvParaphrasingLevel1Content != null) {
      btnSpeakParaphrasingLevel1.setOnClickListener(
          v -> {
            if (ttsActionListener != null) {
              ttsActionListener.onPlayTts(
                  tvParaphrasingLevel1Content.getText().toString(), btnSpeakParaphrasingLevel1);
            }
          });
    }

    if (btnSpeakParaphrasingLevel2 != null && tvParaphrasingLevel2Content != null) {
      btnSpeakParaphrasingLevel2.setOnClickListener(
          v -> {
            if (ttsActionListener != null) {
              ttsActionListener.onPlayTts(
                  tvParaphrasingLevel2Content.getText().toString(), btnSpeakParaphrasingLevel2);
            }
          });
    }

    if (btnSpeakParaphrasingLevel3 != null && tvParaphrasingLevel3Content != null) {
      btnSpeakParaphrasingLevel3.setOnClickListener(
          v -> {
            if (ttsActionListener != null) {
              ttsActionListener.onPlayTts(
                  tvParaphrasingLevel3Content.getText().toString(), btnSpeakParaphrasingLevel3);
            }
          });
    }
  }

  private void bindWritingScore() {
    if (feedback.getWritingScore() == null) {
      return;
    }

    // Make visible
    if (tvWritingScoreLabel != null) tvWritingScoreLabel.setVisibility(View.VISIBLE);
    if (layoutWritingScoreContainer != null)
      layoutWritingScoreContainer.setVisibility(View.VISIBLE);

    int score = feedback.getWritingScore().getScore();
    String encouragement = feedback.getWritingScore().getEncouragementMessage();
    int scoreColor = feedback.getWritingScore().getScoreColor();

    if (tvWritingScore != null) {
      tvWritingScore.setText(String.valueOf(score));
      tvWritingScore.setTextColor(scoreColor);
    }

    if (tvEncouragementMessage != null && encouragement != null) {
      tvEncouragementMessage.setText(encouragement);
      tvEncouragementMessage.setVisibility(View.VISIBLE);
    }
  }

  private void bindGrammar() {
    if (feedback.getGrammar() == null) {
      return;
    }

    // Make visible
    if (tvGrammarLabel != null) tvGrammarLabel.setVisibility(View.VISIBLE);
    if (cardGrammar != null) cardGrammar.setVisibility(View.VISIBLE);

    if (tvGrammarExample != null && feedback.getGrammar().getCorrectedSentence() != null) {
      SpannableStringBuilder spannable =
          buildStyledSentence(feedback.getGrammar().getCorrectedSentence());
      tvGrammarExample.setText(spannable);
    }

    if (tvGrammarExplanation != null && feedback.getGrammar().getExplanation() != null) {
      tvGrammarExplanation.setText(feedback.getGrammar().getExplanation());
    }

    // Bind speaker button if not already bound
    if (btnSpeakGrammar != null && ttsActionListener != null) {
      // Re-setup listener for this specific button as it might not be set in global
      // bind
      setupTtsListeners();
    }
  }

  private void bindConceptualBridge() {
    if (feedback.getConceptualBridge() == null) {
      return;
    }

    // Make visible
    if (tvConceptualBridgeLabel != null) tvConceptualBridgeLabel.setVisibility(View.VISIBLE);
    if (cardConceptualBridge != null) cardConceptualBridge.setVisibility(View.VISIBLE);

    if (tvConceptualBridgeExample != null
        && feedback.getConceptualBridge().getLiteralTranslation() != null) {
      tvConceptualBridgeExample.setText(feedback.getConceptualBridge().getLiteralTranslation());
    }

    if (tvConceptualBridgeExplanation != null
        && feedback.getConceptualBridge().getExplanation() != null) {
      tvConceptualBridgeExplanation.setText(feedback.getConceptualBridge().getExplanation());
    }

    if (tvConceptualBridgeVennGuide != null
        && feedback.getConceptualBridge().getVennDiagramGuide() != null) {
      tvConceptualBridgeVennGuide.setText(feedback.getConceptualBridge().getVennDiagramGuide());
    }

    if (viewConceptualBridgeVenn != null
        && feedback.getConceptualBridge().getVennDiagram() != null) {
      viewConceptualBridgeVenn.setVennDiagram(feedback.getConceptualBridge().getVennDiagram());
    }
  }

  private void bindNaturalness() {
    NaturalnessFeedback naturalness = feedback.getNaturalness();
    if (naturalness == null) {
      return;
    }

    // Make visible
    if (tvNaturalnessLabel != null) tvNaturalnessLabel.setVisibility(View.VISIBLE);
    if (cardNaturalness != null) cardNaturalness.setVisibility(View.VISIBLE);

    if (tvNaturalSentenceExample != null && naturalness.getNaturalSentence() != null) {
      SpannableStringBuilder spannable = buildStyledSentence(naturalness.getNaturalSentence());
      tvNaturalSentenceExample.setText(spannable);
    }

    if (tvNaturalSentenceTranslation != null) {
      String translation = naturalness.getNaturalSentenceTranslation();
      if (translation != null && !translation.isEmpty()) {
        tvNaturalSentenceTranslation.setText(translation);
        tvNaturalSentenceTranslation.setVisibility(View.VISIBLE);
      } else {
        tvNaturalSentenceTranslation.setVisibility(View.GONE);
      }
    }

    if (tvNaturalnessExplanation != null && naturalness.getExplanation() != null) {
      tvNaturalnessExplanation.setText(naturalness.getExplanation());
    }

    List<ReasonItem> reasons = naturalness.getReasons();

    // Reset visibility
    if (tvNaturalnessReason1 != null) tvNaturalnessReason1.setVisibility(View.GONE);
    if (tvNaturalnessReason2 != null) tvNaturalnessReason2.setVisibility(View.GONE);

    if (reasons != null) {
      if (tvNaturalnessReason1 != null && reasons.size() > 0) {
        tvNaturalnessReason1.setText(formatReason(reasons.get(0)));
        tvNaturalnessReason1.setVisibility(View.VISIBLE);
      }
      if (tvNaturalnessReason2 != null && reasons.size() > 1) {
        tvNaturalnessReason2.setText(formatReason(reasons.get(1)));
        tvNaturalnessReason2.setVisibility(View.VISIBLE);
      }
    }
  }

  private void bindToneStyle() {
    ToneStyle toneStyle = feedback.getToneStyle();
    if (toneStyle == null) {
      return;
    }

    // Make visible
    if (tvToneStyleLabel != null) tvToneStyleLabel.setVisibility(View.VISIBLE);
    if (cardToneStyle != null) cardToneStyle.setVisibility(View.VISIBLE);

    if (seekBarToneStyle != null) {
      seekBarToneStyle.setProgress(toneStyle.getDefaultLevel());

      // Set initial sentence
      if (tvToneSampleSentence != null) {
        String initialSentence = toneStyle.getSentenceForLevel(toneStyle.getDefaultLevel());
        tvToneSampleSentence.setText(initialSentence);
      }

      if (tvToneSampleTranslation != null) {
        String initialTranslation = toneStyle.getTranslationForLevel(toneStyle.getDefaultLevel());
        if (initialTranslation != null && !initialTranslation.isEmpty()) {
          tvToneSampleTranslation.setText(initialTranslation);
          tvToneSampleTranslation.setVisibility(View.VISIBLE);
        } else {
          tvToneSampleTranslation.setVisibility(View.GONE);
        }
      }

      // Setup listener
      seekBarToneStyle.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
              if (tvToneSampleSentence != null) {
                String sentence = toneStyle.getSentenceForLevel(progress);
                tvToneSampleSentence.setText(sentence);
              }
              if (tvToneSampleTranslation != null) {
                String translation = toneStyle.getTranslationForLevel(progress);
                if (translation != null && !translation.isEmpty()) {
                  tvToneSampleTranslation.setText(translation);
                  tvToneSampleTranslation.setVisibility(View.VISIBLE);
                } else {
                  tvToneSampleTranslation.setVisibility(View.GONE);
                }
              }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
              // Not used
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
              // Not used
            }
          });
    }
  }

  private void bindParaphrasing() {
    List<ParaphrasingLevel> levels = feedback.getParaphrasing();
    if (levels == null) {
      return;
    }

    // Make visible
    if (tvParaphrasingLabel != null) tvParaphrasingLabel.setVisibility(View.VISIBLE);
    if (cardParaphrasing != null) cardParaphrasing.setVisibility(View.VISIBLE);

    resetParaphrasingBookmarkUi();

    for (ParaphrasingLevel level : levels) {
      switch (level.getLevel()) {
        case 1:
          if (tvParaphrasingLevel1Label != null) {
            tvParaphrasingLevel1Label.setText(level.getLabel());
          }
          if (tvParaphrasingLevel1Content != null) {
            tvParaphrasingLevel1Content.setText(level.getSentence());
          }
          if (tvParaphrasingLevel1Translation != null) {
            String translation = level.getSentenceTranslation();
            if (translation != null && !translation.isEmpty()) {
              tvParaphrasingLevel1Translation.setText(translation);
              tvParaphrasingLevel1Translation.setVisibility(View.VISIBLE);
            } else {
              tvParaphrasingLevel1Translation.setVisibility(View.GONE);
            }
          }
          bindParaphrasingBookmark(level, btnSaveParaphrasingLevel1, tvSaveParaphrasingLevel1);
          break;
        case 2:
          if (tvParaphrasingLevel2Label != null) {
            tvParaphrasingLevel2Label.setText(level.getLabel());
          }
          if (tvParaphrasingLevel2Content != null) {
            tvParaphrasingLevel2Content.setText(level.getSentence());
          }
          if (tvParaphrasingLevel2Translation != null) {
            String translation = level.getSentenceTranslation();
            if (translation != null && !translation.isEmpty()) {
              tvParaphrasingLevel2Translation.setText(translation);
              tvParaphrasingLevel2Translation.setVisibility(View.VISIBLE);
            } else {
              tvParaphrasingLevel2Translation.setVisibility(View.GONE);
            }
          }
          bindParaphrasingBookmark(level, btnSaveParaphrasingLevel2, tvSaveParaphrasingLevel2);
          break;
        case 3:
          if (tvParaphrasingLevel3Label != null) {
            tvParaphrasingLevel3Label.setText(level.getLabel());
          }
          if (tvParaphrasingLevel3Content != null) {
            tvParaphrasingLevel3Content.setText(level.getSentence());
          }
          if (tvParaphrasingLevel3Translation != null) {
            String translation = level.getSentenceTranslation();
            if (translation != null && !translation.isEmpty()) {
              tvParaphrasingLevel3Translation.setText(translation);
              tvParaphrasingLevel3Translation.setVisibility(View.VISIBLE);
            } else {
              tvParaphrasingLevel3Translation.setVisibility(View.GONE);
            }
          }
          bindParaphrasingBookmark(level, btnSaveParaphrasingLevel3, tvSaveParaphrasingLevel3);
          break;
      }
    }
  }

  private void resetParaphrasingBookmarkUi() {
    applyBookmarkUiState(btnSaveParaphrasingLevel1, tvSaveParaphrasingLevel1, false);
    applyBookmarkUiState(btnSaveParaphrasingLevel2, tvSaveParaphrasingLevel2, false);
    applyBookmarkUiState(btnSaveParaphrasingLevel3, tvSaveParaphrasingLevel3, false);
  }

  private void bindParaphrasingBookmark(
      ParaphrasingLevel level, ImageButton saveButton, TextView saveStatusLabel) {
    if (saveButton == null || saveStatusLabel == null || level == null) {
      return;
    }

    String sentence = level.getSentence();
    if (sentence == null || sentence.trim().isEmpty()) {
      saveButton.setOnClickListener(null);
      applyBookmarkUiState(saveButton, saveStatusLabel, false);
      return;
    }

    boolean isSaved =
        paraphrasingBookmarkDelegate != null && paraphrasingBookmarkDelegate.isBookmarked(sentence);
    applyBookmarkUiState(saveButton, saveStatusLabel, isSaved);

    saveButton.setOnClickListener(
        v -> {
          boolean targetSaved = !saveButton.isSelected();
          if (paraphrasingBookmarkDelegate != null) {
            paraphrasingBookmarkDelegate.onToggleBookmark(level, targetSaved);
          }
          boolean finalSaved =
              paraphrasingBookmarkDelegate != null
                  ? paraphrasingBookmarkDelegate.isBookmarked(sentence)
                  : targetSaved;
          applyBookmarkUiState(saveButton, saveStatusLabel, finalSaved);
        });
  }

  private void applyBookmarkUiState(
      ImageButton saveButton, TextView saveStatusLabel, boolean isSaved) {
    if (saveButton != null) {
      int iconRes = isSaved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_border;
      int tintColorRes = isSaved ? R.color.save_icon_gold : R.color.grey_400;
      int tintColor = ContextCompat.getColor(saveButton.getContext(), tintColorRes);
      saveButton.setSelected(isSaved);
      saveButton.setImageResource(iconRes);
      ImageViewCompat.setImageTintList(saveButton, ColorStateList.valueOf(tintColor));
    }
    if (saveStatusLabel != null) {
      saveStatusLabel.setVisibility(isSaved ? View.VISIBLE : View.GONE);
    }
  }

  /** Build SpannableStringBuilder from StyledSentence */
  private SpannableStringBuilder buildStyledSentence(StyledSentence styledSentence) {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    if (styledSentence.getSegments() == null) {
      return builder;
    }

    for (TextSegment segment : styledSentence.getSegments()) {
      int start = builder.length();
      builder.append(segment.getText());
      int end = builder.length();

      applySegmentStyle(builder, segment, start, end);
    }

    return builder;
  }

  private void applySegmentStyle(
      SpannableStringBuilder builder, TextSegment segment, int start, int end) {
    String type = segment.getType();
    if (type == null) {
      type = TextSegment.TYPE_NORMAL;
    }

    switch (type) {
      case TextSegment.TYPE_NORMAL:
        builder.setSpan(
            new ForegroundColorSpan(colorNormal), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;

      case TextSegment.TYPE_INCORRECT:
        builder.setSpan(
            new ForegroundColorSpan(COLOR_INCORRECT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;

      case TextSegment.TYPE_CORRECTION:
        builder.setSpan(
            new ForegroundColorSpan(COLOR_CORRECTION),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;

      case TextSegment.TYPE_HIGHLIGHT:
        builder.setSpan(
            new BackgroundColorSpan(colorHighlightBg),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(
            new ForegroundColorSpan(colorHighlightText),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;
    }
  }

  /** Format a ReasonItem as "**keyword**: description" */
  private SpannableStringBuilder formatReason(ReasonItem reason) {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    if (reason.getKeyword() != null) {
      int start = builder.length();
      builder.append(reason.getKeyword());
      int end = builder.length();
      builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    builder.append(": ");

    if (reason.getDescription() != null) {
      builder.append(reason.getDescription());
    }

    return builder;
  }

  /** Get text for TTS from StyledSentence, skipping incorrect segments */
  private String getTtsText(StyledSentence styledSentence) {
    if (styledSentence == null || styledSentence.getSegments() == null) {
      return "";
    }

    StringBuilder ttsBuilder = new StringBuilder();
    for (TextSegment segment : styledSentence.getSegments()) {
      if (!TextSegment.TYPE_INCORRECT.equals(segment.getType())) {
        ttsBuilder.append(segment.getText());
      }
    }
    return ttsBuilder.toString().trim();
  }
}
