package com.jjundev.oneclickeng.fragment;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.flexbox.FlexboxLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.fragment.shorts.EnglishShortsViewModel;
import com.jjundev.oneclickeng.others.EnglishShortsItem;
import com.jjundev.oneclickeng.others.EnglishShortsPagerAdapter;
import com.jjundev.oneclickeng.others.EnglishShortsTagFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EnglishShortsFragment extends Fragment {
  private static final String TAG = "EnglishShortsFragment";
  private static final String COLLECTION_ENGLISH_SHORTS = "english_shorts";
  private static final String COLLECTION_USERS = "users";
  private static final String COLLECTION_ENGLISH_SHORTS_LIKES = "english_shorts_likes";
  private static final String COLLECTION_ENGLISH_SHORTS_DISLIKES = "english_shorts_dislikes";
  private static final String FIELD_LIKE_COUNT = "likeCount";
  private static final String FIELD_DISLIKE_COUNT = "dislikeCount";
  private static final String FIELD_REACTED_AT = "reactedAt";
  private static final int MAX_DISPLAY_TAG_COUNT = 3;

  @NonNull private final List<View> progressBars = new ArrayList<>();
  @NonNull private final Set<String> likedShortDocIds = new HashSet<>();
  @NonNull private final Set<String> dislikedShortDocIds = new HashSet<>();
  @NonNull private final Set<String> pendingReactionUpdates = new HashSet<>();
  @NonNull private List<EnglishShortsItem> shortsItems = new ArrayList<>();
  @NonNull private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
  @NonNull private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

  @Nullable private ViewPager2 viewPager;
  @Nullable private LinearLayout layoutTopOverlay;
  @Nullable private LinearLayout layoutActionRail;
  @Nullable private LinearLayout layoutBottomMeta;
  @Nullable private LinearLayout progressContainer;
  @Nullable private TextView tvTitle;
  @Nullable private FlexboxLayout layoutTags;
  @Nullable private TextView tvLikeCount;
  @Nullable private TextView tvDislikeLabel;
  @Nullable private AppCompatImageButton btnLike;
  @Nullable private AppCompatImageButton btnDislike;
  @Nullable private ViewPager2.OnPageChangeCallback pageChangeCallback;
  @Nullable private ListenerRegistration likesListenerRegistration;
  @Nullable private ListenerRegistration dislikesListenerRegistration;

  // State views
  @Nullable private View layoutLoading;
  @Nullable private TextView tvEmpty;
  @Nullable private View layoutError;

  @Nullable private EnglishShortsPagerAdapter adapter;
  @Nullable private EnglishShortsViewModel viewModel;
  @Nullable private java.util.concurrent.ExecutorService preloaderExecutor;

  private int topOverlayBasePaddingTop;
  private int actionRailBaseBottomMargin;
  private int bottomMetaBaseBottomMargin;
  private boolean hasInitializedContent;

  private enum ReactionState {
    NEUTRAL,
    LIKED,
    DISLIKED
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_english_shorts, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bindViews(view);
    setupViewPager();
    setupLikeButton(view);
    setupEdgeToEdgeInsets(view);

    viewModel = new ViewModelProvider(this).get(EnglishShortsViewModel.class);
    observeViewModel();
    setupRetryButton(view);
    startUserReactionStateListening();

    logDebug("English Shorts fragment initialized.");
  }

  private void bindViews(@NonNull View root) {
    viewPager = root.findViewById(R.id.vp_english_shorts);
    layoutTopOverlay = root.findViewById(R.id.layout_shorts_top_overlay);
    layoutActionRail = root.findViewById(R.id.layout_shorts_action_rail);
    layoutBottomMeta = root.findViewById(R.id.layout_shorts_bottom_meta);
    progressContainer = root.findViewById(R.id.layout_shorts_progress);
    tvTitle = root.findViewById(R.id.tv_shorts_title);
    layoutTags = root.findViewById(R.id.layout_shorts_tags);
    tvLikeCount = root.findViewById(R.id.tv_shorts_like_count);
    tvDislikeLabel = root.findViewById(R.id.tv_shorts_dislike_label);
    btnLike = root.findViewById(R.id.btn_shorts_like);
    btnDislike = root.findViewById(R.id.btn_shorts_dislike);

    layoutLoading = root.findViewById(R.id.layout_shorts_loading);
    tvEmpty = root.findViewById(R.id.tv_shorts_empty);
    layoutError = root.findViewById(R.id.layout_shorts_error);
  }

  private void observeViewModel() {
    if (viewModel == null) return;

    viewModel
        .getIsLoading()
        .observe(
            getViewLifecycleOwner(),
            isLoading -> {
              if (layoutLoading != null) {
                layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
              }
            });

    viewModel
        .getErrorMessage()
        .observe(
            getViewLifecycleOwner(),
            errorMsg -> {
              if (layoutError != null) {
                layoutError.setVisibility(errorMsg != null ? View.VISIBLE : View.GONE);
              }
            });

    viewModel
        .getShortsItems()
        .observe(
            getViewLifecycleOwner(),
            items -> {
              if (items == null || items.isEmpty()) {
                showEmptyState();
                return;
              }
              showContentState(items);
            });
  }

  private void showEmptyState() {
    if (viewPager != null) viewPager.setVisibility(View.GONE);
    if (layoutTopOverlay != null) layoutTopOverlay.setVisibility(View.GONE);
    if (layoutActionRail != null) layoutActionRail.setVisibility(View.GONE);
    if (layoutBottomMeta != null) layoutBottomMeta.setVisibility(View.GONE);

    Boolean isLoading = viewModel != null ? viewModel.getIsLoading().getValue() : Boolean.FALSE;
    String errorMsg = viewModel != null ? viewModel.getErrorMessage().getValue() : null;
    if (tvEmpty != null && !Boolean.TRUE.equals(isLoading) && errorMsg == null) {
      tvEmpty.setVisibility(View.VISIBLE);
    }
  }

  private void showContentState(@NonNull List<EnglishShortsItem> items) {
    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    if (viewPager != null) viewPager.setVisibility(View.VISIBLE);
    if (layoutTopOverlay != null) layoutTopOverlay.setVisibility(View.VISIBLE);
    if (layoutActionRail != null) layoutActionRail.setVisibility(View.VISIBLE);
    if (layoutBottomMeta != null) layoutBottomMeta.setVisibility(View.VISIBLE);

    int currentIndex = 0;
    String currentDocId = "";
    if (viewPager != null && !shortsItems.isEmpty()) {
      currentIndex = viewPager.getCurrentItem();
      if (currentIndex < 0 || currentIndex >= shortsItems.size()) {
        currentIndex = 0;
      }
      currentDocId = shortsItems.get(currentIndex).getDocumentId();
    }

    boolean shouldRebindAdapter = !hasInitializedContent || isStructureChanged(shortsItems, items);
    shortsItems = items;

    if (shouldRebindAdapter) {
      int targetIndex = resolveTargetIndexByDocId(items, currentDocId, currentIndex);
      if (adapter != null) {
        adapter.submitList(items);
      }
      setupProgressIndicators();
      if (!items.isEmpty()) {
        if (viewPager != null && viewPager.getCurrentItem() != targetIndex) {
          viewPager.setCurrentItem(targetIndex, false);
        }
        renderOverlayAfterLayout(targetIndex);
        playVideoAtPosition(targetIndex, false);
      }
      preloadVideos(items);
      hasInitializedContent = true;
      return;
    }

    if (!items.isEmpty()) {
      int overlayIndex = 0;
      if (viewPager != null) {
        overlayIndex = viewPager.getCurrentItem();
      }
      if (overlayIndex < 0 || overlayIndex >= items.size()) {
        overlayIndex = 0;
      }
      renderOverlay(items.get(overlayIndex), overlayIndex);
    }
  }

  private int resolveTargetIndexByDocId(
      @NonNull List<EnglishShortsItem> items, @NonNull String targetDocId, int fallbackIndex) {
    if (items.isEmpty()) return 0;
    if (!targetDocId.isEmpty()) {
      for (int i = 0; i < items.size(); i++) {
        if (targetDocId.equals(items.get(i).getDocumentId())) {
          return i;
        }
      }
    }
    if (fallbackIndex < 0) return 0;
    if (fallbackIndex >= items.size()) return items.size() - 1;
    return fallbackIndex;
  }

  private boolean isStructureChanged(
      @NonNull List<EnglishShortsItem> oldItems, @NonNull List<EnglishShortsItem> newItems) {
    if (oldItems.size() != newItems.size()) return true;
    for (int i = 0; i < oldItems.size(); i++) {
      EnglishShortsItem oldItem = oldItems.get(i);
      EnglishShortsItem newItem = newItems.get(i);
      if (!oldItem.getDocumentId().equals(newItem.getDocumentId())) return true;
      if (!oldItem.getVideoUrl().equals(newItem.getVideoUrl())) return true;
    }
    return false;
  }

  private void preloadVideos(@NonNull List<EnglishShortsItem> items) {
    if (preloaderExecutor == null) {
      preloaderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    preloaderExecutor.execute(
        () -> {
          try {
            androidx.media3.datasource.cache.SimpleCache cache =
                com.jjundev.oneclickeng.OneClickEngApplication.getCache(
                    requireContext().getApplicationContext());
            androidx.media3.datasource.cache.CacheDataSource cacheDataSource =
                new androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(
                        new androidx.media3.datasource.DefaultHttpDataSource.Factory())
                    .createDataSource();

            for (EnglishShortsItem item : items) {
              String videoUrl = item.getVideoUrl();
              if (videoUrl == null || videoUrl.isEmpty()) continue;
              if (videoUrl.startsWith("\"") && videoUrl.endsWith("\"") && videoUrl.length() > 2) {
                videoUrl = videoUrl.substring(1, videoUrl.length() - 1);
              }

              androidx.media3.datasource.DataSpec dataSpec =
                  new androidx.media3.datasource.DataSpec(android.net.Uri.parse(videoUrl));
              androidx.media3.datasource.cache.CacheWriter cacheWriter =
                  new androidx.media3.datasource.cache.CacheWriter(
                      cacheDataSource, dataSpec, null, null);

              logDebug("Preloading video: " + videoUrl);
              cacheWriter.cache();
              logDebug("Preloaded video: " + videoUrl);
            }
          } catch (Exception e) {
            logDebug("Failed to preload video: " + e.getMessage());
          }
        });
  }

  private void setupRetryButton(@NonNull View root) {
    View btnRetry = root.findViewById(R.id.btn_shorts_retry);
    if (btnRetry != null && viewModel != null) {
      btnRetry.setOnClickListener(v -> viewModel.retry());
    }
  }

  private void setupViewPager() {
    ViewPager2 pager = viewPager;
    if (pager == null) return;

    pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
    pager.setOffscreenPageLimit(1);
    adapter = new EnglishShortsPagerAdapter(shortsItems);
    pager.setAdapter(adapter);
    pager.setPageTransformer(
        (page, position) -> {
          float absPosition = Math.abs(position);
          page.setAlpha(1f - Math.min(absPosition * 0.35f, 0.35f));
          page.setScaleY(Math.max(0.94f, 1f - (absPosition * 0.05f)));
        });

    pageChangeCallback =
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            super.onPageSelected(position);
            if (position < 0 || position >= shortsItems.size()) return;
            renderOverlay(shortsItems.get(position), position);
            playVideoAtPosition(position, true);
            logDebug("Short page changed: index=" + position);
          }
        };
    pager.registerOnPageChangeCallback(pageChangeCallback);
  }

  private void playVideoAtPosition(int position, boolean restartFromBeginning) {
    if (viewPager == null || adapter == null) return;
    RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
    adapter.playAtPosition(recyclerView, position, restartFromBeginning);
  }

  private void setupLikeButton(@NonNull View root) {
    View likeButton = root.findViewById(R.id.btn_shorts_like);
    if (likeButton != null) {
      likeButton.setOnClickListener(v -> onLikeButtonClicked());
    }

    View dislikeButton = root.findViewById(R.id.btn_shorts_dislike);
    if (dislikeButton != null) {
      dislikeButton.setOnClickListener(v -> onDislikeButtonClicked());
    }
  }

  private void startUserReactionStateListening() {
    stopUserReactionStateListening();
    likedShortDocIds.clear();
    dislikedShortDocIds.clear();

    FirebaseUser user = firebaseAuth.getCurrentUser();
    if (user == null) {
      applyCurrentReactionTint();
      return;
    }

    likesListenerRegistration =
        firestore
            .collection(COLLECTION_USERS)
            .document(user.getUid())
            .collection(COLLECTION_ENGLISH_SHORTS_LIKES)
            .addSnapshotListener(
                (snapshot, error) -> {
                  if (error != null) {
                    logDebug("Failed to listen likes: " + error.getMessage());
                    return;
                  }

                  likedShortDocIds.clear();
                  if (snapshot != null) {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                      likedShortDocIds.add(document.getId());
                    }
                  }
                  applyCurrentReactionTint();
                });

    dislikesListenerRegistration =
        firestore
            .collection(COLLECTION_USERS)
            .document(user.getUid())
            .collection(COLLECTION_ENGLISH_SHORTS_DISLIKES)
            .addSnapshotListener(
                (snapshot, error) -> {
                  if (error != null) {
                    logDebug("Failed to listen dislikes: " + error.getMessage());
                    return;
                  }

                  dislikedShortDocIds.clear();
                  if (snapshot != null) {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                      dislikedShortDocIds.add(document.getId());
                    }
                  }
                  applyCurrentReactionTint();
                });
  }

  private void stopUserReactionStateListening() {
    if (likesListenerRegistration != null) {
      likesListenerRegistration.remove();
      likesListenerRegistration = null;
    }
    if (dislikesListenerRegistration != null) {
      dislikesListenerRegistration.remove();
      dislikesListenerRegistration = null;
    }
  }

  private void onLikeButtonClicked() {
    EnglishShortsItem currentItem = getCurrentShortItem();
    if (currentItem == null) return;
    updateReactionState(currentItem, ReactionState.LIKED);
  }

  private void onDislikeButtonClicked() {
    EnglishShortsItem currentItem = getCurrentShortItem();
    if (currentItem == null) return;
    updateReactionState(currentItem, ReactionState.DISLIKED);
  }

  private void updateReactionState(
      @NonNull EnglishShortsItem item, @NonNull ReactionState clickedReaction) {
    FirebaseUser user = firebaseAuth.getCurrentUser();
    if (user == null) {
      Toast.makeText(
              requireContext(),
              getString(R.string.english_shorts_reaction_requires_login),
              Toast.LENGTH_SHORT)
          .show();
      return;
    }

    String shortDocId = item.getDocumentId();
    if (shortDocId.isEmpty()) {
      Toast.makeText(
              requireContext(),
              getString(R.string.english_shorts_reaction_update_failed),
              Toast.LENGTH_SHORT)
          .show();
      return;
    }

    if (pendingReactionUpdates.contains(shortDocId)) return;

    ReactionState previousState = getReactionState(shortDocId);
    ReactionState targetState =
        previousState == clickedReaction ? ReactionState.NEUTRAL : clickedReaction;
    if (previousState == targetState) return;

    int previousLikeCount = Math.max(0, item.getLikeCount());
    int previousDislikeCount = Math.max(0, item.getDislikeCount());
    int optimisticLikeCount = previousLikeCount;
    int optimisticDislikeCount = previousDislikeCount;

    if (previousState == ReactionState.LIKED) optimisticLikeCount = Math.max(0, optimisticLikeCount - 1);
    if (previousState == ReactionState.DISLIKED) {
      optimisticDislikeCount = Math.max(0, optimisticDislikeCount - 1);
    }
    if (targetState == ReactionState.LIKED) optimisticLikeCount++;
    if (targetState == ReactionState.DISLIKED) optimisticDislikeCount++;

    pendingReactionUpdates.add(shortDocId);
    applyOptimisticReactionState(
        item, shortDocId, targetState, optimisticLikeCount, optimisticDislikeCount);

    DocumentReference shortRef = firestore.collection(COLLECTION_ENGLISH_SHORTS).document(shortDocId);
    DocumentReference userLikeRef =
        firestore
            .collection(COLLECTION_USERS)
            .document(user.getUid())
            .collection(COLLECTION_ENGLISH_SHORTS_LIKES)
            .document(shortDocId);
    DocumentReference userDislikeRef =
        firestore
            .collection(COLLECTION_USERS)
            .document(user.getUid())
            .collection(COLLECTION_ENGLISH_SHORTS_DISLIKES)
            .document(shortDocId);

    firestore
        .runTransaction(
            transaction -> {
              DocumentSnapshot shortSnapshot = transaction.get(shortRef);
              DocumentSnapshot likeSnapshot = transaction.get(userLikeRef);
              DocumentSnapshot dislikeSnapshot = transaction.get(userDislikeRef);

              long remoteLikeCount = 0L;
              long remoteDislikeCount = 0L;
              Long storedLikeCount = shortSnapshot.getLong(FIELD_LIKE_COUNT);
              Long storedDislikeCount = shortSnapshot.getLong(FIELD_DISLIKE_COUNT);
              if (storedLikeCount != null) {
                remoteLikeCount = Math.max(0L, storedLikeCount);
              }
              if (storedDislikeCount != null) {
                remoteDislikeCount = Math.max(0L, storedDislikeCount);
              }
              boolean currentlyLiked = likeSnapshot.exists();
              boolean currentlyDisliked = dislikeSnapshot.exists();

              if (currentlyLiked && targetState != ReactionState.LIKED) {
                remoteLikeCount = Math.max(0L, remoteLikeCount - 1L);
                transaction.delete(userLikeRef);
              }
              if (currentlyDisliked && targetState != ReactionState.DISLIKED) {
                remoteDislikeCount = Math.max(0L, remoteDislikeCount - 1L);
                transaction.delete(userDislikeRef);
              }

              Map<String, Object> reactionPayload = new HashMap<>();
              reactionPayload.put(FIELD_REACTED_AT, FieldValue.serverTimestamp());
              if (!currentlyLiked && targetState == ReactionState.LIKED) {
                remoteLikeCount = remoteLikeCount + 1L;
                transaction.set(userLikeRef, reactionPayload);
              }
              if (!currentlyDisliked && targetState == ReactionState.DISLIKED) {
                remoteDislikeCount = remoteDislikeCount + 1L;
                transaction.set(userDislikeRef, reactionPayload);
              }

              Map<String, Object> countUpdates = new HashMap<>();
              countUpdates.put(FIELD_LIKE_COUNT, remoteLikeCount);
              countUpdates.put(FIELD_DISLIKE_COUNT, remoteDislikeCount);
              transaction.update(shortRef, countUpdates);
              return null;
            })
        .addOnSuccessListener(
            unused -> {
              pendingReactionUpdates.remove(shortDocId);
              logDebug(
                  "Reaction state updated. shortDocId="
                      + shortDocId
                      + ", targetState="
                      + targetState.name());
            })
        .addOnFailureListener(
            error -> {
              pendingReactionUpdates.remove(shortDocId);
              rollbackOptimisticReactionState(
                  item, shortDocId, previousState, previousLikeCount, previousDislikeCount);
              Toast.makeText(
                      requireContext(),
                      getString(R.string.english_shorts_reaction_update_failed),
                      Toast.LENGTH_SHORT)
                  .show();
              logDebug("Failed to update reaction state: " + error.getMessage());
            });
  }

  private void applyOptimisticReactionState(
      @NonNull EnglishShortsItem item,
      @NonNull String shortDocId,
      @NonNull ReactionState targetState,
      int likeCount,
      int dislikeCount) {
    applyReactionSets(shortDocId, targetState);
    item.setLikeCount(likeCount);
    item.setDislikeCount(dislikeCount);
    updateCurrentLikeCountIfNeeded(shortDocId, likeCount);
    applyCurrentReactionTint();
  }

  private void rollbackOptimisticReactionState(
      @NonNull EnglishShortsItem item,
      @NonNull String shortDocId,
      @NonNull ReactionState previousState,
      int previousLikeCount,
      int previousDislikeCount) {
    applyReactionSets(shortDocId, previousState);
    item.setLikeCount(previousLikeCount);
    item.setDislikeCount(previousDislikeCount);
    updateCurrentLikeCountIfNeeded(shortDocId, previousLikeCount);
    applyCurrentReactionTint();
  }

  private void applyReactionSets(@NonNull String shortDocId, @NonNull ReactionState state) {
    switch (state) {
      case LIKED:
        likedShortDocIds.add(shortDocId);
        dislikedShortDocIds.remove(shortDocId);
        break;
      case DISLIKED:
        dislikedShortDocIds.add(shortDocId);
        likedShortDocIds.remove(shortDocId);
        break;
      case NEUTRAL:
      default:
        likedShortDocIds.remove(shortDocId);
        dislikedShortDocIds.remove(shortDocId);
        break;
    }
  }

  private void updateCurrentLikeCountIfNeeded(@NonNull String shortDocId, int likeCount) {
    EnglishShortsItem currentItem = getCurrentShortItem();
    if (currentItem == null) return;
    if (!shortDocId.equals(currentItem.getDocumentId())) return;
    if (tvLikeCount != null) {
      tvLikeCount.setText(formatCount(likeCount));
    }
  }

  private boolean isShortLiked(@NonNull String shortDocId) {
    return !shortDocId.isEmpty() && likedShortDocIds.contains(shortDocId);
  }

  private boolean isShortDisliked(@NonNull String shortDocId) {
    return !shortDocId.isEmpty() && dislikedShortDocIds.contains(shortDocId);
  }

  @NonNull
  private ReactionState getReactionState(@NonNull String shortDocId) {
    if (isShortLiked(shortDocId)) return ReactionState.LIKED;
    if (isShortDisliked(shortDocId)) return ReactionState.DISLIKED;
    return ReactionState.NEUTRAL;
  }

  @Nullable
  private EnglishShortsItem getCurrentShortItem() {
    if (viewPager == null || shortsItems.isEmpty()) return null;
    int currentPosition = viewPager.getCurrentItem();
    if (currentPosition < 0 || currentPosition >= shortsItems.size()) return null;
    return shortsItems.get(currentPosition);
  }

  private void applyCurrentReactionTint() {
    if (!isAdded()) return;
    AppCompatImageButton likeButton = btnLike;
    AppCompatImageButton dislikeButton = btnDislike;
    EnglishShortsItem currentItem = getCurrentShortItem();
    ReactionState state =
        currentItem != null ? getReactionState(currentItem.getDocumentId()) : ReactionState.NEUTRAL;

    int activeColor = ContextCompat.getColor(requireContext(), R.color.save_icon_gold);
    int iconDefaultColor = ContextCompat.getColor(requireContext(), R.color.shorts_action_icon);
    int textDefaultColor = ContextCompat.getColor(requireContext(), R.color.shorts_action_count_text);

    if (likeButton != null) {
      likeButton.setImageTintList(
          ColorStateList.valueOf(state == ReactionState.LIKED ? activeColor : iconDefaultColor));
    }
    if (dislikeButton != null) {
      dislikeButton.setImageTintList(
          ColorStateList.valueOf(state == ReactionState.DISLIKED ? activeColor : iconDefaultColor));
    }
    if (tvLikeCount != null) {
      tvLikeCount.setTextColor(state == ReactionState.LIKED ? activeColor : textDefaultColor);
    }
    if (tvDislikeLabel != null) {
      tvDislikeLabel.setTextColor(state == ReactionState.DISLIKED ? activeColor : textDefaultColor);
    }
  }

  private void setupProgressIndicators() {
    LinearLayout container = progressContainer;
    if (container == null) return;

    container.removeAllViews();
    progressBars.clear();
    for (int i = 0; i < shortsItems.size(); i++) {
      View bar = new View(requireContext());
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(4), 1f);
      if (i > 0) params.setMarginStart(dp(5));
      bar.setLayoutParams(params);
      container.addView(bar);
      progressBars.add(bar);
    }
    updateProgressIndicators(0);
  }

  private void renderOverlay(@NonNull EnglishShortsItem item, int index) {
    if (tvTitle != null) tvTitle.setText(item.getTitle());
    renderTagChips(item.getTags());
    if (tvLikeCount != null) tvLikeCount.setText(formatCount(item.getLikeCount()));
    updateProgressIndicators(index);
    applyCurrentReactionTint();
  }

  private void renderOverlayAfterLayout(int fallbackIndex) {
    FlexboxLayout tagsContainer = layoutTags;
    LinearLayout bottomMeta = layoutBottomMeta;
    if (tagsContainer == null || bottomMeta == null || tagsContainer.getParent() == null) {
      renderOverlayAtSafeIndex(fallbackIndex);
      return;
    }

    bottomMeta.post(
        () -> {
          if (!isAdded() || getView() == null || shortsItems.isEmpty()) return;

          int index = fallbackIndex;
          if (viewPager != null) {
            index = viewPager.getCurrentItem();
          }
          if (index < 0) {
            index = 0;
          }
          if (index >= shortsItems.size()) {
            index = shortsItems.size() - 1;
          }

          logDebug(
              "Deferred initial overlay render. index="
                  + index
                  + ", tagsWidth="
                  + tagsContainer.getWidth()
                  + ", tagsHeight="
                  + tagsContainer.getHeight());
          renderOverlay(shortsItems.get(index), index);
        });
  }

  private void renderOverlayAtSafeIndex(int index) {
    if (shortsItems.isEmpty()) return;

    int safeIndex = index;
    if (safeIndex < 0) {
      safeIndex = 0;
    }
    if (safeIndex >= shortsItems.size()) {
      safeIndex = shortsItems.size() - 1;
    }
    renderOverlay(shortsItems.get(safeIndex), safeIndex);
  }

  private void renderTagChips(@NonNull List<String> rawTags) {
    FlexboxLayout tagsContainer = layoutTags;
    if (tagsContainer == null || tagsContainer.getParent() == null) return;

    tagsContainer.removeAllViews();
    List<String> displayTags = EnglishShortsTagFormatter.buildDisplayTags(rawTags, MAX_DISPLAY_TAG_COUNT);
    if (displayTags.isEmpty()) {
      tagsContainer.setVisibility(View.GONE);
      return;
    }

    for (String tagText : displayTags) {
      tagsContainer.addView(createTagChip(tagText));
    }
    tagsContainer.setVisibility(View.VISIBLE);
  }

  @NonNull
  private TextView createTagChip(@NonNull String tagText) {
    TextView chipView = new TextView(requireContext());
    chipView.setText(tagText);
    chipView.setBackgroundResource(R.drawable.bg_shorts_chip);
    chipView.setTextColor(ContextCompat.getColor(requireContext(), R.color.shorts_chip_text));
    chipView.setTextSize(11f);
    chipView.setPadding(dp(10), dp(4), dp(10), dp(4));

    FlexboxLayout.LayoutParams params =
        new FlexboxLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.setMarginEnd(dp(6));
    params.bottomMargin = dp(6);
    chipView.setLayoutParams(params);
    return chipView;
  }

  private void updateProgressIndicators(int currentIndex) {
    for (int i = 0; i < progressBars.size(); i++) {
      View bar = progressBars.get(i);
      int colorRes =
          i <= currentIndex ? R.color.shorts_progress_active : R.color.shorts_progress_inactive;
      int color = ContextCompat.getColor(requireContext(), colorRes);
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.RECTANGLE);
      drawable.setCornerRadius(dp(2));
      drawable.setColor(color);
      bar.setBackground(drawable);
      bar.setAlpha(i < currentIndex ? 0.8f : i == currentIndex ? 1f : 0.42f);
    }
  }

  private void setupEdgeToEdgeInsets(@NonNull View root) {
    LinearLayout topOverlay = layoutTopOverlay;
    LinearLayout actionRail = layoutActionRail;
    LinearLayout bottomMeta = layoutBottomMeta;
    if (topOverlay == null || actionRail == null || bottomMeta == null) return;

    topOverlayBasePaddingTop = topOverlay.getPaddingTop();
    ViewGroup.MarginLayoutParams actionParams =
        (ViewGroup.MarginLayoutParams) actionRail.getLayoutParams();
    actionRailBaseBottomMargin = actionParams.bottomMargin;
    ViewGroup.MarginLayoutParams metaParams =
        (ViewGroup.MarginLayoutParams) bottomMeta.getLayoutParams();
    bottomMetaBaseBottomMargin = metaParams.bottomMargin;

    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          topOverlay.setPadding(
              topOverlay.getPaddingLeft(),
              topOverlayBasePaddingTop + systemBars.top,
              topOverlay.getPaddingRight(),
              topOverlay.getPaddingBottom());
          ViewGroup.MarginLayoutParams ap =
              (ViewGroup.MarginLayoutParams) actionRail.getLayoutParams();
          ap.bottomMargin = actionRailBaseBottomMargin + systemBars.bottom;
          actionRail.setLayoutParams(ap);
          ViewGroup.MarginLayoutParams mp =
              (ViewGroup.MarginLayoutParams) bottomMeta.getLayoutParams();
          mp.bottomMargin = bottomMetaBaseBottomMargin + systemBars.bottom;
          bottomMeta.setLayoutParams(mp);
          return insets;
        });
    ViewCompat.requestApplyInsets(root);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (viewPager != null && adapter != null) {
      RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
      adapter.pauseAll(rv);
    }
  }

  @Override
  public void onStop() {
    if (viewPager != null && adapter != null) {
      RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
      adapter.pauseAll(rv);
    }
    super.onStop();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (viewPager != null && !shortsItems.isEmpty()) {
      playVideoAtPosition(viewPager.getCurrentItem(), false);
    }
  }

  @NonNull
  private String formatCount(int count) {
    if (count >= 1_000_000) return String.format(Locale.US, "%.1fM", count / 1_000_000f);
    if (count >= 1_000) return String.format(Locale.US, "%.1fK", count / 1_000f);
    return String.valueOf(count);
  }

  private int dp(int dp) {
    return Math.round(dp * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onDestroyView() {
    stopUserReactionStateListening();
    if (viewPager != null && adapter != null) {
      RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
      adapter.releaseAll(rv);
    }
    if (viewPager != null && pageChangeCallback != null) {
      viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
    }
    pageChangeCallback = null;
    viewPager = null;
    layoutTopOverlay = null;
    layoutActionRail = null;
    layoutBottomMeta = null;
    progressContainer = null;
    tvTitle = null;
    layoutTags = null;
    tvLikeCount = null;
    tvDislikeLabel = null;
    btnLike = null;
    btnDislike = null;
    layoutLoading = null;
    tvEmpty = null;
    layoutError = null;
    adapter = null;
    likedShortDocIds.clear();
    dislikedShortDocIds.clear();
    pendingReactionUpdates.clear();
    hasInitializedContent = false;
    if (preloaderExecutor != null) {
      preloaderExecutor.shutdownNow();
      preloaderExecutor = null;
    }
    progressBars.clear();
    super.onDestroyView();
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
