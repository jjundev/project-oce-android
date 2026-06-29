package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.R;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
    implements LearningChatRenderer.ChatAdapterHeightAdjuster {
  public interface OnPlayTtsRequestListener {
    void onPlayTts(String text, ImageView speakerButton, String gender);
  }

  public interface OnPlayRecordedAudioRequestListener {
    void onPlayRecordedAudio(@Nullable byte[] audioData, ImageView speakerButton);
  }

  private final List<ChatMessage> messages;
  private final OnPlayTtsRequestListener onPlayTtsRequestListener;
  private final OnPlayRecordedAudioRequestListener onPlayRecordedAudioRequestListener;
  private final RecyclerView recyclerView;
  private int footerHeight = 0;
  private String opponentName;
  private String opponentGender;
  @Nullable private ImageView latestAiSpeakerButton;

  public ChatAdapter(
      @NonNull List<ChatMessage> messages,
      @NonNull OnPlayTtsRequestListener onPlayTtsRequestListener,
      @NonNull OnPlayRecordedAudioRequestListener onPlayRecordedAudioRequestListener,
      @NonNull String opponentName,
      @NonNull String opponentGender,
      @Nullable RecyclerView recyclerView) {
    this.messages = messages;
    this.onPlayTtsRequestListener = onPlayTtsRequestListener;
    this.onPlayRecordedAudioRequestListener = onPlayRecordedAudioRequestListener;
    this.opponentName = opponentName;
    this.opponentGender = opponentGender;
    this.recyclerView = recyclerView;
  }

  public void updateOpponentProfile(@NonNull String opponentName, @NonNull String opponentGender) {
    this.opponentName = opponentName;
    this.opponentGender = opponentGender;
  }

  @Nullable
  public ImageView getLatestAiSpeakerButton() {
    return latestAiSpeakerButton;
  }

  public void postUi(@NonNull Runnable action) {
    if (recyclerView == null) {
      action.run();
      return;
    }
    recyclerView.post(action);
  }

  @Override
  public void setFooterHeight(int height) {
    if (height < 0) {
      height = 0;
    }
    if (this.footerHeight == height) {
      return;
    }

    this.footerHeight = height;
    if (recyclerView != null) {
      int left = recyclerView.getPaddingLeft();
      int top = recyclerView.getPaddingTop();
      int right = recyclerView.getPaddingRight();
      recyclerView.setPadding(left, top, right, height);
    }
  }

  @Override
  public int getItemViewType(int position) {
    return messages.get(position).getType();
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == ChatMessage.TYPE_AI) {
      View view = inflater.inflate(R.layout.item_message_ai, parent, false);
      return new AiMessageViewHolder(view);
    }
    if (viewType == ChatMessage.TYPE_SKELETON) {
      View view = inflater.inflate(R.layout.item_message_skeleton, parent, false);
      return new SkeletonViewHolder(view);
    }

    View view = inflater.inflate(R.layout.item_message_user, parent, false);
    return new UserMessageViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    ChatMessage message = messages.get(position);
    if (holder instanceof AiMessageViewHolder) {
      ((AiMessageViewHolder) holder).bind(message);
    } else if (holder instanceof UserMessageViewHolder) {
      ((UserMessageViewHolder) holder).bind(message);
    }
  }

  @Override
  public int getItemCount() {
    return messages.size();
  }

  private class AiMessageViewHolder extends RecyclerView.ViewHolder {
    private final TextView tvMessageOriginal;
    private final TextView btnShowTranslation;
    private final ImageView btnSpeaker;
    private final ImageView imgProfile;
    private boolean isShowingEnglish = true;

    AiMessageViewHolder(View itemView) {
      super(itemView);
      tvMessageOriginal = itemView.findViewById(R.id.tv_message_original);
      btnShowTranslation = itemView.findViewById(R.id.btn_show_translation);
      btnSpeaker = itemView.findViewById(R.id.btn_speaker);
      imgProfile = itemView.findViewById(R.id.img_profile);
    }

    void bind(ChatMessage message) {
      latestAiSpeakerButton = btnSpeaker;
      isShowingEnglish = true;
      tvMessageOriginal.setText(message.getEngMessage());
      btnShowTranslation.setText("해석 보기");

      if (imgProfile != null) {
        if ("male".equalsIgnoreCase(opponentGender)) {
          imgProfile.setImageResource(R.drawable.ic_male_profile);
        } else {
          imgProfile.setImageResource(R.drawable.ic_female_profile);
        }
      }

      btnSpeaker.setImageResource(R.drawable.ic_speaker);
      btnSpeaker.setOnClickListener(
          v ->
              onPlayTtsRequestListener.onPlayTts(
                  message.getEngMessage(), btnSpeaker, opponentGender));

      TextView tvAiName = itemView.findViewById(R.id.tv_ai_name);
      if (tvAiName != null) {
        tvAiName.setText(opponentName);
      }

      btnShowTranslation.setOnClickListener(
          v -> {
            if (isShowingEnglish) {
              String translation = message.getKoMessage();
              tvMessageOriginal.setText(
                  translation == null ? message.getEngMessage() : translation);
              btnShowTranslation.setText("원문 보기");
              isShowingEnglish = false;
            } else {
              tvMessageOriginal.setText(message.getEngMessage());
              btnShowTranslation.setText("해석 보기");
              isShowingEnglish = true;
            }
          });
    }
  }

  private class UserMessageViewHolder extends RecyclerView.ViewHolder {
    private final TextView tvMessageContent;
    private final ImageView btnSpeaker;

    UserMessageViewHolder(View itemView) {
      super(itemView);
      tvMessageContent = itemView.findViewById(R.id.tv_message_content);
      btnSpeaker = itemView.findViewById(R.id.btn_speaker);
    }

    void bind(ChatMessage message) {
      tvMessageContent.setText(message.getEngMessage());
      btnSpeaker.setImageResource(R.drawable.ic_speaker);

      if (message.hasAudio()) {
        btnSpeaker.setVisibility(View.VISIBLE);
        btnSpeaker.setOnClickListener(
            v ->
                onPlayRecordedAudioRequestListener.onPlayRecordedAudio(
                    message.getAudioData(), btnSpeaker));
      } else {
        btnSpeaker.setVisibility(View.INVISIBLE);
      }
    }
  }

  private static class SkeletonViewHolder extends RecyclerView.ViewHolder {
    SkeletonViewHolder(View itemView) {
      super(itemView);
    }
  }
}
