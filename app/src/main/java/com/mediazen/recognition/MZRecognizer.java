package com.mediazen.recognition;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class MZRecognizer {
    public static final String LOG = "MZRecognizer";
    private static final MZRecognizer ourInstance = new MZRecognizer();
    private MZRecognitionListener listener;
    private AudioRecord mAudioRecord;
    private SocketModule socket;

    private static final int SAMPLE_RATE = 16000;
    //  인식전 초반에 대기하는 시간
    private static final int WAIT_SECONDS = 0;

    // 미사용중
    private int timeout_second = 10;
    private int noaction_second = 5;

    private boolean currentlySendingAudio = false;
    private boolean socketReady = false;
    private String prev_result = "";

    private URI uri; // 웹소켓 주소;

    private int state = 0; // 0 정지, 1 준비, 2 녹음중, 3 closing


    public static MZRecognizer getInstance() {
        return ourInstance;
    }

    public int getState(){return state;};

    private MZRecognizer() {
    }

    public void setRecognitionListener(MZRecognitionListener listener){
        this.listener = listener;
    }
    public void setURI(String uri) {
        try{
        this.uri = new URI(uri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    public void record() {
        try {
            if (uri == null) {
                throw new NotSetURIException("URI isn't set.");
            }
            state = 1;
            Log.d(LOG, "record()");

            socket = new SocketModule(uri);
            socket.connect();
            //소켓 연결 시도를 오디오 스레드보다 먼저 시작하지만,
            // 로그 상에서 스레드가 시작한 후 소켓 OPEN

            Thread thread = new Thread(rec);
            thread.start();

        } catch(Exception e) {
            Log.d(LOG,e.toString());
        }

    }

    public void recordStop() {
        Log.d(LOG, "recordStop()");
        state = 3;
        socketReady = false;
        currentlySendingAudio = false;
        socket.close();


        try {
            if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                mAudioRecord.stop();
            }
            if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mAudioRecord.release();
            }
        }
        catch (Exception ex)
        {

        }

        OnReceiveMessage("!@Stop");
    }


    private class SocketModule extends WebSocketClient {

        public SocketModule(URI serverURI) {
            super(serverURI);
        }

        public SocketModule(URI serverUri, Draft draft) {
            super(serverUri, draft);
        }

        public SocketModule(URI serverUri, Draft draft, Map<String, String> headers, int connecttimeout) {
            super(serverUri, draft, headers, connecttimeout);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            try {
                Log.d(LOG, "Websocket Open...");
                JSONObject join = new JSONObject();
                join.put("language", "ko");
                join.put("intermediates", true);
                join.put("cmd","join");


                send(join.toString());

            } catch (JSONException e){
                Log.d(LOG,"onOpen: JSONhandle error");
            }

        }

        @Override
        public void onMessage(String message) {
            JSONObject jObject;
            try {
                jObject = new JSONObject(message);
                Log.d(LOG, "msg-payload: "+jObject.getString("payload"));
                JSONObject payload = (JSONObject) new JSONObject(jObject.getString("payload"));

                String event = jObject.getString("event");
                //event == new:text

                if (event.equals("reply")){
                    // handshake 성공
                    OnReceiveMessage("!@Ready");
                    state = 2;
                    socketReady = true;
                    Log.d(LOG, "start");

                } else if (event.equals("new:text")) {
                    //epd true result show

                    String epd = payload.getString("epd");

                    if (epd.equals("true")) {
                        String text = payload.getString("text");
                        float confidence = Float.parseFloat(payload.getString("confidence"));
                        //최종 결과
                        ReconitionResult result = new ReconitionResult();
                        result.result = text;
                        result.confidence = confidence;
                        OnReceiveMessage(result);
                    } else if(epd.equals("false")){
                        String text = payload.getString("text");
                        OnReceiveMessage(text);
                    }

                } else if(event.equals("close")){
                    Log.d(LOG,"event close");
                    if(payload.has("status")){
                        String status = payload.getString("status");
                        if(status.equals("full") || status.equals("cant_connect")){
                            OnReceiveMessage(status);
                        }

                            OnReceiveMessage("connection fail");
                    }
                    recordStop();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            if(currentlySendingAudio) {
                Log.d(LOG, "not yet Audio stopped");
                recordStop();
            } else {
                state = 0;
            }
        }

        @Override
        public void onError(Exception ex) {
            Log.d(LOG,ex.toString());
        }
    }

    private Runnable rec = new Runnable() {
        @Override
        public void run() {
            int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            currentlySendingAudio = true;

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            mAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);
            mAudioRecord.startRecording();
            byte[] buffer = new byte[BUFFER_SIZE];
            short[] shorts = new short[BUFFER_SIZE / 2];

            int total_bytes = 0;
            //약 18개를 버린다. 2byte 샘플이므로 9 frame 정도 드랍
            //한번에 1024 정도 읽고 있으므로 18432 byte (약 1.2초 정도의 데이터를 버림)
            int skip_count = 1;
            boolean is_time_out = false;
            Log.d(LOG, "RecordThread start...");
            while (currentlySendingAudio) {

                final int read = mAudioRecord.read(buffer, 0, buffer.length);
                if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                    Log.d(LOG, "Audio error...");
//                            OnReceiveMessage("<eof>");
                    break;
                } else {
                    if (skip_count > 0) {
                        skip_count--;
                        continue;
                    }
                    total_bytes += read;
                    if (WAIT_SECONDS > 0) {
                        if (total_bytes < WAIT_SECONDS * SAMPLE_RATE * 2)
                            continue;
                    }

                    if (socketReady) {
                        if(socket.getReadyState().equals(WebSocket.READYSTATE.OPEN)){
                            OnReceiveMessage(buffer);
                            socket.send(buffer);
                        }
                    }

                }

            }
            if(socket.getReadyState() == WebSocket.READYSTATE.CLOSED) {
                state = 0;
            }
        }

    };

    public void OnReceiveMessage(String message)
    {
        Log.d(LOG,"OnReceiveMessage "+message);
        Message msg = mCallbackhandler.obtainMessage();
        Bundle bundle = new Bundle();
        if(message.equals("!@Ready")){
            msg.what = 3;
            mCallbackhandler.sendMessage(msg);
        } else if(message.equals("!@Stop")){
            msg.what = 4;
            mCallbackhandler.sendMessage(msg);
        } else {

            bundle.putString("data", message);
            msg.what = 1;
            msg.setData(bundle);
            mCallbackhandler.sendMessage(msg);
        }
    }
    public void OnReceiveMessage(ReconitionResult result)
    {
        Log.d(LOG,"OnReceiveMessage last "+result.result);
        Message msg = mCallbackhandler.obtainMessage();

        msg.what = 5;
        msg.obj = result;
        mCallbackhandler.sendMessage(msg);

    }

    public void OnReceiveMessage(byte[] buffer)
    {
        if(buffer != null) {
            Log.d(LOG, "OnReceiveMessage " + buffer.toString());
            Message msg = mCallbackhandler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putByteArray("byte", buffer);
            msg.what = 2;
            msg.setData(bundle);
            mCallbackhandler.sendMessage(msg);
        } else {
            Log.d(LOG,"OnReceiv null");
        }

    }



    final Handler mCallbackhandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            Bundle bundle = msg.getData();
            if (msg.what == 1) {
                String message = bundle.getString("data");
                Log.d(LOG,"handler " +message);
                if(message != null) {
                    if (message.startsWith("<eof>")) {
                        MZRecognizer.getInstance().listener.onPartialResult(prev_result);
                        MZRecognizer.getInstance().listener.onPartialResult(message);
                    } else {
                        prev_result = message;
                        //  중간결과는 보내지 않도록 한다.
                        MZRecognizer.getInstance().listener.onPartialResult(message);
                    }
                }
            } else if(msg.what == 2){
                byte[] buffer = bundle.getByteArray("byte");
                if(buffer != null) {
                    MZRecognizer.getInstance().listener.onRecord(buffer);
                } else {
                    Log.d(LOG,"null");
                }
            } else if(msg.what == 3) {
                MZRecognizer.getInstance().listener.onReady();
            } else if(msg.what == 4) {
                MZRecognizer.getInstance().listener.onRecordStop();
            } else if(msg.what == 5) {
                Log.d(LOG, ((ReconitionResult)msg.obj).result);
                MZRecognizer.getInstance().listener.onResult((ReconitionResult)msg.obj);
            }
         }
    };

    class NotSetURIException extends Exception {
        public NotSetURIException(String msg) {
            super(msg);
        }
        public NotSetURIException(Exception e) {
            super(e);
        }
    }

}
