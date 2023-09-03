package com.example.lbs_app_for_poc;

import java.nio.ByteBuffer;

public class TCPhelpers {

    public static byte[] intToByteArray(int number) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(number);
        return buffer.array();
    }

}
