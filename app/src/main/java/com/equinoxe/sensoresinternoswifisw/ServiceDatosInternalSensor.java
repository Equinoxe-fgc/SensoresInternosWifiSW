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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;


import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ServiceDatosInternalSensor extends Service implements SensorEventListener {
    private static final int INITIAL_SEND_MS = 30 * 1000;
    private static final int MUESTRAS_POR_SEGUNDO_GAME = 60;
    private static final int MUESTRAS_POR_SEGUNDO_FASTEST = 110;
    private final static boolean SENSORS_ON = true;
    private final static boolean SENSORS_OFF = false;

    boolean bAcelerometro, bGiroscopo, bMagnetometro, bHeartRate, bBarometer, bFastestON, bSendWifi, bThreshold;
    int iWindowSize, iSendPeriod;
    String sThresholds;
    boolean bVibrate;

    private SensorManager sensorManager;

    String sCadenaGiroscopo, sCadenaMagnetometro, sCadenaAcelerometro, sCadenaHeartRate, sCadenaBarometro;
    String sSubjectName;
    private ServiceHandler mServiceHandler;
    Timer timerUpdateData;
    Timer timerGrabarDatos;
    Timer timerSendBuffer;

    boolean bSendAccelerometro, bSendGiroscopo, bSendMagnetometro, bSendHeartRate, bSendBarometro;
    boolean bSendingData;
    boolean bSaveSensedData;

    DecimalFormat df;

    SimpleDateFormat sdf;
    FileOutputStream fOut;
    OutputStream fDataAcelerometro, fDataGiroscopo, fDataMagnetometro, fDataHR, fDataBarometro;

    long lNumMsgGiroscopo, lNumMsgMagnetometro, lNumMsgAcelerometro, lNumMsgHR, lNumMsgBarometro;
    long lNumMsgGiroscopoAnterior, lNumMsgMagnetometroAnterior, lNumMsgAcelerometroAnterior, lNumMsgBarometroAnterior;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    int iTamBuffer;
    SensorData []dataAccelerometer;
    SensorData []dataGyroscope;
    SensorData []dataMagnetometer;
    SensorData []dataBarometer;
    //SensorData []dataHeartRate;
    SensorData dataHeartRate = new SensorData();

    byte []dataToSend;

    int iSensorDelay;

    int iPosDataAccelerometer = 0;
    int iPosDataGiroscope = 0;
    int iPosDataMagnetometer = 0;
    int iPosDataBarometer = 0;
    //int iPosDataHeartRate = 0;

    boolean bCaidaDetectada;
    int iMuestrasDesdeCaidaDetectada;
    int []iNumUmbralesDetectados;
    float []fUmbrales;

    String sServer;
    int iPort;
    boolean bFTP;
    EnvioDatosSocket envioAsync;

    String sFicheroLocal;
    UploadFile upFile;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceDatosInternalSensor", HandlerThread.MIN_PRIORITY);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        Looper mServiceLooper = thread.getLooper();
        mServiceHandler = new com.equinoxe.sensoresinternoswifisw.ServiceDatosInternalSensor.ServiceHandler(mServiceLooper);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
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
                case Sensado.BAROMETER:
                    publishSensorValues(Sensado.BAROMETER, msg.arg2, sCadenaBarometro);
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

        sFicheroLocal = Environment.getExternalStorageDirectory() + "/TempFile.txt";

        //createNotificationChannel();
        lNumMsgGiroscopo = 0;
        lNumMsgMagnetometro = 0;
        lNumMsgAcelerometro = 0;
        lNumMsgBarometro = 0;
        //lNumMsgHR = 0;

        lNumMsgGiroscopoAnterior = 0;
        lNumMsgMagnetometroAnterior = 0;
        lNumMsgAcelerometroAnterior = 0;
        lNumMsgBarometroAnterior = 0;

        df = new DecimalFormat("###.##");

        bAcelerometro = intent.getBooleanExtra(getString(R.string.Accelerometer), false);
        bGiroscopo = intent.getBooleanExtra(getString(R.string.Gyroscope), false);
        bMagnetometro = intent.getBooleanExtra(getString(R.string.Magnetometer), false);
        bHeartRate = intent.getBooleanExtra(getString(R.string.HeartRate), false);
        bBarometer = intent.getBooleanExtra(getString(R.string.Barometer), false);

        SharedPreferences pref = getApplicationContext().getSharedPreferences("Settings", MODE_PRIVATE);

        sSubjectName = pref.getString("SubjectName", getResources().getText(R.string.defaultSubject).toString());
        sServer = pref.getString("server", getResources().getText(R.string.defaultServerIP).toString());
        iPort = pref.getInt("puerto", Integer.parseInt(getResources().getString(R.string.DirectSendPort)));
        bFTP = (iPort == 21);

        bFastestON = pref.getBoolean("FastON", false);
        bSendWifi = pref.getBoolean("Wifi", false);
        iWindowSize = pref.getInt("WindowSize", Integer.parseInt(getResources().getString(R.string.DefaultWindowsSize)));
        iSendPeriod = pref.getInt("SendPeriod", Integer.parseInt(getResources().getString(R.string.DefaultSendPeriod)));

        bThreshold = pref.getBoolean("Threshold_ONOFF", false);
        //fThreshold = pref.getFloat("Threshold", 2.5f);
        sThresholds = pref.getString("Thresholds", "2");
        procesaThresholds(sThresholds);
        bVibrate = pref.getBoolean("Vibrate", false);
        bSaveSensedData = pref.getBoolean("SaveSensedData", false);

        iSensorDelay = (bFastestON)?SensorManager.SENSOR_DELAY_FASTEST:SensorManager.SENSOR_DELAY_GAME;

        controlSensors(SENSORS_ON);
        controlHR(SENSORS_OFF);

        sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);

        createLogFile();
        if (bSaveSensedData)
            createSensedDataFiles();

        final TimerTask timerTaskGrabarDatos = new TimerTask() {
            public void run() {
                comprobarParadaSensores();
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

                if (bBarometer) {
                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg2 = 0;
                    msg.arg1 = Sensado.BAROMETER;
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
            dataGyroscope = new SensorData[iTamBuffer];
        if (bMagnetometro)
            dataMagnetometer = new SensorData[iTamBuffer];
        if (bBarometer)
            dataBarometer = new SensorData[iTamBuffer];
        /*if (bHeartRate)
            dataHeartRate = new SensorData[iTamBuffer];*/

        for (int i = 0; i < iTamBuffer; i++) {
            if (bAcelerometro)
                dataAccelerometer[i] = new SensorData();
            if (bGiroscopo)
                dataGyroscope[i] = new SensorData();
            if (bMagnetometro)
                dataMagnetometer[i] = new SensorData();
            if (bBarometer)
                dataBarometer[i] = new SensorData();
            /*if (bHeartRate)
                dataHeartRate[i] = new SensorData();*/
        }

        if (bSendWifi) {
            int iMultiplicador = 0;
            if (bAcelerometro)
                iMultiplicador++;
            if (bGiroscopo)
                iMultiplicador++;
            if (bMagnetometro)
                iMultiplicador++;
            if (bBarometer)
                iMultiplicador++;

            // Se pide memoria para mandar un buffer con todos los datos de los sensores y un dato de HR
            int iTamDataToSend = (iMultiplicador * iTamBuffer * SensorData.BYTES) + SensorData.BYTES;

            dataToSend = new byte[(iMultiplicador * iTamBuffer * SensorData.BYTES) + SensorData.BYTES];
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        final TimerTask timerTaskSendBuffer = new TimerTask() {
            @Override
            public void run() {
                controlSensors(SENSORS_OFF);

                // Si está elegido el HR se activa para tomar una muestra y enviar los buffers cuando llegue
                // En otro caso se envían los buffers
                if (bHeartRate)
                    controlHR(SENSORS_ON);
                else
                    enviarBuffers();
            }
        };

        Timer timerCheckEnSendSensorsData;
        final TimerTask timerTaskCheckSendSensorsData = new TimerTask() {
            @Override
            public void run() {
                if (envioAsync != null) {
                    if (envioAsync.isStarted() && !envioAsync.pendingDataToSend()) {
                        envioAsync.disconnect();
                        envioAsync.interrupt();
                        envioAsync = null;
                    }
                }
            }
        };

        if (bSendWifi) {
            // Si el periodo es 0 se envía de forma continua
            if (iSendPeriod == 0) {
                if (!bFTP) {
                    envioAsync = new EnvioDatosSocket(sServer, iPort, SensorData.BYTES + 1);
                    envioAsync.start();
                }
            } else {    // Si no es 0 es porque hay que enviar cada cierto tiempo y hay que controlarlo
                timerSendBuffer = new Timer();
                timerSendBuffer.scheduleAtFixedRate(timerTaskSendBuffer, INITIAL_SEND_MS, iSendPeriod * 1000);

                timerCheckEnSendSensorsData = new Timer();
                timerCheckEnSendSensorsData.scheduleAtFixedRate(timerTaskCheckSendSensorsData, INITIAL_SEND_MS, 5000);
            }
        }

        bSendAccelerometro = false;
        bSendGiroscopo = false;
        bSendMagnetometro = false;
        bSendHeartRate = false;
        bSendBarometro = false;
        bSendingData = false;

        bCaidaDetectada = false;

        return START_NOT_STICKY;
    }

    private void comprobarParadaSensores() {
        boolean bReiniciar = false;

        bReiniciar = (lNumMsgAcelerometro == lNumMsgAcelerometroAnterior);
        bReiniciar |= (bGiroscopo && lNumMsgGiroscopo == lNumMsgGiroscopoAnterior);
        bReiniciar |= (bMagnetometro && lNumMsgMagnetometro == lNumMsgMagnetometroAnterior);
        bReiniciar |= (bBarometer && lNumMsgBarometro == lNumMsgBarometroAnterior);

        if (bReiniciar) {
            // Se envía un mensaje para que se pare el servicio y se vuelva a iniciar
            publishSensorValues(Sensado.ERROR, Sensado.ERROR, "");
            vibrate(1);
            /*controlSensors(SENSORS_OFF);
            vibrate(1);
            controlSensors(SENSORS_ON);*/

        }
        else {
            lNumMsgAcelerometroAnterior = lNumMsgAcelerometro;
            lNumMsgGiroscopoAnterior = lNumMsgGiroscopo;
            lNumMsgMagnetometroAnterior = lNumMsgMagnetometro;
            lNumMsgBarometroAnterior = lNumMsgBarometro;
        }
    }


    private void createSensedDataFiles() {
        if (bAcelerometro)
            createSensorFile(Sensado.ACELEROMETRO);
        if (bGiroscopo)
            createSensorFile(Sensado.GIROSCOPO);
        if (bMagnetometro)
            createSensorFile(Sensado.MAGNETOMETRO);
        if (bHeartRate)
            createSensorFile(Sensado.HEART_RATE);
        if (bBarometer)
            createSensorFile(Sensado.BAROMETER);
    }

    private void createSensorFile(int iSensor) {
        String sFichero = Environment.getExternalStorageDirectory() + "/" + sSubjectName + "_" + Build.MODEL + "_" +  sdf.format(new Date()) + "_";
        try {
            switch (iSensor) {
                case Sensado.ACELEROMETRO:
                    sFichero += "A.dat";
                    fDataAcelerometro = new BufferedOutputStream(new FileOutputStream(sFichero));
                    break;
                case Sensado.GIROSCOPO:
                    sFichero += "G.dat";
                    fDataGiroscopo = new BufferedOutputStream(new FileOutputStream(sFichero));
                    break;
                case Sensado.MAGNETOMETRO:
                    sFichero += "M.dat";
                    fDataMagnetometro = new BufferedOutputStream(new FileOutputStream(sFichero));
                    break;
                case Sensado.BAROMETER:
                    sFichero += "B.dat";
                    fDataBarometro = new BufferedOutputStream(new FileOutputStream(sFichero));
                    break;
                case Sensado.HEART_RATE:
                    sFichero += "HR.dat";
                    fDataHR = new BufferedOutputStream(new FileOutputStream(sFichero));
                    break;
            }
        } catch (Exception e) {
            Toast.makeText(this, getResources().getString(R.string.ERROR_FICHERO), Toast.LENGTH_LONG).show();
        }
    }

    private void createLogFile() {
        String currentDateandTime = sdf.format(new Date());
        String sFichero = Environment.getExternalStorageDirectory() + "/" + sSubjectName + "_" + Build.MODEL + "_" +  currentDateandTime + "_Log.txt";

        try {
            fOut = new FileOutputStream(sFichero, false);
            String sModel = Build.MODEL;
            sModel = sModel.replace(" ", "_");
            String sCadena = sModel + " A:" + bAcelerometro +
                                      " G:" + bGiroscopo +
                                      " M:" + bMagnetometro +
                                      " B:" + bBarometer +
                                      " HR:" + bHeartRate +
                                      " Fastest:" + bFastestON +
                                      " SendWifi:" + bSendWifi;
            if (bSendWifi)
                sCadena += "(" + iSendPeriod + ")";

            sCadena += " TH:" + bThreshold;
            if (bThreshold) {
                sCadena += "(" + sThresholds + ")";
            }
            sCadena += " " + currentDateandTime + "\n";
            fOut.write(sCadena.getBytes());
            fOut.flush();
        } catch (Exception e) {
            Toast.makeText(this, getResources().getString(R.string.ERROR_FICHERO), Toast.LENGTH_LONG).show();
        }
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
        if (!bFTP) {
            envioAsync = new EnvioDatosSocket(sServer, iPort, dataToSend.length);
            envioAsync.start();
        }

        //BufferedOutputStream fileOut;
        try {
            //fileOut = new BufferedOutputStream(new FileOutputStream(sFicheroLocal));

            int iPosData = 0;

            if (bAcelerometro) {
                for (int i = 0; i < iTamBuffer; i++) {
                    System.arraycopy(dataAccelerometer[iPosDataAccelerometer].getBytes(), 0, dataToSend, iPosData, SensorData.BYTES);

                    iPosData += SensorData.BYTES;

                    iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
                }
            }
            if (bGiroscopo) {
                for (int i = 0; i < iTamBuffer; i++) {
                    System.arraycopy(dataGyroscope[iPosDataGiroscope].getBytes(), 0, dataToSend, iPosData, SensorData.BYTES);

                    iPosData += SensorData.BYTES;

                    iPosDataGiroscope = (iPosDataGiroscope + 1) % iTamBuffer;
                }
            }
            if (bMagnetometro) {
                for (int i = 0; i < iTamBuffer; i++) {
                    System.arraycopy(dataMagnetometer[iPosDataMagnetometer].getBytes(), 0, dataToSend, iPosData, SensorData.BYTES);

                    iPosData += SensorData.BYTES;

                    iPosDataMagnetometer = (iPosDataMagnetometer + 1) % iTamBuffer;
                }
            }
            if (bBarometer) {
                for (int i = 0; i < iTamBuffer; i++) {
                    System.arraycopy(dataBarometer[iPosDataBarometer].getBytes(), 0, dataToSend, iPosData, SensorData.BYTES);

                    iPosData += SensorData.BYTES;

                    iPosDataBarometer = (iPosDataBarometer + 1) % iTamBuffer;
                }
            }
            if (bHeartRate) {
                System.arraycopy(dataHeartRate.getBytes(), 0, dataToSend, iPosData, SensorData.BYTES);
            }

            /*try {
                fileOut.write(dataToSend);
                fileOut.close();
            } catch (IOException e) {
                Toast.makeText(this, "Error writing data to file", Toast.LENGTH_SHORT).show();
            }*/

            if (!bFTP) {
                envioAsync.setData(dataToSend);
            } else {
                upFile = new UploadFile(sServer, iPort, dataToSend);
                upFile.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        controlSensors(SENSORS_ON);
    }

    private void controlSensors(boolean bSensors_ON) {
        if (bAcelerometro) {
            Sensor sensorAcelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorAcelerometro, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorAcelerometro);
        }
        if (bGiroscopo) {
            Sensor sensorGiroscopo = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorGiroscopo, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorGiroscopo);
        }
        if (bMagnetometro) {
            Sensor sensorMagnetometro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorMagnetometro, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorMagnetometro);
        }
        if (bBarometer) {
            Sensor sensorBarometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorBarometer, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorBarometer);
        }
        /*if (bHeartRate) {
            Sensor sensorHeartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (bSensors_ON)
                sensorManager.registerListener(this, sensorHeartRate, iSensorDelay);
            else
                sensorManager.unregisterListener(this, sensorHeartRate);
        }*/
    }

    public void controlHR(boolean bSensor_ON) {
        Sensor sensorHeartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (bSensor_ON)
            sensorManager.registerListener(this, sensorHeartRate, iSensorDelay);
        else
            sensorManager.unregisterListener(this, sensorHeartRate);
    }

    public void grabarMedidas() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        try {
            String sCadena = sdf.format(new Date()) + ":" +
                    batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + ":";
            //fOut.write(sCadena.getBytes());

            long lNumMsg = lNumMsgGiroscopo + lNumMsgMagnetometro + lNumMsgAcelerometro + lNumMsgBarometro + lNumMsgHR;

            sCadena += "(" + lNumMsgAcelerometro + ")";
            sCadena += "(" + lNumMsgGiroscopo + ")";
            sCadena += "(" + lNumMsgMagnetometro + ")";
            sCadena += "(" + lNumMsgBarometro + ")";
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
            case Sensor.TYPE_PRESSURE:
                if (bBarometer) {
                    lNumMsgBarometro++;
                    sCadenaBarometro = "B: " + df.format(event.values[0]);
                    procesarDatosSensados(Sensor.TYPE_PRESSURE, event.timestamp, event.values);
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

    private void vibrate(int iUmbralDetectado) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(250 + 250*iUmbralDetectado, VibrationEffect.DEFAULT_AMPLITUDE));
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

                        if (bVibrate)
                            vibrate(iUmbralDetectado);
                    }
                    if (bCaidaDetectada) {
                        iMuestrasDesdeCaidaDetectada++;
                        if (iMuestrasDesdeCaidaDetectada == iTamBuffer / 2) {
                            if (bSendWifi) {
                                controlSensors(SENSORS_OFF);
                                enviarBuffers();
                            }
                            bCaidaDetectada = false;
                        }
                    }
                }

                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_ACCELEROMETER, dataAccelerometer[iPosDataAccelerometer].getBytes());

                if (bSaveSensedData)
                    try {
                        fDataAcelerometro.write(dataAccelerometer[iPosDataAccelerometer].getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                iPosDataAccelerometer = (iPosDataAccelerometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_GYROSCOPE:
                dataGyroscope[iPosDataGiroscope].setData(timeStamp, values);
                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_GYROSCOPE, dataGyroscope[iPosDataGiroscope].getBytes());

                if (bSaveSensedData)
                    try {
                        fDataGiroscopo.write(dataGyroscope[iPosDataGiroscope].getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                iPosDataGiroscope = (iPosDataGiroscope + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                dataMagnetometer[iPosDataMagnetometer].setData(timeStamp, values);
                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_MAGNETIC_FIELD, dataMagnetometer[iPosDataMagnetometer].getBytes());

                if (bSaveSensedData)
                    try {
                        fDataMagnetometro.write(dataMagnetometer[iPosDataMagnetometer].getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                iPosDataMagnetometer = (iPosDataMagnetometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_PRESSURE:
                dataBarometer[iPosDataBarometer].setData(timeStamp, values);
                if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_PRESSURE, dataBarometer[iPosDataBarometer].getBytes());

                if (bSaveSensedData)
                    try {
                        fDataBarometro.write(dataBarometer[iPosDataBarometer].getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                iPosDataBarometer = (iPosDataBarometer + 1) % iTamBuffer;
                break;
            case Sensor.TYPE_HEART_RATE:
                //dataHeartRate[iPosDataHeartRate].setData(timeStamp, values);
                dataHeartRate.setData(timeStamp, values);

                /*if (bSendWifi && iSendPeriod == 0)
                    envioAsync.setData((byte) Sensor.TYPE_HEART_RATE, dataHeartRate[iPosDataHeartRate].getBytes());*/

                if (bSendWifi) {
                    controlHR(SENSORS_OFF);
                    enviarBuffers();
                }

                /*if (bSaveSensedData)
                    try {
                        fDataHR.write(dataHeartRate[iPosDataHeartRate].getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                iPosDataHeartRate = (iPosDataHeartRate + 1) % iTamBuffer;*/
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

        controlSensors(SENSORS_OFF);

        timerUpdateData.cancel();
        timerGrabarDatos.cancel();
        try {
            timerSendBuffer.cancel();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        try {
            grabarMedidas();
            fOut.close();

            if (bSaveSensedData) {
                if (bAcelerometro)
                    fDataAcelerometro.close();
                if (bGiroscopo)
                    fDataGiroscopo.close();
                if (bMagnetometro)
                    fDataMagnetometro.close();
                if (bHeartRate)
                    fDataHR.close();
                if (bBarometer)
                    fDataBarometro.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (bSendWifi && iSendPeriod == 0)
                envioAsync.disconnect();
                envioAsync.interrupt();
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
