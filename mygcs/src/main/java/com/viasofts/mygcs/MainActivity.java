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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
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
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.jetbrains.annotations.NotNull;

import java.util.List;




public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, DroneListener, TowerListener, LinkListener {

    private NaverMap mMap;
    Spinner maptype_spinner;
    private static final String TAG = MainActivity.class.getSimpleName();
    private double mTakeoffAltitude = 5.5;

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private Spinner modeSelector;

    Button altitudeControl;
    Button addAltitude;
    Button subAltitude;

    private Button startVideoStream;
    private Button stopVideoStream;


    private Button startVideoStreamUsingObserver;
    private Button stopVideoStreamUsingObserver;

    private MediaCodecManager mediaCodecManager;

    private TextureView videoView;

    private String videoTag = "testvideotag";
    private Marker dronePositionMarker = new Marker();

    private LatLng mCurrentDronePosition;
    Marker mDroneMarker = new Marker();

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

        initViews();
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



    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(10, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
//            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
//                @Override
//                public void onError(int executionError) {
//                    alertUser("Unable to arm vehicle.");
//                }
//
//                @Override
//                public void onTimeout() {
//                    alertUser("Arming operation timed out.");
//                }
//            });
            AlertDialog.Builder armAlertBuilder =
                    new AlertDialog.Builder(MainActivity.this);

            armAlertBuilder.setTitle("모터 가동");
            armAlertBuilder.setMessage("모터를 가동합니다.\n모터가 고속으로 회전합니다.");
            armAlertBuilder.setPositiveButton("확인",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which){
                    VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                        @Override
                        public void onError(int executionError) {
                            alertUser("Unable to arm vehicle.");
                        }

                        @Override
                        public void onTimeout() {
                            alertUser("Arming operation timed out.");
                        }
                    });

                }
            });
            armAlertBuilder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            armAlertBuilder.show();
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

        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(35.945256,126.682133));
        naverMap.moveCamera(cameraUpdate);

        maptype_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {


            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mMap.setMapType(NaverMap.MapType.Basic);
                } else if (position == 1) {
                    mMap.setMapType(NaverMap.MapType.Satellite);
                } else if (position == 2) {
                    mMap.setMapType(NaverMap.MapType.Terrain);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initViews() {
        maptype_spinner = findViewById(R.id.maptype_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.map_type, R.layout.custom_spinner_item);
        adapter.setDropDownViewResource(R.layout.custom_spinner_item_click);
        maptype_spinner.setAdapter(adapter);
        altitudeControl = findViewById(R.id.altitudeControl);
        addAltitude = findViewById(R.id.addAltitude);
        subAltitude = findViewById(R.id.subAltitude);

        addAltitude.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mTakeoffAltitude<10){
                    mTakeoffAltitude = mTakeoffAltitude + 0.5;
                    altitudeControl.setText("이륙고도\n"+(double)mTakeoffAltitude  +"m");
                }
            }
        });
        subAltitude.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mTakeoffAltitude>3){
                    mTakeoffAltitude = mTakeoffAltitude - 0.5;
                    altitudeControl.setText("이륙고도\n"+(double)mTakeoffAltitude +"m");
                }
            }
        });

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

            case AttributeEvent.GPS_COUNT:
                countGPS();

            case AttributeEvent.ATTITUDE_UPDATED:
                updateAttitude();

            case AttributeEvent.GPS_POSITION:
                Gps gps = this.drone.getAttribute(AttributeType.GPS);
                LatLong latLong = gps.getPosition();
                if (latLong == null) {
                    break;
                } else {
                    mCurrentDronePosition = new LatLng(gps.getPosition().getLatitude(), gps.getPosition().getLongitude());
                    Log.d("myLog", "현재드론의 위치 : " + mCurrentDronePosition);
                    updateDronePostion(mCurrentDronePosition);
                }



            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void updateDronePostion(LatLng latLng) {

        //TODO 가져온 좌표를 가지고 드론마커를 갱신하세요.
        mDroneMarker.setPosition(latLng);
        mDroneMarker.setMap(mMap);
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
        Button armButton = (Button) findViewById(R.id.armButton);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }
    protected void countGPS(){
        TextView countGPS = (TextView) findViewById(R.id.satelliteview);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        countGPS.setText(String.format("%d", gps.getSatellitesCount()));
    }
    protected void updateAttitude(){
        TextView viewYaw = (TextView) findViewById(R.id.yawview);
        Attitude yaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        float yaw_360 = (float) yaw.getYaw();
        if(yaw_360 < 0){
            yaw_360 = 360 - Math.abs(yaw_360);
            if(yaw_360 == 360) yaw_360 = 0;
        }
        viewYaw.setText(String.format("%d", (int) yaw_360 ) + "deg");
        dronePositionMarker.setAngle(yaw_360);
        //mLocationOverlay.setBearing(yaw_360);
    }

    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        if (pointA == null || pointB == null) {
            return 0;
        }
        double dx = pointA.getLatitude() - pointB.getLatitude();
        double dy = pointA.getLongitude() - pointB.getLongitude();
        double dz = pointA.getAltitude() - pointB.getAltitude();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    protected void updateDistanceFromHome() {
//        TextView distanceTextView = (TextView) findViewById(R.id.distanceValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        double vehicleAltitude = droneAltitude.getAltitude();
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition();

        double distanceFromHome = 0;

        if (droneGps.isValid()) {
            LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
            Home droneHome = this.drone.getAttribute(AttributeType.HOME);
            distanceFromHome = distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
        } else {
            distanceFromHome = 0;
        }

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
