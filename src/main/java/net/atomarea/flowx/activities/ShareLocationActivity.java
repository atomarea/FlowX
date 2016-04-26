package net.atomarea.flowx.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.overlays.MyLocation;
import net.atomarea.flowx.util.Config;
import net.atomarea.flowx.util.LocationHelper;

import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class ShareLocationActivity extends LocationActivity implements LocationListener {

	private Location loc;
	private IMapController mapController;
	private Button shareButton;
	private RelativeLayout snackBar;
	private MapView map;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_share_location);

		// Get map view and configure it.
		map = (MapView) findViewById(R.id.map);
		map.setTileSource(Config.TILE_SOURCE_PROVIDER);
		map.setBuiltInZoomControls(false);
		map.setMultiTouchControls(true);

		this.mapController = map.getController();
		mapController.setZoom(Config.INITIAL_ZOOM_LEVEL);
		mapController.setCenter(Config.INITIAL_POS);

		// Setup the cancel button
		final Button cancelButton = (Button) findViewById(R.id.cancel_button);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});

		// Setup the share button
		this.shareButton = (Button) findViewById(R.id.share_button);
		this.shareButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				if (loc != null) {
					final Intent result = new Intent();
					result.putExtra("latitude", loc.getLatitude());
					result.putExtra("longitude", loc.getLongitude());
					result.putExtra("altitude", loc.getAltitude());
					result.putExtra("accuracy", (int) loc.getAccuracy());
					setResult(RESULT_OK, result);
					finish();
				}
			}
		});

		// Setup the snackbar
		this.snackBar = (RelativeLayout) findViewById(R.id.snackbar);
		final TextView snackbarAction = (TextView) findViewById(R.id.snackbar_action);
		snackbarAction.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
			}
		});

		requestLocationUpdates();
	}

	@Override
	protected void gotoLoc() {
		if (this.loc != null && mapController != null) {
			if (map.getZoomLevel() == Config.INITIAL_ZOOM_LEVEL) {
				mapController.setZoom(Config.FINAL_ZOOM_LEVEL);
			}
			mapController.animateTo(new GeoPoint(this.loc));
		}
	}

	@Override
	protected void setLoc(final Location location) {
		this.loc = location;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void setShareButtonEnabled(final boolean enabled) {
		if (enabled) {
			this.shareButton.setEnabled(true);
			this.shareButton.setTextColor(0xde000000);
			this.shareButton.setText(R.string.share);
		} else {
			this.shareButton.setEnabled(false);
			this.shareButton.setTextColor(0x8a000000);
			this.shareButton.setText(R.string.locating);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (isLocationEnabled()) {
			this.snackBar.setVisibility(View.GONE);
		} else {
			this.snackBar.setVisibility(View.VISIBLE);
		}
		setShareButtonEnabled(false);
	}

	@Override
	public void onLocationChanged(final Location location) {
		if (LocationHelper.isBetterLocation(location, this.loc)) {
			setShareButtonEnabled(true);
			this.loc = location;
			gotoLoc();

			this.map.getOverlays().clear();
			this.map.getOverlays().add(new MyLocation(this, this.loc));
		}
	}

	@Override
	public void onStatusChanged(final String provider, final int status, final Bundle extras) {

	}

	@Override
	public void onProviderEnabled(final String provider) {

	}

	@Override
	public void onProviderDisabled(final String provider) {

	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private boolean isLocationEnabledKitkat() {
		try {
			final int locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
			return locationMode != Settings.Secure.LOCATION_MODE_OFF;
		} catch (final Settings.SettingNotFoundException e) {
			return false;
		}
	}

	@SuppressWarnings("deprecation")
	private boolean isLocationEnabledLegacy() {
		final String locationProviders = Settings.Secure.getString(getContentResolver(),
				Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
		return !TextUtils.isEmpty(locationProviders);
	}

	private boolean isLocationEnabled() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
			return isLocationEnabledKitkat();
		} else {
			return isLocationEnabledLegacy();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_share_location, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.action_about:
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
