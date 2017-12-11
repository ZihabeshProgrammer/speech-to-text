package com.qolbotics.SpeechtoText2;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;

import static com.qolbotics.SpeechtoText2.RecognizerTask.Event.NONE;
import static com.qolbotics.SpeechtoText2.RecognizerTask.Event.SHUTDOWN;

/**
 * Created by macbookpro on 9/18/17.
 */

public class RecognizerTask implements Runnable{
    class AudioTask implements Runnable {
        LinkedBlockingQueue<short[]> q;
        AudioRecord rec;
        int block_size;
        boolean done;
        static final int DEFAULT_BLOCK_SIZE = 512;

        AudioTask() {
            this.init(new LinkedBlockingQueue<short[]>(), DEFAULT_BLOCK_SIZE);
        }

        AudioTask(LinkedBlockingQueue<short[]> q) {
            this.init(q, DEFAULT_BLOCK_SIZE);
        }

        AudioTask(LinkedBlockingQueue<short[]> q, int block_size) {
            this.init(q, block_size);
        }

        void init(LinkedBlockingQueue<short[]> q, int block_size) {
            this.done = false;
            this.q = q;
            this.block_size = block_size;
            this.rec = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, 8192);
        }

        public int getBlockSize() {
            return block_size;
        }

        public void setBlockSize(int block_size) {
            this.block_size = block_size;
        }

        public LinkedBlockingQueue<short[]> getQueue() {
            return q;
        }

        public void stop() {
            this.done = true;
        }

        @Override
        public void run() {
            this.rec.startRecording();
            while (!this.done) {
                int nshorts = this.readBlock();
                if (nshorts <= 0)
                    break;
            }
            this.rec.stop();
            this.rec.release();
        }

