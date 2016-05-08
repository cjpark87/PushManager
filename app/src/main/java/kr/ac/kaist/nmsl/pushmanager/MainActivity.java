package kr.ac.kaist.nmsl.pushmanager;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

import kr.ac.kaist.nmsl.pushmanager.util.BLEUtil;
import kr.ac.kaist.nmsl.pushmanager.util.ServiceUtil;

public class MainActivity extends Activity {
    private static final int ACTIVITY_RESULT_NOTIFICATION_LISTENER_SETTINGS = 142;

    private Context mContext;

    private ServiceState mServiceState = null;
    private long mRemainingTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.mContext = this;

        initializeUIComponents();
        //registerReceiver(mMainActivtyBroadcastReceiver, filter);
    }

    @Override
    protected void onResume() {
        // Register broadcast receiver
        IntentFilter localFilter = new IntentFilter();
        localFilter.addAction(Constants.INTENT_FILTER_MAINSERVICE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, localFilter);

        IntentFilter globalFilter = new IntentFilter();
        globalFilter.addAction(Constants.INTENT_FILTER_BLE);
        globalFilter.addAction(Constants.INTENT_FILTER_BREAKPOINT);
        this.registerReceiver(mGlobalBroadcastReceiver, globalFilter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        this.unregisterReceiver(mGlobalBroadcastReceiver);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void initializeUIComponents() {
        showErrorMessage("", false);
        updateUIComponents();

        // Initialize Button
        final Button btnControl = (Button) findViewById(R.id.btn_control);
        btnControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnControl.getText().equals(getString(R.string.stop))) {
                    Toast.makeText(mContext, "Stopping service", Toast.LENGTH_SHORT).show();
                    mContext.stopService(new Intent(mContext, MainService.class));

                    updateUIComponents();
                } else {
                    long duration = 0;
                    try {
                        EditText edtDuration = (EditText) findViewById(R.id.edt_duration);
                        duration = Long.parseLong(edtDuration.getText().toString());
                    } catch (Exception e) {
                        showErrorMessage("Failed to parse duration. " + e.getMessage(), true);
                        return;
                    }

                    int firstPushManagementServiceToExecute = 0;
                    try {
                        RadioGroup radioPushManagementMethod = (RadioGroup) findViewById(R.id.group_mode);
                        firstPushManagementServiceToExecute = radioPushManagementMethod.getCheckedRadioButtonId();
                    } catch (Exception e) {
                        showErrorMessage("Failed to parse first push management service to execute. " + e.getMessage(), true);
                        return;
                    }

                    Toast.makeText(mContext, "Starting service", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(mContext, MainService.class);
                    intent.putExtra("duration", duration);
                    intent.putExtra("firstPushManagementServiceToExecute", firstPushManagementServiceToExecute);
                    mContext.startService(intent);

                    updateUIComponents();
                }
            }
        });

        // Initialize notification setting button
        final Button btnNotificationSetting = (Button) findViewById(R.id.btn_notification_setting);
        btnNotificationSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingIntent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivityForResult(settingIntent, ACTIVITY_RESULT_NOTIFICATION_LISTENER_SETTINGS);
            }
        });

        // Initialize notification setting button
        final Button btnAccessibilitySetting = (Button) findViewById(R.id.btn_accessibility_setting);
        btnAccessibilitySetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(settingIntent);
            }
        });

        final Button btnEnableAdmin = (Button) findViewById(R.id.btn_admin);
        btnEnableAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(intent);
            }
        });

        final TextView txtOsVersion = (TextView) findViewById(R.id.txt_os_version);
        txtOsVersion.setText(Build.VERSION.RELEASE + " / BLE: " + BLEUtil.isAdvertisingSupportedDevice(this));

        final RadioGroup groupMode = (RadioGroup) findViewById(R.id.group_mode);
        groupMode.check(R.id.radio_btn_no_intervention);
    }

    private void showErrorMessage(String errorMessage, boolean isError) {
        final TextView txtErrorTextView = (TextView) findViewById(R.id.txt_error_status);
        txtErrorTextView.setVisibility(isError ? View.VISIBLE : View.INVISIBLE);
        txtErrorTextView.setText(errorMessage != null ? errorMessage : "");
    }

    private void updateUIComponents() {
        final Button btnControl = (Button) findViewById(R.id.btn_control);
        final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_remaining_time);
        final TextView txtServiceStatus = (TextView) findViewById(R.id.txt_service_status);
        final EditText edtDuration = (EditText) findViewById(R.id.edt_duration);
        final RadioGroup radioPushManagementMethod = (RadioGroup) findViewById(R.id.group_mode);

        if (!ServiceUtil.isServiceRunning(this, MainService.class)) {
            mServiceState = ServiceState.NoService;
        }

        if (mServiceState == null) {
            return;
        }

        switch (mServiceState) {
            case NoService:
                btnControl.setText(getString(R.string.start));
                txtRemainingTime.setText(String.format(getString(R.string.time_zero)));
                txtServiceStatus.setText(mServiceState.toString());
                edtDuration.setEnabled(true);
                radioPushManagementMethod.setEnabled(true);
                break;

            case DeferService:
            case NoIntervention:
                btnControl.setText(getString(R.string.stop));
                String remainingTime = String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(mRemainingTime),
                        TimeUnit.MILLISECONDS.toSeconds(mRemainingTime) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(mRemainingTime))
                );
                txtRemainingTime.setText(remainingTime);
                txtServiceStatus.setText(mServiceState.toString());
                edtDuration.setEnabled(false);
                radioPushManagementMethod.setEnabled(false);
                showErrorMessage("", false);
                break;

        }
    }

    private final BroadcastReceiver mGlobalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.INTENT_FILTER_BLE)) {
                if (intent.hasExtra(Constants.BLUETOOTH_NOT_FOUND)
                        && intent.getBooleanExtra(Constants.BLUETOOTH_NOT_FOUND, false)) {
                    showErrorMessage("Bluetooth not found.", true);
                }

                if (intent.hasExtra(Constants.BLUETOOTH_DISABLED)
                        && intent.getBooleanExtra(Constants.BLUETOOTH_DISABLED, false)) {
                    Intent enableBtIntent = new Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            }

            if (intent.getAction().equals(Constants.INTENT_FILTER_BREAKPOINT)) {
                String msg = "";
                msg += "isBreakpoint: " + intent.getBooleanExtra("breakpoint", false) + "\n";
                msg += "activity: " + intent.getStringExtra("activity") + "\n";
                msg += "is_talking: " + intent.getStringExtra("is_talking") + "\n";
                msg += "is_using: " + intent.getStringExtra("is_using") + "\n";
                msg += "with_others: " + intent.getDoubleExtra("with_others", 0.0) + "\n";
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.INTENT_FILTER_MAINSERVICE)) {
                if (intent.getExtras().containsKey("alive")) {
                    updateUIComponents();
                }

                if (intent.getExtras().containsKey("remainingTime") && intent.getExtras().containsKey("toggleCount") && intent.getExtras().containsKey("totalTime") && intent.getExtras().containsKey("pushManagementMethodId")) {
                    long remainingTimeForThisManagement = intent.getLongExtra("remainingTime", -1L);
                    int toggleCount = intent.getIntExtra("toggleCount", -1);
                    long totalTime = intent.getLongExtra("totalTime", -1L);
                    int pushManagementMethodId = intent.getIntExtra("pushManagementMethodId", -1);
                    long serviceToggleTimeToRun = (totalTime / MainService.TOTAL_NUMBER_OF_TOGGLES);
                    long elapsedTime = serviceToggleTimeToRun * toggleCount + (serviceToggleTimeToRun - remainingTimeForThisManagement);

                    mRemainingTime = totalTime - elapsedTime;
                    mServiceState = pushManagementMethodId == R.id.radio_btn_defer ? ServiceState.DeferService : ServiceState.NoIntervention;
                    updateUIComponents();
                }

                if (intent.getExtras().containsKey("error")) {
                    showErrorMessage(intent.getStringExtra("error"), true);
                }
            }
        }
    };

    /*
    implements
} GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int ACTIVITY_RESULT_NOTIFICATION_LISTENER_SETTINGS = 142;
    public static final String FILE_UTIL_FILE_DATETIME_FORMAT = "yyyyMMdd_HHmmss";

    private Context context;

    private DevicePolicyManager mDPM;

    private GoogleApiClient mGoogleApiClient;

    private MainActivityBroadcastReceiver mBroadcastReceiver;

    private AudioManager mAudioManager;
    private int mOldRingerMode = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayError("", false);

        //initialize SCAN folder
        File dir = Environment.getExternalStoragePublicDirectory(Constants.DIR_NAME);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }

        this.context = getApplicationContext();

        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        mBroadcastReceiver = new MainActivityBroadcastReceiver();

        // Check if service is already running or not
        if (ServiceUtil.isServiceRunning(context, DeferService.class)) {
            currentServiceState = ServiceState.DeferService;
        } else {
            currentServiceState = ServiceState.NoService;
        }

        context.startService(new Intent(context, NotificationService.class));

        //Google API Client
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        // Update UI accordingly
        updateUIComponents();



        // Check bluetooth
        if (!initializeBluetooth()) {
            displayError("Failed to initialize Bluetooth", true);
        }
    }

    private void displayError(String errorMessage, boolean isError) {
        final TextView txtErrorTextView = (TextView) findViewById(R.id.txt_error_status);
        txtErrorTextView.setVisibility(isError ? View.VISIBLE : View.INVISIBLE);
        txtErrorTextView.setText(errorMessage != null ? errorMessage : "");
    }

    private boolean initializeBluetooth() {
        BluetoothAdapter btAdapter = BLEUtil.getBluetoothAdapter();
        if (btAdapter == null) {
            Intent localIntent = new Intent(Constants.INTENT_FILTER_BLE);
            localIntent.putExtra(Constants.BLUETOOTH_NOT_FOUND, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(localIntent);
            return false;
        }

        if (!btAdapter.isEnabled()) {
            Intent localIntent = new Intent(Constants.INTENT_FILTER_BLE);
            localIntent.putExtra(Constants.BLUETOOTH_DISABLED, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(localIntent);
            return false;
        }

        return true;
    }

    private void startPushManagerServices() {
        // Get duration of the experiment
        long duration = 0;
        try {
            duration = Long.parseLong(((EditText) findViewById(R.id.edt_duration)).getText().toString()) * 1000L;
            Toast.makeText(context, "Start managing cellphone use", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            displayError("Failed to parse duration. " + e.getMessage(), true);
            return;
        }

        final RadioGroup radioPushManagementMethod = (RadioGroup) findViewById(R.id.group_mode);
        final int firstPushManagementMethodId = radioPushManagementMethod.getCheckedRadioButtonId();

        Constants.LOG_ENABLED = true;

        mHandler = new Handler();
        mPushManagerRunnable = new PushManagerRunnable(firstPushManagementMethodId);
        mPushManagerRunnableInterval = duration / 4;
        mPushManagerRunnable.run();

        startCountDownTimer();

        mDPM.lockNow();
    }

    private void startDeferService() {
        mOldRingerMode = mAudioManager.getRingerMode();
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);

        final long duration = Long.parseLong(((EditText) findViewById(R.id.edt_duration)).getText().toString()) * 1000L;
        Intent intent = new Intent(context, DeferService.class);
        intent.putExtra("duration", duration);
        context.startService(intent);
        currentServiceState = ServiceState.DeferService;
        updateUIComponents();
        if (Constants.LOG_ENABLED) {
            Util.writeLogToFile(context, Constants.LOG_NAME, "START", "==============Defer started===============");
        }
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, Constants.ACTIVITY_REQUEST_DURATION, getActivityDetectionPendingIntent());

        context.startService(new Intent(context, AudioProcessorService.class));
        context.startService(new Intent(context, BLEService.class));
        //context.startService(new Intent(context, StepCounterService.class));
    }

    private void startNoInterventionService() {
        currentServiceState = ServiceState.NoIntervention;
        updateUIComponents();
        Log.d(Constants.DEBUG_TAG, "NoIntervention started");
        if (Constants.LOG_ENABLED) {
            Util.writeLogToFile(context, Constants.LOG_NAME, "START", "==============NoIntervention started===============");
        }
    }

    private void stopAllServices(boolean stopForGood) {
        context.stopService(new Intent(context, DeferService.class));
        currentServiceState = ServiceState.NoService;
        if (stopForGood) {
            if (mHandler != null && mPushManagerRunnable != null) {
                mHandler.removeCallbacks(mPushManagerRunnable);
            }

            mCountDownTimer.cancel();
            Log.d(Constants.DEBUG_TAG, "Cancelling all timers");

            // Recover old ringer mode
            Log.d(Constants.DEBUG_TAG, "Recovering ringer mode to " + mOldRingerMode);
            mAudioManager.setRingerMode(mOldRingerMode);
            mOldRingerMode = -1;
        }
        updateUIComponents();

        if (Constants.LOG_ENABLED) {
            if (stopForGood) {
                Util.writeLogToFile(context, Constants.LOG_NAME, "END", "==============All ended===============");
                Constants.LOG_ENABLED = false;
            } else {
                Util.writeLogToFile(context, Constants.LOG_NAME, "SWITCH", "++++++++++++++Service Switch++++++++++++++");
            }
        }

        if (mGoogleApiClient.isConnected()) {
            Log.i(Constants.TAG, "google activity request removed");
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
        }

        context.stopService(new Intent(context, AudioProcessorService.class));
        context.stopService(new Intent(context, BLEService.class));
        //context.stopService(new Intent(context, StepCounterService.class));
    }

    private void startCountDownTimer() {
        final long duration = Long.parseLong(((EditText) findViewById(R.id.edt_duration)).getText().toString()) * 1000L;
        mCountDownTimer = new CountDownTimer(duration, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_remaining_time);
                String remainingTime = String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))
                );
                txtRemainingTime.setText(remainingTime);
            }

            @Override
            public void onFinish() {
                final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_remaining_time);
                txtRemainingTime.setText(R.string.time_zero);
            }
        }.start();
    }

    private void updateUIComponents() {
        final Button btnControl = (Button) findViewById(R.id.btn_control);
        final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_remaining_time);
        final TextView txtServiceStatus = (TextView) findViewById(R.id.txt_service_status);

        //if(this.currentServiceState == ServiceState.WarningService) {
        //    // Warning service is already running
        //    String stopServiceMessage = String.format("%s", getString(R.string.stop));
        //    String serviceStatus = getString(R.string.service_status_warning);
        //    btnControl.setText(stopServiceMessage);
        //    txtServiceStatus.setText(serviceStatus);
        //} else if (this.currentServiceState == ServiceState.DeferService) {
        if (this.currentServiceState == ServiceState.DeferService) {
            // Defer service is already running
            String stopServiceMessage = String.format("%s", getString(R.string.stop));
            String serviceStatus = getString(R.string.service_status_defer);
            btnControl.setText(stopServiceMessage);
            txtServiceStatus.setText(serviceStatus);
        } else if (this.currentServiceState == ServiceState.NoIntervention) {
            txtServiceStatus.setText(getString(R.string.service_status_no_invention));
            ((Button) findViewById(R.id.btn_control)).setText(getString(R.string.stop));
        } else {
            // No service is running
            btnControl.setText(R.string.start);
            txtServiceStatus.setText(getString(R.string.service_status_nothing));
            txtRemainingTime.setText(String.format(getString(R.string.time_zero)));
        }
    }

    @Override
    protected void onResume() {
        //initialize SCAN folder
        File dir = Environment.getExternalStoragePublicDirectory(Constants.DIR_NAME);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.INTENT_FILTER_BLE);
        filter.addAction(Constants.INTENT_FILTER_NOTIFICATION);
        filter.addAction(Constants.INTENT_FILTER_BREAKPOINT);

        registerReceiver(mBroadcastReceiver, filter);
        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        mGoogleApiClient.disconnect();
        super.onDestroy();
        //Constants.LOG_ENABLED = false;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    public PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityRecognitionIntentService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return pendingIntent;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Constants.REQUEST_ENABLE_BT == requestCode) {

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public class MainActivityBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "beacon-detection-response-receiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.INTENT_FILTER_BLE)) {
                if (intent.hasExtra(Constants.BLUETOOTH_NOT_FOUND)
                        && intent.getBooleanExtra(Constants.BLUETOOTH_NOT_FOUND, false)) {
                    displayError("Bluetooth not found.", true);
                }

                if (intent.hasExtra(Constants.BLUETOOTH_DISABLED)
                        && intent.getBooleanExtra(Constants.BLUETOOTH_DISABLED, false)) {
                    Intent enableBtIntent = new Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            }



            if (intent.getAction().equals(Constants.INTENT_FILTER_BREAKPOINT)) {
                String msg = "";
                msg += "isBreakpoint: " + intent.getBooleanExtra("breakpoint", false) + "\n";
                msg += "activity: " + intent.getStringExtra("activity") + "\n";
                msg += "is_talking: " + intent.getStringExtra("is_talking") + "\n";
                msg += "is_using: " + intent.getStringExtra("is_using") + "\n";
                msg += "with_others: " + intent.getDoubleExtra("with_others", 0.0) + "\n";
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        }
    }

*/
}
