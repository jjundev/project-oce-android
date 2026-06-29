package com.jjundev.oneclickeng.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.settings.LearningDataRetentionPolicy;

public class LearningDataResetDialog extends DialogFragment {

  public interface OnLearningDataResetListener {
    void onLearningDataResetRequested(
        @NonNull LearningDataRetentionPolicy.Preset preset,
        @NonNull LearningDataResetDialog dialog);
  }

  @Nullable private OnLearningDataResetListener listener;
  @Nullable private View layoutInputForm;
  @Nullable private View layoutLoading;
  @Nullable private RadioGroup rgRetentionPreset;
  @Nullable private TextView tvResetDescription;
  @Nullable private AppCompatButton btnResetCancel;
  @Nullable private AppCompatButton btnResetConfirm;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (getParentFragment() instanceof OnLearningDataResetListener) {
      listener = (OnLearningDataResetListener) getParentFragment();
    } else if (context instanceof OnLearningDataResetListener) {
      listener = (OnLearningDataResetListener) context;
    } else {
      throw new IllegalStateException(context + " must implement OnLearningDataResetListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_learning_data_reset, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    layoutInputForm = view.findViewById(R.id.layout_input_form);
    layoutLoading = view.findViewById(R.id.layout_loading);
    rgRetentionPreset = view.findViewById(R.id.rg_retention_preset);
    tvResetDescription = view.findViewById(R.id.tv_reset_description);
    btnResetCancel = view.findViewById(R.id.btn_reset_cancel);
    btnResetConfirm = view.findViewById(R.id.btn_reset_confirm);

    if (rgRetentionPreset != null && rgRetentionPreset.getCheckedRadioButtonId() == View.NO_ID) {
      rgRetentionPreset.check(R.id.rb_keep_1w);
    }

    if (rgRetentionPreset != null) {
      rgRetentionPreset.setOnCheckedChangeListener(
          (group, checkedId) -> updateDescription(resolvePreset(checkedId)));
    }
    updateDescription(getSelectedPreset());

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
            listener.onLearningDataResetRequested(getSelectedPreset(), this);
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

  @NonNull
  public LearningDataRetentionPolicy.Preset getSelectedPreset() {
    int checkedId =
        rgRetentionPreset == null ? View.NO_ID : rgRetentionPreset.getCheckedRadioButtonId();
    return resolvePreset(checkedId);
  }

  private void updateDescription(@NonNull LearningDataRetentionPolicy.Preset preset) {
    if (tvResetDescription == null) {
      return;
    }
    int descriptionResId;
    switch (preset) {
      case KEEP_1_WEEK:
        descriptionResId = R.string.settings_learning_reset_desc_keep_1w;
        break;
      case KEEP_2_WEEKS:
        descriptionResId = R.string.settings_learning_reset_desc_keep_2w;
        break;
      case KEEP_1_MONTH:
        descriptionResId = R.string.settings_learning_reset_desc_keep_1m;
        break;
      case KEEP_3_MONTHS:
        descriptionResId = R.string.settings_learning_reset_desc_keep_3m;
        break;
      case DELETE_ALL:
      default:
        descriptionResId = R.string.settings_learning_reset_desc_all;
        break;
    }
    tvResetDescription.setText(descriptionResId);
  }

  @NonNull
  private static LearningDataRetentionPolicy.Preset resolvePreset(int checkedId) {
    if (checkedId == R.id.rb_keep_2w) {
      return LearningDataRetentionPolicy.Preset.KEEP_2_WEEKS;
    }
    if (checkedId == R.id.rb_keep_1m) {
      return LearningDataRetentionPolicy.Preset.KEEP_1_MONTH;
    }
    if (checkedId == R.id.rb_keep_3m) {
      return LearningDataRetentionPolicy.Preset.KEEP_3_MONTHS;
    }
    if (checkedId == R.id.rb_delete_all) {
      return LearningDataRetentionPolicy.Preset.DELETE_ALL;
    }
    return LearningDataRetentionPolicy.Preset.KEEP_1_WEEK;
  }
}
