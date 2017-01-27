package com.example.jptalusan.audiorecord;

import android.app.Notification;
import android.app.NotificationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Spinner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize = 0;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private NotificationCompat.Builder mBuilder;
    private double mRmsSmoothed = 0;
    private String audioFileName = "";
    private File audioLogFile;

    //https://developer.android.com/guide/topics/ui/controls/spinner.html
    private Spinner sampleRates;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setButtonHandlers();
        enableButtons(false);

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        System.out.println("Buff size: " + bufferSize);
    }

    //http://stackoverflow.com/questions/35236494/dismiss-current-notification-on-action-clicked
    //cancelling notification on click or point to folder
    //http://stackoverflow.com/questions/34649491/opening-particular-folder-on-clicking-on-status-notification
    private void setupNotificationBuilder() {
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.icon)
                        .setContentTitle("Audio Recorder")
                        .setContentText("Recording...");

        mBuilder.build().flags |= Notification.FLAG_AUTO_CANCEL;

    }

    private void setButtonHandlers() {
        (findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStop)).setOnClickListener(btnClick);
    }

    private void enableButton(int id, boolean isEnable) {
        (findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {
        //changed buffer size from 1024 * 2 to minbuffersize
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, bufferSize);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    private void writeAudioDataToFile() {
        // Write the output audio in byte
        FileWriter audioLog;
        //TODO: Change to human readable date names HOUR:MIN-DAY-MONTH-YEAR.file_ext
        audioFileName = Utilities.generateFileName();
        audioLogFile = new File(Environment.getExternalStorageDirectory().getPath() + "/" + audioFileName + "_raw.csv");
        try {
            audioLogFile.createNewFile();
            audioLog = new FileWriter(audioLogFile, true);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create " + audioFileName);
        }
        String filePath = Environment.getExternalStorageDirectory().getPath() + "/" + audioFileName + "_pcm.pcm";
        short sData[] = new short[BufferElements2Rec * BytesPerElement];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //TODO: Write labels to first row(short or byte) is correct in providing the raw data (but i think i already did this)
        while (isRecording) {
            // gets the voice output from microphone to byte format

            recorder.read(sData, 0, BufferElements2Rec * BytesPerElement);
            //System.out.println("Short writing to file: " + sData.toString());
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                int out = Utilities.calculatePowerDb(sData, 0, BufferElements2Rec * BytesPerElement);
//                double out2 = getPower();
//                double out4 = Utilities.getPower2(sData);

                //TODO: Move bData above (Same as sData) so it wont be wrong values.
                byte bData[] = short2byte(sData);
//                int out3 = Utilities.calculatePowerDb(bData, 0, BufferElements2Rec * BytesPerElement);
                double out5 = Utilities.getPower(bData);
//                System.out.println("out:" + out + ", out3:" + out3  + ", out4:" + out4 +  ", double:" + out2 + ", double2:" + out5);
                String audioData = Utilities.setupDate() + "," + out + "," + out5;
                audioLog.write(audioData + "\r\n");
                os.write(bData, 0, BufferElements2Rec * BytesPerElement * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
            audioLog.flush();
            audioLog.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getPower(){
        Log.d("RMS", mRmsSmoothed + "");
        byte[] buffer = new byte[BufferElements2Rec * BytesPerElement];
        //int read (byte[] audioData, int offsetInBytes, int sizeInBytes)
        recorder.read(buffer, 0, BufferElements2Rec * BytesPerElement);
        /*
         * Noise level meter begins here
         */
        // Compute the RMS value. (Note that this does not remove DC).
        double rms = 0;
        for (int i = 0; i < buffer.length; i++) {
            rms += buffer[i] * buffer[i];
        }
        rms = Math.sqrt(rms / buffer.length);
        double mAlpha = 0.9;
        double mGain = 0.0044;
        /*Compute a smoothed version for less flickering of the
        // display.*/
        mRmsSmoothed = mRmsSmoothed * mAlpha + (1 - mAlpha) * rms;
//		Log.w("rain316", "RMS Smoothed: " + Double.toString(mRmsSmoothed));
        double rmsdB = -1;
        if (mGain * mRmsSmoothed > 0.0f)
            rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed);
        else rmsdB = -30.0;
        //Log.w("rain316", "RMS dB: " + Double.toString(rmsdB));
        return rmsdB;
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    enableButtons(true);
                    setupNotificationBuilder();
                    // Sets an ID for the notification
                    int mNotificationId = 001;
                    // Gets an instance of the NotificationManager service
                    NotificationManager mNotifyMgr =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    // Builds the notification and issues it.
                    mNotifyMgr.notify(mNotificationId, mBuilder.build());
                    startRecording();
                    break;
                }
                case R.id.btnStop: {
                    enableButtons(false);
                    // Sets an ID for the notification
                    int mNotificationId = 001;
                    // Gets an instance of the NotificationManager service
                    NotificationManager mNotifyMgr =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    // Builds the notification and issues it.
                    mBuilder.setContentTitle("Saved at:" + audioFileName);
                    mBuilder.setContentText("Stopped Recording...");
                    mNotifyMgr.notify(mNotificationId, mBuilder.build());
                    stopRecording();
                    break;
                }
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }
}
