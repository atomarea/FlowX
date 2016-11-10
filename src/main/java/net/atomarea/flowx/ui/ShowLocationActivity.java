package net.atomarea.flowx.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;

import java.util.List;
import java.util.Locale;

public class ShowLocationActivity extends Activity implements OnMapReadyCallback {

    private GoogleMap mGoogleMap;
    private LatLng mLocation;
    private String mLocationName;
    private MarkerOptions options;
    private Marker marker;

    class InfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private final View InfoWindow;

        InfoWindowAdapter() {
            InfoWindow = getLayoutInflater().inflate(R.layout.show_location_infowindow, null);
        }

        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {

            TextView Title = ((TextView) InfoWindow.findViewById(R.id.title));
            Title.setText(marker.getTitle());
            TextView Snippet = ((TextView) InfoWindow.findViewById(R.id.snippet));
            Snippet.setText(marker.getSnippet());

            return InfoWindow;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setContentView(R.layout.activity_show_locaction);
        MapFragment fragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map_fragment);
        fragment.getMapAsync(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_navigate:
                double longitude = mLocation.longitude;
                double latitude = mLocation.latitude;
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude)));
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.showlocation, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();

        this.mLocationName = intent != null ? intent.getStringExtra("name") : null;

        if (intent != null && intent.hasExtra("longitude") && intent.hasExtra("latitude")) {
            double longitude = intent.getDoubleExtra("longitude",0);
            double latitude = intent.getDoubleExtra("latitude",0);
            this.mLocation = new LatLng(latitude,longitude);
            if (this.mGoogleMap != null) {
                markAndCenterOnLocation(this.mLocation, this.mLocationName);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.mGoogleMap = googleMap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                this.mGoogleMap.setBuildingsEnabled(true);
                this.mGoogleMap.setMyLocationEnabled(true);
            }
        } else {
            this.mGoogleMap.setBuildingsEnabled(true);
            this.mGoogleMap.setMyLocationEnabled(true);
        }
        if (this.mLocation != null) {
            this.markAndCenterOnLocation(this.mLocation,this.mLocationName);
        }
    }

    private static String getAddress(Context context, LatLng location) {
        double longitude = location.longitude;
        double latitude = location.latitude;
        String address = "";
        if (latitude != 0 && longitude != 0) {
            Geocoder geoCoder = new Geocoder(context, Locale.getDefault());
            try {
                List<Address> addresses = geoCoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null) {
                    Address Address = addresses.get(0);
                    StringBuilder strAddress = new StringBuilder("");
                    for (int i = 0; i < Address.getMaxAddressLineIndex(); i++) {
                        strAddress.append(Address.getAddressLine(i)).append("\n");
                    }
                    address = strAddress.toString();
                    address = address.substring(0, address.length()-1); //trim last \n

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return address;
    }

    private void markAndCenterOnLocation(final LatLng location, String name) {
        mGoogleMap.clear();
        options = new MarkerOptions();
        options.position(location);
        double longitude = mLocation.longitude;
        double latitude = mLocation.latitude;
        mGoogleMap.setInfoWindowAdapter(new InfoWindowAdapter());
        if (name != null) {
            options.title(name);
        }
        if (latitude != 0 && longitude != 0) {
            new AsyncTask<Void, Void, Void>() {
                String address = null;

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    marker = mGoogleMap.addMarker(options);
                    marker.showInfoWindow();
                    mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, Config.DEFAULT_ZOOM));
                }

                @Override
                protected Void doInBackground(Void... params) {
                    address = getAddress(ShowLocationActivity.this, location);
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    marker.remove();
                    options.snippet(String.valueOf(address));
                    marker = mGoogleMap.addMarker(options);
                    marker.showInfoWindow();
                }
            }.execute();
        }

        mGoogleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (marker != null) {
                    marker.showInfoWindow();
                }
            }
        });
    }
}