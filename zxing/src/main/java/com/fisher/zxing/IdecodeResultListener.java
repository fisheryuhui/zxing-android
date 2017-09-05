package com.fisher.zxing;

import android.graphics.Bitmap;

import com.google.zxing.Result;

/**
 * Created by fisher on 2017/9/5.
 */

public interface IdecodeResultListener {
      void decodeQRSucc(String result);

      void decodeQRFail();
}
