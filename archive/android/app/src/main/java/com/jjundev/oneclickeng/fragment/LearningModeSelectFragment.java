package com.jjundev.oneclickeng.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.firebase.auth.FirebaseAuth;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.dialog.NotificationInboxDialog;
import com.jjundev.oneclickeng.settings.LearningPointCloudRepository;
import com.jjundev.oneclickeng.settings.LearningPointStore;
import com.jjundev.oneclickeng.settings.LearningStudyTimeCloudRepository;
import com.jjundev.oneclickeng.settings.LearningStudyTimeStore;
import com.jjundev.oneclickeng.settings.UserNicknameCloudRepository;
import com.jjundev.oneclickeng.widget.SlotMachineTextGroupView;

public class LearningModeSelectFragment extends Fragment {
  private static final long STATUS_ANIMATION_DURATION_MS = 900L;
  private static final long STATUS_ANIMATION_DELAY_STREAK_MS = 0L;
  private static final long STATUS_ANIMATION_DELAY_STUDY_TIME_MS = 120L;
  private static final long STATUS_ANIMATION_DELAY_POINTS_MS = 400L;

  @Nullable private LearningStudyTimeStore learningStudyTimeStore;
  @Nullable private LearningStudyTimeCloudRepository learningStudyTimeCloudRepository;
  @Nullable private LearningPointStore learningPointStore;
  @Nullable private LearningPointCloudRepository learningPointCloudRepository;
  @Nullable private UserNicknameCloudRepository userNicknameCloudRepository;
  @Nullable private LearningStudyTimeStore.StudyTimeSnapshot currentStudyTimeSnapshot;
  @Nullable private PopupWindow studyTimePopup;
  @Nullable private TextView tvGreeting;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_learning_mode_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if (getContext() != null) {
      learningStudyTimeStore = new LearningStudyTimeStore(getContext().getApplicationContext());
      learningStudyTimeCloudRepository =
          new LearningStudyTimeCloudRepository(getContext().getApplicationContext());
      learningPointStore = new LearningPointStore(getContext().getApplicationContext());
      learningPointCloudRepository =
          new LearningPointCloudRepository(
              getContext().getApplicationContext(), learningPointStore);
      userNicknameCloudRepository =
          new UserNicknameCloudRepository(getContext().getApplicationContext());
    }
    recordAppEntry();

