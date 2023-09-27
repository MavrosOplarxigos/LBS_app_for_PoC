package com.example.lbs_app_for_poc;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

public class TCPhelpers {

    public static void reverseByteArray(byte[] byteArray) {
        int left = 0;
        int right = byteArray.length - 1;
        while (left < right) {
            // Swap the elements at left and right positions
            byte temp = byteArray[left];
            byteArray[left] = byteArray[right];
            byteArray[right] = temp;

            // Move the pointers towards the center
            left++;
            right--;
        }
    }

    public static String byteArrayToDecimalString(byte[] byteArray) {
        StringBuilder decimalString = new StringBuilder();
        for (byte b : byteArray) {
            int decimalValue = b & 0xFF;  // Convert to unsigned decimal value
            decimalString.append(decimalValue).append(", ");
        }
        // Remove the trailing ", " if needed
        if (decimalString.length() > 2) {
            decimalString.setLength(decimalString.length() - 2);
        }
        return decimalString.toString();
    }

    public static String byteArrayToHexString(byte[] byteArray) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : byteArray) {
            hexString.append(String.format("\\x%02x", b & 0xFF));
        }
        return hexString.toString();
    }

    public static String retriveOwnIpAddressString(){
        try {
            NetworkInterface wlan0 = NetworkInterface.getByName("wlan0");
            List<InetAddress> all_addresses = Collections.list(wlan0.getInetAddresses());
            for (InetAddress addr : all_addresses) {
                if (addr.toString().contains(":")) {
                    // then it should be a MAC address
                    // TODO: find a better way to check if an address is a MAC-ADDRESS
                    continue;
                } else {
                    Log.d("MYIPR", "IP address found for wlan0 " + addr.toString());
                    String ans = addr.toString();
                    return ans.substring(1);
                }
            }
            Log.d("MYIPR", "Error couldn't retrieved wlan0 ip address");
            return null;
        }
        catch (Exception e){
            Log.d("MYIPR","Could not retrieve IP address on wlan0");
            e.printStackTrace();
            return null;
        }
    }

    public static int byteArrayToIntLittleEndian(byte[] byteArray) {
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    public static int byteArrayToIntBigEndian(byte[] byteArray) {
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        return buffer.getInt();
    }

    public static byte[] intToByteArray(int number) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(number);
        return buffer.array();
    }

    public static ByteArrayOutputStream receiveBuffedBytesNoLimit(DataInputStream dis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1000]; // 1000 bytes has worked with no issue so far (if not use smaller buffer)
        int bytesRead;
        int total_bytes = 0;
        while ((bytesRead = dis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            total_bytes += bytesRead;
            if (bytesRead < buffer.length) {
                Log.d("TCP server","Instance where we are not receiving anything!");
                break; // The buffer is not filled up that means we have reached the EOF
            }
            if (total_bytes > TCPServerControlClass.max_transmission_cutoff) {
                Log.d("TCP server","Transmission cutoff surpassed!");
                throw new RuntimeException();
            }
        }
        return baos;
    }

    public static byte [] buffRead(int numOfBytes, DataInputStream dis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int totalBytesRead = 0;
        while(true){
            int BytesLeft2Read = numOfBytes - totalBytesRead;
            int bufferSize = Math.min(100,BytesLeft2Read); // We read at most 100 bytes every time
            /*if (bufferSize != 100) {
                Log.d("BuffRead", "Size now is " + bufferSize + " and BytesLeft2Read = " + BytesLeft2Read + " out of " + numOfBytes );
            }*/
            byte [] buffer = new byte[bufferSize];
            int tempBytesRead = dis.read(buffer);
            if(tempBytesRead!=bufferSize) {
                if( tempBytesRead < bufferSize ){
                    // If they are less maybe we are still to receive them
                    Log.d("BuffRed","WARNING: We read less than what we expected!");
                }
                else {
                    throw new RuntimeException();
                }
            }
            totalBytesRead += tempBytesRead;
            baos.write(buffer,0,tempBytesRead);
            if(totalBytesRead == numOfBytes){
                break;
            }
        }
        byte [] readByteArray = baos.toByteArray();
        return readByteArray;
    }

    // TODO: Create the function to receive with limits (that is keep receiving until we reach the expected number of bytes)
    // public static byte [] receiveExactBytesSmall

}
