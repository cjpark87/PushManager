package kr.ac.kaist.nmsl.pushmanager;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;

import kr.ac.kaist.nmsl.pushmanager.activity.ActivityRecognitionIntentService;
import kr.ac.kaist.nmsl.pushmanager.activity.StepCounterService;
import kr.ac.kaist.nmsl.pushmanager.ble.BLEService;
import kr.ac.kaist.nmsl.pushmanager.audio.AudioProcessorService;
import kr.ac.kaist.nmsl.pushmanager.defer.DeferService;
import kr.ac.kaist.nmsl.pushmanager.notification.NotificationService;
import kr.ac.kaist.nmsl.pushmanager.util.ServiceUtil;
import kr.ac.kaist.nmsl.pushmanager.util.Util;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    private static final int ACTIVITY_RESULT_NOTIFICATION_LISTENER_SETTINGS = 142;
    public static final String FILE_UTIL_FILE_DATETIME_FORMAT = "yyyyMMdd_HHmmss";

    private Context context;

    enum ServiceState{
        NoService,
        NoIntervention,
        DeferService,
        WarningService
    }
    private ServiceState currentServiceState;

    private Timer mTimer;
    private CountDownTimer mCountDownTimer;

    private DevicePolicyManager mDPM;

    private GoogleApiClient mGoogleApiClient;

    private MainActivityBroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initialize SCAN folder
        File dir = new File(Environment.getExternalStoragePublicDirectory(Constants.DIR_NAME).getAbsolutePath());
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }

        this.context = getApplicationContext();
        /*mCountDownTimer = new CountDownTimer(0,0) {
            @Override
            public void onTick(long millisUntilFinished) {
            }
            @Override
            public void onFinish() {
            }
        };*/
        mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);

        mBroadcastReceiver = new MainActivityBroadcastReceiver();

        // Check if service is already running or not
        //if(ServiceUtil.isServiceRunning(context, WarningService.class)) {
        //    currentServiceState = ServiceState.WarningService;
        //} else if (ServiceUtil.isServiceRunning(context, DeferService.class)){
        if (ServiceUtil.isServiceRunning(context, DeferService.class)){
            currentServiceState = ServiceState.DeferService;
        } else {
            currentServiceState = ServiceState.NoService;
        }

        context.startService(new Intent(context, NotificationService.class));
        context.startService(new Intent(context, StepCounterService.class));

        //Google API Client
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        // Update UI accordingly
        updateUIComponents();

        // Initialize Button
        final Button btnControl = (Button) findViewById(R.id.btn_control);
        btnControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (currentServiceState) {
                    case DeferService:
                    //case WarningService:
                    case NoIntervention:
                        stopAllServices();
                        break;
                    case NoService:
                        Toast.makeText(context, "Start managing cellphone use", Toast.LENGTH_SHORT).show();

                        mTimer = new Timer();
                        final long duration = Long.parseLong(((EditText)findViewById(R.id.edt_duration)).getText().toString()) * 1000L;

                        final RadioGroup radioPushManagementMethod = (RadioGroup) findViewById(R.id.group_mode);
                        int pushManagementMethodId = radioPushManagementMethod.getCheckedRadioButtonId();

                        SimpleDateFormat fileDateFormat = new SimpleDateFormat(FILE_UTIL_FILE_DATETIME_FORMAT);
                        Constants.LOG_ENABLED = true;

                        switch (pushManagementMethodId) {
                            case R.id.radio_btn_no_intervention:
                                Constants.LOG_NAME = fileDateFormat.format(new Date()) + "_" + ServiceState.NoIntervention.toString();
                                startNoInterventionService();
                                break;
                            case R.id.radio_btn_defer:
                                Constants.LOG_NAME = fileDateFormat.format(new Date()) + "_" + ServiceState.DeferService.toString();
                                startDeferService();
                                break;
                            //case R.id.radio_btn_warning:
                            //    Constants.LOG_NAME = fileDateFormat.format(new Date()) + "_" + ServiceState.WarningService.toString();
                            //    startWarningService();
                            //    break;
                        }

                        startCountDownTimer();
                        mTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        stopAllServices();
                                    }
                                });
                            }
                        }, duration);

                        mDPM.lockNow();
                        break;
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

        final RadioGroup groupMode = (RadioGroup) findViewById(R.id.group_mode);
        groupMode.check(R.id.radio_btn_no_intervention);
