package com.equinoxe.sensoresinternoswifisw;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

public class Options extends FragmentActivity {
    private TextView txtServer;
    private TextView txtPuerto;
    private CheckBox checkBoxFastON, checkBoxWifi, checkThreshold;
    private TextView txtWindowSize, txtSendPeriod, txtThreshold;
    private LinearLayout layoutWindowSize, layoutSendPeriod, layoutThreshold, layoutServerData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        txtServer = findViewById(R.id.editTextServerIP);
        txtPuerto = findViewById(R.id.editTextPortNumber);
        checkBoxFastON = findViewById(R.id.checkBoxFastestON);
        checkBoxWifi = findViewById(R.id.checkBoxWifi);
        txtWindowSize = findViewById(R.id.editTextWindowSize);
        txtSendPeriod = findViewById(R.id.editTextSendPeriod);
        txtThreshold = findViewById(R.id.editTextThreshold);
        checkThreshold = findViewById(R.id.checkBoxThreshold);

        layoutWindowSize = findViewById(R.id.layoutWindow);
        layoutSendPeriod = findViewById(R.id.layoutPeriod);
        layoutThreshold = findViewById(R.id.layoutThreshold);
        layoutServerData = findViewById(R.id.layoutServerData);

        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        txtServer.setText(pref.getString("server", "127.0.0.1"));
        String sCadena = "" + pref.getInt("puerto", 8000);
        txtPuerto.setText(sCadena);
        checkBoxFastON.setChecked(pref.getBoolean("FastON", false));

        checkBoxWifi.setChecked(pref.getBoolean("Wifi", false));
        sCadena = "" + pref.getInt("WindowSize", 3000);
        txtWindowSize.setText(sCadena);
        sCadena = "" + pref.getInt("SendPeriod", 600);
        txtSendPeriod.setText(sCadena);

        checkThreshold.setChecked(pref.getBoolean("Threshold_ONOFF", false));
        txtThreshold.setText(Float.toString(pref.getFloat("Threshold", 2.0f)));

        if (checkBoxWifi.isChecked()) {
            layoutSendPeriod.setVisibility(View.VISIBLE);
            layoutServerData.setVisibility(View.VISIBLE);
        } else {
            layoutSendPeriod.setVisibility(View.GONE);
            layoutServerData.setVisibility(View.GONE);
        }

        if (checkThreshold.isChecked()) {
            layoutThreshold.setVisibility(View.VISIBLE);
            layoutSendPeriod.setVisibility(View.GONE);
        } else {
            layoutThreshold.setVisibility(View.GONE);
        }

        checkBoxWifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    layoutServerData.setVisibility(View.VISIBLE);
                    if (!checkThreshold.isChecked())
                        layoutSendPeriod.setVisibility(View.VISIBLE);
                } else {
                    layoutSendPeriod.setVisibility(View.GONE);
                    layoutServerData.setVisibility(View.GONE);
                }
            }
        });

        checkThreshold.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    layoutThreshold.setVisibility(View.VISIBLE);
                    layoutSendPeriod.setVisibility(View.GONE);
                } else {
                    layoutThreshold.setVisibility(View.GONE);
                    if (checkBoxWifi.isChecked())
                        layoutSendPeriod.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void onClickSaveSettings(View v) {
        switch (comprobarSettingsOK()) {
            case 0:
                SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putString("server", txtServer.getText().toString());
                editor.putInt("puerto", Integer.parseInt(txtPuerto.getText().toString()));
                editor.putBoolean("FastON", checkBoxFastON.isChecked());
                editor.putBoolean("Wifi", checkBoxWifi.isChecked());
                editor.putBoolean("Threshold_ONOFF", checkThreshold.isChecked());
                try {
                    editor.putInt("WindowSize", Integer.parseInt(txtWindowSize.getText().toString()));
                    editor.putInt("SendPeriod", Integer.parseInt(txtSendPeriod.getText().toString()));
                    //editor.putFloat("Threshold", Float.parseFloat(txtThreshold.getText().toString()));
                    editor.putString("Thresholds", txtThreshold.getText().toString());
                    editor.apply();

                    Toast.makeText(this, getResources().getText(R.string.Options_saved), Toast.LENGTH_SHORT).show();

                    finish();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, getResources().getText(R.string.Options_Error), Toast.LENGTH_SHORT).show();
                }
                break;
            case 1:
                Toast.makeText(this, getResources().getText(R.string.Incorrect_IP), Toast.LENGTH_SHORT).show();
                break;
            case 2:
                Toast.makeText(this, getResources().getText(R.string.Incorrect_Port), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private int comprobarSettingsOK() {
        String sServer = txtServer.getText().toString();
        if (!Patterns.IP_ADDRESS.matcher(sServer).matches())
            return 1;

        try {
            int iPuerto = Integer.parseInt(txtPuerto.getText().toString());
            if (iPuerto < 0 || iPuerto > 65535)
                return 2;
        } catch (Exception e) {
            return 2;
        }

        return 0;
    }
}