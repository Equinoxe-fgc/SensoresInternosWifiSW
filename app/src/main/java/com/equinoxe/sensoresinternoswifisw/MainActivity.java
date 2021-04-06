package com.equinoxe.sensoresinternoswifisw;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.util.List;

public class MainActivity extends FragmentActivity {
    boolean bAccelerometer, bGyroscope, bMagneticField, bHR;

    private Button buttonStartSensing;
    private CheckBox checkAccelerometer;
    private CheckBox checkGyroscope;
    private CheckBox checkMagnetometer;
    private CheckBox checkHR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonStartSensing = findViewById(R.id.buttonStart);
        checkAccelerometer = findViewById(R.id.checkBoxAccelerometer);
        checkGyroscope = findViewById(R.id.checkBoxGyroscope);
        checkMagnetometer = findViewById(R.id.checkBoxMagnetometer);
        checkHR = findViewById(R.id.checkBoxHR);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> lista = sensorManager.getSensorList(Sensor.TYPE_ALL);
        bAccelerometer = bGyroscope = bMagneticField = bHR = false;
        for (Sensor sensor : lista) {
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    if (sensor.getName().compareTo(getString(R.string.Accelerometer)) == 0)
                        bAccelerometer = true;
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    if (sensor.getName().compareTo(getString(R.string.Gyroscope)) == 0)
                        bGyroscope = true;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if (sensor.getName().compareTo(getString(R.string.Magnetometer)) == 0)
                        bMagneticField = true;
                    break;
                case Sensor.TYPE_HEART_RATE:
                    if (sensor.getName().compareTo(getString(R.string.HeartRate)) == 0)
                        bHR = true;
                    break;
            }
        }

        if (bAccelerometer)
            checkAccelerometer.setEnabled(true);
        if (bGyroscope)
            checkGyroscope.setEnabled(true);
        if (bMagneticField)
            checkMagnetometer.setEnabled(true);
        if (bHR)
            checkHR.setEnabled(true);

        checkAccelerometer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                comprobarSensoresSeleccionados();
            }
        });

        checkGyroscope.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                comprobarSensoresSeleccionados();
            }
        });

        checkMagnetometer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                comprobarSensoresSeleccionados();
            }
        });

        checkHR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                comprobarSensoresSeleccionados();
            }
        });
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
        startActivity(intent);
    }

}