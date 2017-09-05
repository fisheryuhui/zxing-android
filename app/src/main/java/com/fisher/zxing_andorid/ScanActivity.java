package com.fisher.zxing_andorid;


import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.fisher.zxing.CaptureActivity;
import com.fisher.zxing.ViewfinderView;
import com.fisher.zxing.camera.CameraManager;

public class ScanActivity extends CaptureActivity implements SurfaceHolder.Callback {
    private CameraManager cameraManager;
    private ViewfinderView viewfinderView;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private boolean hasSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        setHasSurface(false);
    }

    @Override
    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    @Override
    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    @Override
    public void decodeSuccess(String result) {
        Log.e(":AAAAAA", "decodeSuccess =====" + result);
    }

    @Override
    public void decodeFail() {

    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraManager = new CameraManager(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.zxing_viewfinder_view);
        viewfinderView.setCameraManager(getCameraManager());
        viewfinderView.setVisibility(View.VISIBLE);
        surfaceView = (SurfaceView) findViewById(R.id.zxing_preview_view);
        surfaceHolder = surfaceView.getHolder();
        if (isHasSurface()) {
            initCamera(surfaceHolder);
        }
        else {
            surfaceHolder.addCallback(this);
        }
        playBeepSoundAndVibrate(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getCameraManager().closeDriver();
        if (!isHasSurface()) {
            surfaceView = (SurfaceView) findViewById(R.id.zxing_preview_view);
            surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            return;
        }
        if (!isHasSurface()) {
            setHasSurface(true);
            initCamera(surfaceHolder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        setHasSurface(false);
    }

    public void setHasSurface(boolean hasSurface) {
        this.hasSurface = hasSurface;
    }

    public boolean isHasSurface() {
        return hasSurface;
    }
}
