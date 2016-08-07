package net.atomarea.flowx.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import net.atomarea.flowx.data.ChatMessage;

/**
 * Created by Tom on 06.08.2016.
 */
public class ReadIndicatorView extends View {

    private ChatMessage chatMessage;
    private int Diameter, OffsetX, OffsetY;
    private Paint WhitePaintStroke, WhitePaint;
    private Interpolator interpolator;
    private AnimatorListenerAdapter rotateAnimationAdapter;

    public ReadIndicatorView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        WhitePaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        WhitePaintStroke.setColor(ContextCompat.getColor(getContext(), android.R.color.white));
        WhitePaintStroke.setStyle(Paint.Style.STROKE);
        WhitePaintStroke.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));
        WhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        WhitePaint.setColor(ContextCompat.getColor(getContext(), android.R.color.white));
        interpolator = new AccelerateDecelerateInterpolator();
        rotateAnimationAdapter = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                rotateAnimate();
            }
        };
    }

    private void rotateAnimate() {
        if (getAnimation() != null) getAnimation().cancel();
        if (getRotation() != 0) setRotation(0);
        animate().rotationBy(180f).setDuration(1000).setInterpolator(interpolator).setListener(rotateAnimationAdapter).start();
    }

    public void setChatMessage(ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
        update();
    }

    public void update() {
        invalidate();
        requestLayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == h) {
            OffsetX = 0;
            OffsetY = 0;
            Diameter = w;
        } else if (w < h) {
            OffsetX = 0;
            OffsetY = (h - w) / 2;
            Diameter = w;
        } else if (h < w) {
            OffsetX = (w - h) / 2;
            OffsetY = 0;
            Diameter = h;
        } else Diameter = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (Diameter == -1) return;
        if (chatMessage == null) return;
        if (!chatMessage.isSent()) return;

        if (chatMessage.getState() == ChatMessage.State.NotDelivered) {
            canvas.drawLine(OffsetX + Diameter / 2, OffsetY + Diameter, OffsetX + Diameter / 2, OffsetY, WhitePaintStroke);
            if (getAnimation() == null || !getAnimation().hasStarted() || getAnimation().hasEnded())
                rotateAnimate();
        } else if (chatMessage.getState() == ChatMessage.State.DeliveredToServer) {
            canvas.drawLine(OffsetX + Diameter / 2, OffsetY + Diameter, OffsetX + Diameter / 2, OffsetY, WhitePaintStroke);
        } else if (chatMessage.getState() == ChatMessage.State.DeliveredToContact) {
            canvas.drawCircle(OffsetX + Diameter / 2, OffsetY + Diameter / 2, Diameter / 2 - WhitePaintStroke.getStrokeWidth(), WhitePaintStroke);
        } else if (chatMessage.getState() == ChatMessage.State.ReadByContact) {
            canvas.drawCircle(OffsetX + Diameter / 2, OffsetY + Diameter / 2, Diameter / 2, WhitePaint);
        }
    }

}
