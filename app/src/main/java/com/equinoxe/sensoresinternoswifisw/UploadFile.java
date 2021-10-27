package com.equinoxe.sensoresinternoswifisw;


import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.*;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UploadFile extends Thread {
    String sAddr;
    byte [] localDataToSend;
    int iPort;

    public UploadFile(String sAddr, int iPort, byte []dataToSend) {
        this.sAddr = sAddr;
        this.iPort = iPort;
        localDataToSend = new byte[dataToSend.length];
        System.arraycopy(dataToSend, 0, localDataToSend, 0, dataToSend.length);
    }

    @Override
    public void run() {
            //String sAddr = "192.168.43.168";
            //String sAddr = "192.168.1.34";
            //String sAddr = "192.168.177.229";
            FTPClient client = new FTPClient();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);

            try {
                InetAddress iAddr = InetAddress.getByName(sAddr);
                client.connect(iAddr, iPort);

                if (client.login("Equinoxe", "Frabela_1")) {
                    client.enterLocalPassiveMode();
                    client.setFileType(FTP.BINARY_FILE_TYPE);

                    String sFicheroRemoto = Build.MODEL + "/" + Build.MODEL + "_" + sdf.format(new Date()) + ".txt";

                    InputStream is = new ByteArrayInputStream(localDataToSend);
                    client.storeFile(sFicheroRemoto, is);
                }

                client.logout();
                client.disconnect();
            } catch (Exception e1) {
                Log.d("FTP", e1.toString());
                String sFichero = Environment.getExternalStorageDirectory() + "/FTPError.txt";

                try {
                    FileOutputStream fOut = new FileOutputStream(sFichero, true);
                    sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);
                    String sCadena = sdf.format(new Date()) + " - " + e1.getMessage() + "\n";
                    fOut.write(sCadena.getBytes());
                    fOut.close();
                } catch (Exception e2) {
                    Log.d("FTP", e2.toString());
                }
            }
    }
}
