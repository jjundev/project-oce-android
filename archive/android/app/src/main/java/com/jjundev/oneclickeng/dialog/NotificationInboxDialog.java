package com.jjundev.oneclickeng.dialog;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.notification.AppNotificationEntry;
import com.jjundev.oneclickeng.notification.AppNotificationStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationInboxDialog extends DialogFragment {
  private static final String TAG = "NotificationInboxDialog";

  @Nullable private View layoutLoading;
  @Nullable private View layoutEmpty;
  @Nullable private View layoutListContent;
  @Nullable private LinearLayout layoutNotificationContainer;
  @Nullable private TextView tvEmptyState;
  @Nullable private TextView tvPermissionNotice;

  public static void show(@NonNull FragmentManager fragmentManager) {
    Fragment existingDialog = fragmentManager.findFragmentByTag(TAG);
    if (existingDialog != null && existingDialog.isAdded()) {
      return;
    }
    new NotificationInboxDialog().show(fragmentManager, TAG);
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_notification_inbox, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    layoutLoading = view.findViewById(R.id.layout_loading);
    layoutEmpty = view.findViewById(R.id.layout_empty);
    layoutListContent = view.findViewById(R.id.layout_list_content);
    layoutNotificationContainer = view.findViewById(R.id.layout_notification_container);
    tvEmptyState = view.findViewById(R.id.tv_empty_state);
    tvPermissionNotice = view.findViewById(R.id.tv_permission_notice);

    AppCompatButton btnClose = view.findViewById(R.id.btn_close);
    btnClose.setOnClickListener(v -> dismiss());

    renderPermissionNotice();
    loadNotifications();
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() == null || getDialog().getWindow() == null) {
      return;
    }
    getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
    getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  @Override
  public void onDestroyView() {
    layoutLoading = null;
    layoutEmpty = null;
    layoutListContent = null;
    layoutNotificationContainer = null;
    tvEmptyState = null;
    tvPermissionNotice = null;
    super.onDestroyView();
  }

  private void loadNotifications() {
    showLoading(true);
    if (!isAdded()) {
      return;
    }

    AppNotificationStore store = new AppNotificationStore(requireContext().getApplicationContext());
    List<AppNotificationEntry> entries = store.getEntriesForCurrentUser();

    showLoading(false);
    renderEntries(entries);
  }

  private void renderEntries(@NonNull List<AppNotificationEntry> entries) {
    if (layoutNotificationContainer == null) {
      return;
    }
    layoutNotificationContainer.removeAllViews();

    if (entries.isEmpty()) {
      if (layoutEmpty != null) {
        layoutEmpty.setVisibility(View.VISIBLE);
      }
      if (layoutListContent != null) {
        layoutListContent.setVisibility(View.GONE);
      }
      if (tvEmptyState != null) {
        tvEmptyState.setText(R.string.notification_inbox_empty);
      }
      return;
    }

    if (layoutEmpty != null) {
      layoutEmpty.setVisibility(View.GONE);
    }
    if (layoutListContent != null) {
      layoutListContent.setVisibility(View.VISIBLE);
    }

    LayoutInflater inflater = LayoutInflater.from(requireContext());
    for (int i = 0; i < entries.size(); i++) {
      AppNotificationEntry entry = entries.get(i);
      View itemView =
          inflater.inflate(R.layout.item_notification_inbox, layoutNotificationContainer, false);

      TextView tvSource = itemView.findViewById(R.id.tv_notification_source);
      TextView tvTitle = itemView.findViewById(R.id.tv_notification_title);
      TextView tvBody = itemView.findViewById(R.id.tv_notification_body);
      TextView tvTime = itemView.findViewById(R.id.tv_notification_time);
      View divider = itemView.findViewById(R.id.view_divider);

      tvSource.setText(resolveSourceLabel(entry));
      tvTitle.setText(entry.getTitle());
      tvBody.setText(entry.getBody());

      String formattedTime = formatTime(entry.getReceivedAtEpochMs());
      if (formattedTime == null) {
        tvTime.setVisibility(View.GONE);
      } else {
        tvTime.setVisibility(View.VISIBLE);
        tvTime.setText(formattedTime);
      }

      divider.setVisibility(i == entries.size() - 1 ? View.GONE : View.VISIBLE);
      layoutNotificationContainer.addView(itemView);
    }
  }

  private void showLoading(boolean loading) {
    if (layoutLoading != null) {
      layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
    if (layoutEmpty != null) {
      layoutEmpty.setVisibility(loading ? View.GONE : layoutEmpty.getVisibility());
    }
    if (layoutListContent != null) {
      layoutListContent.setVisibility(loading ? View.GONE : layoutListContent.getVisibility());
    }
  }

  private void renderPermissionNotice() {
    if (tvPermissionNotice == null || !isAdded()) {
      return;
    }

    boolean permissionGranted = true;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionGranted =
          ContextCompat.checkSelfPermission(
                  requireContext(), Manifest.permission.POST_NOTIFICATIONS)
              == PackageManager.PERMISSION_GRANTED;
    }
    boolean notificationsEnabled =
        NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
    boolean canShowSystemNotification = permissionGranted && notificationsEnabled;
    tvPermissionNotice.setVisibility(canShowSystemNotification ? View.GONE : View.VISIBLE);
  }

  @NonNull
  private String resolveSourceLabel(@NonNull AppNotificationEntry entry) {
    int sourceLabelResId =
        AppNotificationEntry.SOURCE_FCM.equals(entry.getSource())
            ? R.string.notification_inbox_source_fcm
            : R.string.notification_inbox_source_local;
    if (entry.isPostedToSystem()) {
      return getString(sourceLabelResId);
    }
    return getString(sourceLabelResId)
        + " · "
        + getString(R.string.notification_inbox_source_not_posted);
  }

  @Nullable
  private String formatTime(long epochMs) {
    if (epochMs <= 0L) {
      return null;
    }
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA);
    return formatter.format(new Date(epochMs));
  }
}
