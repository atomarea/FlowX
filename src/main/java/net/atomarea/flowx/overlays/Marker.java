package net.atomarea.flowx.overlays;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;

import net.atomarea.flowx.R;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.SimpleLocationOverlay;

public class Marker extends SimpleLocationOverlay {
	private final GeoPoint position;
	final Bitmap icon;
	final Point mapCenterPoint;

	public Marker(final Context ctx, final GeoPoint position) {
		super(ctx);
		this.position = position;
		this.icon = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_place_grey600_48dp);
		this.mapCenterPoint = new Point();
	}

	@Override
	public void draw(final Canvas c, final MapView view, final boolean shadow) {
		super.draw(c, view, shadow);

		view.getProjection().toPixels(position, mapCenterPoint);

		c.drawBitmap(icon,
				mapCenterPoint.x - icon.getWidth() / 2,
				mapCenterPoint.y - icon.getHeight(),
				null);

	}
}
