package com.experiment.socketcluster;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.sac.Ack;
import io.github.sac.BasicListener;
import io.github.sac.ReconnectStrategy;
import io.github.sac.Socket;

public class QueueWithSequence extends AppCompatActivity {

    String url = "ws://10.10.9.250:8000/socketcluster/";
    Socket socket;
    int count = 0;
    private String TAG = QueueWithSequence.class.getCanonicalName();
    ConcurrentLinkedQueue<JSONObject> jsonObjects = new ConcurrentLinkedQueue<>();
    final JSONObject[] slidingWindow = new JSONObject[100];
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue_with_sequence);

        socket = new Socket(url);
        socket.setListener(new BasicListener() {
            @Override public void onConnected(Socket socket, Map<String, List<String>> headers) {
                Log.i(TAG, "onConnected: ");
                socket.createChannel("MyClassroom").subscribe(new Ack() {
                    @Override
                    public void call(String name, Object error, Object data) {
                        if (error == null) {
                            Log.i(TAG, "subscribed to channel " + name);
                        }
                    }
                });
            }

            @Override public void onDisconnected(Socket socket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) {
                Log.i(TAG, "Disconnected from end-point");
            }

            @Override public void onConnectError(Socket socket, WebSocketException exception) {
                Log.i(TAG, "onConnectError " + exception.getMessage());
            }

            @Override public void onAuthentication(Socket socket, Boolean status) {
                Log.i(TAG, "onAuthentication: ");
            }

            @Override public void onSetAuthToken(String token, Socket socket) {
                Log.i(TAG, "onSetAuthToken: ");
                socket.setAuthToken(token);
            }
        });

        socket.setReconnection(new ReconnectStrategy().setMaxAttempts(Integer.MAX_VALUE).setDelay(3000));
        socket.connectAsync();

        handler.postDelayed(sweepAndSend, 1000);

        for (int i = 0; i < slidingWindow.length; i++) {
            slidingWindow[i] = null;
        }

        ((Button) findViewById(R.id.start_msg)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (int i = 0; i < 1000; i++) {
                    queueMsgForServer(i);
                }
            }
        });
    }

    Runnable sweepAndSend = new Runnable() {
        @Override public void run() {
            Log.d(TAG, "sweepAndSend run: ");
            for (int i = 0; i < slidingWindow.length; i++) {
                if (slidingWindow[i] != null) {
                    try {
                        slidingWindow[i].getJSONObject("ackCheckSome").put("currentSeq", i);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    socket.getChannelByName("MyClassroom").publish(slidingWindow[i], new Ack() {
                        @Override
                        public void call(String name, Object error, Object data) {
                            Log.i(TAG, "acknowledgment name" + name +
                                    "\nerror" + error +
                                    "\ndata" + data);
                            try {
                                JSONObject jsonObject = (JSONObject) data;
                                int currntSeq = jsonObject.getInt("currentSeq");
                                synchronized (slidingWindow) {
                                    slidingWindow[currntSeq] = null;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
//                        if (error==null){
//                        }
                        }
                    });
                }
            }
            fillSlidingWindow();
            handler.postDelayed(sweepAndSend , 100);
        }
    };

    private void fillSlidingWindow() {
        if(isSlidingWindowEmpty()){
            for (int i = 0; i < slidingWindow.length; i++) {
                slidingWindow[i] = jsonObjects.poll();
            }
        }
    }

    private boolean isSlidingWindowEmpty() {
        for (JSONObject aSlidingWindow : slidingWindow) {
            if (aSlidingWindow != null) return false;
        }
        return true;
    }

    private void queueMsgForServer(int i) {
        ++count;
        JSONObject object = new JSONObject();
        try {
            JSONObject dataObject = new JSONObject();
            dataObject.put("data", "{ " + count + "}");
            object.put("data", dataObject);
            JSONObject ack = new JSONObject();
            ack.put("seq", i);
            ack.put("checksome", Md5.encrypt(dataObject.toString()));
            ack.put("currentSeq", i);
            object.put("ackCheckSome", ack);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        jsonObjects.add(object);
    }
}
