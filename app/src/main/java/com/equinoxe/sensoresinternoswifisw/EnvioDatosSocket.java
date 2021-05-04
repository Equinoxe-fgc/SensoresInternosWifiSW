package com.equinoxe.sensoresinternoswifisw;

import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EnvioDatosSocket extends Thread {
    private OutputStream outputStream = null;
    //private FileOutputStream fOut = null;
    private Socket socket = null;
    private SimpleDateFormat sdf;
    private byte []data;
    private boolean bDataToSend = false;
    private String sServer;
    private int iPuerto;
    private int iTamano;
    private String sCadena;
    private boolean bConnected;


    public EnvioDatosSocket(String sServer, int iPuerto, int iTamano) {
        this.sServer = sServer;
        this.iPuerto = iPuerto;
        this.iTamano = iTamano;
        data = new byte[iTamano];

        sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        bConnected = false;
        /*try {
            fOut = new FileOutputStream(Environment.getExternalStorageDirectory() + "/LOG_Envio.txt", true);
            sCadena = sdf.format(new Date()) + " - Inicio sesión\n";
            fOut.write(sCadena.getBytes());
        } catch (IOException e) {
            Log.d("EnvioDatosSocket.java", "Error en constructor");
        }*/
    }

    public void setData(byte iDevice, byte []data) {
        synchronized (this) {
            this.data[0] = iDevice;
            System.arraycopy(data, 0, this.data, 1, iTamano-1);

            bDataToSend = true;
        }
    }

    public void setBufferData (byte iDevice, SensorData []data, int iTamData) {

    }

    /*public void finishSend() {
        try {
            outputStream.close();
            socket.close();
        } catch (Exception e) {
                try {
                    if (!socket.isClosed())
                        socket.close();
                } catch (Exception ee) {}
        }
    }*/

    public boolean isConnected() {
        return (socket != null && !socket.isClosed() && socket.isConnected());
    }

    public void connect() {
        try {
            socket = new Socket(sServer, iPuerto);
            Log.d("EnvioDatosSocket.java", "Socket creado");
            outputStream = socket.getOutputStream();
            Log.d("EnvioDatosSocket.java", "Stream creado");

            bConnected = true;
        } catch (Exception e) {
            Log.d("EnvioDatosSocket.java", "Error conectando: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            //fOut.close();
            bConnected = false;
            outputStream.flush();
            outputStream.close();
            socket.close();

            socket = null;
        } catch (Exception e) {
            Log.d("EnvioDatosSocket.java", "Error desconectando: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                    //synchronized (this) {
                        try {
                            if (bDataToSend) {
                                //Log.d("EnvioDatosSocket.java", "Enviando datos");
                                synchronized (this) {
                                    outputStream.write(data);
                                    bDataToSend = false;
                                }
                            }
                        } catch (Exception e) {
                            bConnected = false;

                            /*sCadena = sdf.format(new Date()) + " While Exception " + e.getMessage() + "\n";
                            fOut.write(sCadena.getBytes());*/
                            Log.d("EnvioDatosSocket.java", "Excepcion: " + e.getMessage());
                            e.printStackTrace();

                            // Se cierran las conexiones
                            disconnect();

                            /*sCadena = sdf.format(new Date()) + " - Reconexión\n";
                            fOut.write(sCadena.getBytes());*/
                            // Se vuelve a crear la conexión
                            connect();

                        }
                    //}
            }

            /*outputStream.close();
            fOut.close();
            sCadena = sdf.format(new Date()) + " Socket cerrado\n";*/
        } catch (Exception e) {
            Log.d("EnvioDatosSocket.java", "Excepcion externa: " + e.getMessage());
            bConnected = false;

            /*sCadena = sdf.format(new Date()) + " Error creación socket " + e.getMessage() + "\n";
            try {
                fOut.write(sCadena.getBytes());
            } catch (IOException ee) {}*/
            //Log.d("EnvioDatosSocket.java", "Error al crear socket o stream");
        }

        /*try {
            fOut.write(sCadena.getBytes());
            fOut.close();
        } catch (Exception e) {}*/
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            disconnect();
        } catch (Exception e) {}

        //finishSend();
        super.finalize();
    }
}
