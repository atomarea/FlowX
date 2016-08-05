package net.atomarea.flowx.other;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

/**
 * Created by Tom on 05.08.2016.
 */
public class DrawableItemDecoration extends RecyclerView.ItemDecoration {

    private Drawable mDrawable;

    public DrawableItemDecoration(Drawable drawable) {
        mDrawable = drawable;
    }

    public DrawableItemDecoration(Context context, int drawable) {
        this(ContextCompat.getDrawable(context, drawable));
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        if (parent.getChildAdapterPosition(view) == 0) return;
        outRect.top = mDrawable.getIntrinsicHeight();
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int dividerLeft = parent.getPaddingLeft();
        int dividerRight = parent.getWidth() - parent.getPaddingRight();
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount - 1; i++) {
            View child = parent.getChildAt(i);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
            int dividerTop = child.getBottom() + params.bottomMargin;
            int dividerBottom = dividerTop + mDrawable.getIntrinsicHeight();

            mDrawable.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom);
            mDrawable.draw(c);
        }
    }
}
