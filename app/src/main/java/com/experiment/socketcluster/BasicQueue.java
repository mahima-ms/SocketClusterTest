package com.experiment.socketcluster;

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

public class BasicQueue extends AppCompatActivity {

    String url = "ws://10.10.9.250:8000/socketcluster/";
    Socket socket;
    int count = 0;
    ConcurrentLinkedQueue<JSONObject> concurrentLinkedQueue = new ConcurrentLinkedQueue<>();
    private String TAG = BasicQueue.class.getCanonicalName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_queue);
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
                startExecutionIfNotRunning();
            }

            @Override public void onDisconnected(Socket socket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) {
                Log.i(TAG, "Disconnected from end-point");
                currentTask = null;
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

        ((Button) findViewById(R.id.start_msg)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (int i = 0; i < 10; i++) {
                    queueMsgForServer(i);
                }
            }
        });
    }

    private void queueMsgForServer(int i) {
        ++count;
        JSONObject object = new JSONObject();
        try {
            JSONObject dataObject = new JSONObject();
            dataObject.put("data", "{ " + count + "}");
            object.put("data", dataObject);
            object.put("ackCheckSome", Md5.encrypt(dataObject.toString()));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        concurrentLinkedQueue.add(object);
        startExecutionIfNotRunning();
    }

    JSONObject currentTask = null;

    private void startExecutionIfNotRunning() {
        if (currentTask == null && !concurrentLinkedQueue.isEmpty()) {
            currentTask = concurrentLinkedQueue.peek();
            socket.getChannelByName("MyClassroom").publish(currentTask, new Ack() {
                @Override
                public void call(String name, Object error, Object data) {
                    Log.i(TAG, "acknowledgment name" + name +
                            "\nerror" + error +
                            "\ndata" + data);
                    try {
                        if (currentTask.getString("ackCheckSome").contentEquals((String) data)) {
                            concurrentLinkedQueue.poll();
                            currentTask = null;
                            startExecutionIfNotRunning();
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
}
