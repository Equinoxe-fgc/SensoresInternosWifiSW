package com.equinoxe.sensoresinternoswifisw;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

public class Options extends FragmentActivity {
    private TextView txtServer;
    private TextView txtPuerto;
    private CheckBox checkBoxFastON, checkBoxWifi, checkThreshold, checkVibrate, checkSaveSensedData;
    private TextView txtWindowSize, txtSendPeriod, txtThreshold, txtSubjectName;
    private LinearLayout layoutSendPeriod, layoutThreshold, layoutServerData;
    private RadioButton rbFTPSend, rbDirectSend;

    boolean bFTPSend;

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
        txtSubjectName = findViewById(R.id.editTextSubjectName);
        checkThreshold = findViewById(R.id.checkBoxThreshold);
        checkVibrate = findViewById(R.id.checkBoxVibrate);
        checkSaveSensedData = findViewById(R.id.checkBoxSaveSensedData);

        //layoutWindowSize = findViewById(R.id.layoutWindow);
        layoutSendPeriod = findViewById(R.id.layoutPeriod);
        layoutThreshold = findViewById(R.id.layoutThreshold);
        layoutServerData = findViewById(R.id.layoutServerData);

        rbDirectSend = findViewById(R.id.rbDirectSend);
        rbFTPSend = findViewById(R.id.rbFTPSend);

        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        txtSubjectName.setText(pref.getString("SubjectName", getResources().getText(R.string.defaultSubject).toString()));
        txtServer.setText(pref.getString("server", getResources().getText(R.string.defaultServerIP).toString()));
        String sCadena = "" + pref.getInt("puerto", 8000);
        txtPuerto.setText(sCadena);
        checkBoxFastON.setChecked(pref.getBoolean("FastON", false));

        checkBoxWifi.setChecked(pref.getBoolean("Wifi", false));
        bFTPSend = pref.getBoolean("FTPSend", true);
        if (bFTPSend)
            rbFTPSend.toggle();

        sCadena = "" + pref.getInt("WindowSize", 3000);
        txtWindowSize.setText(sCadena);
        sCadena = "" + pref.getInt("SendPeriod", 600);
        txtSendPeriod.setText(sCadena);

        checkThreshold.setChecked(pref.getBoolean("Threshold_ONOFF", false));
        txtThreshold.setText(pref.getString("Thresholds", "2.0"));
        checkVibrate.setChecked(pref.getBoolean("Vibrate", false));

        checkSaveSensedData.setChecked(pref.getBoolean("SaveSensedData", false));

        if (checkBoxWifi.isChecked()) {
            layoutSendPeriod.setVisibility(View.VISIBLE);
            layoutServerData.setVisibility(View.VISIBLE);
            if (bFTPSend)
                txtPuerto.setText(getResources().getText(R.string.FTPPort));
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

        checkBoxWifi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutServerData.setVisibility(View.VISIBLE);
                if (!checkThreshold.isChecked())
                    layoutSendPeriod.setVisibility(View.VISIBLE);
            } else {
                layoutSendPeriod.setVisibility(View.GONE);
                layoutServerData.setVisibility(View.GONE);
            }
        });

        checkThreshold.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutThreshold.setVisibility(View.VISIBLE);
                layoutSendPeriod.setVisibility(View.GONE);
            } else {
                layoutThreshold.setVisibility(View.GONE);
                if (checkBoxWifi.isChecked())
                    layoutSendPeriod.setVisibility(View.VISIBLE);
            }
        });
    }

    public void onClickSaveSettings(View v) {
        switch (comprobarSettingsOK()) {
            case 0:
                SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();

                editor.putString("SubjectName", txtSubjectName.getText().toString());
                editor.putString("server", txtServer.getText().toString());
                editor.putInt("puerto", Integer.parseInt(txtPuerto.getText().toString()));
                editor.putBoolean("FastON", checkBoxFastON.isChecked());
                editor.putBoolean("Wifi", checkBoxWifi.isChecked());
                editor.putBoolean("FTP", rbFTPSend.isChecked());
                editor.putBoolean("Threshold_ONOFF", checkThreshold.isChecked());

                editor.putInt("WindowSize", Integer.parseInt(txtWindowSize.getText().toString()));
                editor.putInt("SendPeriod", Integer.parseInt(txtSendPeriod.getText().toString()));
                editor.putString("Thresholds", txtThreshold.getText().toString());
                editor.putBoolean("Vibrate", checkVibrate.isChecked());
                editor.putBoolean("SaveSensedData", checkSaveSensedData.isChecked());
                editor.apply();

                Toast.makeText(this, getResources().getText(R.string.Options_saved), Toast.LENGTH_SHORT).show();

                finish();
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