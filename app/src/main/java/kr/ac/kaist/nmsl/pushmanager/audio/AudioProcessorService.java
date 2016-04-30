package kr.ac.kaist.nmsl.pushmanager.audio;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import kr.ac.kaist.nmsl.pushmanager.Constants;

public class AudioProcessorService extends Service {
    private Timer mTimer;

    public AudioProcessorService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final AudioDispatcher dispatcher = SilenceAudioDispatcherFactory.fromMicrophone(MediaRecorder.AudioSource.MIC, 22050,1024,0);
                dispatcher.addAudioProcessor(new HighPass(50, dispatcher.getFormat().getSampleRate()));
                dispatcher.addAudioProcessor(new LowPassFS(300, dispatcher.getFormat().getSampleRate()));
                dispatcher.addAudioProcessor(new SilenceAudioProcessor(Constants.AUDIO_SILENCE_SPL, Constants.AUDIO_SAMPLING_DURATION, new SilenceAudioProcessor.SilenceHandler() {
                    @Override
                    public void handleSilence(boolean isSilence, double currentSPL, AudioEvent audioEvent) {
                        //if (!isSilence) {
                            Log.d(Constants.DEBUG_TAG, isSilence + ", " + currentSPL + ", " + audioEvent.getTimeStamp());
                        //}
                    }
                }, new SilenceAudioProcessor.TimeoutHandler() {
                    @Override
                    public void handleTimeout() {
                        dispatcher.stop();
                    }
                }));
                dispatcher.addAudioProcessor(new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, new PitchDetectionHandler() {
                    @Override
                    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                        final float pitchInHz = pitchDetectionResult.getPitch();
                        Log.d(Constants.DEBUG_TAG, "detected pitch: " + pitchInHz);
                    }
                }));

                dispatcher.run();
            }
        }, Constants.AUDIO_SAMPLING_PERIOD * 1000, Constants.AUDIO_SAMPLING_PERIOD * 1000);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mTimer.cancel();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
