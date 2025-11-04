package com.app.ralaunch.controls.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 网格覆盖层 - 用于编辑器中辅助对齐
 */
public class GridOverlayView extends View {
    private static final int GRID_SIZE = 50; // 网格大小（像素）
    private Paint mGridPaint;
    private Paint mAxisPaint;
    private boolean mVisible = false;

    public GridOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // 网格线画笔（灰色半透明）
        mGridPaint = new Paint();
        mGridPaint.setColor(0x33FFFFFF);
        mGridPaint.setStrokeWidth(1);
        mGridPaint.setStyle(Paint.Style.STROKE);

        // 坐标轴画笔（更明显的线）
        mAxisPaint = new Paint();
        mAxisPaint.setColor(0x55FFFFFF);
        mAxisPaint.setStrokeWidth(2);
        mAxisPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * 设置网格可见性
     */
    public void setGridVisible(boolean visible) {
        mVisible = visible;
        invalidate();
    }

    /**
     * 切换网格显示
     */
    public boolean toggleGrid() {
        mVisible = !mVisible;
        invalidate();
        return mVisible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!mVisible) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        // 绘制垂直网格线
        for (int x = 0; x <= width; x += GRID_SIZE) {
            canvas.drawLine(x, 0, x, height, mGridPaint);
        }

        // 绘制水平网格线
        for (int y = 0; y <= height; y += GRID_SIZE) {
            canvas.drawLine(0, y, width, y, mGridPaint);
        }

        // 绘制中心十字参考线（可选）
        int centerX = width / 2;
        int centerY = height / 2;
        canvas.drawLine(centerX, 0, centerX, height, mAxisPaint);
        canvas.drawLine(0, centerY, width, centerY, mAxisPaint);
    }
}

