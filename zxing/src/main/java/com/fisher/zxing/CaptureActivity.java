package com.fisher.zxing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.fisher.zxing.camera.CameraManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Created by fisher on 2017/9/5.
 */

public abstract class CaptureActivity extends AppCompatActivity implements IdecodeResultListener {

    private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
    private CaptureActivityHandler handler;
    private IntentSource source;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    private AmbientLightManager ambientLightManager;
    private boolean playBeep;

    public abstract CameraManager getCameraManager();

    public abstract ViewfinderView getViewfinderView();

    public abstract void drawViewfinder();

    public abstract void decodeSuccess(String result);

    public abstract void decodeFail();

    public void playBeepSoundAndVibrate(boolean play) {
        playBeep = play;
    }

    @Override
    protected void onResume() {
        super.onResume();
        initManager();
        source = IntentSource.NATIVE_APP_INTENT;
        handler = null;
        beepManager.updatePrefs();
        ambientLightManager.start(getCameraManager());
        inactivityTimer.onResume();
        source = IntentSource.NATIVE_APP_INTENT;
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        beepManager.close();
        getCameraManager().closeDriver();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        initManager();
        super.onCreate(savedInstanceState, persistentState);
    }

    @Override
    public void decodeQRSucc(String result) {

        decodeSuccess(result);
    }

    @Override
    public void decodeQRFail() {
        decodeFail();
    }

    public void decodeBitmap(final Bitmap bitmap) {
        decodeQRCode(bitmap);
    }

    private void initManager() {
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);
    }

    Handler getHandler() {
        return handler;
    }

    public void initCamera(SurfaceHolder surfaceHolder) {
       setUpCamera(surfaceHolder);
    }

    private void setUpCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (getCameraManager().isOpen()) {
            return;
        }
        try {
            getCameraManager().openDriver(surfaceHolder);
            if (handler == null) {
                handler = new CaptureActivityHandler(this, null, null, null, getCameraManager());
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        catch (RuntimeException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();
        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
            if (playBeep) {
                beepManager.playBeepSoundAndVibrate();
            }
            drawResultPoints(barcode, scaleFactor, rawResult);
        }

        handleDecodeExternally(rawResult, barcode);
    }

    /**
     * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
     *
     * @param barcode     A bitmap of the captured image.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param rawResult   The decoded results which contains the points to draw.
     */
    protected void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
        ResultPoint[] points = rawResult.getResultPoints();
        if (points != null && points.length > 0) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(getResources().getColor(R.color.result_points));
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
            }
            else if (points.length == 4 && (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A || rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
                drawLine(canvas, paint, points[2], points[3], scaleFactor);
            }
            else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    if (point != null) {
                        canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
                    }
                }
            }
        }
    }

    protected static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
        if (a != null && b != null) {
            canvas.drawLine(scaleFactor * a.getX(),
                    scaleFactor * a.getY(),
                    scaleFactor * b.getX(),
                    scaleFactor * b.getY(),
                    paint);
        }
    }

    protected void handleDecodeExternally(Result rawResult, Bitmap barcode) {

        if (barcode != null) {
            getViewfinderView().drawResultBitmap(barcode);
        }

        long resultDurationMS;
        if (getIntent() == null) {
            resultDurationMS = DEFAULT_INTENT_RESULT_DURATION_MS;
        }
        else {
            resultDurationMS = getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                    DEFAULT_INTENT_RESULT_DURATION_MS);
        }

        if (source == IntentSource.NATIVE_APP_INTENT) {
            Intent intent = new Intent(getIntent().getAction());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
            intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.getBarcodeFormat().toString());
            byte[] rawBytes = rawResult.getRawBytes();
            if (rawBytes != null && rawBytes.length > 0) {
                intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
            }
            Map<ResultMetadataType, ?> metadata = rawResult.getResultMetadata();
            if (metadata != null) {
                if (metadata.containsKey(ResultMetadataType.UPC_EAN_EXTENSION)) {
                    intent.putExtra(Intents.Scan.RESULT_UPC_EAN_EXTENSION,
                            metadata.get(ResultMetadataType.UPC_EAN_EXTENSION).toString());
                }
                Number orientation = (Number) metadata.get(ResultMetadataType.ORIENTATION);
                if (orientation != null) {
                    intent.putExtra(Intents.Scan.RESULT_ORIENTATION, orientation.intValue());
                }
                String ecLevel = (String) metadata.get(ResultMetadataType.ERROR_CORRECTION_LEVEL);
                if (ecLevel != null) {
                    intent.putExtra(Intents.Scan.RESULT_ERROR_CORRECTION_LEVEL, ecLevel);
                }
                @SuppressWarnings("unchecked")
                Iterable<byte[]> byteSegments = (Iterable<byte[]>) metadata.get(ResultMetadataType.BYTE_SEGMENTS);
                if (byteSegments != null) {
                    int i = 0;
                    for (byte[] byteSegment : byteSegments) {
                        intent.putExtra(Intents.Scan.RESULT_BYTE_SEGMENTS_PREFIX + i, byteSegment);
                        i++;
                    }
                }
            }
            sendReplyMessage(R.id.zxing_return_scan_result, intent, resultDurationMS);
        }
    }

    private void sendReplyMessage(int id, Object arg, long delayMS) {
        if (getHandler() != null) {
            Message message = Message.obtain(getHandler(), id, arg);
            if (delayMS > 0L) {
                getHandler().sendMessageDelayed(message, delayMS);
            }
            else {
                getHandler().sendMessage(message);
            }
        }
    }

    private void decodeQRCode(final Bitmap bitmap) {
        final Map<DecodeHintType, Object> HINTS = new EnumMap<>(DecodeHintType.class);
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    int[] pixels = new int[width * height];
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                    RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
                    Result result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS);
                    return result.getText();
                }
                catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                Result rawResult = new Result(result, null, null, BarcodeFormat.QR_CODE);
                Intent intent = new Intent(getIntent().getAction());
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
                intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.getBarcodeFormat().toString());
                byte[] rawBytes = rawResult.getRawBytes();
                if (rawBytes != null && rawBytes.length > 0) {
                    intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
                }
                sendReplyMessage(R.id.zxing_return_scan_result, intent, 0L);
                decodeQRSucc(result);
            }
        }.execute();
    }


}
