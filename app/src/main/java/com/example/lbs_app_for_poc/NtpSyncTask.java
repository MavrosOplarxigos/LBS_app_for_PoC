package com.example.lbs_app_for_poc;

import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import com.instacart.library.truetime.SntpClient;
import com.instacart.library.truetime.TrueTime;

import java.io.IOException;

public class NtpSyncTask extends AsyncTask<Void, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Void... params) {
        try {

            TrueTime.build()
                    .withNtpHost("pool.ntp.org")
                    .withConnectionTimeout(10000)
                    .initialize();

            if (TrueTime.isInitialized()) {
                long currentTime = TrueTime.now().getTime();
                long systemTime = System.currentTimeMillis();
                long offset = currentTime - systemTime;

                // can't set the system's time (PROTECTED PERMISSION)
                // SystemClock.setCurrentTimeMillis(systemTime + offset);

                // we are instead going to save the offset for future tasks
                InterNodeCrypto.NTP_TIME_OFFSET = offset;
                return true;
            }

            /*
                SntpClient sntpClient = new SntpClient();
                if (sntpClient.requestTime("pool.ntp.org", TIMEOUT_MILLISECONDS)) {
                    long networkTime = sntpClient.getNtpTime();
                    // Use the network time for your app's internal purposes
                } else {
                    // NTP synchronization failed, handle accordingly
                }
            */

        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        // NTP synchronization complete, handle the result
        if (success) {
            // Time synchronization successful
            Log.d("NTP SYNC","SUCCESS! Offsett = " + InterNodeCrypto.NTP_TIME_OFFSET);
        } else {
            // Time synchronization failed
            Log.d("NTP SYNC","FAIL!");
        }
    }
}

