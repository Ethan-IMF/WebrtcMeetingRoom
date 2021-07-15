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
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

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

                handleMessage(message);


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

    private void handleMessage(String message) {
        Map map  = JSON.parseObject(message,Map.class);
        String eventName= (String) map.get("eventName");
        if (eventName.equals("_peers")){
            handleJoinRoom(map);
        }
        // 对方的响应呼叫 获取对方的  iceCandidate
        if (eventName.equals("_ice_candidate")){
            handleRemoteCandidate(map);
        }

        // 接受对方的SDP
        if (eventName.equals("_answer")){
            handleAnswer(map);
        }

    }

    private void handleAnswer(Map map) {
        Map data = (Map) map.get("data");
        Map sdpDic;
        if (data != null) {
            sdpDic = (Map) data.get("sdp");
            String socketId = (String) data.get("socketId");
            String sdp = (String) sdpDic.get("sdp");
            peerConnectionManager.onReceiverAnswer(socketId, sdp);
        }

    }

    // 对方响应呼叫 传回的信息
    private void handleRemoteCandidate(Map map) {
        Map data = (Map) map.get("data");
        String socketId;
        if (data != null) {
            socketId = (String) data.get("socketId");
            String sdpMid = (String) data.get("id");
            sdpMid = (null == sdpMid) ? "video" : sdpMid;
            int sdpMLineIndex = (int) Double.parseDouble(String.valueOf(data.get("label")));
            String candidate = (String) data.get("candidate");
//            生成 IceCandidate对象
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            peerConnectionManager.onRemoteIceCandidate(socketId,iceCandidate);
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

    public void sendOffer(String socketId, SessionDescription sdp){
        HashMap<String, Object> childMap1 = new HashMap();
        childMap1.put("type", "offer");
        childMap1.put("sdp", sdp.description);

        HashMap<String, Object> childMap2 = new HashMap();
        childMap2.put("socketId", socketId);
        childMap2.put("sdp", childMap1);

        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__offer");
        map.put("data", childMap2);

        com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject(map);
        String jsonString  = object.toString();
        Log.d(TAG, "send-->" + jsonString);
        mWebSocketClient.send(jsonString);
    }


    // 将自己的 iceCandidate 传递给服务器
    public void sendIceCandidate(String socketId, IceCandidate iceCandidate) {
        HashMap<String, Object> childMap = new HashMap();
        childMap.put("id", iceCandidate.sdpMid);
        childMap.put("label", iceCandidate.sdpMLineIndex);
        childMap.put("candidate", iceCandidate.sdp);
        childMap.put("socketId", socketId);
        HashMap<String, Object> map = new HashMap();
        map.put("eventName", "__ice_candidate");
        map.put("data", childMap);
        com.alibaba.fastjson.JSONObject object = new com.alibaba.fastjson.JSONObject(map);
        String jsonString = object.toString();
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
