package com.jjundev.oneclickeng.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;

public class ExitConfirmDialog extends DialogFragment {
  private static final String ARG_MESSAGE = "arg_message";

  public interface OnExitConfirmListener {
    void onConfirmExit();
  }

  private OnExitConfirmListener listener;

  @NonNull
  public static ExitConfirmDialog newInstance(@Nullable String message) {
    ExitConfirmDialog dialog = new ExitConfirmDialog();
    if (message != null && !message.trim().isEmpty()) {
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
    }
    return dialog;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (context instanceof OnExitConfirmListener) {
      listener = (OnExitConfirmListener) context;
    } else {
      //             Activity가 리스너를 직접 구현하지 않은 경우를 위해 예외 방지 (강제성은 생략 가능하나 권장됨)
      throw new RuntimeException(context.toString() + " must implement OnExitConfirmListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_exit_confirm, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    TextView messageView = view.findViewById(R.id.tv_message);
    AppCompatButton btnCancel = view.findViewById(R.id.btn_cancel);
    AppCompatButton btnConfirm = view.findViewById(R.id.btn_confirm);
    Bundle args = getArguments();
    if (messageView != null && args != null) {
      String overrideMessage = args.getString(ARG_MESSAGE);
      if (overrideMessage != null && !overrideMessage.trim().isEmpty()) {
        messageView.setText(overrideMessage);
      }
    }

    btnCancel.setOnClickListener(v -> dismiss());

    btnConfirm.setOnClickListener(
        v -> {
          if (listener != null) {
            listener.onConfirmExit();
          }
          dismiss();
        });
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      // 투명 배경 설정 (둥근 모서리 적용을 위해 필수)
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

      // 가로 너비를 화면의 90%로 설정
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }
}
