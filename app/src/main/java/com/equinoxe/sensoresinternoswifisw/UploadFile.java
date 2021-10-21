package com.equinoxe.sensoresinternoswifisw;


import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.*;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UploadFile extends Thread {
    //byte []addr = {(byte)150, (byte)214, 59, 23};
    String sAddr;
    //String sFicheroLocal;
    byte [] localDataToSend;
    int iPort;

    public UploadFile(String sAddr, int iPort, byte []dataToSend) {
        this.sAddr = sAddr;
        this.iPort = iPort;
        //this.sFicheroLocal = sFicheroLocal;
        localDataToSend = new byte[dataToSend.length];
        System.arraycopy(dataToSend, 0, localDataToSend, 0, dataToSend.length);
    }

    @Override
    public void run() {
        try  {
            //byte []addr = {(byte)192, (byte)168, (byte)177, (byte)229};
            //String sAddr = "192.168.43.168";
            //String sAddr = "192.168.1.34";
            String sAddr = "192.168.177.229";
            FTPClient client = new FTPClient();

            try {
                InetAddress iAddr = InetAddress.getByName(sAddr);
                client.connect(iAddr, iPort);

                if (client.login("Equinoxe", "Frabela_1")) {
                    client.enterLocalPassiveMode();
                    client.setFileType(FTP.BINARY_FILE_TYPE);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);
                    String sFicheroRemoto = Build.MODEL + "/" + Build.MODEL + "_" + sdf.format(new Date()) + ".txt";

                    InputStream is = new ByteArrayInputStream(localDataToSend);
                    client.storeFile(sFicheroRemoto, is);
                    //client.storeFile(sFicheroRemoto, new FileInputStream(sFicheroLocal));
                }

                client.logout();
                client.disconnect();
            } catch (Exception e) {
                Log.d("FTP", e.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}