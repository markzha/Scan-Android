package com.uc56.scancore;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.uc56.scancore.camera.CameraPreviewA;

import java.util.Queue;
import java.util.concurrent.*;


public class ScanView extends RelativeLayout implements Camera.PreviewCallback {
    protected Camera mCamera;
    protected CameraPreviewA mPreview;
    protected BoxScanView mScanBoxView;

    private Queue<IHandleScanDataListener> handleScanDataListenerQueue = new ConcurrentLinkedQueue<IHandleScanDataListener>();

    protected Handler mHandler;
    protected boolean mSpotAble = false;
    protected Thread mProcessDataTask;
    private int mOrientation;
    RelativeLayout.LayoutParams layoutParams;
    private Context context;
    private BoxScanView qrCodeBoxScanView, barCodeBoxScanView, idCardBoxScanView;

    public ScanView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ScanView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHandler = new Handler();
        initView(context, attrs);
    }


    private void initView(Context context, AttributeSet attrs) {
        this.context = context;
        mPreview = new CameraPreviewA(getContext());
        mPreview.setId(R.id.scan_camera_preview);
        addView(mPreview);
        mOrientation = ScanUtil.getOrientation(context);

        layoutParams = new RelativeLayout.LayoutParams(context, attrs);
        layoutParams.addRule(RelativeLayout.ALIGN_TOP, mPreview.getId());
        layoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, mPreview.getId());

        qrCodeBoxScanView = new BoxQRCodeScanView(context);
        qrCodeBoxScanView.initCustomAttrs(context, attrs);
        barCodeBoxScanView = new BoxBarCodeView(context);
        barCodeBoxScanView.initCustomAttrs(context, attrs);
        idCardBoxScanView = new BoxIDCardScanView(context);
        idCardBoxScanView.initCustomAttrs(context, attrs);
    }

    public static int Box_Type_QR = 0, Box_Type_Bar = 1, Box_Type_IDCard = 2;

    //int type = 0 ->QR  1->bar 2->idcard
    public void showBoxView(int type) {
        if (mScanBoxView != null) {
            removeView(mScanBoxView);
            mScanBoxView.setVisibility(GONE);
        }
        if (type == Box_Type_QR)
            mScanBoxView = qrCodeBoxScanView;
        else if (type == Box_Type_Bar)
            mScanBoxView = barCodeBoxScanView;
        else if (type == Box_Type_IDCard)
            mScanBoxView = idCardBoxScanView;
        addView(mScanBoxView, layoutParams);
        mScanBoxView.setVisibility(VISIBLE);
    }

    private synchronized Queue<IHandleScanDataListener> getHandleScanDataListenerQueque() {
        return handleScanDataListenerQueue;
    }

    /**
     * 添加扫描二维码的处理
     *
     * @param listener 扫描二维码的处理
     */
    public void addHandleScanDataListener(IHandleScanDataListener listener) {
        if (listener == null)
            return;
        if (!getHandleScanDataListenerQueque().isEmpty()) {
            for (IHandleScanDataListener itemListener : getHandleScanDataListenerQueque()) {
                if (itemListener == listener)
                    return;
            }
        }
        getHandleScanDataListenerQueque().add(listener);
    }

    /**
     * 删除扫描二维码的处理
     *
     * @param listener 扫描二维码的处理
     */
    public void removeHandleScanDataListener(IHandleScanDataListener listener) {
        if (listener == null)
            return;
        if (!getHandleScanDataListenerQueque().isEmpty()) {
            for (IHandleScanDataListener itemListener : getHandleScanDataListenerQueque()) {
                if (itemListener == listener) {
                    getHandleScanDataListenerQueque().remove(listener);
                    return;
                }
            }
        }
    }

    public void removeHandleScanDataListenerAll() {
        getHandleScanDataListenerQueque().clear();
    }

    public BoxScanView getScanBoxView() {
        return mScanBoxView;
    }

    /**
     * 显示扫描框
     */
    public void showScanRect() {
        if (mScanBoxView != null) {
            mScanBoxView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏扫描框
     */
    public void hiddenScanRect() {
        if (mScanBoxView != null) {
            mScanBoxView.setVisibility(View.GONE);
        }
    }

    /**
     * 打开后置摄像头开始预览，但是并未开始识别
     */
    public void startCamera() {
        startCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    /**
     * 打开指定摄像头开始预览，但是并未开始识别
     *
     * @param cameraFacing
     */
    public void startCamera(int cameraFacing) {
        if (mCamera != null) {
            return;
        }
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
            Camera.getCameraInfo(cameraId, cameraInfo);
            if (cameraInfo.facing == cameraFacing) {
                startCameraById(cameraId);
                break;
            }
        }
    }

    private void startCameraById(int cameraId) {
        try {
            mCamera = Camera.open(cameraId);
            mPreview.setTag(cameraId);
            mPreview.setCamera(mCamera);
        } catch (Exception e) {
            Toast.makeText(getContext(), "打开相机出错", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 关闭摄像头预览，并且隐藏扫描框
     */
    public void stopCamera() {
        try {
            stopSpotAndHiddenRect();
            if (mCamera != null) {
                mPreview.stopCameraPreview();
                mPreview.setCamera(null);
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
        }
    }

    /**
     * 延迟0.5秒后开始识别
     */
    public void startSpot() {
        startSpotDelay(0);
    }

    /**
     * 延迟delay毫秒后开始识别
     *
     * @param delay
     */
    public void startSpotDelay(int delay) {
        mSpotAble = true;

        startCamera();
        // 开始前先移除之前的任务
        mHandler.removeCallbacks(mOneShotPreviewCallbackTask);
        mHandler.postDelayed(mOneShotPreviewCallbackTask, delay);
    }

    /**
     * 停止识别
     */
    public void stopSpot() {
        cancelProcessDataTask();

        mSpotAble = false;

        if (mCamera != null) {
            try {
                mCamera.setOneShotPreviewCallback(null);
            } catch (Exception e) {
            }
        }
        if (mHandler != null) {
            mHandler.removeCallbacks(mOneShotPreviewCallbackTask);
        }
    }

    /**
     * 停止识别，并且隐藏扫描框
     */
    public void stopSpotAndHiddenRect() {
        stopSpot();
        hiddenScanRect();
    }

    /**
     * 显示扫描框，并且延迟1.5秒后开始识别
     */
    public void startSpotAndShowRect() {
        startSpot();
        showScanRect();
    }

    /**
     * 打开闪光灯
     */
    public void openFlashlight() {
        mPreview.openFlashlight();
    }

    /**
     * 关闭散光灯
     */
    public void closeFlashlight() {
        mPreview.closeFlashlight();
    }

    /**
     * 销毁二维码扫描控件
     */
    public void onDestroy() {
        stopCamera();
        mHandler = null;
        mOneShotPreviewCallbackTask = null;
    }

    /**
     * 取消数据处理任务
     */
    protected void cancelProcessDataTask() {
        if (mProcessDataTask != null) {
            try {
                mProcessDataTask.stop();
            } catch (Exception e) {
            }
        }
        mProcessDataTask = null;
    }

    @Override
    public void onPreviewFrame(final byte[] previewData, final Camera camera) {
        if (!mSpotAble || getHandleScanDataListenerQueque().isEmpty() || camera == null || camera.getParameters() == null)
            return;

        final Camera.Parameters parameters = mCamera.getParameters();
        final Camera.Size size = parameters.getPreviewSize();
        final Rect rect = mScanBoxView.getScanBoxAreaRect(camera);

        cancelProcessDataTask();
        mProcessDataTask = new Thread(new Runnable() {
            @Override
            public void run() {
                try { //数据处理
                    int width = size.width;
                    int height = size.height;

                    byte[] data = previewData;

                    if (mOrientation == ScanUtil.ORIENTATION_PORTRAIT) {
                        data = new byte[previewData.length];
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                data[x * height + height - y - 1] = previewData[x + y * width];
                            }
                        }

                        int tmp = width;
                        width = height;
                        height = tmp;
                    }

                    for (IHandleScanDataListener listener : getHandleScanDataListenerQueque()) {
                        if (listener.onHandleScanData(data, width, height, rect))
                            break;
                    }
                } catch (Exception e) {
                }

                try { //是否继续扫描
                    if (!mSpotAble || getHandleScanDataListenerQueque().isEmpty())
                        return;

                    boolean isContinuity = false;
                    for (IHandleScanDataListener listener : getHandleScanDataListenerQueque()) {
                        if (listener.isContinuity()) {
                            isContinuity = listener.isContinuity();
                            break;
                        }
                    }

                    if (!isContinuity)
                        return;
                } catch (Exception e) {
                }

                try { //继续扫描
                    mHandler.post(mOneShotPreviewCallbackTask);
                } catch (Exception e) {
                }
            }
        });
        mProcessDataTask.start();
    }

    private Runnable mOneShotPreviewCallbackTask = new Runnable() {
        @Override
        public void run() {
            if (mCamera != null && mSpotAble) {
                try {
                    mCamera.setOneShotPreviewCallback(ScanView.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public interface IHandleScanDataListener {
        Boolean onHandleScanData(byte[] data, int width, int height, Rect rect);

        public Boolean isContinuity();//是否连续
    }
}