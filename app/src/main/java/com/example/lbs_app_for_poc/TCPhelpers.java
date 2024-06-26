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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        return byteArrayToDecimalStringFirst10(byteArray);
        /*StringBuilder decimalString = new StringBuilder();
        for (byte b : byteArray) {
            int decimalValue = b & 0xFF;  // Convert to unsigned decimal value
            decimalString.append(decimalValue).append(", ");
        }
        // Remove the trailing ", " if needed
        if (decimalString.length() > 2) {
            decimalString.setLength(decimalString.length() - 2);
        }
        return decimalString.toString();*/
    }

    /*public static String byteArrayToDecimalStringLast10(byte[] byteArray) {
        int limit = Math.min(10,byteArray.length);
        StringBuilder decimalString = new StringBuilder();
        byte [] fourstThing = new byte[limit];

        for(int i=0;i<limit;i++){
            fourstThing[i] = byteArray[(byteArray.length-1)-i];
        }

        for (byte b : fourstThing) {
            int decimalValue = b & 0xFF;  // Convert to unsigned decimal value
            decimalString.append(decimalValue).append(", ");
        }
        // Remove the trailing ", " if needed
        if (decimalString.length() > 2) {
            decimalString.setLength(decimalString.length() - 2);
        }
        return decimalString.toString();
    }*/

    public static String byteArrayToDecimalStringFirst10(byte[] byteArray) {
        int limit = Math.min(10,byteArray.length);
        StringBuilder decimalString = new StringBuilder();
        byte [] fourstThing = new byte[limit];
        for(int i=0;i<limit;i++) {
            fourstThing[i] = byteArray[i];
        }
        for (byte b : fourstThing) {
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
        // ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(number);
        return buffer.array();
    }

    public static byte[] intToByteArrayBigEndian(int number) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(number);
        return buffer.array();
    }

    public static byte[] intToByteArrayLittleEndian(int number) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(number);
        return buffer.array();
    }

    /*public static ByteArrayOutputStream receiveBuffedBytesNoLimit(DataInputStream dis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[100]; // 100 bytes
        int bytesRead;
        int total_bytes = 0;
        while ((bytesRead = dis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            total_bytes += bytesRead;
            if (bytesRead < buffer.length) {
                // But there are cases that it's not the last block so deprecate this function and make everything be
                Log.d("TCP server","Instance where we are not receiving anything! Must be last block.");
                break; // The buffer is not filled up that means we have reached the EOF
            }
            if (total_bytes > TCPServerControlClass.max_transmission_cutoff) {
                Log.d("TCP server","Transmission cutoff surpassed!");
                throw new RuntimeException();
            }
        }
        return baos;
    }*/

    public static byte [] buffRead(int numOfBytes, DataInputStream dis) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int totalBytesRead = 0;
        int waiting_time_given = 0;
        final int MIN_BUFF_SIZE = Math.min(100000,numOfBytes);
        byte [] buffer = new byte[MIN_BUFF_SIZE];

        while(true){

            int BytesLeft2Read = numOfBytes - totalBytesRead;
            if(BytesLeft2Read == 0){break;}

            int tempBytesRead = dis.read(buffer); // reading the bytes that we have now

            if(tempBytesRead > buffer.length){
                throw new RuntimeException("Impossible how could we have read more tempBytesReads than the size of our buffer!");
            }
            if(tempBytesRead == 0){
                // we have read no bytes which means we should give some chance to the other party to forward those
                if(waiting_time_given > 10){
                    throw new IOException("The BuffRead function has given 3 seconds to the other party to write in the buffer already! But it failed to do so!");
                }
                // If they are less maybe we are still to receive them
                Log.d("BuffRead","WARNING: We read less than what we expected! We will sleep for 2 seconds to give time to the other party to send the bytes");
                try {
                    Thread.sleep(300);
                    waiting_time_given++;
                }
                catch (Exception ex){
                    throw new IOException("Could not sleep in thread! That's weird!");
                }
                continue;
            }

            if(tempBytesRead != -1) {
                waiting_time_given = 0; // reset in case of re-checking the buffer
                totalBytesRead += tempBytesRead;
                baos.write(buffer, 0, tempBytesRead);
                if (totalBytesRead == numOfBytes) {
                    break;
                }
            }
            else{
                Log.d("BuffRead","If we are on a blocking kind of socket how can we get -1 then?");
                throw new RuntimeException("Bytes that were expected to be here are not!\n" +
                        "BUFFREADERR -> Expected: " + numOfBytes + " Got: " + totalBytesRead
                );
            }

        }
        byte [] readByteArray = baos.toByteArray();
        baos = null;
        return readByteArray;
    }

    /*public static String calculateSHA256Hash(byte[] byteArray) {
        try {
            // Create a MessageDigest instance for SHA-256
            MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");

            // Update the digest with the byte array
            sha256Digest.update(byteArray);

            // Calculate the hash as a byte array
            byte[] hashBytes = sha256Digest.digest();

            // Convert the byte array to a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            Log.d("Array Hashing Error:", "Could not has array " + TCPhelpers.byteArrayToDecimalStringFirst10(byteArray));
            e.printStackTrace();
            return null;
        }
    }*/

}
