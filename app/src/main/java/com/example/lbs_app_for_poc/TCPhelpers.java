package com.example.lbs_app_for_poc;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TCPhelpers {

    public static byte[] intToByteArray(int number) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(number);
        return buffer.array();
    }

    public static ByteArrayOutputStream receiveBuffedBytesNoLimit(DataInputStream dis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1000]; // 1000 bytes has worked with no issue so far
        int bytesRead;
        int total_bytes = 0;
        while ((bytesRead = dis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            total_bytes += bytesRead;
            if (bytesRead < buffer.length) {
                break; // The buffer is not filled up that means we have reached the EOF
            }
            if (total_bytes > TCPServerControlClass.max_transmission_cutoff) {
                Log.d("TCP server","Transmission cutoff surpassed!");
                throw new RuntimeException();
            }
        }
        return baos;
    }

}
