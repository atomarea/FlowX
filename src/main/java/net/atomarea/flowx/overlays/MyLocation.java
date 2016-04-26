package net.atomarea.flowx.overlays;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;

import net.atomarea.flowx.util.Config;
import net.atomarea.flowx.R;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.SimpleLocationOverlay;

import microsoft.mappoint.TileSystem;

public class MyLocation extends SimpleLocationOverlay {
	private final GeoPoint position;
	private final float accuracy;
	private final Point mapCenterPoint;
	private final Paint fill;
	private final Paint outline;

	public MyLocation(final Context ctx, final Location position) {
		super(ctx);
		this.mapCenterPoint = new Point();
		this.fill = new Paint(Paint.ANTI_ALIAS_FLAG);
		final int accent = ctx.getResources().getColor(R.color.accent);
		fill.setColor(accent);
		fill.setStyle(Paint.Style.FILL);
		this.outline = new Paint(Paint.ANTI_ALIAS_FLAG);
		outline.setColor(accent);
		outline.setAlpha(50);
		outline.setStyle(Paint.Style.FILL);
		this.position = new GeoPoint(position);
		this.accuracy = position.getAccuracy();
	}

	@Override
	public void draw(final Canvas c, final MapView view, final boolean shadow) {
		super.draw(c, view, shadow);

		view.getProjection().toPixels(position, mapCenterPoint);
		c.drawCircle(mapCenterPoint.x, mapCenterPoint.y,
				Math.max(Config.MY_LOCATION_INDICATOR_SIZE + Config.MY_LOCATION_INDICATOR_OUTLINE_SIZE,
						accuracy / (float) TileSystem.GroundResolution(position.getLatitude(), view.getZoomLevel())
				), this.outline);
		c.drawCircle(mapCenterPoint.x, mapCenterPoint.y, Config.MY_LOCATION_INDICATOR_SIZE, this.fill);
	}
}
