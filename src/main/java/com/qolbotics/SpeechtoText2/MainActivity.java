package com.qolbotics.SpeechtoText2;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.*;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
//import edu.cmu.pocketsphinx.Config;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class MainActivity extends Activity implements RecognitionListener, OnTouchListener {

    SpeechRecognizer recognizer;
    TextView recognized_word;
    String comando;
    int conteo = 0;
    int permiso_flag = 0;
    Handler a = new Handler();
    private Button homebutton;
    private TextView textout;

    static {
        System.loadLibrary("pocketsphinx_jni");
    }

    RecognizerTask rec;
    Thread rec_thread;
    Date start_date;
    float speech_dur;
    ProgressDialog rec_dialog;
    TextView performance_text;
    EditText edit_text;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // setContentView(android.R.layout.activity_main);

        homebutton = (Button) findViewById(R.id.button);
        /*textout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });*/
        textout = (TextView) findViewById(R.id.txtOutput);

        homebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textout.setText(comando);
                Toast.makeText(getApplicationContext(), "Started recording", Toast.LENGTH_SHORT).show();
            }
        });

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(getApplicationContext());
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                } else {
                    FireRecognition();
                }
            }
        }.execute();

    }


    @Override
    public void onStop() {
        super.onStop();
        recognizer.removeListener(this);
    }

    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                start_date = new Date();
                this.rec.start();
                break;
            case MotionEvent.ACTION_UP:
                try {
                    this.rec.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    Date end_date = new Date();
                    long nmsec = end_date.getTime() - start_date.getTime();
                    this.speech_dur = (float) nmsec / 1000;
                    this.rec_dialog = ProgressDialog.show(MainActivity.this, "", "Recognizing speech...", true);
                    this.rec_dialog.setCancelable(false);
                    this.rec.stop();
                } catch (InterruptedException e) {
                    return true;
                }

                break;
            default:
                ;

                this.rec_thread = new Thread(this.rec);
                Button b = (Button) findViewById(R.id.button);
                b.setOnTouchListener(this);
                this.performance_text = (TextView) findViewById(R.id.txtOutput);
                this.edit_text = (EditText) findViewById(R.id.editText);
                this.rec.setRecognitionListener(this);
                this.rec_thread.start();
        }
        return true;
    }


    public void FireRecognition() {
        Log.d("Recognition", "Recognition Started");
        conteo = 0;
        recognizer.stop();
        recognizer.startListening("digits");
    }

    @Override
    public void onBeginningOfSpeech() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onEndOfSpeech() {
        // TODO Auto-generated method stub

    }

    private void setupRecognizer(File assetsDir) {
        File modelsDir = new File(assetsDir, "models");
        recognizer = defaultSetup().setAcousticModel(new File(modelsDir, "hmm/en-us-semi")).setDictionary(new File(modelsDir, "dict/cmu07a.dic")).setRawLogDir(assetsDir).setKeywordThreshold(1e-40f).getRecognizer();
        recognizer.addListener(this);


        File digitsGrammar = new File(modelsDir, "grammar/digits.gram");
        recognizer.addGrammarSearch("digits", digitsGrammar);

    }

    @Override
    public void onResult(Hypothesis hup) {
        conteo += 1;
        if (conteo == 1) {
            recognizer.stop();
            Timer();
        }
        Bundle b = new Bundle();
        final String hyp = b.getString("hyp");
        final MainActivity that = this;
        this.edit_text.post(new Runnable() {
            public void run() {
                that.edit_text.setText(hyp);
                Date end_date = new Date();
                long nmsec = end_date.getTime() - that.start_date.getTime();
                float rec_dur = (float) nmsec / 1000;
                that.performance_text.setText(String.format("%.2f seconds %.2f xRT", that.speech_dur, rec_dur / that.speech_dur));
                that.rec_dialog.dismiss();
            }
        });

       /* @Override
        public void onError(int err) {
            final MainActivity that = this;
            that.edit_text.post(new Runnable() {
		public void run() {
                    that.rec_dialog.dismiss();
                }
            });
        }*/
    }


    public void Timer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    a.post(new Runnable() {
                        @Override
                        public void run() {
                            FireRecognition();
                        }
                    });
                } catch (Exception e) {
                }
            }

        }).start();
    }

    @Override
    public void onPartialResult(Hypothesis arg0) {

        if (arg0 == null) {
            return;
        }
        String comando = arg0.getHypstr();
        recognized_word.setText(comando);
        conteo += 1;
        if (conteo == 1) {
            conteo = 0;
            Log.d("Res", comando);
            recognizer.stop();
            Timer();
        }
        Bundle b = new Bundle();
        final MainActivity that = this;
        final String hyp = b.getString("hyp");
        that.textout.post(new Runnable() {
            public void run() {
                that.edit_text.setText(hyp);
            }
        });

    }
}


