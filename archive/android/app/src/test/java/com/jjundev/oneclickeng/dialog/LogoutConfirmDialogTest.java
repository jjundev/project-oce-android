package com.jjundev.oneclickeng.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.jjundev.oneclickeng.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogoutConfirmDialogTest {

  @Test
  public void confirmButton_click_callsListenerOnce() {
    TestHostActivity activity = Robolectric.buildActivity(TestHostActivity.class).setup().get();

    LogoutConfirmDialog dialog = new LogoutConfirmDialog();
    dialog.show(activity.getSupportFragmentManager(), "logout-confirm-test");
    activity.getSupportFragmentManager().executePendingTransactions();

    android.app.Dialog shownDialog = dialog.getDialog();
    assertNotNull(shownDialog);

    android.view.View confirmButton = shownDialog.findViewById(R.id.btn_confirm);
    assertNotNull(confirmButton);
    confirmButton.performClick();
    activity.getSupportFragmentManager().executePendingTransactions();

    assertEquals(1, activity.getLogoutConfirmedCallCount());
  }

  @Test
  public void cancelButton_click_doesNotCallListener() {
    TestHostActivity activity = Robolectric.buildActivity(TestHostActivity.class).setup().get();

    LogoutConfirmDialog dialog = new LogoutConfirmDialog();
    dialog.show(activity.getSupportFragmentManager(), "logout-cancel-test");
    activity.getSupportFragmentManager().executePendingTransactions();

    android.app.Dialog shownDialog = dialog.getDialog();
    assertNotNull(shownDialog);

    android.view.View cancelButton = shownDialog.findViewById(R.id.btn_cancel);
    assertNotNull(cancelButton);
    cancelButton.performClick();
    activity.getSupportFragmentManager().executePendingTransactions();

    assertEquals(0, activity.getLogoutConfirmedCallCount());
  }

  @Test
  public void dialogTexts_matchStringResources() {
    TestHostActivity activity = Robolectric.buildActivity(TestHostActivity.class).setup().get();

    LogoutConfirmDialog dialog = new LogoutConfirmDialog();
    dialog.show(activity.getSupportFragmentManager(), "logout-text-test");
    activity.getSupportFragmentManager().executePendingTransactions();

    android.app.Dialog shownDialog = dialog.getDialog();
    assertNotNull(shownDialog);

    TextView headerView = shownDialog.findViewById(R.id.tv_header);
    TextView messageView = shownDialog.findViewById(R.id.tv_message);
    TextView cancelView = shownDialog.findViewById(R.id.btn_cancel);
    TextView confirmView = shownDialog.findViewById(R.id.btn_confirm);

    assertNotNull(headerView);
    assertNotNull(messageView);
    assertNotNull(cancelView);
    assertNotNull(confirmView);

    assertEquals(activity.getString(R.string.settings_logout_dialog_title), headerView.getText());
    assertEquals(
        activity.getString(R.string.settings_logout_dialog_message), messageView.getText());
    assertEquals(activity.getString(R.string.settings_logout_dialog_cancel), cancelView.getText());
    assertEquals(
        activity.getString(R.string.settings_logout_dialog_confirm), confirmView.getText());
  }

  public static class TestHostActivity extends AppCompatActivity
      implements LogoutConfirmDialog.OnLogoutConfirmListener {
    private int logoutConfirmedCallCount;

    @Override
    public void onLogoutConfirmed() {
      logoutConfirmedCallCount++;
    }

    int getLogoutConfirmedCallCount() {
      return logoutConfirmedCallCount;
    }
  }
}
