package com.equinoxe.sensoresinternoswifisw;


import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UploadFile extends AsyncTask {
    byte []addr = {(byte)150, (byte)214, 59, 23};
    FTPClient client = new FTPClient();

    @Override
    protected Object doInBackground(Object[] objects) {
        try {
            client.connect(InetAddress.getByAddress(addr), Integer.parseInt((String)objects[1]));
            client.login("Equinoxe", "Frabela_1");

            client.enterLocalActiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK);
            //String sFicheroLocal = Environment.getExternalStorageDirectory() + "/TempFile.txt";
            String sFicheroLocal = (String) objects[2];
            String sFicheroRemoto = Environment.getExternalStorageDirectory() + "/" + Build.MODEL + "_" +  sdf.format(new Date()) + ".txt";

            client.storeUniqueFile(sFicheroRemoto, new FileInputStream(sFicheroLocal));

        } catch (Exception e) {
            Log.d("FTP", e.toString());
            return false;
        }

        return false;
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);

        try {
            client.disconnect();
        } catch (IOException e) {}
    }
}
