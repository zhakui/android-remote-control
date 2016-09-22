package com.kuaifa.capturescreen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordService extends Service {

    private static final String TAG = "RService";
    private MediaProjectionManager mMpmngr;
    private MediaProjection mMpj;
    private VirtualDisplay mVirtualDisplay;
    private int windowWidth;
    private int windowHeight;
    private int screenDensity;

    private Surface mSurface;
    private MediaCodec mMediaCodec;

    DatagramSocket socket;
    InetAddress address;
    private LinearLayout mCaptureLl;
    private WindowManager wm;
    public byte[] configbyte;
    private boolean isRecordOn;

    private AtomicBoolean mIsQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createEnvironment();
        configureMedia();
        createFloatView();
    }

    private void configureMedia() {
        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 480, 800);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1024*256);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,30);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mMediaCodec.createInputSurface();
        mMediaCodec.start();
    }

    private void createEnvironment() {
        mMpmngr = ((MyApplication) getApplication()).getMpmngr();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        windowWidth = wm.getDefaultDisplay().getWidth();
        windowHeight = wm.getDefaultDisplay().getHeight();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        screenDensity = displayMetrics.densityDpi;
        try {

            socket = new DatagramSocket();
            address = InetAddress.getByName("10.8.230.68");
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void createFloatView() {

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams
                (WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888);
        params.x = windowWidth;
        params.y = windowHeight/2;
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        mCaptureLl = (LinearLayout) inflater.inflate(com.kuaifa.capturescreen.R.layout.float_record, null);
        final ImageView mCaptureIv = (ImageView) mCaptureLl.findViewById(com.kuaifa.capturescreen.R.id.iv_record);
        wm.addView(mCaptureLl, params);

        mCaptureIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isRecordOn = !isRecordOn;
                if (isRecordOn) {
                    mCaptureIv.setImageResource(com.kuaifa.capturescreen.R.mipmap.ic_recording);
                    Toast.makeText(RecordService.this.getApplicationContext(), "开始录屏", Toast.LENGTH_SHORT).show();
                    recordStart();
                } else {
                    mCaptureIv.setImageResource(com.kuaifa.capturescreen.R.mipmap.ic_record);
                    Toast.makeText(RecordService.this.getApplicationContext(), "结束录屏", Toast.LENGTH_SHORT).show();
                    recordStop();
                }
            }
        });

        mCaptureIv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                params.x = (int) (motionEvent.getRawX() - mCaptureIv.getMeasuredWidth() / 2);
                params.y = (int) (motionEvent.getRawY() - mCaptureIv.getMeasuredHeight() / 2 - 20);
                wm.updateViewLayout(mCaptureLl, params);
                return false;
            }
        });
    }

    private void recordStop() {
        mIsQuit.set(true);
    }

    private void recordStart() {

        configureMedia();
        startVirtual();
        new Thread() {
            @Override
            public void run() {
                Log.e(TAG, "start startRecord");
                startRecord();
            }
        }.start();
    }

    private void startRecord() {
            recordVirtualDisplay();
    }

    private void startVirtual() {
        if (mMpj != null) {
            virtualDisplay();
        } else {
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    private void setUpMediaProjection() {
        int resultCode = ((MyApplication) getApplication()).getResultCode();
        Intent data = ((MyApplication) getApplication()).getResultIntent();
        mMpj = mMpmngr.getMediaProjection(resultCode, data);
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMpj.createVirtualDisplay("main_screen", 480, 800, 160,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mSurface, null, null);
    }

    private void recordVirtualDisplay() {
        while (!mIsQuit.get()) {
            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
            if (outputBufferIndex >= 0) {
                byte[] outData = new byte[mBufferInfo.size];
                ByteBuffer outputBuffer =  mMediaCodec.getOutputBuffer(outputBufferIndex);
                outputBuffer.get(outData);
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    configbyte = new byte[mBufferInfo.size];
                    configbyte = outData;
                } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                    try {
                        DatagramPacket packet = new DatagramPacket(keyframe, keyframe.length, address, 5000);
                        socket.send(packet);
                    } catch (IOException e) {}
                } else {
                    try {
                        DatagramPacket packet = new DatagramPacket(outData, outData.length, address, 5000);
                        socket.send(packet);
                    } catch (IOException e) {}
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {}
            }
        }
    }

    private void release() {
        mIsQuit.set(false);
        Log.i(TAG, " release() ");
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        release();
        if (mMpj != null) {
            mMpj.stop();
        }
        if (mCaptureLl != null) {
            wm.removeView(mCaptureLl);
        }
    }
}
