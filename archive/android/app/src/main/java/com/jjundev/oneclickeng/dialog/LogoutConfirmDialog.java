package com.jjundev.oneclickeng.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;

public class LogoutConfirmDialog extends DialogFragment {

  public interface OnLogoutConfirmListener {
    void onLogoutConfirmed();
  }

  @Nullable private OnLogoutConfirmListener listener;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (getParentFragment() instanceof OnLogoutConfirmListener) {
      listener = (OnLogoutConfirmListener) getParentFragment();
    } else if (context instanceof OnLogoutConfirmListener) {
      listener = (OnLogoutConfirmListener) context;
    } else {
      throw new IllegalStateException(context + " must implement OnLogoutConfirmListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_logout_confirm, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    AppCompatButton btnCancel = view.findViewById(R.id.btn_cancel);
    AppCompatButton btnConfirm = view.findViewById(R.id.btn_confirm);

    if (btnCancel != null) {
      btnCancel.setOnClickListener(v -> dismiss());
    }
    if (btnConfirm != null) {
      btnConfirm.setOnClickListener(
          v -> {
            if (listener != null) {
              listener.onLogoutConfirmed();
            }
            dismiss();
          });
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }

  @Override
  public void onDetach() {
    listener = null;
    super.onDetach();
  }
}
