package home.fbclient;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Zooms the whole page, like a smaller screen would. The child (the WebView) is laid out
 * 1/zoom times as large and then scaled up by zoom, so Facebook sees a narrower window and
 * lays out text, photos and boxes for it together.
 *
 * WebSettings.setTextZoom does not work on m.facebook.com: Facebook divides its font size
 * variables by the text zoom, and when that is overridden, the text spills out of the
 * fixed-height boxes its server laid out for 100% text.
 */
final class ZoomFrame extends FrameLayout {

    private float zoom = 1f;

    ZoomFrame(Context c) {
        super(c);
    }

    void setZoom(float z) {
        if (z == zoom) return;
        zoom = z;
        requestLayout();
    }

    float zoom() {
        return zoom;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        int w = Math.round(getMeasuredWidth() / zoom), h = Math.round(getMeasuredHeight() / zoom);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            c.layout(0, 0, c.getMeasuredWidth(), c.getMeasuredHeight());
            c.setPivotX(0);
            c.setPivotY(0);
            c.setScaleX(zoom);
            c.setScaleY(zoom);
        }
    }
}
