package com.example.webrtcmeetingroom;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.example.webrtcmeetingroom.utils.PermissionUtil;
import com.example.webrtcmeetingroom.utils.Utils;

import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoomActivity extends AppCompatActivity {

    private FrameLayout wrVideoLayout;
    private WebRTCManager webRTCManager;
    private EglBase rootEglBase;
    private VideoTrack localVideoTrack;
    private Map<String, SurfaceViewRenderer> videoViews = new HashMap<>();
    private List<String> persons = new ArrayList<>();




    public static void openActivity(Activity activity) {
        Intent intent = new Intent(activity, ChatRoomActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);
        initView();
    }

    private void initView() {
        rootEglBase = EglBase.create();
        wrVideoLayout = findViewById(R.id.wr_video_view);
        wrVideoLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams
                .MATCH_PARENT, ViewGroup.LayoutParams
                .MATCH_PARENT));
        webRTCManager = WebRTCManager.getInstance();
//        webRTCManager.setCallBack(this);
        if (!PermissionUtil.isNeedRequestPermission(this)) {
            webRTCManager.joinRoom(this, rootEglBase);
        }

    }

    public void onSetLocalStream(MediaStream stream, String myId) {
        List<VideoTrack> videoTracks  = stream.videoTracks;
        if (videoTracks.size()>0){
            localVideoTrack =videoTracks.get(0);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(myId,stream);
            }
        });

    }

    private void addView(String myId, MediaStream stream) {
        SurfaceViewRenderer renderer = new SurfaceViewRenderer(this);
        renderer.init(rootEglBase.getEglBaseContext(),null);
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        renderer.setMirror(true);

        if (stream.videoTracks.size()>0){
            stream.videoTracks.get(0).addSink(renderer);
        }

        videoViews.put(myId,renderer);
        persons.add(myId);
        wrVideoLayout.addView(renderer);
        // 宽度和高度
        int size  = videoViews.size();
        for (int i =0;i<size;i++){
            String peerId = persons.get(i);
            SurfaceViewRenderer surfaceViewRenderer  =videoViews
                    .get(peerId);
            if (surfaceViewRenderer!=null){
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.height  = Utils.getWidth(this,size);
                layoutParams.height = Utils.getWidth(this,size);
                layoutParams.leftMargin = Utils.getX(this,size,i);
                layoutParams.topMargin = Utils.getY(this,size,i);
                surfaceViewRenderer.setLayoutParams(layoutParams);
            }
        }
    }

    /**
     *
     * @param mediaStream
     * @param socketId
     */
    public void onAddRemoteStream(MediaStream mediaStream, String socketId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addView(socketId,mediaStream);
            }
        });
    }
}