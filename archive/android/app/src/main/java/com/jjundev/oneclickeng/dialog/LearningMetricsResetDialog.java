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

public class LearningMetricsResetDialog extends DialogFragment {

  public interface OnLearningMetricsResetListener {
    void onLearningMetricsResetRequested(@NonNull LearningMetricsResetDialog dialog);
  }

  @Nullable private OnLearningMetricsResetListener listener;
  @Nullable private View layoutInputForm;
  @Nullable private View layoutLoading;
  @Nullable private AppCompatButton btnResetCancel;
  @Nullable private AppCompatButton btnResetConfirm;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (getParentFragment() instanceof OnLearningMetricsResetListener) {
      listener = (OnLearningMetricsResetListener) getParentFragment();
    } else if (context instanceof OnLearningMetricsResetListener) {
      listener = (OnLearningMetricsResetListener) context;
    } else {
      throw new IllegalStateException(context + " must implement OnLearningMetricsResetListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_learning_metrics_reset, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    layoutInputForm = view.findViewById(R.id.layout_input_form);
    layoutLoading = view.findViewById(R.id.layout_loading);
    btnResetCancel = view.findViewById(R.id.btn_reset_cancel);
    btnResetConfirm = view.findViewById(R.id.btn_reset_confirm);

    if (btnResetCancel != null) {
      btnResetCancel.setOnClickListener(v -> dismiss());
    }
    if (btnResetConfirm != null) {
      btnResetConfirm.setOnClickListener(
          v -> {
            if (listener == null) {
              return;
            }
            showLoading(true);
            listener.onLearningMetricsResetRequested(this);
          });
    }

    showLoading(false);
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

  public void showLoading(boolean loading) {
    if (layoutInputForm != null) {
      layoutInputForm.setVisibility(loading ? View.GONE : View.VISIBLE);
    }
    if (layoutLoading != null) {
      layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
    if (btnResetCancel != null) {
      btnResetCancel.setEnabled(!loading);
    }
    if (btnResetConfirm != null) {
      btnResetConfirm.setEnabled(!loading);
    }
    setCancelable(!loading);
  }
}
