package com.jjundev.oneclickeng.learning.quiz;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.jjundev.oneclickeng.R;

public class QuizResultFragment extends Fragment {
  public interface Host {
    void onQuizFinishRequested();
  }

  @Nullable private Host host;
  @Nullable private TextView tvCompletedSummary;
  @Nullable private MaterialButton btnFinish;

  public QuizResultFragment() {
    super(R.layout.fragment_quiz_result);
  }

  @NonNull
  public static QuizResultFragment newInstance() {
    return new QuizResultFragment();
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (context instanceof Host) {
      host = (Host) context;
      return;
    }
    throw new IllegalStateException(context + " must implement QuizResultFragment.Host");
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    tvCompletedSummary = view.findViewById(R.id.tv_quiz_completed_summary);
    btnFinish = view.findViewById(R.id.btn_quiz_finish);

    if (btnFinish != null) {
      btnFinish.setOnClickListener(
          v -> {
            if (host != null) {
              host.onQuizFinishRequested();
            }
          });
    }

    DialogueQuizViewModel viewModel =
        new ViewModelProvider(requireActivity()).get(DialogueQuizViewModel.class);
    viewModel
        .getUiState()
        .observe(
            getViewLifecycleOwner(),
            state -> {
              if (state.getStatus() != DialogueQuizViewModel.QuizUiState.Status.COMPLETED) {
                return;
              }
              if (tvCompletedSummary != null) {
                tvCompletedSummary.setText(
                    getString(
                        R.string.quiz_completed_summary_format,
                        state.getCorrectAnswerCount(),
                        state.getTotalQuestions()));
              }
            });
  }

  @Override
  public void onDestroyView() {
    tvCompletedSummary = null;
    btnFinish = null;
    super.onDestroyView();
  }

  @Override
  public void onDetach() {
    host = null;
    super.onDetach();
  }
}
