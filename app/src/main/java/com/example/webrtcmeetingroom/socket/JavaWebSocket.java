package com.example.webrtcmeetingroom.socket;

import android.net.Uri;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.example.webrtcmeetingroom.ChatRoomActivity;
import com.example.webrtcmeetingroom.MainActivity;
import com.example.webrtcmeetingroom.connection.PeerConnectionManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class JavaWebSocket {
    private String TAG = JavaWebSocket.class.getSimpleName();

    private PeerConnectionManager peerConnectionManager;
    private WebSocketClient mWebSocketClient;
    private MainActivity activity;

    public JavaWebSocket(MainActivity activity){
        this.activity = activity;
    }

    public void connect(String wss){
        peerConnectionManager = PeerConnectionManager.getOurInstance();
        URI uri = null;
        try {
            uri = new URI(wss);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {

                Log.i(TAG, "onOpen: ");
                ChatRoomActivity.openActivity(activity);
            }

            @Override
            public void onMessage(String message) {
                Log.i(TAG, "onMessage: "+message);
                /**
                 * onMessage: {"eventName":"_peers",
                 * "data":{"connections":[],
                 * "you":"e1e7027c-20d5-434b-9902-2a8691afa733"}}
                 *
                 *  拿到：e1e7027c-20d5-434b-9902-2a8691afa733 就可以去建立p2p连接了
                 */
                Map map  = JSON.parseObject(message,Map.class);
                String eventName= (String) map.get("eventName");
                if (eventName.equals("_peers")){
                    handleJoinRoom(map);
                }

            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i(TAG, "onClose: ");
            }

            @Override
            public void onError(Exception e) {
                Log.i(TAG, "onError: ");
            }
        };
        if (wss.startsWith("wss")){
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null,new TrustManager[]{new TrustManagerTest()},new SecureRandom());
                SSLSocketFactory factory = null;
                if (sslContext!=null){
                    factory = sslContext.getSocketFactory();
                }
                if (factory!=null){
                    mWebSocketClient.setSocket(factory.createSocket());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            mWebSocketClient.connect();

        }
    }

    private void handleJoinRoom(Map map) {
        Map data = (Map) map.get("data");
        JSONArray arr;
        if (data!=null){
            arr = (JSONArray) data.get("connections");
            String js = com.alibaba.fastjson.JSONObject.toJSONString(arr, SerializerFeature.WriteClassName);
            ArrayList<String> connections = (ArrayList<String>) com.alibaba.fastjson.JSONObject.parseArray(js,String.class);
            String myId = (String) data.get("you");
            peerConnectionManager.joinToRoom(this,true,connections,myId);
        }
    }

    /**
     *  告诉服务器要加入房间，其实就是给服务器发送一个规定格式 的json数据
     *
     * @param roomId
     *
     *        事件类型
     *             1  __join
     *             2  __answer
     *             3  __offer
     *             4  __ice_candidate
     *             5  __peer
     */
    public void joinRoom(String roomId) {
        Map<String, Object> map = new HashMap<>();
        map.put("eventName", "__join");
        Map<String, String> childMap = new HashMap<>();
        childMap.put("room", roomId);
        map.put("data", childMap);
        JSONObject object = new JSONObject(map);
        final String jsonString = object.toString();
        Log.d(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);

    }




    public static class TrustManagerTest implements X509TrustManager{

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }



}
