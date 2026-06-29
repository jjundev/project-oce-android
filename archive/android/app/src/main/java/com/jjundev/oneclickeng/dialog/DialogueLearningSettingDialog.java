package com.jjundev.oneclickeng.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;

public class DialogueLearningSettingDialog extends DialogFragment {
  private static final String TAG = "JOB_J-20260216-005";

  @Nullable private AppSettingsStore appSettingsStore;

  private boolean bindingState;

  private SwitchMaterial switchMuteAll;
  private SeekBar seekTtsRate;
  private TextView tvTtsRateValue;
  private TextView tvTtsProviderDesc;
  private RadioGroup rgTtsProvider;
  private View btnResetSpeed;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_dialogue_learning_setting, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    appSettingsStore = new AppSettingsStore(requireContext().getApplicationContext());

    bindViews(view);
    setupListeners();
    renderSettings();

    AppCompatButton btnClose = view.findViewById(R.id.btn_close);
    btnClose.setOnClickListener(
        v -> {
          logDebug("settings dialog close clicked");
          dismiss();
        });
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
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    logDebug("settings dialog dismissed");
  }

  private void bindViews(@NonNull View view) {
    switchMuteAll = view.findViewById(R.id.switch_mute_all);
    seekTtsRate = view.findViewById(R.id.seek_tts_rate);
    tvTtsRateValue = view.findViewById(R.id.tv_tts_rate_value);
    tvTtsProviderDesc = view.findViewById(R.id.tv_tts_provider_desc);
    rgTtsProvider = view.findViewById(R.id.rg_tts_provider);
    btnResetSpeed = view.findViewById(R.id.btn_reset_speed);
  }

  private void setupListeners() {
    switchMuteAll.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (bindingState) {
            return;
          }
          AppSettingsStore store = appSettingsStore;
          if (store == null) {
            return;
          }
          store.setMuteAllPlayback(isChecked);
          logDebug("Saved mute setting: " + isChecked);
        });

    seekTtsRate.setMax(100);
    seekTtsRate.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float speechRate = progressToSpeechRate(progress);
            tvTtsRateValue.setText(getString(R.string.settings_tts_rate_format, speechRate));
            if (!fromUser || bindingState) {
              return;
            }
            AppSettingsStore store = appSettingsStore;
            if (store != null) {
              store.setTtsSpeechRate(speechRate);
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            logDebug("Saved TTS speech rate.");
          }
        });

    rgTtsProvider.setOnCheckedChangeListener(
        (group, checkedId) -> {
          updateTtsProviderDescription(checkedId);
          if (bindingState) return;
          AppSettingsStore store = appSettingsStore;
          if (store == null) return;

          if (checkedId == R.id.rb_provider_android) {
            store.setTtsProvider(AppSettings.TTS_PROVIDER_ANDROID);
          } else if (checkedId == R.id.rb_provider_gemini) {
            store.setTtsProvider(AppSettings.TTS_PROVIDER_GOOGLE);
          }
        });

    if (btnResetSpeed != null) {
      btnResetSpeed.setOnClickListener(
          v -> {
            AppSettingsStore store = appSettingsStore;
            if (store != null) {
              store.setTtsSpeechRate(AppSettings.DEFAULT_TTS_SPEECH_RATE);
              // Update UI immediately since the listener will only update store if fromUser
              // is true
              // But wait, setProgress triggers onProgressChanged.
              // We need to make sure onProgressChanged handles programmatic changes
              // correctly.
              // In my implementation above, onProgressChanged checks !fromUser ||
              // bindingState.
              // So calling setProgress programmatically won't update the store if !fromUser.
              // But we just updated the store directly.
              // So we just need to update the UI.

              bindingState = true;
              int defaultProgress = speechRateToProgress(AppSettings.DEFAULT_TTS_SPEECH_RATE);
              seekTtsRate.setProgress(defaultProgress);
              tvTtsRateValue.setText(
                  getString(
                      R.string.settings_tts_rate_format, AppSettings.DEFAULT_TTS_SPEECH_RATE));
              bindingState = false;
            }
          });
    }
  }

  private void renderSettings() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return;
    }
    AppSettings settings = store.getSettings();
    bindingState = true;
    switchMuteAll.setChecked(settings.isMuteAllPlayback());

    int speechRateProgress = speechRateToProgress(settings.getTtsSpeechRate());
    seekTtsRate.setProgress(speechRateProgress);
    tvTtsRateValue.setText(
        getString(R.string.settings_tts_rate_format, settings.getTtsSpeechRate()));

    String provider = settings.getTtsProvider();
    if (AppSettings.TTS_PROVIDER_GOOGLE.equals(provider)) {
      rgTtsProvider.check(R.id.rb_provider_gemini);
    } else {
      rgTtsProvider.check(R.id.rb_provider_android);
    }
    updateTtsProviderDescription(rgTtsProvider.getCheckedRadioButtonId());

    bindingState = false;
  }

  private void updateTtsProviderDescription(int checkedId) {
    if (tvTtsProviderDesc == null) {
      return;
    }

    int descriptionTextResId;
    if (checkedId == R.id.rb_provider_android) {
      descriptionTextResId = R.string.dialogue_learning_settings_tts_android_desc;
    } else if (checkedId == R.id.rb_provider_gemini) {
      descriptionTextResId = R.string.dialogue_learning_settings_tts_gemini_desc;
    } else {
      tvTtsProviderDesc.setText(null);
      tvTtsProviderDesc.setVisibility(View.GONE);
      return;
    }

    tvTtsProviderDesc.setVisibility(View.VISIBLE);
    tvTtsProviderDesc.setText(descriptionTextResId);
  }

  private int speechRateToProgress(float speechRate) {
    float clamped = Math.max(0.5f, Math.min(speechRate, 1.5f));
    return Math.round((clamped - 0.5f) * 100f);
  }

  private float progressToSpeechRate(int progress) {
    return 0.5f + (Math.max(0, Math.min(progress, 100)) / 100f);
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
