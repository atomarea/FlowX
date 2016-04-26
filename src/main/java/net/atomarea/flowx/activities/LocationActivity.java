package net.atomarea.flowx.activities;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import net.atomarea.flowx.util.Config;

public abstract class LocationActivity extends Activity implements LocationListener {
	private LocationManager locationManager;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	}

	protected abstract void gotoLoc() throws UnsupportedOperationException;
	protected abstract void setLoc(final Location location);

	protected void requestLocationUpdates() {
		final Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if (lastKnownLocation != null) {
			setLoc(lastKnownLocation);
			try {
				gotoLoc();
			} catch (final UnsupportedOperationException ignored) {
			}
		}

		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Config.LOCATION_FIX_TIME_DELTA,
				Config.LOCATION_FIX_SPACE_DELTA, this);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Config.LOCATION_FIX_TIME_DELTA,
				Config.LOCATION_FIX_SPACE_DELTA, this);

		// If something else is also querying for location more frequently than we are, the battery is already being
		// drained. Go ahead and use the existing locations as often as we can get them.
		locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);
	}

	protected void pauseLocationUpdates() {
		locationManager.removeUpdates(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseLocationUpdates();
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.setLoc(null);

		requestLocationUpdates();
	}
}
