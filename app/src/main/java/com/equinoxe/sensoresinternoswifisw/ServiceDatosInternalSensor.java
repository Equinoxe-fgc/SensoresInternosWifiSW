package com.equinoxe.sensoresinternoswifisw;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ServiceDatosInternalSensor extends Service implements SensorEventListener {
    private static int MUESTRAS_POR_SEGUNDO_GAME = 60;
    private static int MUESTRAS_POR_SEGUNDO_FASTEST = 110;
    private static int SEGUNDOS_VENTANA = 5;

    private boolean bAcelerometro, bGiroscopo, bMagnetometro, bHeartRate, bFastestON, bSendWifi;

    private SensorManager sensorManager;
    private Sensor sensorAcelerometro, sensorGiroscopo, sensorMagnetometro, sensorHeartRate;

    String sCadenaGiroscopo, sCadenaMagnetometro, sCadenaAcelerometro, sCadenaHeartRate;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    Timer timerUpdateData;
    Timer timerGrabarDatos;

    DecimalFormat df;

    SimpleDateFormat sdf;
    FileOutputStream fOut;

    long lNumMsgGiroscopo, lNumMsgMagnetometro, lNumMsgAcelerometro, lNumMsgHR;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    int iTamBuffer;
    SensorData []dataAccelerometer;
    SensorData []dataGiroscope;
    SensorData []dataMagnetometer;
    SensorData []dataHeartRate;
    int iPosDataAccelerometer = 0;
    int iPosDataGiroscope = 0;
    int iPosDataMagnetometer = 0;
    int iPosDataHeartRate;

    String sServer;
    int iPort;
    EnvioDatosSocket envioAsync;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceDatosInternalSensor", HandlerThread.MIN_PRIORITY);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new com.equinoxe.sensoresinternoswifisw.ServiceDatosInternalSensor.ServiceHandler(mServiceLooper);
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.arg1) {
                case Sensado.GIROSCOPO:
                    publishSensorValues(Sensado.GIROSCOPO, msg.arg2, sCadenaGiroscopo);
                    break;
                case Sensado.MAGNETOMETRO:
                    publishSensorValues(Sensado.MAGNETOMETRO, msg.arg2, sCadenaMagnetometro);
                    break;
                case Sensado.ACELEROMETRO:
                    publishSensorValues(Sensado.ACELEROMETRO, msg.arg2, sCadenaAcelerometro);
                    break;
                case Sensado.HEART_RATE:
                    publishSensorValues(Sensado.HEART_RATE, msg.arg2, sCadenaHeartRate);
                    break;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::WakelockInterno");
            if (wakeLock.isHeld())
                wakeLock.release();
            wakeLock.acquire();
        } catch (NullPointerException e) {
            Log.e("NullPointerException", "ServiceDatosInternalSensor - onStartCommand");
        }

        //createNotificationChannel();
        lNumMsgGiroscopo = 0;
        lNumMsgMagnetometro = 0;
        lNumMsgAcelerometro = 0;
        lNumMsgHR = 0;

        df = new DecimalFormat("###.##");

        bAcelerometro = intent.getBooleanExtra(getString(R.string.Accelerometer), false);
        bGiroscopo = intent.getBooleanExtra(getString(R.string.Gyroscope), false);
        bMagnetometro = intent.getBooleanExtra(getString(R.string.Magnetometer), false);
        bHeartRate = intent.getBooleanExtra(getString(R.string.HeartRate), false);

        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);
        sServer = pref.getString("server", "127.0.0.1");
        iPort = pref.getInt("puerto", 8000);
        bFastestON = pref.getBoolean("FastON", false);
        bSendWifi = pref.getBoolean("Wifi", false);

        int iSensorDelay = (bFastestON)?SensorManager.SENSOR_DELAY_FASTEST:SensorManager.SENSOR_DELAY_GAME;

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (bAcelerometro) {
            sensorAcelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensorAcelerometro, iSensorDelay);
        }
        if (bGiroscopo) {
            sensorGiroscopo = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sensorManager.registerListener(this, sensorGiroscopo, iSensorDelay);
        }
        if (bMagnetometro) {
            sensorMagnetometro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            sensorManager.registerListener(this, sensorMagnetometro, iSensorDelay);
        }
        if (bHeartRate) {
            sensorHeartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            sensorManager.registerListener(this, sensorHeartRate, iSensorDelay);
        }


            sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);

            File file;
            int iNumFichero = 0;
            String sFichero;
            do {
                sFichero = Environment.getExternalStorageDirectory() + "/" + Build.MODEL + "_" +  iNumFichero + "_Interno.txt";
                file = new File(sFichero);
                iNumFichero++;
            } while (file.exists());

            try {
                String currentDateandTime = sdf.format(new Date());

                fOut = new FileOutputStream(sFichero, false);
                String sModel = Build.MODEL;
                sModel = sModel.replace(" ", "_");
                String sCadena = sModel + " " +
                                 bAcelerometro + " " + bGiroscopo + " " + bMagnetometro + " " + bHeartRate + " " + bFastestON + " " + currentDateandTime + "\n";
                fOut.write(sCadena.getBytes());
                fOut.flush();
            } catch (Exception e) {
                Toast.makeText(this, getResources().getString(R.string.ERROR_FICHERO), Toast.LENGTH_LONG).show();
            }

            final TimerTask timerTaskGrabarDatos = new TimerTask() {
                public void run() {
                    grabarMedidas();
                }
            };

            timerGrabarDatos = new Timer();
            timerGrabarDatos.scheduleAtFixedRate(timerTaskGrabarDatos, Sensado.TIEMPO_GRABACION_DATOS, Sensado.TIEMPO_GRABACION_DATOS);

        final TimerTask timerTaskUpdateData = new TimerTask() {
            public void run() {
                if (bAcelerometro) {
                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg1 = Sensado.ACELEROMETRO;
                    msg.arg2 = 0;
                    mServiceHandler.sendMessage(msg);
                }

                if (bGiroscopo) {
                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg2 = 0;
                    msg.arg1 = Sensado.GIROSCOPO;
                    mServiceHandler.sendMessage(msg);
                }

                if (bMagnetometro) {
                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg2 = 0;
                    msg.arg1 = Sensado.MAGNETOMETRO;
                    mServiceHandler.sendMessage(msg);
                }

                if (bHeartRate) {
                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg2 = 0;
                    msg.arg1 = Sensado.HEART_RATE;
                    mServiceHandler.sendMessage(msg);
                }
            }
        };

        timerUpdateData = new Timer();
        timerUpdateData.scheduleAtFixedRate(timerTaskUpdateData, Sensado.AMBIENT_INTERVAL_MS / 2, Sensado.AMBIENT_INTERVAL_MS);

        iTamBuffer = (bFastestON)?(MUESTRAS_POR_SEGUNDO_FASTEST*SEGUNDOS_VENTANA):(MUESTRAS_POR_SEGUNDO_GAME*SEGUNDOS_VENTANA);
        if (bAcelerometro)
            dataAccelerometer = new SensorData[iTamBuffer];
        if (bGiroscopo)
            dataGiroscope = new SensorData[iTamBuffer];
        if (bMagnetometro)
            dataMagnetometer = new SensorData[iTamBuffer];
        if (bHeartRate)
            dataHeartRate = new SensorData[iTamBuffer];

        for (int i = 0; i < iTamBuffer; i++) {
            if (bAcelerometro)
                dataAccelerometer[i] = new SensorData();
            if (bGiroscopo)
                dataGiroscope[i] = new SensorData();
            if (bMagnetometro)
                dataMagnetometer[i] = new SensorData();
            if (bHeartRate)
                dataHeartRate[i] = new SensorData();
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        if (bSendWifi) {
            envioAsync = new EnvioDatosSocket(sServer, iPort, SensorData.BYTES + 1);
            envioAsync.start();
        }

        return START_NOT_STICKY;
    }

    public void grabarMedidas() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        try {
            String sCadena = sdf.format(new Date()) + ":" +
                    batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + ":";
            fOut.write(sCadena.getBytes());

            long lNumMsg = lNumMsgGiroscopo + lNumMsgMagnetometro + lNumMsgAcelerometro + lNumMsgHR;
            sCadena = "(" + lNumMsg + ",0)";

            sCadena += "(" + lNumMsgAcelerometro + ",0)";
            sCadena += "(" + lNumMsgGiroscopo + ",0)";
            sCadena += "(" + lNumMsgMagnetometro + ",0)";
            sCadena += "(" + lNumMsgHR + ",0)";

            sCadena += "(" + lNumMsg + ",0)\n";
            fOut.write(sCadena.getBytes());
            fOut.flush();
        } catch (Exception e) {
            Log.e("Fichero de resultados", e.getMessage(), e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                if (bAcelerometro) {
                    lNumMsgAcelerometro++;
                    sCadenaAcelerometro = "A: " + df.format(event.values[0]) + " "
                            + df.format(event.values[1]) + " "
                            + df.format(event.values[2]);
                    procesarDatosSensados(Sensor.TYPE_ACCELEROMETER, event.timestamp, event.values);
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                if (bGiroscopo) {
                    lNumMsgGiroscopo++;
                    sCadenaGiroscopo = "G: " + df.format(event.values[0]) + " "
                            + df.format(event.values[1]) + " "
                            + df.format(event.values[2]);
                    procesarDatosSensados(Sensor.TYPE_GYROSCOPE, event.timestamp, event.values);
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (bMagnetometro) {
                    lNumMsgMagnetometro++;
                    sCadenaMagnetometro = "M: " + df.format(event.values[0]) + " "
                            + df.format(event.values[1]) + " "
                            + df.format(event.values[2]);
                    procesarDatosSensados(Sensor.TYPE_MAGNETIC_FIELD, event.timestamp, event.values);
                }
                break;
            case Sensor.TYPE_HEART_RATE:
                if (bHeartRate) {
                    lNumMsgHR++;
                    sCadenaHeartRate = "HR: " + df.format(event.values[0]) + " - " + lNumMsgHR;
                    procesarDatosSensados(Sensor.TYPE_HEART_RATE, event.timestamp, event.values);
                }
                break;
        }
    }

    private void procesarDatosSensados(int iSensor, long timeStamp, float []values) {
        switch (iSensor) {
            case Sensor.TYPE_ACCELEROMETER:
                dataAccelerometer[iPosDataAccelerometer].setData(timeStamp, values);
                double dModule = dataAccelerometer[iPosDataAccelerometer].calculateModule();

                if (bSendWifi)
                    envioAsync.setData((byte) Sensor.TYPE_ACCELEROMETER, dataAccelerometer[iPosDataAccelerometer].getBytes());
                /*SensorData dataPrueba = new SensorData();
                float[] f = new float[]{0.0f, 0.0f, 1.0f};

                dataPrueba.setData(timeStamp, f);
                envioAsync.setData((byte) 1, dataPrueba.getBytes());*/

                iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_GYROSCOPE:
                dataGiroscope[iPosDataGiroscope].setData(timeStamp, values);
                if (bSendWifi)
                    envioAsync.setData((byte) Sensor.TYPE_GYROSCOPE, dataGiroscope[iPosDataGiroscope].getBytes());
                iPosDataGiroscope = (iPosDataGiroscope + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                dataMagnetometer[iPosDataMagnetometer].setData(timeStamp, values);
                if (bSendWifi)
                    envioAsync.setData((byte) Sensor.TYPE_MAGNETIC_FIELD, dataMagnetometer[iPosDataMagnetometer].getBytes());
                iPosDataMagnetometer = (iPosDataMagnetometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_HEART_RATE:
                dataHeartRate[iPosDataHeartRate].setData(timeStamp, values);
                if (bSendWifi)
                    envioAsync.setData((byte) Sensor.TYPE_HEART_RATE, dataHeartRate[iPosDataHeartRate].getBytes());
                iPosDataHeartRate = (iPosDataHeartRate + 1) % iTamBuffer;
                break;
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void publishSensorValues(int iSensor, int iDevice, String sCadena) {
        Intent intent = new Intent(Sensado.NOTIFICATION);
        intent.putExtra("Sensor", iSensor);
        intent.putExtra("Device", iDevice);
        intent.putExtra("Cadena", sCadena);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            timerUpdateData.cancel();
            timerGrabarDatos.cancel();
            grabarMedidas();
            fOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (bSendWifi)
                envioAsync.finalize();
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }


        if (bAcelerometro) {
            sensorManager.unregisterListener(this, sensorAcelerometro);
        }
        if (bGiroscopo) {
            sensorManager.unregisterListener(this, sensorGiroscopo);
        }
        if (bMagnetometro) {
            sensorManager.unregisterListener(this, sensorMagnetometro);
        }
        if (bHeartRate) {
            sensorManager.unregisterListener(this, sensorHeartRate);
        }

        wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

}
