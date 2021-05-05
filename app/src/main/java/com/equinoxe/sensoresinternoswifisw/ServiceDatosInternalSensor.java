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
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ServiceDatosInternalSensor extends Service implements SensorEventListener {
    private static final int MUESTRAS_POR_SEGUNDO_GAME = 60;
    private static final int MUESTRAS_POR_SEGUNDO_FASTEST = 110;
    private final static boolean SENSORS_ON = true;
    private final static boolean SENSORS_OFF = false;

    boolean bAcelerometro, bGiroscopo, bMagnetometro, bHeartRate, bFastestON, bSendWifi, bThreshold;
    int iWindowSize, iSendPeriod;
    String sThresholds;

    private SensorManager sensorManager;
    private Sensor sensorAcelerometro, sensorGiroscopo, sensorMagnetometro, sensorHeartRate;

    String sCadenaGiroscopo, sCadenaMagnetometro, sCadenaAcelerometro, sCadenaHeartRate;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    Timer timerUpdateData;
    Timer timerGrabarDatos;
    Timer timerSendBuffer;
    Timer timerSendAcelerometro;
    TimerTask timerTaskSendAcelerometro = null;

    int iDataAccelerometroSent, iDataGiroscopoSent, iDataMagnetometroSent, iDataHeartRateSent;
    boolean bSendAccelerometro, bSendGiroscopo, bSendMagnetometro, bSendHeartRate;
    boolean bSendingData;

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
    int iPosDataHeartRate = 0;

    boolean bCaidaDetectada;
    int iMuestrasDesdeCaidaDetectada;
    int []iNumUmbralesDetectados;
    float []fUmbrales;

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
        iWindowSize = pref.getInt("WindowSize", 3000);
        iSendPeriod = pref.getInt("SendPeriod", 600);

        bThreshold = pref.getBoolean("Threshold_ONOFF", false);
        //fThreshold = pref.getFloat("Threshold", 2.5f);
        sThresholds = pref.getString("Thresholds", "2");
        procesaThresholds(sThresholds);

        final int iSensorDelay = (bFastestON)?SensorManager.SENSOR_DELAY_FASTEST:SensorManager.SENSOR_DELAY_GAME;

        controlSensors(SENSORS_ON, iSensorDelay);

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
                                 bAcelerometro + " " + bGiroscopo + " " + bMagnetometro + " " + bHeartRate + " " +
                                 bFastestON + " " + bSendWifi + " " + bThreshold;
                if (bThreshold) {
                    sCadena += "(" + sThresholds + ")";
                }
                sCadena += " " + currentDateandTime + "\n";
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

        iTamBuffer = (bFastestON)?(MUESTRAS_POR_SEGUNDO_FASTEST*iWindowSize/1000):(MUESTRAS_POR_SEGUNDO_GAME*iWindowSize/1000);
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

            if (iSendPeriod == 0) {
                envioAsync.connect();
            }
        }

        final TimerTask timerTaskSendBuffer = new TimerTask() {
            @Override
            public void run() {
                controlSensors(SENSORS_OFF, 0);
                enviarBuffers();
            }
        };

        if (bSendWifi && iSendPeriod != 0) {
            timerSendBuffer = new Timer();
            timerSendBuffer.scheduleAtFixedRate(timerTaskSendBuffer, iSendPeriod * 1000, iSendPeriod * 1000);
        }


        timerTaskSendAcelerometro = new TimerTask() {
            @Override
            public void run() {
                if (!bSendingData)
                    return;

                if (bSendAccelerometro) {
                    envioAsync.setData((byte) Sensor.TYPE_ACCELEROMETER, dataAccelerometer[iPosDataAccelerometer].getBytes());
                    iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
                    iDataAccelerometroSent++;

                    if (iDataAccelerometroSent == iTamBuffer) {
                        bSendAccelerometro = false;
                    }
                } else if (bSendGiroscopo) {
                    envioAsync.setData((byte) Sensor.TYPE_GYROSCOPE, dataGiroscope[iPosDataGiroscope].getBytes());
                    iPosDataGiroscope = (iPosDataGiroscope + 1) % iTamBuffer;
                    iDataGiroscopoSent++;

                    if (iDataGiroscopoSent == iTamBuffer) {
                        bSendGiroscopo = false;
                    }
                } else if (bSendMagnetometro) {
                    envioAsync.setData((byte) Sensor.TYPE_MAGNETIC_FIELD, dataMagnetometer[iPosDataMagnetometer].getBytes());
                    iPosDataMagnetometer = (iPosDataMagnetometer + 1) % iTamBuffer;
                    iDataMagnetometroSent++;

                    if (iDataMagnetometroSent == iTamBuffer) {
                        bSendMagnetometro = false;
                    }
                } else if (bSendHeartRate) {
                    envioAsync.setData((byte) Sensor.TYPE_HEART_RATE, dataHeartRate[iPosDataHeartRate].getBytes());
                    iPosDataHeartRate = (iPosDataHeartRate + 1) % iTamBuffer;
                    iDataHeartRateSent++;

                    if (iDataHeartRateSent == iTamBuffer) {
                        bSendHeartRate = false;
                    }
                }

                if (bSendingData && !bSendAccelerometro && !bSendGiroscopo && !bSendMagnetometro && !bSendHeartRate) {
                    bSendingData = false;
                    envioAsync.disconnect();
                    controlSensors(SENSORS_ON, iSensorDelay);
                }
            }
        };

        bSendAccelerometro = false;
        bSendGiroscopo = false;
        bSendMagnetometro = false;
        bSendHeartRate = false;
        bSendingData = false;

        bCaidaDetectada = false;

        timerSendAcelerometro = new Timer();
        timerSendAcelerometro.scheduleAtFixedRate(timerTaskSendAcelerometro, 1, 20);

        return START_NOT_STICKY;
    }

    private void procesaThresholds(String sThresholds) {
        String []sUmbrales = sThresholds.split(" ");

        iNumUmbralesDetectados = new int[sUmbrales.length];
        for (int i = 0; i < sUmbrales.length; i++)
            iNumUmbralesDetectados[i] = 0;

        fUmbrales = new float[sUmbrales.length];
        for (int i = 0; i < sUmbrales.length; i++) {
            fUmbrales[i] = Float.parseFloat(sUmbrales[i]);
        }
    }

    private void enviarBuffers() {
        envioAsync.connect();

        if (bAcelerometro) {
            iDataAccelerometroSent = 0;
            bSendAccelerometro = true;
        }
        if (bGiroscopo) {
            iDataGiroscopoSent = 0;
            bSendGiroscopo = true;
        }
        if (bMagnetometro) {
            iDataMagnetometroSent = 0;
            bSendMagnetometro = true;
        }
        if (bHeartRate) {
            iDataHeartRateSent = 0;
            bSendHeartRate = true;
        }
        bSendingData = true;
    }

    private void controlSensors(boolean bSensors_ON, int iSensorDelay) {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (bAcelerometro) {
            sensorAcelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorAcelerometro, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorAcelerometro);
        }
        if (bGiroscopo) {
            sensorGiroscopo = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorGiroscopo, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorGiroscopo);
        }
        if (bMagnetometro) {
            sensorMagnetometro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorMagnetometro, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorMagnetometro);
        }
        if (bHeartRate) {
            sensorHeartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorHeartRate, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorHeartRate);
        }
    }

    public void grabarMedidas() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        try {
            String sCadena = sdf.format(new Date()) + ":" +
                    batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + ":";
            fOut.write(sCadena.getBytes());

            long lNumMsg = lNumMsgGiroscopo + lNumMsgMagnetometro + lNumMsgAcelerometro + lNumMsgHR;

            sCadena += "(" + lNumMsgAcelerometro + ")";
            sCadena += "(" + lNumMsgGiroscopo + ")";
            sCadena += "(" + lNumMsgMagnetometro + ")";
            sCadena += "(" + lNumMsgHR + ")";

            sCadena += "(" + lNumMsg + ")";

            if (bThreshold) {
                sCadena += "(";
                for (int i = 0; i < iNumUmbralesDetectados.length; i++) {
                    if (i < iNumUmbralesDetectados.length - 1)
                        sCadena += iNumUmbralesDetectados[i] + ",";
                    else
                        sCadena += iNumUmbralesDetectados[i] + ")";
                }
            }

            sCadena += "\n";

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

    // Devuelve -1 si no se sobrepasa ningún umbral, en otro caso se devuelve la posición del umbral dentro
    // del array de umbrales
    private int detectaUmbral(double dModule) {
        int iDetectado;

        for (iDetectado = fUmbrales.length - 1; iDetectado >= 0; iDetectado--) {
            if (dModule > fUmbrales[iDetectado])
                break;
        }

        return iDetectado;
    }

    private void procesarDatosSensados(int iSensor, long timeStamp, float []values) {
        switch (iSensor) {
            case Sensor.TYPE_ACCELEROMETER:
                dataAccelerometer[iPosDataAccelerometer].setData(timeStamp, values);

                if (bThreshold) {
                    double dModule = dataAccelerometer[iPosDataAccelerometer].calculateModuleGravity();
                    int iUmbralDetectado = detectaUmbral(dModule);
                    if (!bCaidaDetectada && iUmbralDetectado != -1) {
                        bCaidaDetectada = true;
                        iNumUmbralesDetectados[iUmbralDetectado]++;
                        iMuestrasDesdeCaidaDetectada = -1;  // Es -1 porque la primera vez se le suma 1 más adelante
                    }
                    if (bCaidaDetectada) {
                        iMuestrasDesdeCaidaDetectada++;
                        if (iMuestrasDesdeCaidaDetectada == iTamBuffer / 2) {
                            if (bSendWifi) {
                                controlSensors(SENSORS_OFF, 0);
                                enviarBuffers();
                            }
                            bCaidaDetectada = false;
                        }
                    }
                }

                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_ACCELEROMETER, dataAccelerometer[iPosDataAccelerometer].getBytes());

                iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_GYROSCOPE:
                dataGiroscope[iPosDataGiroscope].setData(timeStamp, values);
                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_GYROSCOPE, dataGiroscope[iPosDataGiroscope].getBytes());
                iPosDataGiroscope = (iPosDataGiroscope + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                dataMagnetometer[iPosDataMagnetometer].setData(timeStamp, values);
                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_MAGNETIC_FIELD, dataMagnetometer[iPosDataMagnetometer].getBytes());
                iPosDataMagnetometer = (iPosDataMagnetometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_HEART_RATE:
                dataHeartRate[iPosDataHeartRate].setData(timeStamp, values);
                if (bSendWifi && iSendPeriod == 0)
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

        controlSensors(SENSORS_OFF, 0);

        timerUpdateData.cancel();
        timerGrabarDatos.cancel();
        try {
            timerSendBuffer.cancel();
        } catch (NullPointerException e) {}

        try {
            grabarMedidas();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (bSendWifi && iSendPeriod == 0)
                envioAsync.disconnect();
                envioAsync.finalize();
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

}
