package com.google.zxing.client.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.xw.wexxlib.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
  private static final long ANIMATION_DELAY = 80L;
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;

  private CameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int resultColor;
  private final int laserColor;
  private final int resultPointColor;
  private int scannerAlpha;
  private List<ResultPoint> possibleResultPoints;
  private List<ResultPoint> lastPossibleResultPoints;
  private int slideTop;//扫描线的位置
  private static final int SPEEN_DISTANCE = 20;//扫描线的移动速度

  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(R.color.viewfinder_mask);
    resultColor = resources.getColor(R.color.result_view);
    laserColor = resources.getColor(R.color.viewfinder_mask);
    resultPointColor = resources.getColor(R.color.possible_result_points);
    scannerAlpha = 0;
    possibleResultPoints = new ArrayList<>(5);
    lastPossibleResultPoints = null;
  }

  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

  @SuppressLint("DrawAllocation")
  @Override
  public void onDraw(Canvas canvas) {
    if (cameraManager == null) {
      return; // not ready yet, early draw before done configuring
    }
    Rect frame = cameraManager.getFramingRect();
    Rect previewFrame = cameraManager.getFramingRectInPreview();    
    if (frame == null || previewFrame == null) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    paint.setColor(getResources().getColor(R.color.viewfinder_green));
    canvas.drawRect(frame.left, frame.top - 20, frame.left + 48, frame.top - 35, paint);// 左上角的横
    canvas.drawRect(frame.left, frame.top -30, frame.left + 15, frame.top + 15, paint);//左上角的竖

    canvas.drawRect(frame.right - 48, frame.top - 20, frame.right, frame.top - 35, paint);//右上角的横
    canvas.drawRect(frame.right - 15, frame.top - 30, frame.right, frame.top + 15, paint);//右上角的竖

    canvas.drawRect(frame.left, frame.bottom - 85, frame.left + 15, frame.bottom - 35, paint);//左下角的竖
    canvas.drawRect(frame.left, frame.bottom - 50 , frame.left + 45, frame.bottom - 35, paint);//左下角的横

    canvas.drawRect(frame.right - 15, frame.bottom - 85, frame.right, frame.bottom - 35, paint);//右下角的竖
    canvas.drawRect(frame.right - 45, frame.bottom - 50, frame.right, frame.bottom -35, paint);//右下角的横

    //识别动画线
    if (slideTop < frame.top - 80) {
      slideTop = frame.top;
    }

    slideTop += SPEEN_DISTANCE;//定义好描线每秒的移动速度

    if (slideTop >= frame.bottom - 50) {
      slideTop = frame.top;
    }
    Rect lineRect = new Rect();
    lineRect.left = frame.left;
    lineRect.right = frame.right;
    lineRect.top = slideTop;
    lineRect.bottom = slideTop + 18;

    canvas.drawBitmap(((BitmapDrawable) (getResources()
            .getDrawable(R.mipmap.iv_line))).getBitmap(), null, lineRect, paint);

    paint.setColor(resultBitmap != null ? resultColor : maskColor);
    int rectWidth = frame.right - frame.left;
    int rectTop = (getHeight() - rectWidth) / 2;
    int rectBottom = rectTop + rectWidth;
    canvas.drawRect(0, 0, width, rectTop, paint);
    canvas.drawRect(0, rectTop, frame.left, rectBottom, paint);
    canvas.drawRect(frame.right + 1, rectTop, width, rectBottom, paint);
    canvas.drawRect(0, rectBottom, width, height, paint);

    if (resultBitmap != null) {
      paint.setAlpha(CURRENT_POINT_OPACITY);
      canvas.drawBitmap(resultBitmap, null, frame, paint);
    } else {
//      paint.setColor(laserColor);
//      paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
//      scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
//      int middle = frame.height() / 2 + frame.top;
//      canvas.drawRect(frame.left + 15, middle - 1, frame.right - 15, middle + 2, paint);

      float scaleX = frame.width() / (float) previewFrame.width();
      float scaleY = frame.height() / (float) previewFrame.height();

      List<ResultPoint> currentPossible = possibleResultPoints;
      List<ResultPoint> currentLast = lastPossibleResultPoints;
      int frameLeft = frame.left;
      int frameTop = frame.top;
      if (currentPossible.isEmpty()) {
        lastPossibleResultPoints = null;
      } else {
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = currentPossible;
        paint.setAlpha(CURRENT_POINT_OPACITY);
        paint.setColor(resultPointColor);
        synchronized (currentPossible) {
          for (ResultPoint point : currentPossible) {
            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              POINT_SIZE, paint);
          }
        }
      }
      if (currentLast != null) {
        paint.setAlpha(CURRENT_POINT_OPACITY / 2);
        paint.setColor(resultPointColor);
        synchronized (currentLast) {
          float radius = POINT_SIZE / 2.0f;
          for (ResultPoint point : currentLast) {
            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              radius, paint);
          }
        }
      }

      postInvalidateDelayed(ANIMATION_DELAY,
                            frame.left - POINT_SIZE,
                            frame.top - POINT_SIZE,
                            frame.right + POINT_SIZE,
                            frame.bottom + POINT_SIZE);
    }
  }

  public void drawViewfinder() {
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }

  /**
   * Draw a bitmap with the result points highlighted instead of the live scanning display.
   *
   * @param barcode An image of the decoded barcode.
   */
  public void drawResultBitmap(Bitmap barcode) {
    resultBitmap = barcode;
    invalidate();
  }

  public void addPossibleResultPoint(ResultPoint point) {
    List<ResultPoint> points = possibleResultPoints;
    synchronized (points) {
      points.add(point);
      int size = points.size();
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
      }
    }
  }

}
