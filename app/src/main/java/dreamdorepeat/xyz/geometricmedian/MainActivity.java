package dreamdorepeat.xyz.geometricmedian;

import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

import algorithms.weiszfeld.Point;
import algorithms.weiszfeld.WeightedPoint;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<LocationSettingsResult> {

    private GoogleMap mMap;

    private ArrayList<Marker> markers = new ArrayList<>();

    private Marker median;
    private ProgressBar pbLoader;
    private Button btnOpen;

    protected GoogleApiClient mGoogleApiClient;
    protected LocationRequest locationRequest;
    int REQUEST_CHECK_SETTINGS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        pbLoader = (ProgressBar) findViewById(R.id.pbLoadingPoint);
        btnOpen = (Button) findViewById(R.id.btnOpen);

        btnOpen.setVisibility(View.GONE);
        pbLoader.setVisibility(View.GONE);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, this).build();
        mGoogleApiClient.connect();
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(30 * 1000);
        locationRequest.setFastestInterval(5 * 1000);
    }

    /**
     * Dispatch onStart() to all fragments.  Ensure any created loaders are
     * now started.
     */
    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        UiSettings ui = mMap.getUiSettings();
        ui.setMyLocationButtonEnabled(true);
        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException se) {
            se.printStackTrace();
            Toast.makeText(this, R.string.we_need_location_permission_to_show_your_location, Toast.LENGTH_LONG).show();
        }

        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                MarkerOptions mo = new MarkerOptions()
                        .draggable(false)
                        .position(latLng)
                        .title(getString(R.string.touch_to_erase))
                        ;
                Marker marker = mMap.addMarker(mo);
                markers.add(marker);

                triggerMedianCalc();
            }
        });

        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                for (Marker m : markers) {
                    if (m.equals(marker)) {
                        markers.remove(m);
                        m.remove();
                        m = null;
                        break;
                    }
                }
                triggerMedianCalc();
            }
        });
    }

    private void triggerMedianCalc() {
        algorithms.weiszfeld.Input input = new algorithms.weiszfeld.Input();

        ArrayList<WeightedPoint> wPoints = new ArrayList<>();

        int N_DIM = 2;

        for (Marker marker : markers) {
            double values[] = new double[N_DIM];

            values[0] = marker.getPosition().latitude;
            values[1] = marker.getPosition().longitude;

            WeightedPoint wPoint = new WeightedPoint();
            wPoint.setPoint(new Point(values));
            wPoint.setWeight(1);

            wPoints.add(wPoint);
        }

        input.setDimension(N_DIM);
        input.setPoints(wPoints);
        input.setPermissibleError(1E-8);

        algorithms.weiszfeld.WeiszfeldAlgorithm weiszfeld = new algorithms.weiszfeld.WeiszfeldAlgorithm();
        algorithms.weiszfeld.Output output = weiszfeld.process(input);

        algorithms.weiszfeld.Point result = output.getPoint();

        double vals[] = result.getValues();

        LatLng ll = new LatLng(vals[0], vals[1]);


        if (median != null) {
            median.remove();
            median = null;
        }

        MarkerOptions mo = new MarkerOptions()
                .position(ll)
                .title(getString(R.string.spatial_median))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_golf_course_white_48dp))
                ;
        median = mMap.addMarker(mo);

        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (median == null) return;

                Intent intent = new Intent(Intent.ACTION_VIEW);

                Uri uri = Uri.parse("geo:" + String.valueOf(median.getPosition().latitude)
                        + "," + String.valueOf(median.getPosition().longitude) );

                intent.setData(uri);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            }
        });

        btnOpen.setVisibility(View.VISIBLE);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        mGoogleApiClient,
                        builder.build()
                );
        result.setResultCallback(this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                // NO need to show the dialog;

                break;

            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                //  GPS disabled show the user a dialog to turn it on
                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().

                    status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);

                } catch (IntentSender.SendIntentException e) {

                    //failed to show dialog
                }


                break;

            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                // Location settings are unavailable so not possible to show any dialog now
                break;
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {

                Toast.makeText(getApplicationContext(), "GPS enabled", Toast.LENGTH_LONG).show();
            } else {

                Toast.makeText(getApplicationContext(), "GPS is not enabled", Toast.LENGTH_LONG).show();
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }
}
