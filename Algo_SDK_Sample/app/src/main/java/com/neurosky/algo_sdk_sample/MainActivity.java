package com.neurosky.algo_sdk_sample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.content.res.AssetManager;
import android.app.AlertDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import android.media.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.neurosky.AlgoSdk.NskAlgoBCQType;
import com.neurosky.AlgoSdk.NskAlgoConfig;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoSignalQuality;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;
import com.neurosky.connection.DataType.MindDataType;

import com.androidplot.xy.*;


public class MainActivity extends Activity {

    final String TAG = "MainActivityTag";
    //Reproductor
    MediaPlayer mp;
    int songpos;
    Random rand;
    int contadorDisgusto;
    int contadorYY;
    boolean playing;
    //Timer

    // graph plot variables
    private final static int X_RANGE = 50;
    private SimpleXYSeries yySeries = null;
    private SimpleXYSeries bp_deltaSeries = null;
    private SimpleXYSeries bp_thetaSeries = null;
    private SimpleXYSeries bp_alphaSeries = null;
    private SimpleXYSeries bp_betaSeries = null;
    private SimpleXYSeries bp_gammaSeries = null;

    // COMM SDK handles
    private TgStreamReader tgStreamReader;
    private BluetoothAdapter mBluetoothAdapter;

    // internal variables
    private boolean bInited = false;
    private boolean bRunning = false;
    private NskAlgoType currentSelectedAlgo;
    private int yyInterval = 1;
    private NskAlgoConfig.NskAlgoBCQThreshold crThreshold = NskAlgoConfig.NskAlgoBCQThreshold.NSK_ALGO_BCQ_THRESHOLD_LIGHT;
    private NskAlgoConfig.NskAlgoBCQThreshold alThreshold = NskAlgoConfig.NskAlgoBCQThreshold.NSK_ALGO_BCQ_THRESHOLD_LIGHT;
    private NskAlgoConfig.NskAlgoBCQThreshold cpThreshold = NskAlgoConfig.NskAlgoBCQThreshold.NSK_ALGO_BCQ_THRESHOLD_LIGHT;

    // canned data variables
    private short raw_data[] = {0};
    private int raw_data_index= 0;
    private int f_index = 0;
    private float output_data[];
    private int output_data_count = 0;
    private int raw_data_sec_len = 235;
    
    // UI components
    private XYPlot plot;
    private EditText text;

    private Button headsetButton;
    private Button cannedButton;
    private Button setAlgosButton;
    private Button setIntervalButton;
    private Button startButton;
    private Button stopButton;

    private Button pauseSongButton;
    private Button nextSongButton;

    private SeekBar intervalSeekBar;
    private TextView intervalText;

    private Button yyText;

    private CheckBox blinkCheckBox;
    private CheckBox yyCheckBox;

    private TextView sqText;
    private TextView lastSongText;
    private ImageView blinkImage;

    private ImageView yyGustoImage;

    private ImageView yyDisgustoImage;

    private NskAlgoSdk nskAlgoSdk;

    private int bLastOutputInterval = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        rand = new Random();
        contadorYY = 0;
        contadorDisgusto = 0;
        songpos = 0;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nskAlgoSdk = new NskAlgoSdk();

