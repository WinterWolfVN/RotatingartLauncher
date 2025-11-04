package com.app.ralaunch.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.app.ralaunch.utils.PerformanceMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * 性能监控覆盖层
 * 
 * 在屏幕上显示实时性能数据（FPS、内存使用等）
 */
public class PerformanceOverlay extends View implements PerformanceMonitor.PerformanceListener {
    private static final int MAX_FPS_HISTORY = 120; // 保存2秒的FPS历史（60fps）
    
    private Paint mTextPaint;
    private Paint mGraphPaint;
    private Paint mBackgroundPaint;
    private Rect mBounds = new Rect();
    
    private List<Float> mFpsHistory = new ArrayList<>();
    private PerformanceMonitor.PerformanceData mCurrentData;
    
    private boolean mShowGraph = true;
    private boolean mShowDetailed = false;
    
    public PerformanceOverlay(Context context) {
        super(context);
        init();
    }
    
    public PerformanceOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(dpToPx(12));
        mTextPaint.setAntiAlias(true);
        mTextPaint.setShadowLayer(2, 0, 0, Color.BLACK);
        
        mGraphPaint = new Paint();
        mGraphPaint.setColor(Color.GREEN);
        mGraphPaint.setStrokeWidth(2);
        mGraphPaint.setAntiAlias(true);
        
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(0xAA000000); // 半透明黑色
    }
    
    /**
     * 更新性能数据
     */
    public void updateData(PerformanceMonitor.PerformanceData data) {
        mCurrentData = data;
        
        // 更新FPS历史（使用C#游戏真实FPS，如果为0则使用Java UI FPS）
        float displayFps = (data.gameFps > 0) ? data.gameFps : data.fps;
        mFpsHistory.add(displayFps);
        if (mFpsHistory.size() > MAX_FPS_HISTORY) {
            mFpsHistory.remove(0);
        }
        
        // 刷新显示
        invalidate();
    }
    
    /**
     * 设置是否显示FPS图表
     */
    public void setShowGraph(boolean show) {
        mShowGraph = show;
        invalidate();
    }
    
    /**
     * 设置是否显示详细信息
     */
    public void setShowDetailed(boolean show) {
        mShowDetailed = show;
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mCurrentData == null) return;
        
        int padding = dpToPx(8);
        int lineHeight = dpToPx(16);
        int y = padding + lineHeight;
        
        // 绘制背景
        if (mShowDetailed) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundPaint);
        } else {
            int bgHeight = mShowGraph ? dpToPx(100) : lineHeight * 6; // 增加到6行（FPS + CPU + GPU + Memory + 空间）
            canvas.drawRect(0, 0, getWidth(), bgHeight, mBackgroundPaint);
        }
        
        // 绘制FPS（使用C#游戏真实FPS，如果为0则使用Java UI FPS）
        float displayFps = (mCurrentData.gameFps > 0) ? mCurrentData.gameFps : mCurrentData.fps;
        String fpsText = String.format("FPS: %.1f", displayFps);
        if (displayFps < 30) {
            mTextPaint.setColor(Color.RED);
        } else if (displayFps < 50) {
            mTextPaint.setColor(Color.YELLOW);
        } else {
            mTextPaint.setColor(Color.GREEN);
        }
        canvas.drawText(fpsText, padding, y, mTextPaint);
        mTextPaint.setColor(Color.WHITE);
        y += lineHeight;
        
        // 绘制CPU使用率
        String cpuText = String.format("CPU: %.1f%%", mCurrentData.cpuUsage);
        if (mCurrentData.cpuUsage > 80) {
            mTextPaint.setColor(Color.RED);
        } else if (mCurrentData.cpuUsage > 50) {
            mTextPaint.setColor(Color.YELLOW);
        } else {
            mTextPaint.setColor(Color.GREEN);
        }
        canvas.drawText(cpuText, padding, y, mTextPaint);
        mTextPaint.setColor(Color.WHITE);
        y += lineHeight;
        
        // 绘制GPU使用率
        String gpuText = String.format("GPU: %.1f%%", mCurrentData.gpuUsage);
        if (mCurrentData.gpuUsage > 80) {
            mTextPaint.setColor(Color.RED);
        } else if (mCurrentData.gpuUsage > 50) {
            mTextPaint.setColor(Color.YELLOW);
        } else {
            mTextPaint.setColor(Color.GREEN);
        }
        canvas.drawText(gpuText, padding, y, mTextPaint);
        mTextPaint.setColor(Color.WHITE);
        y += lineHeight;
        
        // 绘制内存信息
        String memText = String.format("Memory: %.1f MB", 
            mCurrentData.totalMemory / 1024.0 / 1024.0);
        canvas.drawText(memText, padding, y, mTextPaint);
        y += lineHeight;
        
        if (mShowDetailed) {
            // 详细内存信息
            String javaMemText = String.format("  Java: %.1f MB", 
                mCurrentData.javaHeap / 1024.0 / 1024.0);
            canvas.drawText(javaMemText, padding, y, mTextPaint);
            y += lineHeight;
            
            String nativeMemText = String.format("  Native: %.1f MB", 
                mCurrentData.nativeHeap / 1024.0 / 1024.0);
            canvas.drawText(nativeMemText, padding, y, mTextPaint);
            y += lineHeight;
            
            String availMemText = String.format("Available: %.1f MB", 
                mCurrentData.availableMemory / 1024.0 / 1024.0);
            canvas.drawText(availMemText, padding, y, mTextPaint);
            y += lineHeight;
            
            String gcText = String.format("GC Count: %d", mCurrentData.gcCount);
            canvas.drawText(gcText, padding, y, mTextPaint);
            y += lineHeight;
            
            if (mCurrentData.isMemoryLow) {
                mTextPaint.setColor(Color.RED);
                canvas.drawText("⚠️ LOW MEMORY", padding, y, mTextPaint);
                mTextPaint.setColor(Color.WHITE);
                y += lineHeight;
            }
        }
        
        // 绘制FPS图表
        if (mShowGraph && mFpsHistory.size() > 1) {
            drawFpsGraph(canvas);
        }
    }
    
    /**
     * 绘制FPS折线图
     */
    private void drawFpsGraph(Canvas canvas) {
        int graphHeight = dpToPx(60);
        int graphTop = dpToPx(80);
        int graphWidth = getWidth() - dpToPx(16);
        int graphLeft = dpToPx(8);
        
        // 绘制基准线
        mGraphPaint.setColor(0x44FFFFFF);
        mGraphPaint.setStrokeWidth(1);
        
        // 60 FPS基准线
        int y60 = graphTop + graphHeight;
        canvas.drawLine(graphLeft, y60, graphLeft + graphWidth, y60, mGraphPaint);
        
        // 30 FPS基准线
        int y30 = graphTop + graphHeight / 2;
        canvas.drawLine(graphLeft, y30, graphLeft + graphWidth, y30, mGraphPaint);
        
        // 绘制FPS曲线
        mGraphPaint.setStrokeWidth(2);
        float maxFps = 60.0f;
        for (int i = 1; i < mFpsHistory.size(); i++) {
            float fps1 = Math.min(mFpsHistory.get(i - 1), maxFps);
            float fps2 = Math.min(mFpsHistory.get(i), maxFps);
            
            float x1 = graphLeft + (i - 1) * graphWidth / (float) MAX_FPS_HISTORY;
            float y1 = graphTop + graphHeight - (fps1 / maxFps) * graphHeight;
            
            float x2 = graphLeft + i * graphWidth / (float) MAX_FPS_HISTORY;
            float y2 = graphTop + graphHeight - (fps2 / maxFps) * graphHeight;
            
            // 根据FPS设置颜色
            if (fps2 < 30) {
                mGraphPaint.setColor(Color.RED);
            } else if (fps2 < 50) {
                mGraphPaint.setColor(Color.YELLOW);
            } else {
                mGraphPaint.setColor(Color.GREEN);
            }
            
            canvas.drawLine(x1, y1, x2, y2, mGraphPaint);
        }
    }
    
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
    
    // PerformanceListener接口实现
    @Override
    public void onPerformanceUpdate(PerformanceMonitor.PerformanceData data) {
        updateData(data);
    }
    
    @Override
    public void onMemoryWarning(String message) {
        // 可以在这里添加内存警告的UI提示
    }
    
    @Override
    public void onMemoryLeak(String message) {
        // 可以在这里添加内存泄漏的UI提示
    }
}

