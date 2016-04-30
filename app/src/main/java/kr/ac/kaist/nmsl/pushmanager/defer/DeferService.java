package kr.ac.kaist.nmsl.pushmanager.defer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;

import java.util.*;

import kr.ac.kaist.nmsl.pushmanager.Constants;
import kr.ac.kaist.nmsl.pushmanager.activity.PhoneState;
import kr.ac.kaist.nmsl.pushmanager.util.BLEUtil;

public class DeferService extends Service {
    private static final int VIBRATION_DURATION = 500;

    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private Timer mTimer;
    private int mNotificationCount;

    private DeferServiceReceiver mDeferServiceReceiver = null;

    public DeferService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mNotificationCount = 0;

        mDeferServiceReceiver = new DeferServiceReceiver();

        mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);

        mVibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);

        long duration = (intent.getLongExtra("duration", 60 * 1000L) * 10 / 25);
        Log.d(Constants.DEBUG_TAG, "Defer duration: " + duration);

        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (mNotificationCount > 0) {
                    mNotificationCount = 0;
                    mVibrator.vibrate(VIBRATION_DURATION);
                    Log.i(Constants.DEBUG_TAG, "Vibrated");
                }
            }
        }, duration, duration);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.INTENT_FILTER_ACTIVITY);
        filter.addAction(Constants.INTENT_FILTER_NOTIFICATION);
        filter.addAction(Constants.INTENT_FILTER_BLE);
        registerReceiver(mDeferServiceReceiver, filter);

        Log.i(Constants.DEBUG_TAG, "DeferService started.");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mDeferServiceReceiver);
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        mTimer.cancel();
        Log.i(Constants.DEBUG_TAG, "DeferService destroyed.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private PhoneState.State getActivityName(int activityType) {
        return PhoneState.getStateByDetectedActivity(activityType);
    }

    //TODO: management policy to be added!!
    class DeferServiceReceiver extends BroadcastReceiver {
        public DeferServiceReceiver() {
            Log.d(Constants.TAG, "Starting DeferServiceReceiver broadcast receiver");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.INTENT_FILTER_NOTIFICATION)) {
                if (intent.getStringExtra("notification_action").equals("posted")) {
                    Log.i(Constants.DEBUG_TAG, "Notification incremented");
                    mNotificationCount++;
                } else if (intent.getStringExtra("notification_action").equals("removed")) {
                    Log.i(Constants.DEBUG_TAG, "Notification decremented");
                    mNotificationCount--;
                }
            }

            if (intent.getAction().equals(Constants.INTENT_FILTER_ACTIVITY)) {
                int prob = intent.getIntExtra("activity_probability", 0);
                PhoneState.State state = getActivityName(intent.getIntExtra("activity_type", -1));
                PhoneState.getInstance().updateMyState(state);
                Log.d(Constants.DEBUG_TAG, "[Detected Activity] " + state.name() + ": " + prob);
                Toast.makeText(context, "[Detected Activity] " + state.name() + ": " + prob, Toast.LENGTH_SHORT).show();
            }

            if (intent.getAction().equals(Constants.INTENT_FILTER_BLE)) {
                if (intent.hasExtra(Constants.BLUETOOTH_LE_BEACON)) {
                    ArrayList<Beacon> detectedBeacons =
                            intent.getParcelableArrayListExtra(Constants.BLUETOOTH_LE_BEACON);

                    Log.d(Constants.DEBUG_TAG, "=================== detected beacons ==================");
                    for (Beacon detectedBeacon : detectedBeacons) {
                        Log.d(Constants.DEBUG_TAG, detectedBeacon.getBluetoothAddress() + ", " + detectedBeacon.getDataFields().size() + ", " + detectedBeacon.getExtraDataFields().size() + ", " + String.valueOf(detectedBeacon.getRssi()));

                        if (detectedBeacon.getDataFields().size() > 0) {

                            long blePhoneNumber = BLEUtil.getBLEPhoneNumber(detectedBeacon);
                            PhoneState.State bleState = PhoneState.getStateFromBeacon(detectedBeacon);

                            // TODO: Use BLE Phone number and status
                            if (!bleState.name().equals("STILL")) {
                                Log.d(Constants.DEBUG_TAG, "BLEPhoneNumber: " + blePhoneNumber + "  /  BLEState: " + bleState.name());
                            }

                            //Toast.makeText(context, "[BLE] " + blePhoneNumber + " / His state: "+bleState.name(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
    }
}