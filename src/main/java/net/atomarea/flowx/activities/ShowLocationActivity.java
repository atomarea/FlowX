package net.atomarea.flowx.activities;

import android.app.ActionBar;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;

import net.atomarea.flowx.R;
import net.atomarea.flowx.overlays.Marker;
import net.atomarea.flowx.overlays.MyLocation;
import net.atomarea.flowx.util.Config;
import net.atomarea.flowx.util.LocationHelper;

import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ShowLocationActivity extends LocationActivity implements LocationListener {

	private GeoPoint loc = Config.INITIAL_POS;
	private Location myLoc = null;
	private IMapController mapController;
	private MapView map;

	private Uri createGeoUri() {
		return Uri.parse("geo:" + this.loc.getLatitude() + "," + this.loc.getLongitude());
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_show_location);

		// Get map view and configure it.
		map = (MapView) findViewById(R.id.map);
		map.setTileSource(Config.TILE_SOURCE_PROVIDER);
		map.setBuiltInZoomControls(false);
		map.setMultiTouchControls(true);

		this.mapController = map.getController();
		mapController.setZoom(Config.INITIAL_ZOOM_LEVEL);
		mapController.setCenter(this.loc);

		requestLocationUpdates();
	}

	@Override
	protected void gotoLoc() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void setLoc(final Location location) {
		this.myLoc = location;
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_show_location, menu);

		final MenuItem item = menu.findItem(R.id.action_share_location);
		if (item.getActionProvider() != null && loc != null) {
			final ShareActionProvider mShareActionProvider = (ShareActionProvider) item.getActionProvider();
			final Intent shareIntent = new Intent();
			shareIntent.setAction(Intent.ACTION_SEND);
			shareIntent.putExtra(Intent.EXTRA_TEXT, createGeoUri().toString());
			shareIntent.setType("text/plain");
			mShareActionProvider.setShareIntent(shareIntent);
		} else {
			// This isn't really necessary, but while I was testing it was useful. Possibly remove it?
			item.setVisible(false);
		}

		return true;
	}

	private void addOverlays() {
		this.map.getOverlays().clear();
		this.map.getOverlays().add(0, new Marker(this, this.loc));

		if (myLoc != null) {
			this.map.getOverlays().add(1, new MyLocation(this, this.myLoc));
			this.map.invalidate();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		final Intent intent = getIntent();

		boolean zoom = true;

		if (intent != null) {
			switch (intent.getAction()) {
				case "net.atomarea.flowx.location.request":
					if (intent.hasExtra("longitude") && intent.hasExtra("latitude")) {
						final double longitude = intent.getDoubleExtra("longitude", 0);
						final double latitude = intent.getDoubleExtra("latitude", 0);
						this.loc = new GeoPoint(latitude, longitude);
					}
					break;
				case Intent.ACTION_VIEW:
					// TODO: This is some serious spaghetti code. Write a proper geo URI parser.
					final Uri geoUri = intent.getData();
					// Seriously terrible control flow here.
					boolean posInQuery = false;

					// Attempt to set zoom level if the geo URI specifies it
					if (geoUri != null && geoUri.getQuery() != null && !geoUri.getQuery().isEmpty()) {
						final String[] query = geoUri.getQuery().split("&");
						for (final String param : query) {
							final String[] keyval = param.split("=");
							switch (keyval[0]) {
								case "z":
									if (keyval.length == 2 && keyval[1] != null && !keyval[1].isEmpty()) {
										try {
											mapController.setZoom(Integer.valueOf(keyval[1]));
											zoom = false;
										} catch (final Exception ignored) {
										}
									}
									break;
								case "q":
									final Pattern latlng = Pattern.compile("/^([-+]?[0-9]+(\\.[0-9]+)?),([-+]?[0-9]+(\\.[0-9]+)?)(\\(.*\\))?/");

									if (keyval.length == 2 && keyval[1] != null && !keyval[1].isEmpty()) {
										final Matcher m = latlng.matcher(keyval[1]);
										if (m.matches()) {
											try {
												this.loc = new GeoPoint(Double.valueOf(m.group(1)), Double.valueOf(m.group(3)));
											} catch (final Exception ignored) {
											}
											posInQuery = true;
										}
									}
									break;
							}
						}
					}

					if (geoUri != null && geoUri.getSchemeSpecificPart() != null && !geoUri.getSchemeSpecificPart().isEmpty()) {
						final String[] parts = geoUri.getSchemeSpecificPart().split(",");
						if (parts[1].contains("?")) {
							parts[1] = parts[1].substring(0, parts[1].indexOf("?"));
						}
						if (parts.length == 2 && !posInQuery) {
							try {
								this.loc = new GeoPoint(Double.valueOf(parts[0]), Double.valueOf(parts[1]));
							} catch (final Exception ignored) {
							}
						}
					}
					break;
			}
			if (this.mapController != null && this.loc != null) {
				if (zoom) {
					mapController.setZoom(Config.FINAL_ZOOM_LEVEL);
				}
				mapController.animateTo(this.loc);

				addOverlays();
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.action_about:
				return true;
			case R.id.action_copy_location:
				final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				final ClipData clip = ClipData.newPlainText("location", createGeoUri().toString());
				clipboard.setPrimaryClip(clip);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onLocationChanged(final Location location) {
		if (LocationHelper.isBetterLocation(location, this.myLoc)) {
			this.myLoc = location;
			addOverlays();
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
}
