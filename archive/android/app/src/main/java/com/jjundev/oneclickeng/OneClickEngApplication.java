package com.jjundev.oneclickeng;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.jjundev.oneclickeng.billing.BillingStartupDiagnostics;
import java.io.File;

@UnstableApi
public class OneClickEngApplication extends Application {
  private static final String BILLING_DIAG_TAG = "BillingStartupDiag";

  @Nullable private static SimpleCache simpleCache;

  @Override
  public void onCreate() {
    super.onCreate();
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    Log.i(
        BILLING_DIAG_TAG,
        BillingStartupDiagnostics.buildStartupLogPayload(
            BuildConfig.CREDIT_BILLING_VERIFY_URL,
            BuildConfig.DEBUG,
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
            user != null,
            user != null ? user.getUid() : null));
    MobileAds.initialize(this, initializationStatus -> {});
  }

  @NonNull
  public static synchronized SimpleCache getCache(@NonNull android.content.Context context) {
    if (simpleCache == null) {
      File cacheDir = new File(context.getCacheDir(), "media_cache");
      // 100MB cache size
      LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024);
      DatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);
      simpleCache = new SimpleCache(cacheDir, evictor, databaseProvider);
    }
    return simpleCache;
  }
}
