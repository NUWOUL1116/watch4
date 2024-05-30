package com.nuwoul.watchtu;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthData;
import com.samsung.android.sdk.healthdata.HealthDataResolver;
import com.samsung.android.sdk.healthdata.HealthDataService;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthResultHolder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "HealthData";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private UUID uuid = UUID.fromString("YOUR-DEVICE-UUID"); // UUID를 실제 값으로 변경하세요

    private HealthDataStore mStore;

    private TextView tvStatus;
    private TextView tvReceivedData;
    private Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        btnConnect = findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(v -> {
            if (checkPermissions()) {
                initializeBluetooth();
                listPairedDevices();
                initializeSamsungHealth();
            }
        });
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void initializeBluetooth() {
        if (checkPermissions()) {
            try {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter == null) {
                    Log.e(TAG, "Bluetooth not supported on this device");
                    Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!bluetoothAdapter.isEnabled()) {
                    // Bluetooth가 비활성화된 경우 사용자에게 활성화를 요청할 수 있습니다.
                    // Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in initializeBluetooth", e);
                Toast.makeText(this, "Error in initializeBluetooth: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Permissions are not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void listPairedDevices() {
        if (checkPermissions()) {
            try {
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                if (pairedDevices.size() > 0) {
                    for (BluetoothDevice device : pairedDevices) {
                        Log.d(TAG, "Paired Device: " + device.getName() + " - " + device.getAddress());
                        // Get the device's UUIDs
                        ParcelUuid[] uuids = device.getUuids();
                        if (uuids != null) {
                            for (ParcelUuid parcelUuid : uuids) {
                                Log.d(TAG, "UUID: " + parcelUuid.getUuid().toString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in listPairedDevices", e);
                Toast.makeText(this, "Error in listPairedDevices: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Permissions are not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeSamsungHealth() {
        HealthDataService healthDataService = new HealthDataService();
        try {
            healthDataService.initialize(this);
        } catch (Exception e) {
            Log.e(TAG, "HealthDataService initialization failed.", e);
        }

        mStore = new HealthDataStore(this, mConnectionListener);
        mStore.connectService();
    }

    private final HealthDataStore.ConnectionListener mConnectionListener = new HealthDataStore.ConnectionListener() {
        @Override
        public void onConnected() {
            Log.d(TAG, "Health data service is connected.");
            // 연결이 완료되면 데이터를 요청할 수 있습니다.
            requestHeartRateData();
            requestBloodPressureData();
        }

        @Override
        public void onConnectionFailed(HealthConnectionErrorResult error) {
            Log.e(TAG, "Health data service is not available.");
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "Health data service is disconnected.");
        }
    };

    private void requestHeartRateData() {
        HealthDataResolver resolver = new HealthDataResolver(mStore, null);

        // 오늘의 심박수 데이터를 가져옵니다.
        GregorianCalendar today = new GregorianCalendar();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);

        HealthDataResolver.Filter filter = HealthDataResolver.Filter.and(
                HealthDataResolver.Filter.eq("source_type", -1),
                HealthDataResolver.Filter.greaterThanEquals(HealthConstants.HeartRate.START_TIME, today.getTimeInMillis())
        );

        HealthDataResolver.ReadRequest request = new HealthDataResolver.ReadRequest.Builder()
                .setDataType(HealthConstants.HeartRate.HEART_RATE)
                .setProperties(new String[]{HealthConstants.HeartRate.HEART_RATE})
                .setFilter(filter)
                .build();

        try {
            resolver.read(request).setResultListener(result -> {
                try {
                    for (HealthData data : result) {
                        float heartRate = data.getFloat(HealthConstants.HeartRate.HEART_RATE);
                        Log.d(TAG, "Heart Rate: " + heartRate);
                    }
                } finally {
                    result.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Reading heart rate data failed.", e);
        }
    }

    private void requestBloodPressureData() {
        HealthDataResolver resolver = new HealthDataResolver(mStore, null);

        // 오늘의 혈압 데이터를 가져옵니다.
        GregorianCalendar today = new GregorianCalendar();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);

        HealthDataResolver.Filter filter = HealthDataResolver.Filter.and(
                HealthDataResolver.Filter.eq("source_type", -1),
                HealthDataResolver.Filter.greaterThanEquals(HealthConstants.BloodPressure.START_TIME, today.getTimeInMillis())
        );

        HealthDataResolver.ReadRequest request = new HealthDataResolver.ReadRequest.Builder()
                .setDataType(HealthConstants.BloodPressure.HEALTH_DATA_TYPE)
                .setProperties(new String[]{HealthConstants.BloodPressure.SYSTOLIC, HealthConstants.BloodPressure.DIASTOLIC})
                .setFilter(filter)
                .build();

        try {
            resolver.read(request).setResultListener(result -> {
                try {
                    for (HealthData data : result) {
                        float systolic = data.getFloat(HealthConstants.BloodPressure.SYSTOLIC);
                        float diastolic = data.getFloat(HealthConstants.BloodPressure.DIASTOLIC);
                        Log.d(TAG, "Blood Pressure: Systolic=" + systolic + ", Diastolic=" + diastolic);
                    }
                } finally {
                    result.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Reading blood pressure data failed.", e);
        }
    }

    @Override
    protected void onDestroy() {
        mStore.disconnectService();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 부여되면 블루투스를 초기화하고 연결 시도
                initializeBluetooth();
                listPairedDevices();
                initializeSamsungHealth();
            } else {
                // 권한이 거부된 경우
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
