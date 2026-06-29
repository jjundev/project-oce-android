package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.dialog.NotificationInboxDialog;

public class GameModeSelectFragment extends Fragment {

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_game_mode_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    View notificationsButton = view.findViewById(R.id.btn_notifications);
    if (notificationsButton != null) {
      notificationsButton.setOnClickListener(
          v -> NotificationInboxDialog.show(getChildFragmentManager()));
    }
  }
}
