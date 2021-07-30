package com.equinoxe.sensoresinternoswifisw;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.Socket;

public class EnvioDatosSocket extends Thread {
    private OutputStream outputStream = null;
    private Socket socket = null;
    private byte []data;
    private boolean bDataToSend = false;
    private boolean bStarted = false;
    private String sServer;
    private int iPuerto;
    private int iTamano;


    public EnvioDatosSocket(String sServer, int iPuerto, int iTamano) {
        this.sServer = sServer;
        this.iPuerto = iPuerto;
        this.iTamano = iTamano;
        data = new byte[iTamano];

        //connect();
    }

    public void setData(byte iDevice, byte []data) {
        synchronized (this) {
            this.data[0] = iDevice;
            System.arraycopy(data, 0, this.data, 1, iTamano-1);

            bDataToSend = true;
        }
    }

    public void setData(byte []data) {
        synchronized (this) {
            System.arraycopy(data, 0, this.data, 0, iTamano);

            bDataToSend = true;
        }
    }

    public boolean isConnected() {
        return (socket != null && !socket.isClosed() && socket.isConnected());
    }

    public boolean isStarted() {
        return bStarted;
    }

    public void connect() {
        try {
            socket = new Socket(sServer, iPuerto);
            Log.d("EnvioDatosSocket.java", "Socket creado");
            outputStream = socket.getOutputStream();
            Log.d("EnvioDatosSocket.java", "Stream creado");
        } catch (Exception e) {
            Log.d("EnvioDatosSocket.java", "Error conectando: " + e.toString());
        }
    }

    public void disconnect() {
        try {
            outputStream.flush();
            outputStream.close();
            socket.close();

            socket = null;
        } catch (Exception e) {
            Log.d("EnvioDatosSocket.java", "Error desconectando: " + e.toString());
        }
    }

    public boolean pendingDataToSend() {
        return bDataToSend;
    }

    @Override
    public void run() {
        connect();
        bStarted = true;
        while (!Thread.interrupted()) {
            try {
                if (bDataToSend && isConnected()) {
                    //Log.d("EnvioDatosSocket.java", "Enviando datos");
                    synchronized (this) {
                        outputStream.write(data);
                        outputStream.flush();
                        bDataToSend = false;
                    }
                }
            } catch (Exception e) {
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
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            disconnect();
        } catch (Exception e) {}

        super.finalize();
    }
}
