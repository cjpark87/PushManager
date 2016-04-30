package kr.ac.kaist.nmsl.pushmanager.audio;

import android.util.Log;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import kr.ac.kaist.nmsl.pushmanager.Constants;

/**
 * Created by cjpark on 2016-04-28.
 */
public class SilenceAudioProcessor implements AudioProcessor {
    private double samplingDuration;

    private SilenceDetector mSilenceDetector;

    private SilenceHandler silenceHandler;
    private TimeoutHandler timeoutHandler;

    public SilenceAudioProcessor(double silenceSPL, double samplingDuration, SilenceHandler silenceHandler, TimeoutHandler timeoutHandler) {
        this.samplingDuration = samplingDuration;

        this.silenceHandler = silenceHandler;
        this.timeoutHandler = timeoutHandler;

        mSilenceDetector = new SilenceDetector(silenceSPL, false);
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        if (audioEvent.getTimeStamp() <= samplingDuration) {
            this.silenceHandler.handleSilence(mSilenceDetector.isSilence(audioEvent.getFloatBuffer()), mSilenceDetector.currentSPL(), audioEvent);
            return true;
        } else {
            this.timeoutHandler.handleTimeout();
            return false;
        }
    }

    @Override
    public void processingFinished() {
        Log.d(Constants.DEBUG_TAG, "processingFinished, "+this.toString());
    }

    public interface SilenceHandler {
        public void handleSilence(boolean isSilence, double currentSPL, AudioEvent audioEvent);
    }

    public interface TimeoutHandler {
        public void handleTimeout();
    }
}
