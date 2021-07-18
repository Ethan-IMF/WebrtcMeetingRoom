package com.example.webrtcmeetingroom;

import com.example.webrtcmeetingroom.connection.PeerConnectionManager;
import com.example.webrtcmeetingroom.interfaces.IViewCallback;
import com.example.webrtcmeetingroom.socket.JavaWebSocket;

import org.webrtc.EglBase;

public class WebRTCManager {
    private static WebRTCManager webRTCManager;
    private JavaWebSocket webSocket;
    private PeerConnectionManager peerConnectionManager;
    private String roomId = "";

    private WebRTCManager(){}

    public static WebRTCManager getInstance(){
        if (webRTCManager==null){
            webRTCManager = new WebRTCManager();
        }
        return webRTCManager;
    }

    public void connect(MainActivity activity,String roomId){
        this.roomId = roomId;
        webSocket = new JavaWebSocket(activity);
        peerConnectionManager = PeerConnectionManager.getOurInstance();
        webSocket.connect("wss://81.68.102.137/wss"); // 建立socket长链接

    }


    public void joinRoom(ChatRoomActivity chatRoomActivity, EglBase rootEglBase) {
        peerConnectionManager.initContext(chatRoomActivity,rootEglBase);

        webSocket.joinRoom(roomId);

    }

    public void toggleMic(boolean enableMic) {
        if (peerConnectionManager != null) {
            peerConnectionManager.toggleSpeaker(enableMic);
        }
    }

    public void toggleLarge(boolean enableSpeaker) {
        if (peerConnectionManager != null) {
            peerConnectionManager.toggleLarge(enableSpeaker);
        }
    }

    public void switchCamera() {
        if (peerConnectionManager != null) {
            peerConnectionManager.switchCamera();
        }

    }

    public void exitRoom() {
        if (peerConnectionManager != null) {
            webSocket = null;
            peerConnectionManager.exitRoom();
        }
    }

    public void setCallBack(IViewCallback viewCallback) {
        peerConnectionManager.setViewCallback(viewCallback);
    }
}