        try {
            // (1) Make sure that the device supports Bluetooth and Bluetooth is on
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program !",
                        Toast.LENGTH_LONG).show();
                //finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "error:" + e.getMessage());
            return;
        }

        headsetButton = (Button)this.findViewById(R.id.headsetButton);
        cannedButton = (Button)this.findViewById(R.id.cannedDatabutton);
        setAlgosButton = (Button)this.findViewById(R.id.setAlgosButton);
        setIntervalButton = (Button)this.findViewById(R.id.setIntervalButton);
        startButton = (Button)this.findViewById(R.id.startButton);
        stopButton = (Button)this.findViewById(R.id.stopButton);

        pauseSongButton = (Button) this.findViewById(R.id.pauseSongButton);
        nextSongButton = (Button) this.findViewById(R.id.nextSongButton);

        intervalSeekBar = (SeekBar)this.findViewById(R.id.intervalSeekBar);
        intervalText = (TextView)this.findViewById(R.id.intervalText);

        yyText = (Button)this.findViewById(R.id.yyTitle);

        blinkCheckBox = (CheckBox)this.findViewById(R.id.blinkCheckBox);
        yyCheckBox = (CheckBox)this.findViewById(R.id.yyCheckBox);

        blinkImage = (ImageView)this.findViewById(R.id.blinkImage);
        yyGustoImage = (ImageView) this.findViewById(R.id.yyGustoImage);
        yyDisgustoImage = (ImageView) this.findViewById(R.id.yyDisgustoImage);

        sqText = (TextView)this.findViewById(R.id.sqText);
        lastSongText = (TextView) this.findViewById(R.id.lastSongText);
        headsetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                output_data_count = 0;
                output_data = null;
                raw_data = new short[512];
                raw_data_index = 0;

                cannedButton.setEnabled(false);
                headsetButton.setEnabled(false);

                startButton.setEnabled(false);

                // Example of constructor public TgStreamReader(BluetoothAdapter ba, TgStreamHandler tgStreamHandler)
                tgStreamReader = new TgStreamReader(mBluetoothAdapter,callback);

                if(tgStreamReader != null && tgStreamReader.isBTConnected()){

                    // Prepare for connecting
                    tgStreamReader.stop();
                    tgStreamReader.close();
                }

                // (4) Demo of  using connect() and start() to replace connectAndStart(),
                // please call start() when the state is changed to STATE_CONNECTED
                tgStreamReader.connect();
            }
        });

        cannedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                output_data_count = 0;
                output_data = null;
                System.gc();

                headsetButton.setEnabled(false);
                cannedButton.setEnabled(false);

                AssetManager assetManager = getAssets();
                InputStream inputStream = null;

                Log.d(TAG, "Reading output data");
                try {
                    int j;
                    // check the output count first
                    inputStream = assetManager.open("output_data.bin");
                    output_data_count = 0;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    try {
                        String line = reader.readLine();
                        while (!(line == null || line.isEmpty())) {
                            output_data_count++;
                            line = reader.readLine();
                        }
                    } catch (IOException e) {

                    }
                    inputStream.close();

                    if (output_data_count > 0) {
                        inputStream = assetManager.open("output_data.bin");
                        output_data = new float[output_data_count];
                        j = 0;
                        reader = new BufferedReader(new InputStreamReader(inputStream));
                        try {
                            String line = reader.readLine();
                            while (j < output_data_count) {
                                output_data[j++] = Float.parseFloat(line);
                                line = reader.readLine();
                            }
                        } catch (IOException e) {

                        }
                        inputStream.close();
                    }
                } catch (IOException e) {
                }


                Thread mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        AssetManager assetManager = getAssets();
                        InputStream inputStream = null;
                        int count = 0;


                        Log.d(TAG, "Reading raw data");
                        try {
                            inputStream = assetManager.open("raw_data_em.bin");
                            raw_data = readData(inputStream, 512*raw_data_sec_len);
                            raw_data_index = 512*raw_data_sec_len;
                            short raw[] = new short[512];
                            short pq[] = new short[1];
                            pq[0] = 0;
                            inputStream.close();
                            /*while (count < raw_data_sec_len) {
                                for (int i=0;i<512;i++) {
                                    raw[i] = raw_data[count*512 + i];
                                }
                                //nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_BULK_EEG.value, raw_data, 512 * raw_data_sec_len);
                                nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw, 512);
                                nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pq, 1);
                                count++;
                                Thread.sleep(500);
                                //Log.d(TAG, "Sent [" + count + "s]");
                            }*/
                            nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_BULK_EEG.value, raw_data, 512 * raw_data_sec_len);
                        } catch (IOException e) {

                        }/* catch (InterruptedException e) {
                            e.printStackTrace();
                        }*/
                        Log.d(TAG, "Finished reading data");

                    }
                });
                mThread.start();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bRunning == false) {
                    nskAlgoSdk.NskAlgoStart(false);
                } else {
                    nskAlgoSdk.NskAlgoPause();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nskAlgoSdk.NskAlgoStop();
            }
        });

        setAlgosButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // check selected algos
                int algoTypes = 0;// = NskAlgoType.NSK_ALGO_TYPE_CR.value;

                startButton.setEnabled(false);
                stopButton.setEnabled(false);
                clearAllSeries();
                text.setVisibility(View.INVISIBLE);
                text.setText("");

                yyText.setEnabled(false);

                currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_INVALID;
                intervalSeekBar.setEnabled(false);
                setIntervalButton.setEnabled(false);
                intervalText.setText("--");

                crThreshold = alThreshold = cpThreshold = NskAlgoConfig.NskAlgoBCQThreshold.NSK_ALGO_BCQ_THRESHOLD_LIGHT;
                yyInterval = 1;
                sqText.setText("");

                if (blinkCheckBox.isChecked()) {
                    algoTypes += NskAlgoType.NSK_ALGO_TYPE_BLINK.value;
                }
                if (yyCheckBox.isChecked()) {
                    algoTypes += NskAlgoType.NSK_ALGO_TYPE_YY.value;
                    yyText.setEnabled(true);
                    yySeries = createSeries("YY");
                }

                if (algoTypes == 0) {
                    showDialog("Please select at least one algorithm");
                } else {
                    if (bInited) {
                        nskAlgoSdk.NskAlgoUninit();
                        bInited = false;
                    }
                    int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath(), "NeuroSky_Release_To_GeneralFreeLicense_Use_Only_Nov 25 2016");
                    if (ret == 0) {
                        bInited = true;
                        showToast("EEG Algo SDK has been initialized successfully", Toast.LENGTH_LONG);
                    }

                    Log.d(TAG, "NSK_ALGO_Init() " + ret);
                    if (false) {
                        int result = nskAlgoSdk.NskAlgoSetConfig(NskAlgoType.NSK_ALGO_TYPE_CR.value, new NskAlgoConfig(1, NskAlgoConfig.NskAlgoBCQThreshold.NSK_ALGO_BCQ_THRESHOLD_LIGHT, 60));
                        if (result != 0) {
                            showToast("Failed to set config [" + result + "]", Toast.LENGTH_LONG);
                        }
                    }
                    String sdkVersion = "SDK ver.: " + nskAlgoSdk.NskAlgoSdkVersion();

                    if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BLINK.value) != 0) {
                        sdkVersion += "\nBlink ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BLINK.value);
                    }
                    if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_YY.value) != 0) {
                        sdkVersion += "\nYinYang ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_YY.value);
                    }
                    showToast(sdkVersion, Toast.LENGTH_LONG);
                }
                removeAllSeriesFromPlot();
                setupPlot(-1, 1, "YinYang");
                addSeries(plot, yySeries, R.xml.line_point_formatter_with_plf1);
                plot.redraw();

                text.setVisibility(View.INVISIBLE);

                currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_YY;

                intervalSeekBar.setMax(9);
                intervalSeekBar.setProgress(yyInterval - 1);
                intervalSeekBar.setEnabled(true);
                intervalText.setText(String.format("%d", yyInterval));
                setIntervalButton.setEnabled(true);
            }
        });

        yyText.setEnabled(false);
        yyText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeAllSeriesFromPlot();
                setupPlot(-1, 1, "YinYang");
                addSeries(plot, yySeries, R.xml.line_point_formatter_with_plf1);
                plot.redraw();

                text.setVisibility(View.INVISIBLE);

                currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_YY;

                intervalSeekBar.setMax(9);
                intervalSeekBar.setProgress(yyInterval - 1);
                intervalSeekBar.setEnabled(true);
                intervalText.setText(String.format("%d", yyInterval));
                setIntervalButton.setEnabled(true);
            }
        });

        intervalSeekBar.setEnabled(false);
        intervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_AP) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_ME) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_ME2) {
                    intervalText.setText(String.format("%d", (progress + 30)));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_F) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_F2) {
                    intervalText.setText(String.format("%d", (progress + 30)));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_CR) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_AL) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_CP) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_ET) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_YY) {
                    intervalText.setText(String.format("%d", progress + 1));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                bLastOutputInterval = seekBar.getProgress();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_YY) {
                    yyInterval = seekBar.getProgress() + 1;
                    intervalText.setText(String.format("%d", yyInterval));
                }

            }
        });

        setIntervalButton.setEnabled(false);
        setIntervalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int ret = -1;
                String toastStr = "";
                if (currentSelectedAlgo == NskAlgoType.NSK_ALGO_TYPE_YY) {
                    ret = nskAlgoSdk.NskAlgoSetConfig(currentSelectedAlgo.value, new NskAlgoConfig(yyInterval));
                    if (ret == 0) {
                        toastStr = "Output interval of " + currentSelectedAlgo + " set to " + yyInterval;
                    } else {
                        toastStr = "Failed to set output interval of " + currentSelectedAlgo + " to " + yyInterval;
                    }
                }

                if (ret == 0) {
                    showToast(toastStr + ": success", Toast.LENGTH_SHORT);
                } else {
                    showToast(toastStr + ": fail", Toast.LENGTH_SHORT);
                }
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(int level) {
                //Log.d(TAG, "NskAlgoSignalQualityListener: level: " + level);
                final int fLevel = level;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        String sqStr = NskAlgoSignalQuality.values()[fLevel].toString();
                        sqText.setText(sqStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnStateChangeListener(new NskAlgoSdk.OnStateChangeListener() {
            @Override
            public void onStateChange(int state, int reason) {
                String stateStr = "";
                String reasonStr = "";
                for (NskAlgoState s : NskAlgoState.values()) {
                    if (s.value == state) {
                        stateStr = s.toString();
                    }
                }
                for (NskAlgoState r : NskAlgoState.values()) {
                    if (r.value == reason) {
                        reasonStr = r.toString();
                    }
                }
                Log.d(TAG, "NskAlgoSdkStateChangeListener: state: " + stateStr + ", reason: " + reasonStr);
                final String finalStateStr = stateStr + " | " + reasonStr;
                final int finalState = state;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here

                        if (finalState == NskAlgoState.NSK_ALGO_STATE_RUNNING.value || finalState == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                            bRunning = true;
                            startButton.setText("Pausar");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                            bRunning = false;
                            raw_data = null;
                            raw_data_index = 0;
                            startButton.setText("1. Comenzar");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);

                            headsetButton.setEnabled(true);
                            cannedButton.setEnabled(true);

                            if (tgStreamReader != null && tgStreamReader.isBTConnected()) {

                                // Prepare for connecting
                                tgStreamReader.stop();
                                tgStreamReader.close();
                            }

                            output_data_count = 0;
                            output_data = null;

                            System.gc();
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                            bRunning = false;
                            startButton.setText("1. Comenzar");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                            bRunning = true;
                            startButton.setText("1. Comenzar");
                            startButton.setEnabled(false);
                            stopButton.setEnabled(true);
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_INITED.value || finalState == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                            bRunning = false;
                            startButton.setText("1. Comenzar");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                        }
                    }
                });
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(final int level) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        String sqStr = NskAlgoSignalQuality.values()[level].toString();
                        sqText.setText(sqStr);
                    }
                });
            }
        });

        /**
         * DESCUBRIMIENTO
         * AQUI ocurre la magia con el algoritmo YY
         * Value puede ser -1 (no me gujta), 0 o 1 (me gujta)
         * "Que tanto" me gusta esta dado por el algoritmo ET, que fue borrado por facilidad
         */
        nskAlgoSdk.setOnYYAlgoIndexListener(new NskAlgoSdk.OnYYAlgoIndexListener() {
            @Override
            public void onYYAlgoIndex(final float value) {
                Log.d(TAG, "NskAlgoYYAlgoIndexListener: YY: " + value);
                final String yyStr = "[" + value + "]";
                final String finalYYStr = yyStr;
                final float fValue = value;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Aqui se cambian las cosas en la interfaz!
                        // AQUI TAMBIEN TENEMOS QUE HACER LA COMUNICACION CON EL REPRODUCTOR
                        if (value == 0){
                            contadorYY++;
                        }
                        if (value > 0) {
                            //ESTO QUIERE DECIR QUE ME GUSTA (para efectos del prototipo no deberia hacerse nada aqui)
                            contadorYY++;
                            yyGustoImage.setImageResource(R.mipmap.led_on);
                            Timer timer = new Timer();
                            timer.schedule(new TimerTask() {
                                public void run() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            yyGustoImage.setImageResource(R.mipmap.led_off);
                                        }
                                    });
                                }
                            }, 500);
                        }
                        if (value < 0){
                            //ESTO QUIERE DECIR QUE NO ME GUSTA
                            //android.media.
                            contadorYY++;
                            contadorDisgusto++;

                            yyDisgustoImage.setImageResource(R.mipmap.led_on);
                            Timer timer = new Timer();
                            
                            timer.schedule(new TimerTask() {
                                public void run() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            yyDisgustoImage.setImageResource(R.mipmap.led_off);
                                        }
                                    });
                                }
                            }, 500);
                            if(contadorDisgusto>=1 && playing)
                            {
                                ArrayList<File> songs = findSongs(new File("/sdcard"));
                                songpos = rand.nextInt((songs.size()-1));
                                File song = findSongs(new File("/sdcard")).get(songpos);
                                Uri ur =  Uri.parse(song.toString());
                                if(mp.isPlaying()){
                                    mp.stop();
                                    mp.release();
                                }
                                mp = MediaPlayer.create(getApplicationContext(), ur);
                                //Reproduce cancion correctamente
                                mp.start();
                                contadorDisgusto = 0;
                                contadorYY = 0;
                            }
                        }
                        AddValueToPlot(yySeries, fValue);
                    }
                });
            }
        });

        nskAlgoSdk.setOnEyeBlinkDetectionListener(new NskAlgoSdk.OnEyeBlinkDetectionListener() {
            @Override
            public void onEyeBlinkDetect(int strength) {
                Log.d(TAG, "NskAlgoEyeBlinkDetectionListener: Eye blink detected: " + strength);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        blinkImage.setImageResource(R.mipmap.led_on);
                        if(playing){
                            playing = false;
                            mp.pause();
                            lastSongText.setText("Pausado");
                            pauseSongButton.setText(">");
                        }
                        else
                        {
                            mp.start();
                            playing = true;
                            lastSongText.setText("Reproduciendo");
                            pauseSongButton.setText("||");
                        }
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        blinkImage.setImageResource(R.mipmap.led_off);
                                    }
                                });
                            }
                        }, 500);
                    }
                });
            }
        });

        //INICIALIZAR BOTONES Y ESAS COSAS

        pauseSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mp != null){
                    if(playing){
                        playing = false;
                        mp.pause();
                        lastSongText.setText("Pausado");
                        pauseSongButton.setText(">");
                    }
                    else
                    {
                        mp.start();
                        playing = true;
                        lastSongText.setText("Reproduciendo");
                        pauseSongButton.setText("||");
                    }
                }
            }
        });

        nextSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lastSongText.setText("Reproduciendo");
                if (mp != null && playing){
                    ArrayList<File> songs = findSongs(new File("/sdcard"));
                    songpos = rand.nextInt((songs.size()-1));
                    File song = findSongs(new File("/sdcard")).get(songpos);
                    Uri ur =  Uri.parse(song.toString());
                    mp.stop();
                    mp.release();
                    mp = MediaPlayer.create(getApplicationContext(), ur);
                    //Reproduce cancion correctamente
                    mp.start();
                }
            }
        });

        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.myPlot);
        plot.setVisibility(View.INVISIBLE);
        text = (EditText) findViewById(R.id.myText);
        text.setVisibility(View.INVISIBLE);

        //Reproduce musica
        ArrayList<File> songs = findSongs(new File("/sdcard"));
        songpos = rand.nextInt((songs.size()-1));
        File song = songs.get((songpos));
        Uri ur =  Uri.parse(song.toString());

        if(mp == null && song != null) {
            mp = MediaPlayer.create(getApplicationContext(), ur);
            //Reproduce cancion correctamente
            mp.start();
            playing = true;
        }

        setAlgosButton.performClick();
    }

    //Esto encuentra canciones dada una ruta especifica, pero no es llamado en ningun momento aun
    private ArrayList<File> findSongs(File root)
    {
        ArrayList<File> songs = new ArrayList<File>();
        File[] file =  root.listFiles();
        System.out.println(file);
        System.out.println(root);
        for(File singlefile : file)
        {
          if(singlefile.isDirectory() && !singlefile.isHidden())
          {
              songs.addAll(findSongs(singlefile));
          }
          else
          {
              if(singlefile.getName().endsWith(".mp3"))
              {
                  songs.add(singlefile);
              }
          }
        }
        return songs;
    }

    private void removeAllSeriesFromPlot () {
        if (yySeries != null) {
            plot.removeSeries(yySeries);
        }
        if (bp_deltaSeries != null) {
            plot.removeSeries(bp_deltaSeries);
        }
        if (bp_thetaSeries != null) {
            plot.removeSeries(bp_thetaSeries);
        }
        if (bp_alphaSeries != null) {
            plot.removeSeries(bp_alphaSeries);
        }
        if (bp_gammaSeries != null) {
            plot.removeSeries(bp_gammaSeries);
        }
        if (bp_betaSeries != null) {
            plot.removeSeries(bp_betaSeries);
        }
        System.gc();
    }

    private void clearAllSeries () {
        if (yySeries != null) {
            plot.removeSeries(yySeries);
            yySeries = null;
        }
        if (bp_deltaSeries != null) {
            plot.removeSeries(bp_deltaSeries);
            bp_deltaSeries = null;
        }
        if (bp_thetaSeries != null) {
            plot.removeSeries(bp_thetaSeries);
            bp_thetaSeries = null;
        }
        if (bp_alphaSeries != null) {
            plot.removeSeries(bp_alphaSeries);
            bp_alphaSeries = null;
        }
        if (bp_gammaSeries != null) {
            plot.removeSeries(bp_gammaSeries);
            bp_gammaSeries = null;
        }
        if (bp_betaSeries != null) {
            plot.removeSeries(bp_betaSeries);
            bp_betaSeries = null;
        }
        plot.setVisibility(View.INVISIBLE);
        System.gc();
    }

    private XYPlot setupPlot (Number rangeMin, Number rangeMax, String title) {
        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.myPlot);

        plot.setDomainLeftMax(0);
        plot.setDomainRightMin(X_RANGE);
        plot.setDomainRightMax(X_RANGE);

        if ((rangeMax.intValue() - rangeMin.intValue()) < 10) {
            plot.setRangeStepValue((rangeMax.intValue() - rangeMin.intValue() + 1));
        } else {
            plot.setRangeStepValue(11);
        }
        plot.setRangeBoundaries(rangeMin.intValue(), rangeMax.intValue(), BoundaryMode.FIXED);

        plot.getGraphWidget().getGridBackgroundPaint().setColor(Color.WHITE);

        plot.setTicksPerDomainLabel(10);
        plot.getGraphWidget().setDomainLabelOrientation(-45);

        plot.setPlotPadding(0, 0, 0, 0);
        plot.setTitle(title);

        plot.setVisibility(View.VISIBLE);

        return plot;
    }

    private SimpleXYSeries createSeries (String seriesName) {
        // Turn the above arrays into XYSeries':
        SimpleXYSeries series = new SimpleXYSeries(
                null,          // SimpleXYSeries takes a List so turn our array into a List
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
                seriesName);                             // Set the display title of the series

        series.useImplicitXVals();

        return series;
    }

    private SimpleXYSeries addSeries (XYPlot plot, SimpleXYSeries series, int formatterId) {

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter seriesFormat = new LineAndPointFormatter();
        seriesFormat.setPointLabelFormatter(null);
        seriesFormat.configure(getApplicationContext(), formatterId);
        seriesFormat.setVertexPaint(null);
        series.useImplicitXVals();

        // add a new series' to the xyplot:
        plot.addSeries(series, seriesFormat);

        return series;
    }

    private int gcCount = 0;
    private void AddValueToPlot (SimpleXYSeries series, float value) {
        if (series.size() >= X_RANGE) {
            series.removeFirst();
        }
        Number num = value;
        series.addLast(null, num);
        plot.redraw();
        gcCount++;
        if (gcCount >= 20) {
            System.gc();
            gcCount = 0;
        }
    }

    private short [] readData(InputStream is, int size) {
        short data[] = new short[size];
        int lineCount = 0;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            while (lineCount < size) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    Log.d(TAG, "lineCount=" + lineCount);
                    break;
                }
                data[lineCount] = Short.parseShort(line);
                lineCount++;
            }
            Log.d(TAG, "lineCount=" + lineCount);
        } catch (IOException e) {

        }
        return data;
    }

    @Override
    public void onBackPressed() {
        nskAlgoSdk.NskAlgoUninit();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public static String Datetime()
    {
        Calendar c = Calendar.getInstance();

        String sDate = "[" + c.get(Calendar.YEAR) + "/"
                + (c.get(Calendar.MONTH)+1)
                + "/" + c.get(Calendar.DAY_OF_MONTH)
                + " " + c.get(Calendar.HOUR_OF_DAY)
                + ":" + String.format("%02d", c.get(Calendar.MINUTE))
                + ":" + String.format("%02d", c.get(Calendar.SECOND)) + "]";
        return sDate;
    }
    
    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    // Do something when connecting
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    showToast("Connected", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //(9) demo of recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    //tgStreamReader.startRecordRawData();

                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Button startButton = (Button) findViewById(R.id.startButton);
                            startButton.setEnabled(true);
                        }

                    });

                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    //tgStreamReader.stopRecordRawData();

                    showToast("Get data time out!", Toast.LENGTH_SHORT);

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that.

                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e(TAG,"onRecordFail: " +flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short)data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                default:
                    break;
            }
        }

    };

    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    private void showDialog (String message) {
        new AlertDialog.Builder(this)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