        int readBlock() {
            short[] buf = new short[this.block_size];
            int nshorts = this.rec.read(buf, 0, buf.length);
            if (nshorts > 0) {
                Log.d(getClass().getName(), "Posting " + nshorts + " samples to queue");
                this.q.add(buf);
            }
            return nshorts;
            }
        }

    Decoder ps;
    AudioRecord rec;
    AudioTask audio;
    Boolean recording;
	Thread audio_thread;
    Event mailbox;
    LinkedBlockingQueue<short[]> audioq;
    RecognitionListener rl;
    boolean use_partials;


    public RecognizerTask() {

        this.createDecoder();
        this.createAudio();
        this.recording = false;

        RecognizerTask.this.getExternalFilesDir("/sdcard/Android/data/edu.cmu.pocketsphinx/pocketsphinx.log");
        Config c;
        c=null;
        c.setString("-rawlogdir", "/sdcard/Android/data/edu.cmu.pocketsphinx/lm/en_US/hub4.5000.DMP");
        c.setFloat("-samprate", 8000.0);
        c.setInt("-maxhmmpf", 2000);
        c.setInt("-maxwpf", 10);
        c.setBoolean("-backtrace", true);
        c.setBoolean("-bestpath", false);
        this.ps = new Decoder(c);
        this.audio = null;
        this.audioq = new LinkedBlockingQueue<short[]>();
        this.use_partials = false;
    }

    private void getExternalFilesDir(String s) {
    }

    enum State {
		IDLE, LISTENING
	}
    enum Event {
		NONE, START, STOP, SHUTDOWN
	}
    void createDecoder() {
        //Event mailbox;
        Config c;
        c=null;

        c.setString("-hmm", "/sdcard/Android/data/edu.cmu.SpeechtoTecxt2/hmm/en_US/hub4wsj_sc_8k");
        c.setString("-dict", "/sdcard/Android/data/edu.cmu.SpeechtoTecxt2/lm/en_US/hub4.5000.dic");
        c.setString("-lm", "/sdcard/Android/data/edu.cmu.SpeechtoTecxt2/lm/en_US/hub4.5000.DMP");
        /* Necessary because binary data is always big-endian in Java. */
        c.setString("-input_endian", "big");
        c.setString("-rawlogdir", "/sdcard/Android/data/edu.cmu.pocketsphinx");
        c.setInt("-samprate", 8000);
        c.setInt("-pl_window", 2);
        c.setBoolean("-backtrace", true);
        c.setBoolean("-bestpath", false);
        this.ps = new Decoder(c);
    }

        public RecognitionListener getRecognitionListener() {
            return rl;
        }

        public void setRecognitionListener(RecognitionListener rl) {
            this.rl = rl;
        }

        public void setUsePartials(boolean use_partials) {
            this.use_partials = use_partials;
        }


        public boolean getUsePartials() {
            	return this.use_partials;
            }


        void createAudio() {
            this.rec = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 8000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 16384);
             }

    @Override
    public void run() {
        String partial_hyp = null;
        while (true) {
            try {
                synchronized (this.recording) {
                    this.recording.wait();
                    /* Main loop for this thread. */
                    boolean done = false;
                    /* State of the main loop. */
                    State state = State.IDLE;
                    /* Previous partial hypothesis. */
                    while (!done) {
                        /* Read the mail. */
                        Event todo = NONE;
                        synchronized (this.mailbox) {
                           todo = this.mailbox;
                            if (state == State.IDLE && todo == NONE) {
                                try {
                                    Log.d(getClass().getName(), "waiting");
                                    this.mailbox.wait();
                                    todo = this.mailbox;
                                    Log.d(getClass().getName(), "got" + todo);

                                } catch (InterruptedException e) {
                                    /* Quit main loop. */
                                    Log.e(getClass().getName(), "Interrupted waiting for mailbox, shutting down");
                                    todo = SHUTDOWN;
                                }
                            }
                        }
                    }
                }
                Log.d(getClass().getName(), (this.recording ? "" : "not ")
                        + "recording!");
                if (!this.recording)
                    continue;

            } catch (InterruptedException e) {
                Log.d(getClass().getName(), "interrupted!");
                continue;
                /* Reset the mailbox before releasing, to avoid race condition. */
                //mailbox = NONE;
            }
            this.rec.startRecording();
            this.ps.startUtt(partial_hyp);
            short[] buf = new short[512];
            String hypstr = null;
            long l= Long.parseLong(null);
            while (this.recording) {
                int nshorts = this.rec.read(buf, 0, buf.length);
                Log.d(getClass().getName(), "Read " + nshorts + " values");
                if (nshorts <= 0)
                    break;
                this.ps.processRaw(buf,l, false, false);
                Hypothesis hyp = this.ps.hyp();
                if (hyp != null && hyp.getHypstr() != hypstr) {
                    hypstr = hyp.getHypstr();
                    Log.d(getClass().getName(), "partial hyp: " + hypstr);
                                /* Do whatever the mail says to do. */
                    Event todo = NONE;
                    State state = State.IDLE;
                    switch (todo) {
                        case NONE:
                            if (state == State.IDLE)
                                Log.e(getClass().getName(), "Received NONE in mailbox when IDLE, threading error?");
                            break;
                        case START:
                            if (state == State.IDLE) {
                                Log.d(getClass().getName(), "START");
                                this.audio = new AudioTask(this.audioq);
                                this.audio_thread = new Thread(this.audio);
                                this.ps.startUtt(partial_hyp);
                                this.audio_thread.start();
                                state = State.LISTENING;
                            } else
                                Log.e(getClass().getName(), "Received START in mailbox when LISTENING");
                            break;
                        case STOP:
                            state = State.IDLE;
                            if ( state == State.IDLE)
                                Log.e(getClass().getName(), "Received STOP in mailbox when IDLE");
                            else {
                                Log.d(getClass().getName(), "STOP");
                                assert this.audio != null;
                                this.audio.stop();
                                try {
                                    this.audio_thread.join();
                                } catch (InterruptedException e) {
                                    Log.e(getClass().getName(), "Interrupted waiting for audio thread, shutting down");
                                    //Done = true;
                                }
                                        /* Drain the audio queue. */
                                // short[] buf;
                                while ((buf = this.audioq.poll()) != null) {
                                    Log.d(getClass().getName(), "Reading " + buf.length + " samples from queue");
                                    this.ps.processRaw(buf, buf.length, false, false);
                                }
                                this.ps.endUtt();
                                this.audio = null;
                                this.audio_thread = null;
                                hyp = this.ps.hyp();
                                if (this.rl != null) {
                                    if (hyp == null) {
                                        Log.d(getClass().getName(), "Recognition failure");
                                        //this.rl.onError(-1);
                                    } else {
                                        Bundle b = new Bundle();
                                        Log.d(getClass().getName(), "Final hypothesis: " + hyp.getHypstr());
                                        b.putString("hyp", hyp.getHypstr());
                                        this.rl.onResult(hyp);
                                    }
                                }
                            }
                            break;
                        case SHUTDOWN:
                            Log.d(getClass().getName(), "SHUTDOWN");
                            if (this.audio != null) {
                                this.audio.stop();
                                assert this.audio_thread != null;
                                try {
                                    this.audio_thread.join();
                                } catch (InterruptedException e) {
                                                /* We don't care! */
                                }
                            }

                            this.ps.endUtt();
                            this.audio = null;
                            this.audio_thread = null;
                            //state = State.IDLE;
                            //done = true;
                            break;
                    }
                    Log.d(getClass().getName(), "end of utterance");
                    this.rec.stop();
                    this.ps.endUtt();
                    hyp = this.ps.hyp();
                    Log.d(getClass().getName(), "hyp: " + hyp.getHypstr());
                                /* Do whatever's appropriate for the current state.  Actually this just means processing audio if possible. */
                    if (state == State.LISTENING) {
                        assert this.audio != null;
                        try {
                            buf = this.audioq.take();
                            Log.d(getClass().getName(), "Reading " + buf.length + " samples from queue");
                            this.ps.processRaw(buf, buf.length, false, false);
                            hyp = this.ps.hyp();
                            if (hyp != null) {
                                hypstr = hyp.getHypstr();
                                if (hypstr != partial_hyp) {
                                    Log.d(getClass().getName(), "Hypothesis: " + hyp.getHypstr());
                                    if (this.rl != null && hyp != null) {
                                        Bundle b = new Bundle();
                                        b.putString("hyp", hyp.getHypstr());
                                        this.rl.onPartialResult(hyp);
                                    }
                                }
                                partial_hyp = hypstr;
                            }
                        } catch (InterruptedException e) {
                            Log.d(getClass().getName(), "Interrupted in audioq.take");
                        }
                    }
                }
            }
        }
    }


        public void start () {
            Log.d(getClass().getName(), "start");
            synchronized (this.recording) {
                this.recording.notifyAll();
                this.recording = true;
                Log.d(getClass().getName(), "signalling START");
                synchronized (this.mailbox) {
                            /*
+			 * Note that after calling this, the lock is still held, so it's
+			 * okay to set the mailbox down there. Android has a mysterious
+			 * feature where calling notify() or notifyAll() in anything other
+			 * than the first statement of a synchronized block results in an
+			 * IllegalMonitorStateException.
+			 */
                }
                this.mailbox.notifyAll();
                Log.d(getClass().getName(), "signalled START");
                this.mailbox = Event.START;
            }
        }

    public void stop() throws InterruptedException {
        Log.d(getClass().getName(), "signalling STOP");
        synchronized (this.mailbox) {
            this.mailbox.notifyAll();
            Log.d(getClass().getName(), "signalled STOP");
            this.mailbox = Event.STOP;
        }
    }
    public void shutdown () throws InterruptedException {
            Log.d(getClass().getName(), "signalling SHUTDOWN");
            synchronized (this.mailbox) {
                this.mailbox.notifyAll();
                Log.d(getClass().getName(), "signalled SHUTDOWN");
                this.mailbox = SHUTDOWN;
            }
        }
}
