package com.example.webrtcmeetingroom.connection;

import android.content.Context;

import com.example.webrtcmeetingroom.ChatRoomActivity;
import com.example.webrtcmeetingroom.socket.JavaWebSocket;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Capturer;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
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
    private ChatRoomActivity context;
    private EglBase rootEglBase ;
    private MediaStream localStream;
    private AudioSource audioSource;
    // 音视频轨
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    // 获取摄像头设备
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer capturerAndroid;
    // 视频源
    private VideoSource videoSource;


    //    googEchoCancellation   回音消除
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    //    googNoiseSuppression   噪声抑制
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    //    googAutoGainControl    自动增益控制
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    //    googHighpassFilter     高通滤波器
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";


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
                if (factory==null){
                    factory = createConnectionFactory();
                }
                if (localStream==null){
                    createLocalStream();
                }

            }
        });
    }

    /**
     *  本底流的处理，摄像头预览
     */
    private void createLocalStream() {
        localStream = factory.createLocalMediaStream("ARDAMS");
        // 音频
        audioSource = factory.createAudioSource(createAudioConstraints());

        localStream.addTrack(localAudioTrack);
        if (videoEnable){
            capturerAndroid = createVideoCapture();
            videoSource = factory.createVideoSource(capturerAndroid.isScreencast());
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread",rootEglBase.getEglBaseContext());
            capturerAndroid.initialize(surfaceTextureHelper,context,videoSource.getCapturerObserver());
            capturerAndroid.startCapture(320,240,10);

            localVideoTrack = factory.createVideoTrack("ARDAMSv0",videoSource);
            localStream.addTrack(localVideoTrack);

            if (context!=null){
                context.onSetLocalStream(localStream,myId);
            }

        }

    }

    private VideoCapturer createVideoCapture() {
        VideoCapturer videoCapturer = null;
        if (Camera2Enumerator.isSupported(context)){
            Camera2Enumerator enumerator =new Camera2Enumerator(context);
            videoCapturer = createCameraCapture(enumerator);
        }else {
            Camera1Enumerator enumerator = new Camera1Enumerator(true);
            videoCapturer =createCameraCapture(enumerator);
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        //        0   1  front
        String[] deviceNames =enumerator.getDeviceNames();
        for (String deviceName:deviceNames){
            if (enumerator.isFrontFacing(deviceName)){
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName,null);
                if (videoCapturer!=null){
                    return videoCapturer;
                }
            }
        }

        for (String deviceName:deviceNames){
            if (!enumerator.isFrontFacing(deviceName)){
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName,null);
                if (videoCapturer!=null){
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT,"true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT,"false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT,"true"));
        return audioConstraints;
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
