package com.jjundev.oneclickeng.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.credit.CreditHistoryEventLogger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CreditHistoryFragment extends Fragment {
  private static final String TAG = "CreditHistoryFragment";
  private static final int PAGE_SIZE = 20;
  private static final String COLLECTION_USERS = "users";
  private static final String COLLECTION_CREDIT_EVENTS = "credit_events";
  private static final String FIELD_TIMESTAMP_EPOCH_MS = "timestamp_epoch_ms";
  private static final String FIELD_TIMESTAMP_SERVER = "timestamp_server";
  private static final String FIELD_EVENT = "event";
  private static final String FIELD_DELTA_CREDITS = "delta_credits";
  private static final String FIELD_CREDIT_AFTER = "credit_after";

  @Nullable private RecyclerView rvCreditHistory;
  @Nullable private View layoutLoading;
  @Nullable private View layoutEmpty;
  @Nullable private View layoutPagingLoading;
  @Nullable private TextView tvEmptyState;
  @Nullable private CreditHistoryAdapter adapter;
  @Nullable private DocumentSnapshot lastVisibleDocument;

  @NonNull private final List<CreditHistoryItem> historyItems = new ArrayList<>();

  @Nullable private String emptyMessage;
  private boolean initialLoading;
  private boolean pagingLoading;
  private boolean hasMore = true;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_credit_history, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bindViews(view);
    setupRecyclerView();
    setupBackNavigation();
    loadInitialHistory();
  }

  @Override
  public void onDestroyView() {
    rvCreditHistory = null;
    layoutLoading = null;
    layoutEmpty = null;
    layoutPagingLoading = null;
    tvEmptyState = null;
    adapter = null;
    super.onDestroyView();
  }

  private void bindViews(@NonNull View view) {
    ImageButton btnBack = view.findViewById(R.id.btn_back);
    btnBack.setOnClickListener(v -> navigateBackFromCreditHistory());

    rvCreditHistory = view.findViewById(R.id.rv_credit_history);
    layoutLoading = view.findViewById(R.id.layout_loading);
    layoutEmpty = view.findViewById(R.id.layout_empty);
    layoutPagingLoading = view.findViewById(R.id.layout_paging_loading);
    tvEmptyState = view.findViewById(R.id.tv_empty_state);
  }

  private void setupRecyclerView() {
    RecyclerView recyclerView = rvCreditHistory;
    if (recyclerView == null) {
      return;
    }

    CreditHistoryAdapter historyAdapter = new CreditHistoryAdapter(historyItems);
    adapter = historyAdapter;

    LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(historyAdapter);
    recyclerView.setHasFixedSize(false);
    recyclerView.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (dy <= 0 || initialLoading || pagingLoading || !hasMore) {
              return;
            }
            int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
            int totalCount = historyAdapter.getItemCount();
            if (totalCount == 0) {
              return;
            }
            if (lastVisibleItem >= totalCount - 4) {
              loadNextPage();
            }
          }
        });
  }

  private void setupBackNavigation() {
    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(
            getViewLifecycleOwner(),
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                navigateBackFromCreditHistory();
              }
            });
  }

  private void loadInitialHistory() {
    historyItems.clear();
    lastVisibleDocument = null;
    hasMore = true;
    emptyMessage = null;
    CreditHistoryAdapter historyAdapter = adapter;
    if (historyAdapter != null) {
      historyAdapter.notifyDataSetChanged();
    }
    fetchHistoryPage(true);
  }

  private void loadNextPage() {
    fetchHistoryPage(false);
  }

  private void fetchHistoryPage(boolean isInitialLoad) {
    if (isInitialLoad) {
      if (initialLoading) {
        return;
      }
      initialLoading = true;
    } else {
      if (pagingLoading || !hasMore) {
        return;
      }
      pagingLoading = true;
    }
    renderState();

    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      finishLoading(isInitialLoad);
      showEmpty(getString(R.string.credit_history_login_required));
      return;
    }

    Query query =
        FirebaseFirestore.getInstance()
            .collection(COLLECTION_USERS)
            .document(user.getUid())
            .collection(COLLECTION_CREDIT_EVENTS)
            .orderBy(FIELD_TIMESTAMP_EPOCH_MS, Query.Direction.DESCENDING)
            .limit(PAGE_SIZE);
    if (lastVisibleDocument != null) {
      query = query.startAfter(lastVisibleDocument);
    }

    query.get()
        .addOnSuccessListener(
            querySnapshot -> {
              if (!isAdded()) {
                return;
              }

              List<DocumentSnapshot> documents = querySnapshot.getDocuments();
              if (!documents.isEmpty()) {
                int startIndex = historyItems.size();
                for (DocumentSnapshot doc : documents) {
                  historyItems.add(toHistoryItem(doc));
                }
                lastVisibleDocument = documents.get(documents.size() - 1);
                hasMore = documents.size() >= PAGE_SIZE;
                CreditHistoryAdapter historyAdapter = adapter;
                if (historyAdapter != null) {
                  historyAdapter.notifyItemRangeInserted(startIndex, documents.size());
                }
                showContent();
              } else {
                hasMore = false;
                if (historyItems.isEmpty()) {
                  showEmpty(getString(R.string.credit_history_empty));
                } else {
                  showContent();
                }
              }

              finishLoading(isInitialLoad);
            })
        .addOnFailureListener(
            exception -> {
              if (!isAdded()) {
                return;
              }
              logDebug("Failed to load credit history: " + exception.getMessage());
              if (historyItems.isEmpty()) {
                showEmpty(getString(R.string.credit_history_load_failed));
              } else {
                showToastSafe(getString(R.string.credit_history_load_failed));
              }
              finishLoading(isInitialLoad);
            });
  }

  @NonNull
  private CreditHistoryItem toHistoryItem(@NonNull DocumentSnapshot doc) {
    String event = readString(doc, FIELD_EVENT);
    long deltaCredits = readLong(doc, FIELD_DELTA_CREDITS);
    long creditAfter = readLong(doc, FIELD_CREDIT_AFTER);
    long timestampEpochMs = readLong(doc, FIELD_TIMESTAMP_EPOCH_MS);
    if (timestampEpochMs <= 0L) {
      Timestamp serverTimestamp = doc.getTimestamp(FIELD_TIMESTAMP_SERVER);
      if (serverTimestamp != null) {
        timestampEpochMs = serverTimestamp.toDate().getTime();
      }
    }

    return new CreditHistoryItem(
        resolveEventLabel(event), deltaCredits, creditAfter, formatTime(timestampEpochMs));
  }

  @NonNull
  private String resolveEventLabel(@NonNull String event) {
    if (CreditHistoryEventLogger.EVENT_PURCHASE_CHARGE.equals(event)) {
      return getString(R.string.credit_history_event_purchase_charge);
    }
    if (CreditHistoryEventLogger.EVENT_AD_CHARGE.equals(event)) {
      return getString(R.string.credit_history_event_ad_charge);
    }
    if (CreditHistoryEventLogger.EVENT_LEARNING_USE.equals(event)) {
      return getString(R.string.credit_history_event_learning_use);
    }
    if (CreditHistoryEventLogger.EVENT_QUIZ_USE.equals(event)) {
      return getString(R.string.credit_history_event_quiz_use);
    }
    if (CreditHistoryEventLogger.EVENT_SIGNUP_BONUS_CHARGE.equals(event)) {
      return getString(R.string.credit_history_event_signup_bonus_charge);
    }
    return getString(R.string.credit_history_event_unknown);
  }

  @NonNull
  private String formatTime(long epochMs) {
    if (epochMs <= 0L) {
      return getString(R.string.credit_history_time_unknown);
    }
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA);
    return formatter.format(new Date(epochMs));
  }

  private void finishLoading(boolean isInitialLoad) {
    if (isInitialLoad) {
      initialLoading = false;
    } else {
      pagingLoading = false;
    }
    renderState();
  }

  private void showContent() {
    emptyMessage = null;
    renderState();
  }

  private void showEmpty(@NonNull String message) {
    emptyMessage = message;
    renderState();
  }

  private void renderState() {
    View loadingView = layoutLoading;
    View emptyView = layoutEmpty;
    View pagingLoadingView = layoutPagingLoading;
    RecyclerView recyclerView = rvCreditHistory;

    if (loadingView == null || emptyView == null || pagingLoadingView == null || recyclerView == null) {
      return;
    }

    if (initialLoading) {
      loadingView.setVisibility(View.VISIBLE);
      emptyView.setVisibility(View.GONE);
      pagingLoadingView.setVisibility(View.GONE);
      recyclerView.setVisibility(View.GONE);
      return;
    }

    loadingView.setVisibility(View.GONE);
    if (historyItems.isEmpty()) {
      emptyView.setVisibility(View.VISIBLE);
      recyclerView.setVisibility(View.GONE);
      pagingLoadingView.setVisibility(View.GONE);
      TextView emptyStateText = tvEmptyState;
      if (emptyStateText != null) {
        emptyStateText.setText(
            emptyMessage != null ? emptyMessage : getString(R.string.credit_history_empty));
      }
      return;
    }

    emptyView.setVisibility(View.GONE);
    recyclerView.setVisibility(View.VISIBLE);
    pagingLoadingView.setVisibility(pagingLoading ? View.VISIBLE : View.GONE);
  }

  private void navigateBackFromCreditHistory() {
    if (!isAdded()) {
      return;
    }

    NavController navController = NavHostFragment.findNavController(this);
    if (navController.popBackStack()) {
      return;
    }

    int startDestinationId = navController.getGraph().getStartDestinationId();
    NavOptions navOptions =
        new NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(startDestinationId, false, true)
            .build();

    try {
      navController.navigate(R.id.settingFragment, null, navOptions);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      logDebug(
          "Failed to navigate to fallback destination from CreditHistory: "
              + exception.getMessage());
    }
  }

  private void showToastSafe(@NonNull String message) {
    if (!isAdded()) {
      return;
    }
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }

  @NonNull
  private static String readString(@NonNull DocumentSnapshot doc, @NonNull String key) {
    String value = doc.getString(key);
    return value == null ? "" : value.trim();
  }

  private static long readLong(@NonNull DocumentSnapshot doc, @NonNull String key) {
    Long value = doc.getLong(key);
    return value != null ? value : 0L;
  }

  private static class CreditHistoryAdapter
      extends RecyclerView.Adapter<CreditHistoryAdapter.CreditHistoryViewHolder> {

    @NonNull private final List<CreditHistoryItem> items;

    CreditHistoryAdapter(@NonNull List<CreditHistoryItem> items) {
      this.items = items;
    }

    @NonNull
    @Override
    public CreditHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.item_credit_history_card, parent, false);
      return new CreditHistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CreditHistoryViewHolder holder, int position) {
      CreditHistoryItem item = items.get(position);
      holder.tvEvent.setText(item.eventLabel);
      holder.tvDelta.setText(
          holder.itemView.getContext().getString(R.string.credit_history_delta_format, item.deltaCredits));
      holder.tvDelta.setTextColor(resolveDeltaColor(holder.itemView, item.deltaCredits));
      holder.tvMeta.setText(
          holder.itemView
              .getContext()
              .getString(R.string.credit_history_meta_format, item.creditAfter, item.formattedTime));
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    private int resolveDeltaColor(@NonNull View view, long deltaCredits) {
      if (deltaCredits > 0L) {
        return ContextCompat.getColor(view.getContext(), R.color.expression_natural_accent);
      }
      if (deltaCredits < 0L) {
        return ContextCompat.getColor(view.getContext(), R.color.state_error);
      }
      return ContextCompat.getColor(view.getContext(), R.color.color_sub_text);
    }

    static class CreditHistoryViewHolder extends RecyclerView.ViewHolder {
      @NonNull private final TextView tvEvent;
      @NonNull private final TextView tvDelta;
      @NonNull private final TextView tvMeta;

      CreditHistoryViewHolder(@NonNull View itemView) {
        super(itemView);
        tvEvent = itemView.findViewById(R.id.tv_credit_history_event);
        tvDelta = itemView.findViewById(R.id.tv_credit_history_delta);
        tvMeta = itemView.findViewById(R.id.tv_credit_history_meta);
      }
    }
  }

  private static class CreditHistoryItem {
    @NonNull private final String eventLabel;
    private final long deltaCredits;
    private final long creditAfter;
    @NonNull private final String formattedTime;

    CreditHistoryItem(
        @NonNull String eventLabel, long deltaCredits, long creditAfter, @NonNull String formattedTime) {
      this.eventLabel = eventLabel;
      this.deltaCredits = deltaCredits;
      this.creditAfter = creditAfter;
      this.formattedTime = formattedTime;
    }
  }
}
