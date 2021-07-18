package com.example.webrtcmeetingroom.connection;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.example.webrtcmeetingroom.ChatRoomActivity;
import com.example.webrtcmeetingroom.interfaces.IViewCallback;
import com.example.webrtcmeetingroom.socket.JavaWebSocket;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    private VideoCapturer captureAndroid;
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

    // ICE服务器集合
    private ArrayList<PeerConnection.IceServer> iceServers;
    // 会议室的所有ID
    private ArrayList<String> connectionIdArray;
    // 会议室的每一个用户，会对本地实现一个p2p连接
    private Map<String,Peer> connectionPeerDic;
    private AudioManager mAudioManager;
    private IViewCallback viewCallback;

    public void setViewCallback(IViewCallback viewCallback) {
        this.viewCallback = viewCallback;
    }

    /**
     * 当别人在会议室 我此时进去
     * @param socketId
     * @param iceCandidate
     */
    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        // 通过 socketId 取出连接对象
        Peer peer = connectionPeerDic.get(socketId);
        if (peer!=null){
            peer.peerConnection.addIceCandidate(iceCandidate);
        }
    }


    /**
     * 对方的SDp
     * @param socketId
     * @param sdp
     */
    public void onReceiverAnswer(String socketId, String sdp) {
        // 对方的回话 SDP
        // 耗时操作
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Peer peer =connectionPeerDic.get(socketId);
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER,sdp);
                if (peer!=null){
                    peer.peerConnection.setRemoteDescription(peer,sessionDescription);
                }
            }
        });
    }

    public void onRemoteJoinToRoom(String socketId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                /*可能出现一进入房间， 就已经有其他人进入房间，并且给你发送一个_peer_new*/
                if (localStream == null) {
                    createLocalStream();
                }
                Peer peer = new Peer(socketId);
                connectionIdArray.add(socketId);
                connectionPeerDic.put(socketId, peer);
            }
        });
    }

    public void onReceiveOffer(String socketId, String description) {
        //       角色发生变化     由主叫变成 被叫

        executor.execute(new Runnable() {
            @Override
            public void run() {
                role = Role.Receiver;
                Peer mPeer = connectionPeerDic.get(socketId);
                SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, description);
                mPeer.peerConnection.setRemoteDescription(mPeer,sdp);
            }
        });
    }

    public void toggleSpeaker(boolean enableMic) {
        if (localAudioTrack != null) {
//            切换是否允许将本地的麦克风数据推送到远端
            localAudioTrack.setEnabled(enableMic);
        }
    }

    public void toggleLarge(boolean enableSpeaker) {
        if (mAudioManager != null) {
            mAudioManager.setSpeakerphoneOn(enableSpeaker);
        }

    }

    public void switchCamera() {
        if (captureAndroid == null) return;
        if (captureAndroid instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) captureAndroid;
            cameraVideoCapturer.switchCamera(null);
        }
    }

    public void exitRoom() {
        ArrayList<String> myCopy;
        myCopy = (ArrayList) connectionIdArray.clone();
        for (String Id : myCopy) {
            closePeerConnection( Id);
        }
//        释放ID集合
        if (connectionIdArray != null) {
            connectionIdArray.clear();
        }
//释放音频源
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
//        释放视频源
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
//停止预览摄像头
        if (captureAndroid != null) {
            try {
                captureAndroid.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            captureAndroid.dispose();
            captureAndroid = null;
        }
//        释放Surfacetext

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        if (webSocket != null) {
            webSocket.close();
        }

    }

    private void closePeerConnection(String connectionId) {
        Peer mPeer = connectionPeerDic.get(connectionId);
        if (mPeer != null) {
            mPeer.peerConnection.close();
        }
        connectionPeerDic.remove(connectionId);
        connectionIdArray.remove(connectionId);
//        通知UI层更新
        if (viewCallback != null) {
            viewCallback.onCloseWithId(connectionId);
        }
    }


    // 角色 邀请者 被邀请者
    enum Role{Caller,Receiver}
    private Role role;

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
        this.iceServers = new ArrayList<>();
        this.connectionPeerDic = new HashMap<>();
        this.connectionIdArray = new ArrayList<>();

        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("stun:81.68.102.137:3478?transport=udp")
                .setUsername("").setPassword("").createIceServer();

        PeerConnection.IceServer iceServer1 = PeerConnection.IceServer.builder("turn:81.68.102.137:3478?transport=udp")
                .setUsername("ddssingsong").setPassword("123456").createIceServer();

        iceServers.add(iceServer);
        iceServers.add(iceServer1);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void joinToRoom(JavaWebSocket javaWebSocket, boolean videoEnable, ArrayList<String> connections, String myId) {
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

                connectionIdArray.addAll(connections);
                createPeerConnections();
                // 本地的数据流推向会议室的每一个人的能力
                addStream();
                // 发送邀请
                createOffers();
            }
        });
    }

    private void addStream() {

        for (Map.Entry<String,Peer> entry:connectionPeerDic.entrySet()){
            if (localStream==null){
                createLocalStream();
            }
            entry.getValue().peerConnection.addStream(localStream);
        }
    }

    private void createOffers() {
        //邀请
        for (Map.Entry<String,Peer> entry:connectionPeerDic.entrySet()){
            role = Role.Caller;
            Peer peer =entry.getValue();
            // 向会议室每一个人发送邀请，并且传递我的数据类型（音频 视频）
            peer.peerConnection.createOffer(peer,offerOrAnswerConstrains());
        }

    }

    private MediaConstraints offerOrAnswerConstrains() {
//        媒体约束
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
//        音频  必须传输
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
//        videoEnable
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(videoEnable)));

        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    /**
     * 对每一个会
     */
    private void createPeerConnections() {
        for(String id:connectionIdArray){
            Peer peer =new Peer(id);
            connectionPeerDic.put(id,peer);
        }
    }

    /**
     *  本底流的处理，摄像头预览
     */
    private void createLocalStream() {
        localStream = factory.createLocalMediaStream("ARDAMS");
        // 音频
        audioSource = factory.createAudioSource(createAudioConstraints());
        // 采集音频
        localAudioTrack = factory.createAudioTrack("ARDAMSa0",audioSource);
        localStream.addTrack(localAudioTrack);
        if (videoEnable){
            captureAndroid = createVideoCapture();
            videoSource = factory.createVideoSource(captureAndroid.isScreencast());
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread",rootEglBase.getEglBaseContext());
            captureAndroid.initialize(surfaceTextureHelper,context,videoSource.getCapturerObserver());
            captureAndroid.startCapture(320,240,10);

            localVideoTrack = factory.createVideoTrack("ARDAMSv0",videoSource);
            localStream.addTrack(localVideoTrack);

            if (viewCallback!=null){
                viewCallback.onSetLocalStream(localStream,myId);
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
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT
                , "true"));
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



    class Peer implements SdpObserver, PeerConnection.Observer {

        private String TAG = Peer.class.getSimpleName();

        //myid  跟远端用户之间的连接
        private PeerConnection peerConnection;
        //        socket是其他用的id
        private String socketId;

        public Peer(String socketId){
            this.socketId = socketId;
            PeerConnection.RTCConfiguration rtcConfiguration  = new PeerConnection.RTCConfiguration(iceServers);
            peerConnection = factory.createPeerConnection(rtcConfiguration,this);
        }

        //内网状态发生改变   如音视频通话中 4G--->切换成wifi
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        //连接上了ICE服务器
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        //onIceCandidate 调用的时机有两次  第一次在连接到ICE服务器的时候  调用次数是网络中有多少个路由节点(1-n)
// 第二类(有人进入这个房间) 对方 到ICE服务器的 路由节点  调用次数是 视频通话的人在网络中离ICE服务器有多少个路由节点(1-n)
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
//            socket-----》   传递
        // 将自己的 iceCandidate 传递给服务器
            webSocket.sendIceCandidate(socketId, iceCandidate);

        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        //p2p建立成功之后   mediaStream（视频流  音段流）  子线程
        @Override
        public void onAddStream(MediaStream mediaStream) {
            if (viewCallback != null) {
                viewCallback.onAddRemoteStream(mediaStream,socketId);
            }
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }


        //------------------------------------SDPobserver-------------------------------------------------

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "onCreateSuccess: ");
            // 设置本地的SDP  如果设置成功则回调 onSetSuccess
            peerConnection.setLocalDescription(this,sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            Log.i(TAG, "onSetSuccess: ");
            // 交换彼此的SDP iceCanndidate
            if (peerConnection.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
//                把自己的sdp发送给对方    peerConnection.createAnswer   ----->  onSetSuccess
                peerConnection.createAnswer(Peer.this, offerOrAnswerConstraint());
            }else  if (peerConnection.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
//                websocket
                webSocket.sendOffer(socketId,peerConnection.getLocalDescription());
//              由于设置了  peerConnection.createAnswer
            } else if (peerConnection.signalingState() == PeerConnection.SignalingState.STABLE) {
//                把自己的sdp  发送给对方
                webSocket.sendAnswer(socketId, peerConnection.getLocalDescription().description);
            }
        }

        /**
         * 设置传输音视频
         * 音频()
         * 视频(false)
         * @return
         */
        private MediaConstraints offerOrAnswerConstraint() {
//        媒体约束
            MediaConstraints mediaConstraints = new MediaConstraints();
            ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
//        音频  必须传输
            keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
//        videoEnable
            keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(videoEnable)));

            mediaConstraints.mandatory.addAll(keyValuePairs);
            return mediaConstraints;
        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }


}
