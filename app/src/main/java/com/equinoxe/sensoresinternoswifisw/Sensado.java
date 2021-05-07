package com.equinoxe.sensoresinternoswifisw;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import java.util.concurrent.TimeUnit;

public class Sensado extends FragmentActivity implements AmbientModeSupport.AmbientCallbackProvider {
    private static final String AMBIENT_UPDATE_ACTION = "com.equinoxe.sensoresinternoswifisw.action.AMBIENT_UPDATE";

    public static final long TIEMPO_GRABACION_DATOS = TimeUnit.SECONDS.toMillis(120);
    public static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(60);
    public static final String NOTIFICATION = "com.equinoxe.sensoresinternoswifisw.NOTIFICACION";

    final static int ACELEROMETRO = 0;
    final static int GIROSCOPO    = 1;
    final static int MAGNETOMETRO = 2;
    final static int HEART_RATE   = 3;
    final static int ERROR        = 100;
    final static int MSG          = 200;

    boolean bAccelerometer, bGyroscope, bMagneticField, bHR;

    String sMsgAccelerometer, sMsgGyroscope, sMsgMagnetometer, sMsgHR;

    AmbientModeSupport.AmbientController controller;
    private AlarmManager ambientUpdateAlarmManager;
    private PendingIntent ambientUpdatePendingIntent;
    private BroadcastReceiver ambientUpdateBroadcastReceiver;

    private TextView textViewMsg, textBattery, textViewAccelerometer, textViewGyroscope, textViewMagnetometer, textViewHR;

    Intent intentServicioDatosInternalSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensado);

        registerReceiver(receiver, new IntentFilter(NOTIFICATION));

        textBattery = findViewById(R.id.textBattery);
        textViewAccelerometer = findViewById(R.id.textViewAccelerometer);
        textViewGyroscope = findViewById(R.id.textViewGyroscope);
        textViewMagnetometer = findViewById(R.id.textViewMagnetometer);
        textViewHR = findViewById(R.id.textViewHR);
        textViewMsg = findViewById(R.id.textViewMsg);

        Bundle extras = getIntent().getExtras();
        try {
            bAccelerometer = extras.getBoolean("bAccelerometer", true);
            bGyroscope = extras.getBoolean("bGyroscope", false);
            bMagneticField = extras.getBoolean("bMagneticField", false);
            bHR = extras.getBoolean("bHR", false);
        } catch (NullPointerException e) {
            bAccelerometer = true;
            bGyroscope = false;
            bMagneticField = false;
            bHR = false;
        }

        controller = AmbientModeSupport.attach(this);

        ambientUpdateAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent ambientUpdateIntent = new Intent(AMBIENT_UPDATE_ACTION);

        ambientUpdatePendingIntent = PendingIntent.getBroadcast(
                this, 0, ambientUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        ambientUpdateBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshDisplayAndSetNextUpdate();
            }
        };

        sMsgAccelerometer = sMsgGyroscope = sMsgMagnetometer = sMsgHR = "";
        crearServicio();
    }

    private void refreshDisplayAndSetNextUpdate() {
        /*if (controller.isAmbient()) {
            // Implement data retrieval and update the screen for ambient mode
        } else {
            // Implement data retrieval and update the screen for interactive mode
        }*/

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        String sBateria = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + " %";
        textBattery.setText(sBateria);

        textViewAccelerometer.setText(sMsgAccelerometer);
        textViewGyroscope.setText(sMsgGyroscope);
        textViewMagnetometer.setText(sMsgMagnetometer);
        textViewHR.setText(sMsgHR);

        long timeMs = System.currentTimeMillis();
        // Schedule a new alarm
        // Calculate the next trigger time
        long delayMs = AMBIENT_INTERVAL_MS - (timeMs % AMBIENT_INTERVAL_MS);
        long triggerTimeMs = timeMs + delayMs;
        ambientUpdateAlarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                ambientUpdatePendingIntent);

        /*if (controller.isAmbient()) {

        } else {
            // Calculate the next trigger time for interactive mode
        }*/
    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new AmbientModeSupport.AmbientCallback() {
            public void onEnterAmbient(Bundle ambientDetails) {
                refreshDisplayAndSetNextUpdate();
            }

            public void onExitAmbient(Bundle ambientDetails) {
                ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
            }

            @Override
            public void onUpdateAmbient() {
                refreshDisplayAndSetNextUpdate();
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(AMBIENT_UPDATE_ACTION);
        registerReceiver(ambientUpdateBroadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(ambientUpdateBroadcastReceiver);
        ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
    }

    @Override
    public void onDestroy() {
        //ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
        super.onDestroy();

        unregisterReceiver(receiver);
    }

    public void onClickStop(View v) {
        stopService(intentServicioDatosInternalSensor);
        finish();
    }

    private void crearServicio() {
            intentServicioDatosInternalSensor = new Intent(this, ServiceDatosInternalSensor.class);

            intentServicioDatosInternalSensor.putExtra(getString(R.string.Accelerometer), bAccelerometer);
            intentServicioDatosInternalSensor.putExtra(getString(R.string.Gyroscope), bGyroscope);
            intentServicioDatosInternalSensor.putExtra(getString(R.string.Magnetometer), bMagneticField);
            intentServicioDatosInternalSensor.putExtra(getString(R.string.HeartRate), bHR);

            startService(intentServicioDatosInternalSensor);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                int iSensor = bundle.getInt("Sensor");
                int iDevice = bundle.getInt("Device");
                String sCadena = bundle.getString("Cadena");

                if (iDevice == MSG)
                    textViewMsg.setText(sCadena);
                else {
                    if (iDevice != ERROR) {
                        switch (iSensor) {
                            case ACELEROMETRO:
                                sMsgAccelerometer = sCadena;
                                break;
                            case GIROSCOPO:
                                sMsgGyroscope = sCadena;
                                break;
                            case MAGNETOMETRO:
                                sMsgMagnetometer = sCadena;
                                break;
                            case HEART_RATE:
                                sMsgHR = sCadena;
                                break;
                        }
                    }
                }
            }
        }
    };
}