package com.fsck.k9.service;


import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.crypto.e3.E3KeyScanResult;
import com.fsck.k9.crypto.e3.E3KeyScanner;
import com.fsck.k9.power.TracingPowerManager;
import com.fsck.k9.power.TracingPowerManager.TracingWakeLock;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class E3KeyScanService extends CoreService {
    private static final String START_SERVICE = "com.fsck.k9.service.E3KeyScanService.startService";
    private static final String STOP_SERVICE = "com.fsck.k9.service.E3KeyScanService.stopService";

    private E3KeyScanner scanner;

    private final Map<Account, E3KeyScanResult> accountsToResults = new HashMap<>();
    private TracingWakeLock wakeLock = null;
    private int startId = -1;

    public static void startService(Context context) {
        Intent i = new Intent();
        i.setClass(context, E3KeyScanService.class);
        i.setAction(E3KeyScanService.START_SERVICE);
        addWakeLock(context, i);
        context.startService(i);
    }

    public static void stopService(Context context) {
        Intent i = new Intent();
        i.setClass(context, PollService.class);
        i.setAction(E3KeyScanService.STOP_SERVICE);
        addWakeLock(context, i);
        context.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setAutoShutdown(false);
    }

    @Override
    public int startService(Intent intent, int startId) {
        if (START_SERVICE.equals(intent.getAction())) {
            Timber.i("E3KeyScanService started with startId = %d", startId);

            setStartId(startId);
            wakeLockAcquire();

            if (scanner == null) {
                scanner = new E3KeyScanner(getApplication());
            }

            final Preferences prefs = Preferences.getPreferences(getApplication());

            Timber.i("***** E3KeyScanService *****: starting scan");
            for (final Account account : prefs.getAvailableAccounts()) {
                final E3KeyScanResult result = scanner.scanRemote(account, true);

                accountsToResults.put(account, result);
            }
        } else if (STOP_SERVICE.equals(intent.getAction())) {
            Timber.i("E3KeyScanService stopping");
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    // wakelock strategy is to be very conservative.  If there is any reason to release, then release
    // don't want to take the chance of running wild
    synchronized void wakeLockAcquire() {
        TracingWakeLock oldWakeLock = wakeLock;

        TracingPowerManager pm = TracingPowerManager.getPowerManager(E3KeyScanService.this);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "E3KeyScanService wakeLockAcquire");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(K9.WAKE_LOCK_TIMEOUT);

        if (oldWakeLock != null) {
            oldWakeLock.release();
        }

    }

    synchronized void wakeLockRelease() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void release() {
        MailService.saveLastCheckEnd(getApplication());

        MailService.actionReschedulePoll(E3KeyScanService.this, null);
        wakeLockRelease();

        Timber.i("PollService stopping with startId = %d", startId);
        stopSelf(startId);
    }

    public int getStartId() {
        return startId;
    }

    public void setStartId(int startId) {
        this.startId = startId;
    }
}