    setupClickListeners(view);
    startSlotMachineAnimations(view);
    startCardAnimations(view);
    setupGreeting(view);
    refreshStudyTimeData();
    refreshPointData();
  }

  @Override
  public void onResume() {
    super.onResume();
    recordAppEntry();
    syncGreetingNicknameFromCloud();
    refreshStudyTimeData();
    refreshPointData();
  }

  @Override
  public void onPause() {
    dismissStudyTimePopup();
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    dismissStudyTimePopup();
    View view = getView();
    if (view != null) {
      cancelStatusAnimations(view);
    }
    tvGreeting = null;
    currentStudyTimeSnapshot = null;
    super.onDestroyView();
  }

  private void setupGreeting(View view) {
    tvGreeting = view.findViewById(R.id.tv_greeting);
    if (tvGreeting == null) {
      return;
    }

    UserNicknameCloudRepository nicknameRepository = userNicknameCloudRepository;
    if (nicknameRepository != null) {
      applyGreetingNickname(nicknameRepository.getCachedOrFallbackNickname());
    } else {
      applyGreetingNickname("학습자");
    }
    syncGreetingNicknameFromCloud();
  }

  private void syncGreetingNicknameFromCloud() {
    UserNicknameCloudRepository nicknameRepository = userNicknameCloudRepository;
    if (nicknameRepository == null) {
      return;
    }
    nicknameRepository.fetchOrBootstrapForCurrentUser(
        new UserNicknameCloudRepository.NicknameFetchCallback() {
          @Override
          public void onSuccess(@NonNull String nickname, boolean bootstrapped) {
            if (!isAdded()) {
              return;
            }
            applyGreetingNickname(nickname);
          }

          @Override
          public void onFailure(@NonNull String fallbackNickname) {
            if (!isAdded()) {
              return;
            }
            applyGreetingNickname(fallbackNickname);
          }

          @Override
          public void onNoUser(@NonNull String fallbackNickname) {
            if (!isAdded()) {
              return;
            }
            applyGreetingNickname(fallbackNickname);
          }
        });
  }

  private void applyGreetingNickname(@NonNull String nickname) {
    TextView greetingView = tvGreeting;
    if (greetingView == null) {
      return;
    }
    greetingView.setText(getString(R.string.greeting_format, nickname));
  }

  private void startCardAnimations(View view) {
    View[] cards = {view.findViewById(R.id.card_script_mode)};

    long baseDelay = 0; // Start simultaneously with slot machine animations

    for (int i = 0; i < cards.length; i++) {
      View card = cards[i];
      if (card != null) {
        card.setAlpha(0f);
        card.setTranslationX(-100f);
        card.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(baseDelay + (i * 150L))
            .setDuration(500)
            .start();
      }
    }
  }

  private void startSlotMachineAnimations(View view) {
    SlotMachineTextGroupView tvStreak = view.findViewById(R.id.tv_streak_info);
    SlotMachineTextGroupView tvStudyTime = view.findViewById(R.id.tv_study_time);
    SlotMachineTextGroupView tvPoints = view.findViewById(R.id.tv_points);
    LearningStudyTimeStore.StudyTimeSnapshot snapshot = getLocalSnapshot();
    currentStudyTimeSnapshot = snapshot;

    if (tvStreak != null) {
      tvStreak.cancelAnimation();
      tvStreak.animateText(
          formatStreakInfo(snapshot.getTotalStreakDays()),
          STATUS_ANIMATION_DURATION_MS,
          STATUS_ANIMATION_DELAY_STREAK_MS);
    }
    if (tvStudyTime != null) {
      tvStudyTime.cancelAnimation();
      tvStudyTime.animateText(
          formatStudyDuration(snapshot.getTotalVisibleMillis()),
          STATUS_ANIMATION_DURATION_MS,
          STATUS_ANIMATION_DELAY_STUDY_TIME_MS);
    }
    if (tvPoints != null) {
      tvPoints.cancelAnimation();
      tvPoints.animateText(
          formatPoints(getLocalTotalPoints()),
          STATUS_ANIMATION_DURATION_MS,
          STATUS_ANIMATION_DELAY_POINTS_MS);
    }
  }

  @NonNull
  private LearningStudyTimeStore.StudyTimeSnapshot getLocalSnapshot() {
    if (learningStudyTimeStore != null) {
      return learningStudyTimeStore.getLocalSnapshot();
    }
    if (getContext() != null) {
      learningStudyTimeStore = new LearningStudyTimeStore(getContext().getApplicationContext());
      return learningStudyTimeStore.getLocalSnapshot();
    }
    return new LearningStudyTimeStore.StudyTimeSnapshot(0L, 0L, 0, 0, 0L);
  }

  private void recordAppEntry() {
    long now = System.currentTimeMillis();
    if (learningStudyTimeStore == null && getContext() != null) {
      learningStudyTimeStore = new LearningStudyTimeStore(getContext().getApplicationContext());
    }
    if (learningStudyTimeCloudRepository == null && getContext() != null) {
      learningStudyTimeCloudRepository =
          new LearningStudyTimeCloudRepository(getContext().getApplicationContext());
    }
    if (learningStudyTimeStore != null) {
      learningStudyTimeStore.recordAppEntry(now);
    }
    if (learningStudyTimeCloudRepository != null) {
      learningStudyTimeCloudRepository.recordAppEntryForCurrentUser(now);
    }
  }

  private int getLocalTotalPoints() {
    if (learningPointStore != null) {
      return learningPointStore.getTotalPoints();
    }
    if (getContext() != null) {
      learningPointStore = new LearningPointStore(getContext().getApplicationContext());
      return learningPointStore.getTotalPoints();
    }
    return 0;
  }

  private void refreshPointData() {
    int localTotalPoints = getLocalTotalPoints();
    applyTotalPoints(localTotalPoints);

    if (learningPointCloudRepository == null) {
      return;
    }
    if (FirebaseAuth.getInstance().getCurrentUser() == null) {
      return;
    }

    learningPointCloudRepository.flushPendingForCurrentUser(
        success -> fetchCloudPointsWithFallback(localTotalPoints));
  }

  private void fetchCloudPointsWithFallback(int fallbackPoints) {
    if (learningPointCloudRepository == null) {
      return;
    }
    learningPointCloudRepository.fetchCurrentUserTotalPoints(
        new LearningPointCloudRepository.TotalPointsCallback() {
          @Override
          public void onSuccess(int totalPoints) {
            if (!isAdded() || getView() == null) {
              return;
            }
            int mergedPoints = totalPoints;
            if (learningPointStore != null) {
              mergedPoints = learningPointStore.mergeCloudTotalPoints(totalPoints);
            }
            applyTotalPoints(mergedPoints);
          }

          @Override
          public void onFailure() {
            if (!isAdded() || getView() == null) {
              return;
            }
            applyTotalPoints(fallbackPoints);
          }

          @Override
          public void onNoUser() {
            if (!isAdded() || getView() == null) {
              return;
            }
            applyTotalPoints(fallbackPoints);
          }
        });
  }

  private void refreshStudyTimeData() {
    LearningStudyTimeStore.StudyTimeSnapshot localSnapshot = getLocalSnapshot();
    applyStudyTimeSnapshot(localSnapshot);

    if (learningStudyTimeCloudRepository == null) {
      return;
    }
    if (FirebaseAuth.getInstance().getCurrentUser() == null) {
      return;
    }

    learningStudyTimeCloudRepository.flushPendingForCurrentUser(
        success -> fetchCloudSnapshotWithFallback(localSnapshot));
  }

  private void fetchCloudSnapshotWithFallback(
      @NonNull LearningStudyTimeStore.StudyTimeSnapshot fallbackSnapshot) {
    if (learningStudyTimeCloudRepository == null) {
      return;
    }
    learningStudyTimeCloudRepository.fetchCurrentUserSnapshot(
        new LearningStudyTimeCloudRepository.SnapshotCallback() {
          @Override
          public void onSuccess(@NonNull LearningStudyTimeStore.StudyTimeSnapshot snapshot) {
            if (!isAdded() || getView() == null) {
              return;
            }
            applyStudyTimeSnapshot(snapshot);
          }

          @Override
          public void onFailure() {
            if (!isAdded() || getView() == null) {
              return;
            }
            applyStudyTimeSnapshot(fallbackSnapshot);
          }

          @Override
          public void onNoUser() {
            if (!isAdded() || getView() == null) {
              return;
            }
            applyStudyTimeSnapshot(fallbackSnapshot);
          }
        });
  }

  private void applyStudyTimeSnapshot(@NonNull LearningStudyTimeStore.StudyTimeSnapshot snapshot) {
    currentStudyTimeSnapshot = snapshot;

    View view = getView();
    if (view == null) {
      return;
    }

    SlotMachineTextGroupView tvStreak = view.findViewById(R.id.tv_streak_info);
    SlotMachineTextGroupView tvStudyTime = view.findViewById(R.id.tv_study_time);
    if (tvStreak != null) {
      tvStreak.animateText(
          formatStreakInfo(snapshot.getTotalStreakDays()),
          STATUS_ANIMATION_DURATION_MS,
          STATUS_ANIMATION_DELAY_STREAK_MS);
    }
    if (tvStudyTime != null) {
      tvStudyTime.animateText(
          formatStudyDuration(snapshot.getTotalVisibleMillis()),
          STATUS_ANIMATION_DURATION_MS,
          STATUS_ANIMATION_DELAY_STUDY_TIME_MS);
    }
    updatePopupTextIfVisible();
  }

  private void applyTotalPoints(int totalPoints) {
    View view = getView();
    if (view == null) {
      return;
    }
    SlotMachineTextGroupView tvPoints = view.findViewById(R.id.tv_points);
    if (tvPoints == null) {
      return;
    }
    tvPoints.animateText(
        formatPoints(totalPoints), STATUS_ANIMATION_DURATION_MS, STATUS_ANIMATION_DELAY_POINTS_MS);
  }

  @NonNull
  private String formatStudyDuration(long durationMillis) {
    long totalMinutes = Math.max(0L, durationMillis) / 60_000L;
    long hours = totalMinutes / 60L;
    long minutes = totalMinutes % 60L;
    if (hours <= 0L) {
      return getString(R.string.study_time_minutes_format, minutes);
    }
    return getString(R.string.study_time_hours_minutes_format, hours, minutes);
  }

  @NonNull
  private String formatStreakInfo(int totalStreakDays) {
    return getString(R.string.streak_info_format, Math.max(0, totalStreakDays));
  }

  @NonNull
  private String formatPoints(int points) {
    return Math.max(0, points) + "XP";
  }

  @NonNull
  private String getTodayStudyPopupText() {
    LearningStudyTimeStore.StudyTimeSnapshot snapshot = currentStudyTimeSnapshot;
    if (snapshot == null) {
      snapshot = getLocalSnapshot();
      currentStudyTimeSnapshot = snapshot;
    }
    String todayText = formatStudyDuration(snapshot.getTodayVisibleMillis());
    return getString(R.string.study_time_today_popup_format, todayText);
  }

  private void toggleStudyTimePopup(@NonNull View anchor) {
    if (studyTimePopup != null && studyTimePopup.isShowing()) {
      dismissStudyTimePopup();
      return;
    }
    showStudyTimePopup(anchor);
  }

  private void showStudyTimePopup(@NonNull View anchor) {
    if (getContext() == null || getView() == null) {
      return;
    }

    View popupContent =
        LayoutInflater.from(getContext()).inflate(R.layout.view_study_time_popup, null, false);
    TextView popupText = popupContent.findViewById(R.id.tv_today_study_time_popup);
    if (popupText != null) {
      popupText.setText(getTodayStudyPopupText());
    }

    PopupWindow popupWindow =
        new PopupWindow(
            popupContent,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true);
    popupWindow.setOutsideTouchable(true);
    popupWindow.setFocusable(true);
    popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      popupWindow.setElevation(dpToPx(6));
    }

    popupContent.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int popupWidth = popupContent.getMeasuredWidth();
    int popupHeight = popupContent.getMeasuredHeight();

    int[] anchorLocation = new int[2];
    anchor.getLocationOnScreen(anchorLocation);

    int margin = dpToPx(8);
    int screenWidth = getResources().getDisplayMetrics().widthPixels;
    int x = anchorLocation[0] + ((anchor.getWidth() - popupWidth) / 2);
    x = Math.max(margin, Math.min(x, screenWidth - popupWidth - margin));

    int y = anchorLocation[1] - popupHeight - dpToPx(8);
    if (y < margin) {
      y = anchorLocation[1] + anchor.getHeight() + dpToPx(8);
    }

    popupWindow.setOnDismissListener(
        () -> {
          if (studyTimePopup == popupWindow) {
            studyTimePopup = null;
          }
        });
    popupWindow.showAtLocation(requireView(), Gravity.NO_GRAVITY, x, y);
    studyTimePopup = popupWindow;
  }

  private void updatePopupTextIfVisible() {
    if (studyTimePopup == null || !studyTimePopup.isShowing()) {
      return;
    }
    View contentView = studyTimePopup.getContentView();
    if (contentView == null) {
      return;
    }
    TextView popupText = contentView.findViewById(R.id.tv_today_study_time_popup);
    if (popupText != null) {
      popupText.setText(getTodayStudyPopupText());
    }
  }

  private void dismissStudyTimePopup() {
    if (studyTimePopup != null) {
      studyTimePopup.dismiss();
      studyTimePopup = null;
    }
  }

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return (int) (dp * density);
  }

  private void setupClickListeners(View view) {
    View studyTimeCard = view.findViewById(R.id.card_study_time);
    if (studyTimeCard != null) {
      studyTimeCard.setOnClickListener(this::toggleStudyTimePopup);
    }
    View notificationsButton = view.findViewById(R.id.btn_notifications);
    if (notificationsButton != null) {
      notificationsButton.setOnClickListener(
          v -> NotificationInboxDialog.show(getChildFragmentManager()));
    }

    view.findViewById(R.id.card_script_mode)
        .setOnClickListener(
            v -> {
              Navigation.findNavController(v)
                  .navigate(R.id.action_studyModeSelectFragment_to_scriptSelectFragment);
            });
  }

  private void cancelStatusAnimations(@NonNull View view) {
    SlotMachineTextGroupView tvStreak = view.findViewById(R.id.tv_streak_info);
    SlotMachineTextGroupView tvStudyTime = view.findViewById(R.id.tv_study_time);
    SlotMachineTextGroupView tvPoints = view.findViewById(R.id.tv_points);
    if (tvStreak != null) {
      tvStreak.cancelAnimation();
    }
    if (tvStudyTime != null) {
      tvStudyTime.cancelAnimation();
    }
    if (tvPoints != null) {
      tvPoints.cancelAnimation();
    }
  }
}
