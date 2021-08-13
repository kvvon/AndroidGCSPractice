package com.viasofts.mygcs;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.android.client.utils.video.MediaCodecManager;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;

import org.jetbrains.annotations.NotNull;

import java.util.List;




public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, DroneListener, TowerListener, LinkListener {

    private NaverMap mMap;
    private static final String TAG = MainActivity.class.getSimpleName();

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private Spinner modeSelector;

    private Button startVideoStream;
    private Button stopVideoStream;


    private Button startVideoStreamUsingObserver;
    private Button stopVideoStreamUsingObserver;

    private MediaCodecManager mediaCodecManager;

    private TextureView videoView;

    private String videoTag = "testvideotag";

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        modeSelector = findViewById(R.id.modeSelect);


        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment)fm.findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mapFragment).commit();
        }

        mapFragment.getMapAsync(this);


    }
    //드론 연결 및 해제
    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else{
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onMapReady(@NonNull @org.jetbrains.annotations.NotNull NaverMap naverMap) {
        mMap = naverMap;
        mMap.setMapType(NaverMap.MapType.Satellite);
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.HOME_UPDATED:
                updateDistanceFromHome();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull @NotNull LinkConnectionStatus connectionStatus) {
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {

    }

    // UI updating
    // ==========================================================

    protected void updateConnectedButton(Boolean isConnected) {
       Button connectButton = (Button) findViewById(R.id.btnConnect);
       if (isConnected) {
           connectButton.setText("Disconnect");
       } else {
           connectButton.setText("Connect");
       }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
//        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

//        if (!this.drone.isConnected()) {
//            armButton.setVisibility(View.INVISIBLE);
//        } else {
//            armButton.setVisibility(View.VISIBLE);
//        }
//
//        if (vehicleState.isFlying()) {
//            // Land
//            armButton.setText("LAND");
//        } else if (vehicleState.isArmed()) {
//            // Take off
//            armButton.setText("TAKE OFF");
//        } else if (vehicleState.isConnected()) {
//            // Connected but not Armed
//            armButton.setText("ARM");
//        }
    }

    protected void updateAltitude() {
//        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
//        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
//        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateSpeed() {
//        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
//        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
//        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateDistanceFromHome() {
//        TextView distanceTextView = (TextView) findViewById(R.id.distanceValueTextView);
//        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
//        double vehicleAltitude = droneAltitude.getAltitude();
//        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
//        LatLong vehiclePosition = droneGps.getPosition();
//
//        double distanceFromHome = 0;
//
//        if (droneGps.isValid()) {
//            LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
//            Home droneHome = this.drone.getAttribute(AttributeType.HOME);
//            distanceFromHome = distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
//        } else {
//            distanceFromHome = 0;
//        }
//
//        distanceTextView.setText(String.format("%3.1f", distanceFromHome) + "m");
    }

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d("mylog", message);
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }
}
