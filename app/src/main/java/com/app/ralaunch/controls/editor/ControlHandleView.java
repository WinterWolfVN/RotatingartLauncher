package com.app.ralaunch.controls.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.app.ralaunch.controls.ControlData;
import com.app.ralaunch.controls.ControlView;

/**
 * 控件编辑句柄View
 * 提供拖拽、缩放、编辑等功能
 */
public class ControlHandleView extends View {
    private static final String TAG = "ControlHandleView";
    
    // 句柄类型
    private static final int HANDLE_NONE = 0;
    private static final int HANDLE_MOVE = 1;
    private static final int HANDLE_RESIZE = 2;
    
    private ControlView mTargetControl;
    private Paint mHandlePaint;
    private Paint mBorderPaint;
    private RectF mResizeHandleRect;
    private static final float HANDLE_SIZE = 40f;
    
    private int mCurrentHandle = HANDLE_NONE;
    private float mLastTouchX, mLastTouchY;
    private float mDownX, mDownY;
    private OnEditListener mEditListener;
    
    public interface OnEditListener {
        void onControlMoved(ControlView control, float newX, float newY);
        void onControlResized(ControlView control, float newWidth, float newHeight);
        void onControlClicked(ControlView control);
    }
    
    public ControlHandleView(Context context, ControlView targetControl) {
        super(context);
        mTargetControl = targetControl;
        initPaints();
        
        // 设置位置和大小与目标控件一致
        View targetView = (View) targetControl;
        setX(targetView.getX());
        setY(targetView.getY());
        setLayoutParams(targetView.getLayoutParams());
    }
    
    private void initPaints() {
        mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHandlePaint.setColor(0xFF00FF00); // 绿色句柄
        mHandlePaint.setStyle(Paint.Style.FILL);
        
        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setColor(0xFF00FF00);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(dpToPx(2));
        
        mResizeHandleRect = new RectF();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制边框
        canvas.drawRect(0, 0, getWidth(), getHeight(), mBorderPaint);
        
        // 绘制右下角缩放句柄
        float handleSize = dpToPx(HANDLE_SIZE);
        mResizeHandleRect.set(
            getWidth() - handleSize,
            getHeight() - handleSize,
            getWidth(),
            getHeight()
        );
        canvas.drawRect(mResizeHandleRect, mHandlePaint);
        
        // 在句柄中绘制"resize"标记
        mHandlePaint.setTextSize(dpToPx(12));
        mHandlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⇲", mResizeHandleRect.centerX(), mResizeHandleRect.centerY() + dpToPx(4), mHandlePaint);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mResizeHandleRect.contains(x, y)) {
                    mCurrentHandle = HANDLE_RESIZE;
                } else {
                    mCurrentHandle = HANDLE_MOVE;
                }
                mLastTouchX = x;
                mLastTouchY = y;
                mDownX = x;
                mDownY = y;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastTouchX;
                float dy = y - mLastTouchY;
                
                if (mCurrentHandle == HANDLE_MOVE) {
                    // 移动控件
                    float newX = getX() + dx;
                    float newY = getY() + dy;
                    setX(newX);
                    setY(newY);
                    
                    // 同步目标控件位置
                    View targetView = (View) mTargetControl;
                    targetView.setX(newX);
                    targetView.setY(newY);
                    
                    if (mEditListener != null) {
                        mEditListener.onControlMoved(mTargetControl, newX, newY);
                    }
                } else if (mCurrentHandle == HANDLE_RESIZE) {
                    // 调整大小
                    float newWidth = getWidth() + dx;
                    float newHeight = getHeight() + dy;
                    
                    // 最小尺寸限制
                    newWidth = Math.max(50, newWidth);
                    newHeight = Math.max(50, newHeight);
                    
                    getLayoutParams().width = (int) newWidth;
                    getLayoutParams().height = (int) newHeight;
                    requestLayout();
                    
                    // 同步目标控件大小
                    View targetView = (View) mTargetControl;
                    targetView.getLayoutParams().width = (int) newWidth;
                    targetView.getLayoutParams().height = (int) newHeight;
                    targetView.requestLayout();
                    
                    if (mEditListener != null) {
                        mEditListener.onControlResized(mTargetControl, newWidth, newHeight);
                    }
                    
                    mLastTouchX = x;
                    mLastTouchY = y;
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                // 单击事件（没有移动）
                if (Math.abs(x - mDownX) < 10 && Math.abs(y - mDownY) < 10) {
                    if (mEditListener != null) {
                        mEditListener.onControlClicked(mTargetControl);
                    }
                }
                mCurrentHandle = HANDLE_NONE;
                return true;
        }
        
        return super.onTouchEvent(event);
    }
    
    public void setEditListener(OnEditListener listener) {
        mEditListener = listener;
    }
    
    public ControlView getTargetControl() {
        return mTargetControl;
    }
    
    /**
     * 获取目标控件（用于ControlEditorActivity）
     */
    public ControlView getControlView() {
        return mTargetControl;
    }
    
    /**
     * 获取控件数据（用于ControlEditorActivity）
     */
    public ControlData getData() {
        return mTargetControl != null ? mTargetControl.getData() : null;
    }
    
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}

