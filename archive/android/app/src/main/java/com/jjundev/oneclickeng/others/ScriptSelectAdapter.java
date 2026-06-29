package com.jjundev.oneclickeng.others;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.jjundev.oneclickeng.R;
import java.util.ArrayList;
import java.util.List;

public class ScriptSelectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int VIEW_TYPE_NORMAL = 0;
  private static final int VIEW_TYPE_SKELETON = 1;

  private final List<ScriptTemplate> templates;
  private final OnTemplateClickListener listener;
  private boolean skeletonMode = false;

  public interface OnTemplateClickListener {
    void onTemplateClick(ScriptTemplate template);
  }

  public ScriptSelectAdapter(List<ScriptTemplate> templates, OnTemplateClickListener listener) {
    this.templates = templates;
    this.listener = listener;
  }

  public void setSkeletonMode(boolean skeleton) {
    this.skeletonMode = skeleton;
    notifyDataSetChanged();
  }

  public boolean isSkeletonMode() {
    return skeletonMode;
  }

  @Override
  public int getItemViewType(int position) {
    return skeletonMode ? VIEW_TYPE_SKELETON : VIEW_TYPE_NORMAL;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_SKELETON) {
      View view = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.item_script_card_skeleton, parent, false);
      return new SkeletonViewHolder(view);
    }
    View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.item_script_card, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof SkeletonViewHolder) {
      ((SkeletonViewHolder) holder).startShimmer();
      return;
    }
    ViewHolder vh = (ViewHolder) holder;
    ScriptTemplate template = templates.get(position);
    vh.tvEmoji.setText(template.getEmoji());
    vh.tvTitle.setText(template.getTitle());
    vh.tvSubtitle.setText(template.getSubtitle());
    vh.card.setOnClickListener(v -> listener.onTemplateClick(template));
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
    super.onViewDetachedFromWindow(holder);
    if (holder instanceof SkeletonViewHolder) {
      ((SkeletonViewHolder) holder).stopShimmer();
    }
  }

  @Override
  public int getItemCount() {
    if (skeletonMode) {
      return 4; // Always show 4 skeleton cards
    }
    return templates.size();
  }

  public void updateTemplates(List<ScriptTemplate> newTemplates) {
    templates.clear();
    templates.addAll(newTemplates);
    notifyDataSetChanged();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    MaterialCardView card;
    TextView tvEmoji, tvTitle, tvSubtitle;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);
      card = itemView.findViewById(R.id.card_script);
      tvEmoji = itemView.findViewById(R.id.tv_card_emoji);
      tvTitle = itemView.findViewById(R.id.tv_card_title);
      tvSubtitle = itemView.findViewById(R.id.tv_card_subtitle);
    }
  }

  static class SkeletonViewHolder extends RecyclerView.ViewHolder {
    private final View skeletonEmoji;
    private final View skeletonTitle;
    private final View skeletonSubtitle;
    private final List<ObjectAnimator> animators = new ArrayList<>();

    public SkeletonViewHolder(@NonNull View itemView) {
      super(itemView);
      skeletonEmoji = itemView.findViewById(R.id.skeleton_emoji);
      skeletonTitle = itemView.findViewById(R.id.skeleton_title);
      skeletonSubtitle = itemView.findViewById(R.id.skeleton_subtitle);
    }

    void startShimmer() {
      stopShimmer();
      applyPulse(skeletonEmoji, 0L);
      applyPulse(skeletonTitle, 100L);
      applyPulse(skeletonSubtitle, 200L);
    }

    void stopShimmer() {
      for (ObjectAnimator anim : animators) {
        anim.cancel();
      }
      animators.clear();
    }

    private void applyPulse(View view, long startDelay) {
      ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.4f, 1f);
      animator.setDuration(1200L);
      animator.setRepeatCount(ValueAnimator.INFINITE);
      animator.setRepeatMode(ValueAnimator.RESTART);
      animator.setStartDelay(startDelay);
      animator.start();
      animators.add(animator);
    }
  }
}
