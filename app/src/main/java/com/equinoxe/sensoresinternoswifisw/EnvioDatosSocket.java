package com.equinoxe.sensoresinternoswifisw;

import android.util.Log;
import java.io.OutputStream;
import java.net.Socket;

public class EnvioDatosSocket extends Thread {
    private OutputStream outputStream = null;
    private Socket socket = null;
    private byte []data;
    private boolean bDataToSend = false;
    private String sServer;
    private int iPuerto;
    private int iTamano;


    public EnvioDatosSocket(String sServer, int iPuerto, int iTamano) {
        this.sServer = sServer;
        this.iPuerto = iPuerto;
        this.iTamano = iTamano;
        data = new byte[iTamano];
    }

    public void setData(byte iDevice, byte []data) {
        synchronized (this) {
            this.data[0] = iDevice;
            System.arraycopy(data, 0, this.data, 1, iTamano-1);

            bDataToSend = true;
        }
    }

    public boolean isConnected() {
        return (socket != null && !socket.isClosed() && socket.isConnected());
    }

    public void connect() {
        try {
            socket = new Socket(sServer, iPuerto);
            Log.d("EnvioDatosSocket.java", "Socket creado");
            outputStream = socket.getOutputStream();
            Log.d("EnvioDatosSocket.java", "Stream creado");
        } catch (Exception e) {
            Log.d("EnvioDatosSocket.java", "Error conectando: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
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
