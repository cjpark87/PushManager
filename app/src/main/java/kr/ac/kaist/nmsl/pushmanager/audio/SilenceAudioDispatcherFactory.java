package kr.ac.kaist.nmsl.pushmanager.audio;

import android.media.AudioRecord;
import android.media.MediaRecorder;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;

/**
 * Created by cjpark on 2016-04-30.
 */
public class SilenceAudioDispatcherFactory extends AudioDispatcherFactory {
    public SilenceAudioDispatcherFactory () {
        super();
    }

    public static AudioDispatcher fromMicrophone(final int source, final int sampleRate,
                                                        final int audioBufferSize, final int bufferOverlap) {
        int minAudioBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT);
        int minAudioBufferSizeInSamples =  minAudioBufferSize/2;
        if(minAudioBufferSizeInSamples <= audioBufferSize ){
            AudioRecord audioInputStream = new AudioRecord(
                    source, sampleRate,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    audioBufferSize * 2);

            TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16,1, true, false);

            TarsosDSPAudioInputStream audioStream = new AndroidAudioInputStream(audioInputStream, format);
            //start recording ! Opens the stream.
            audioInputStream.startRecording();
            return new AudioDispatcher(audioStream,audioBufferSize,bufferOverlap);
        }else{
            throw new IllegalArgumentException("Buffer size too small should be at least " + (minAudioBufferSize *2));
        }
    }
}
