package com.equinoxe.sensoresinternoswifisw;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.List;

public class MainActivity extends FragmentActivity {
    boolean bAccelerometer, bGyroscope, bMagneticField, bHR, bBarometer;

    private Button buttonStartSensing;
    private CheckBox checkAccelerometer;
    private CheckBox checkGyroscope;
    private CheckBox checkMagnetometer;
    private CheckBox checkHR;
    private CheckBox checkBarometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonStartSensing = findViewById(R.id.buttonStart);
        checkAccelerometer = findViewById(R.id.checkBoxAccelerometer);
        checkGyroscope = findViewById(R.id.checkBoxGyroscope);
        checkMagnetometer = findViewById(R.id.checkBoxMagnetometer);
        checkHR = findViewById(R.id.checkBoxHR);
        checkBarometer = findViewById(R.id.checkBoxBarometer);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> lista = sensorManager.getSensorList(Sensor.TYPE_ALL);
        bAccelerometer = bGyroscope = bMagneticField = bHR = false;
        for (Sensor sensor : lista) {
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    bAccelerometer = true;
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    bGyroscope = true;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    bMagneticField = true;
                    break;
                case Sensor.TYPE_HEART_RATE:
                    bHR = true;
                    break;
                case Sensor.TYPE_PRESSURE:
                    bBarometer = true;
            }
        }

        checkAccelerometer.setEnabled(bAccelerometer);
        checkGyroscope.setEnabled(bGyroscope);
        checkMagnetometer.setEnabled(bMagneticField);
        checkHR.setEnabled(bHR);
        checkBarometer.setEnabled(bBarometer);

        checkForPermissions();

        checkAccelerometer.setOnCheckedChangeListener((buttonView, isChecked) -> comprobarSensoresSeleccionados());

        checkGyroscope.setOnCheckedChangeListener((buttonView, isChecked) -> comprobarSensoresSeleccionados());

        checkMagnetometer.setOnCheckedChangeListener((buttonView, isChecked) -> comprobarSensoresSeleccionados());

        checkHR.setOnCheckedChangeListener((buttonView, isChecked) -> comprobarSensoresSeleccionados());

        checkBarometer.setOnCheckedChangeListener((buttonView, isChecked) -> comprobarSensoresSeleccionados());

        /*SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.commit();*/
    }

    public void comprobarSensoresSeleccionados() {
        boolean bEnabled = checkAccelerometer.isChecked() || checkGyroscope.isChecked() || checkMagnetometer.isChecked() || checkHR.isChecked();
        buttonStartSensing.setEnabled(bEnabled);
    }


    public void onClickStartSending(View v) {
        Intent intent = new Intent(this, Sensado.class);
        intent.putExtra("bAccelerometer", checkAccelerometer.isChecked());
        intent.putExtra("bGyroscope", checkGyroscope.isChecked());
        intent.putExtra("bMagneticField", checkMagnetometer.isChecked());
        intent.putExtra("bHR", checkHR.isChecked());
        intent.putExtra("bBarometer", checkBarometer.isChecked());
        startActivity(intent);
    }

    public void onClickOptions(View v) {
        Intent intent = new Intent(this, Options.class);
        startActivity(intent);
    }

    private void checkForPermissions() {
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, 1);
        }

        permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, 1);
        }
    }
}