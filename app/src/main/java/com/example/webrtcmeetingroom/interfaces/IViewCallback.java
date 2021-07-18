
package com.example.webrtcmeetingroom.interfaces;

import org.webrtc.MediaStream;

public interface IViewCallback {

    void onSetLocalStream(MediaStream stream, String socketId);

    void onAddRemoteStream(MediaStream stream, String socketId);

    void onCloseWithId(String socketId);
}
