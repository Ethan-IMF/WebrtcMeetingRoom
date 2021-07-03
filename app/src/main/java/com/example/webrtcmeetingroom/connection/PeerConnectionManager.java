package com.example.webrtcmeetingroom.connection;

import android.content.Context;

import com.example.webrtcmeetingroom.ChatRoomActivity;
import com.example.webrtcmeetingroom.socket.JavaWebSocket;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnectionManager {

    private static PeerConnectionManager ourInstance;
    private JavaWebSocket    webSocket;
    private String myId;
    private boolean videoEnable;
    private ExecutorService executor;
    private PeerConnectionFactory factory;
    private Context context;
    private EglBase rootEglBase ;

    private PeerConnectionManager(){
        executor = Executors.newSingleThreadExecutor();
    }

    public static PeerConnectionManager getOurInstance(){
        if (ourInstance ==null){
            ourInstance = new PeerConnectionManager();
        }
        return ourInstance;
    }

    public void initContext(ChatRoomActivity chatRoomActivity, EglBase rootEglBase) {
        this.context = chatRoomActivity;
        this.rootEglBase  = rootEglBase;
    }

    public void joinToRoom(JavaWebSocket javaWebSocket, boolean b, ArrayList<String> connections, String myId) {
        this.webSocket = javaWebSocket;
        this.myId = myId;
        this.videoEnable = videoEnable;
        //        PeerConnection    情况1    会议室已经有人   的情

        executor.execute(new Runnable() {
            @Override
            public void run() {
                factory = createConnectionFactory();
            }
        });
    }

    /**
     * PeerConnection 的实例化
     *
     * @return
     */
    private PeerConnectionFactory createConnectionFactory() {
        VideoEncoderFactory encoderFactory;
        VideoDecoderFactory decoderFactory;
        //        其他参数设置成默认的
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions());
        encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(),
                true,true);
        decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        return PeerConnectionFactory.builder().setOptions(options)
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
                .setVideoDecoderFactory(decoderFactory)
                .setVideoEncoderFactory(encoderFactory).createPeerConnectionFactory();

    }


}
