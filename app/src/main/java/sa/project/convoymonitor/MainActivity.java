package sa.project.convoymonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_FINE_GPS = 1;

    MqttAndroidClient mqttAndroidClient;

    final String serverUri = "tcp://mqtt-broker.ru:1883";

    String clientId = "ConvoyMonitor";
    final String publishTopic = "test";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET},
                    REQUEST_CODE_FINE_GPS);
            return;
        }


        init();

    }

    private void init() {
        setContentView(R.layout.activity_main);

        clientId = clientId + System.currentTimeMillis();

        connectToBroker();

        setupLocationMonitoring();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_FINE_GPS && grantResults[0] != PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Not enough permissions!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            init();
        }
    }

    @SuppressLint("MissingPermission")
    private void setupLocationMonitoring() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                publishMessage(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    private void connectToBroker() {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    Toast.makeText(getApplicationContext(), "Reconnected to : " + serverURI, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Connected to : " + serverURI, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Toast.makeText(getApplicationContext(), "The Connection was lost.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Toast.makeText(getApplicationContext(), "Message delivered", Toast.LENGTH_LONG).show();
            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);


        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    exception.printStackTrace();
                    Toast.makeText(getApplicationContext(), "Failed to connect to: " + serverUri, Toast.LENGTH_LONG).show();
                }
            });


        } catch (MqttException e) {
            Toast.makeText(getApplicationContext(), "Error Connecting: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void publishMessage(Location location) {


        MqttMessage message = new MqttMessage();
        message.setQos(2); // Quality of Service (garanteed delivery - exactly once).
        try {
            message.setPayload(formatMessage(location).getBytes());
            mqttAndroidClient.publish(publishTopic, message);
        } catch (MqttException | JSONException e) {
            Toast.makeText(getApplicationContext(), "Error Publishing: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

//        2018-02-03 12:21:32"

    }

    private String formatMessage(Location location) throws JSONException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = df.format(new Date());
        String vehicle_id = clientId;
        String lon = String.valueOf(location.getLongitude());
        String lat = String.valueOf(location.getLatitude());

        JSONObject json = new JSONObject();
        json.put("timestamp", timestamp);
        json.put("vehicle_id", vehicle_id);
        json.put("mission_id", 1);
        json.put("driver_id", "sergey");
        json.put("vehicle_type", "suv");

        JSONObject loc = new JSONObject();
        loc.put("lon", lon);
        loc.put("lat", lat);

        json.put("location", loc);
        return json.toString();
    }
}
