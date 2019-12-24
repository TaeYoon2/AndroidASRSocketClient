package com.mediazen.mzasrmemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.mediazen.recognition.MZRecognitionListener;
import com.mediazen.recognition.MZRecognizer;
import com.mediazen.recognition.ReconitionResult;

public class MainActivity extends AppCompatActivity implements MZRecognitionListener, View.OnClickListener {
    static final String LOG = "MZ-main";
    TextView tv;
    TextView tv2;
    Button btn;
    MZRecognizer recognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestStoragePermissions();

        tv = (TextView) findViewById(R.id.tv);
        tv2 = (TextView) findViewById(R.id.tv2);
        btn = (Button) findViewById(R.id.btn);
        btn.setOnClickListener(this);

        recognizer = MZRecognizer.getInstance();
        recognizer.setRecognitionListener(this);

        // Here you should set the Server IP & Port
        recognizer.setURI("ws://IP:PORT/ws");

    }

    @Override
    public void onClick(View view) {
        if(view == btn) {
            if(recognizer.getState() == 0){
                tv.setText("");
                tv2.setText("");
            recognizer.record();
            btn.setText("stop");
            } else if(recognizer.getState() == 2){
                btn.setEnabled(false);
                recognizer.recordStop();
                tv.setText("stopped");

            }
        }
    }

    @Override
    public void onReady() {
        Log.d(LOG,"onReady");
        tv.setText("Ready");
    }

    @Override
    public void onRecord(byte[] buffer) {
        Log.d(LOG,buffer.toString());

    }

    @Override
    public void onPartialResult(String partialResult) {

        Log.d(LOG,"onPartialResult");
        Log.d(LOG,"onPartial: "+ partialResult);
        tv.setText(partialResult);
    }

    @Override
    public void onResult(ReconitionResult result) {
        Log.d(LOG,"onResult");
        Log.d(LOG,"onResult: "+result.result);
        tv.setText(result.result);
        tv2.setText(String.valueOf(result.confidence));
    }

    @Override
    public void onRecordStop() {
        btn.setText("record");
        btn.setEnabled(true);
    }

    public final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    public final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2;
    private void requestStoragePermissions() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
//                //...
//            } else {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
//                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
//            }
//        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                //...
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                }
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}