/*
        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,0);


        dispatcher.addAudioProcessor(new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {

            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult,
                                    AudioEvent audioEvent) {
                final float pitchInHz = pitchDetectionResult.getPitch();
                Log.d(Constants.DEBUG_TAG, "pitch: " + pitchInHz);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //TextView text = (TextView) findViewById(R.id.textView1);
                        //text.setText("" + pitchInHz);

                        Toast.makeText(context, "pitch: " + pitchInHz, Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }));
        new Thread(dispatcher,"Audio Dispatcher").start();
*/
        /*static final int SAMPLE_RATE = 8000;
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        byte[] buffer = new byte[bufferSize];
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);


        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.AMDF, SAMPLE_RATE, bufferSize, pdh);
        TarsosDSPAudioFormat mTarsosFormat = new TarsosDSPAudioFormat(SAMPLE_RATE, 16, 1, true, false);

        int bufferReadResult = recorder.read(buffer, 0, bufferSize);
        AudioEvent audioEvent = new AudioEvent(mTarsosFormat, bufferReadResult);
        audioEvent.setFloatBuffer(buffer);
        p.process(audioEvent);*/
    }

    /*private void startWarningService(){
        context.startService(new Intent(context, WarningService.class));
        currentServiceState = ServiceState.WarningService;
        updateUIComponents();
        if (Constants.LOG_ENABLED) {
            Util.writeLogToFile(context, Constants.LOG_NAME, "==============Warning started===============");
        }
    }*/

    private void startDeferService(){
        final long duration = Long.parseLong(((EditText)findViewById(R.id.edt_duration)).getText().toString()) * 1000L;
        Intent intent = new Intent(context, DeferService.class);
        intent.putExtra("duration", duration);
        context.startService(intent);
        currentServiceState = ServiceState.DeferService;
        updateUIComponents();
        if (Constants.LOG_ENABLED) {
            Util.writeLogToFile(context, Constants.LOG_NAME, "==============Defer started===============");
        }
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, Constants.ACTIVITY_REQUEST_DURATION, getActivityDetectionPendingIntent());

        context.startService(new Intent(context, AudioProcessorService.class));
        context.startService(new Intent(context, BLEService.class));
    }

    private void startNoInterventionService() {
        currentServiceState = ServiceState.NoIntervention;
        updateUIComponents();
        Log.d(Constants.DEBUG_TAG, "NoIntervention started");
        if (Constants.LOG_ENABLED) {
            Util.writeLogToFile(context, Constants.LOG_NAME, "==============NoIntervention started===============");
        }
    }

    private void stopAllServices(){
        //context.stopService(new Intent(context, WarningService.class));
        context.stopService(new Intent(context, DeferService.class));
        currentServiceState = ServiceState.NoService;
        mTimer.cancel();
        mCountDownTimer.cancel();
        updateUIComponents();
        Log.d(Constants.DEBUG_TAG, "All ended");
        if (Constants.LOG_ENABLED) {
            Util.writeLogToFile(context, Constants.LOG_NAME, "==============All ended===============");
            Constants.LOG_ENABLED = false;
        }

        if (mGoogleApiClient.isConnected()) {
            Log.i(Constants.TAG, "google activity request removed");
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, getActivityDetectionPendingIntent());
        }

        context.stopService(new Intent(context, AudioProcessorService.class));
        context.stopService(new Intent(context, BLEService.class));
    }

    private void startCountDownTimer() {
        final long duration = Long.parseLong(((EditText)findViewById(R.id.edt_duration)).getText().toString()) * 1000L;
        mCountDownTimer = new CountDownTimer(duration, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_ramaining_time);
                String remainingTime = String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))
                        );
                txtRemainingTime.setText(remainingTime);
            }

            @Override
            public void onFinish() {
                final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_ramaining_time);
                txtRemainingTime.setText(String.format(getString(R.string.time_zero), currentServiceState.ordinal(), ServiceState.values().length - 1));
            }
        }.start();
    }

    private void updateUIComponents(){
        final Button btnControl = (Button) findViewById(R.id.btn_control);
        final TextView txtRemainingTime = (TextView) findViewById(R.id.txt_ramaining_time);
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
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.INTENT_FILTER_BLE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);
        super.onResume();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
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
        Intent intent = new Intent( this, ActivityRecognitionIntentService.class );
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
                    Toast.makeText(getApplicationContext(), "Bluetooth not found.", Toast.LENGTH_LONG).show();
                }

                if (intent.hasExtra(Constants.BLUETOOTH_DISABLED)
                        && intent.getBooleanExtra(Constants.BLUETOOTH_DISABLED, false)) {
                    Intent enableBtIntent = new Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            }
        }
    }
}
