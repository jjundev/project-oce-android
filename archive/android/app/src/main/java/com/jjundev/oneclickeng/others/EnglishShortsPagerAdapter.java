package com.jjundev.oneclickeng.others;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the English Shorts ViewPager2. Each page displays a full-screen video.
 */
public class EnglishShortsPagerAdapter
    extends RecyclerView.Adapter<EnglishShortsPagerAdapter.ShortViewHolder> {

  @NonNull
  private final List<EnglishShortsItem> items;
  @NonNull
  private final Set<ShortViewHolder> attachedHolders = new HashSet<>();

  public EnglishShortsPagerAdapter(@NonNull List<EnglishShortsItem> items) {
    this.items = new ArrayList<>(items);
  }

  /** Replaces the current item list and refreshes the adapter. */
  public void submitList(@NonNull List<EnglishShortsItem> newItems) {
    items.clear();
    items.addAll(newItems);
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ShortViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.item_english_short_page, parent, false);
    return new ShortViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ShortViewHolder holder, int position) {
    holder.bind(items.get(position));
  }

  @Override
  public void onViewRecycled(@NonNull ShortViewHolder holder) {
    super.onViewRecycled(holder);
    attachedHolders.remove(holder);
    holder.releasePlayer();
  }

  @Override
  public void onViewAttachedToWindow(@NonNull ShortViewHolder holder) {
    super.onViewAttachedToWindow(holder);
    attachedHolders.add(holder);
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull ShortViewHolder holder) {
    super.onViewDetachedFromWindow(holder);
    attachedHolders.remove(holder);
    holder.pause();
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  /** Pauses all currently playing videos in the attached RecyclerView. */
  public void pauseAll(@Nullable RecyclerView recyclerView) {
    if (recyclerView == null)
      return;
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
      if (holder instanceof ShortViewHolder) {
        ((ShortViewHolder) holder).pause();
      }
    }
  }

  /** Releases all players regardless of RecyclerView attach state. */
  public void releaseAll(@Nullable RecyclerView recyclerView) {
    if (recyclerView != null) {
      for (int i = 0; i < recyclerView.getChildCount(); i++) {
        RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
        if (holder instanceof ShortViewHolder) {
          ((ShortViewHolder) holder).releasePlayer();
        }
      }
    }
    List<ShortViewHolder> holders = new ArrayList<>(attachedHolders);
    for (ShortViewHolder holder : holders) {
      holder.releasePlayer();
    }
    attachedHolders.clear();
  }

  /** Plays the video at the given position and pauses all others. */
  public void playAtPosition(
      @Nullable RecyclerView recyclerView, int position, boolean restartFromBeginning) {
    if (recyclerView == null)
      return;
    pauseAll(recyclerView);
    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
    if (holder instanceof ShortViewHolder) {
      ((ShortViewHolder) holder).play(restartFromBeginning);
    }
  }

  /** ViewHolder for a single Shorts page. */
  public static class ShortViewHolder extends RecyclerView.ViewHolder {
    @NonNull
    private final PlayerView playerView;
    @NonNull
    private final ImageView ivThumbnail;
    @NonNull
    private final ImageView ivPlayPause;
    @NonNull
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private ExoPlayer player;
    @Nullable
    private EnglishShortsItem currentItem;

    ShortViewHolder(@NonNull View itemView) {
      super(itemView);
      playerView = itemView.findViewById(R.id.player_view);
      ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
      ivPlayPause = itemView.findViewById(R.id.iv_play_pause_indicator);

      playerView.setOnClickListener(v -> togglePlayback());
    }

    private void togglePlayback() {
      if (player != null) {
        if (player.isPlaying()) {
          player.pause();
          showPlayPauseIndicator(false);
        } else {
          player.play();
          showPlayPauseIndicator(true);
        }
      }
    }

    /**
     * Shows a play or pause indicator in the center of the screen.
     *
     * @param isPlay true = playing (show play icon then fade out), false = paused
     *               (show pause icon
     *               and keep visible)
     */
    private void showPlayPauseIndicator(boolean isPlay) {
      ivPlayPause.clearAnimation();
      handler.removeCallbacksAndMessages(null);
      ivPlayPause.setImageResource(
          isPlay ? R.drawable.ic_shorts_play : R.drawable.ic_shorts_pause);
      ivPlayPause.setAlpha(0f);
      ivPlayPause.setVisibility(View.VISIBLE);
      ivPlayPause
          .animate()
          .alpha(1f)
          .setDuration(200)
          .withEndAction(
              () -> {
                if (isPlay) {
                  handler.postDelayed(
                      () -> ivPlayPause
                          .animate()
                          .alpha(0f)
                          .setDuration(300)
                          .withEndAction(() -> ivPlayPause.setVisibility(View.GONE))
                          .start(),
                      500);
                }
                // Paused: keep the indicator visible
              })
          .start();
    }

    void bind(@NonNull EnglishShortsItem item) {
      this.currentItem = item;
      ivThumbnail.setVisibility(View.VISIBLE);
      releasePlayer();
    }

    /** Starts playback if a player is ready, or initializes it. */
    public void play() {
      play(false);
    }

    /** Starts playback if a player is ready, or initializes it. */
    public void play(boolean restartFromBeginning) {
      if (currentItem == null)
        return;
      String videoUrl = currentItem.getVideoUrl();
      if (videoUrl == null || videoUrl.isEmpty())
        return;

      if (player == null) {
        androidx.media3.datasource.DataSource.Factory cacheDataSourceFactory = new androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(
                com.jjundev.oneclickeng.OneClickEngApplication.getCache(
                    itemView.getContext().getApplicationContext()))
            .setUpstreamDataSourceFactory(
                new androidx.media3.datasource.DefaultHttpDataSource.Factory());

        player = new ExoPlayer.Builder(itemView.getContext())
            .setMediaSourceFactory(
                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    cacheDataSourceFactory))
            .build();
        playerView.setPlayer(player);

        // Remove surrounding quotes from JSON parsing if they exist
        if (videoUrl.startsWith("\"") && videoUrl.endsWith("\"") && videoUrl.length() > 2) {
          videoUrl = videoUrl.substring(1, videoUrl.length() - 1);
        }

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        player.setMediaItem(mediaItem);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setVolume(1f);
        player.addListener(
            new Player.Listener() {
              @Override
              public void onRenderedFirstFrame() {
                ivThumbnail.setVisibility(View.GONE);
              }
            });
        player.prepare();
      }
      if (restartFromBeginning && player != null) {
        player.seekTo(0);
      }
      // Hide play/pause indicator when playback starts from page change
      handler.removeCallbacksAndMessages(null);
      ivPlayPause.clearAnimation();
      ivPlayPause.setVisibility(View.GONE);
      player.play();
    }

    /** Pauses playback while keeping the decoder allocated. */
    public void pause() {
      if (player != null) {
        player.pause();
      }
    }

    /** Releases the ExoPlayer instance to free resources. */
    public void releasePlayer() {
      handler.removeCallbacksAndMessages(null);
      ivPlayPause.clearAnimation();
      ivPlayPause.setVisibility(View.GONE);
      playerView.setPlayer(null);
      if (player != null) {
        player.pause();
        player.clearVideoSurface();
        player.release();
        player = null;
      }
      ivThumbnail.setVisibility(View.VISIBLE);
    }
  }
}
