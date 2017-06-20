package com.example.jptalusan.audiorecord;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.jptalusan.audiorecord.R.id.spinner;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 1000;
    private int sampleRate = 44100;
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
    private TextView name;
    ArrayAdapter<Integer> adapter;

    //https://developer.android.com/guide/topics/ui/controls/spinner.html
    private Spinner sampleRates;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();
        //Set permissions here

        sampleRates = (Spinner) findViewById(spinner);

        setButtonHandlers();
        enableButtons(false);

        sampleRates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sampleRate = (int)parent.getItemAtPosition(position);
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        sampleRate, RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING, bufferSize);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        name = (TextView)findViewById(R.id.textView);

        System.out.println("Buff size: " + bufferSize);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (recorder != null)
            recorder.release();
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

        audioFileName = Utilities.generateFileName();
        name.setText(audioFileName);

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

        audioLogFile = new File(Environment.getExternalStorageDirectory().getPath() + "/" + audioFileName + "_raw.csv");
        try {
            audioLogFile.createNewFile();
            audioLog = new FileWriter(audioLogFile, true);
            audioLog.write("Timestamp, myEquation, theirEquation");
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
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                int out = Utilities.calculatePowerDb(sData, 0, BufferElements2Rec * BytesPerElement);
                // Just a short snippet to prevent no data errors
                out = out < -70 ? -70 : out;
                double out5 = Utilities.getPower(sData);
                byte bData[] = short2byte(sData);
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

    private  boolean checkAndRequestPermissions() {
        int permissionRecordAudio = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);
        int permissionWriteStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (permissionWriteStorage != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissionRecordAudio != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d(TAG, "Permission callback called-------");
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {

                Map<String, Integer> perms = new HashMap<>();
                // Initialize the map with both permissions
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);
                // Fill with actual results from user
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    // Check for both permissions
                    if (perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "storage and audio record services permission granted");
                        adapter = new ArrayAdapter<>(getApplicationContext(),
                                android.R.layout.simple_spinner_item, Utilities.findAudioRecord());
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        sampleRates.setAdapter(adapter);

                        // process the normal flow
                        //else any one or both the permissions are not granted
                    } else {
                        Log.d(TAG, "Some permissions are not granted ask again ");
                        //permission is denied (this is the first time, when "never ask again" is not checked) so ask again explaining the usage of permission
//                        // shouldShowRequestPermissionRationale will return true
                        //show the dialog or snackbar saying its necessary and try again otherwise proceed with setup.
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            showDialogOK("SMS and Location Services Permission required for this app",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (which) {
                                                case DialogInterface.BUTTON_POSITIVE:
                                                    checkAndRequestPermissions();
                                                    break;
                                                case DialogInterface.BUTTON_NEGATIVE:
                                                    // proceed with logic by disabling the related features or quit the app.
                                                    finish();
                                                    break;
                                            }
                                        }
                                    });
                        }
                        //permission is denied (and never ask again is  checked)
                        //shouldShowRequestPermissionRationale will return false
                        else {
                            Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_LONG)
                                    .show();
                            //                            //proceed with logic by disabling the related features or quit the app.
                        }
                    }
                }
            }
        }

    }

    private void showDialogOK(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", okListener)
                .create()
                .show();
    }

}
