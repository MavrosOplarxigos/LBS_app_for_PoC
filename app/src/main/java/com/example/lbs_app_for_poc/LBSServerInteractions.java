package com.example.lbs_app_for_poc;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class LBSServerInteractions {

    public static JSONObject execute_api_call(String call_string){

        try {
            JSONObject answer = null;

            // Here the integrity of the answer when returned from the intermediate node
            // can only be checked if the public key used every time by the LBS is the same
            // and thus it can be known by both parties (search initiator node and intermediate node)
            // In our case we have a new HTTPS connection every time and thus a different handshake takes place

            URL api_call_url = new URL(call_string);
            HttpsURLConnection connection = (HttpsURLConnection) api_call_url.openConnection();
            connection.setRequestMethod("GET");

            // Here we can print the certificate chain of the server
            // TODO: Discover why when trying to just print the certificate chain the connection establishment fails
            /*
            String server_certificat_chain = "";
            for(int i=0;i< connection.getServerCertificates().length; i++){
                server_certificat_chain += "\n";
                server_certificat_chain += connection.getServerCertificates()[i].toString();
            }
            Log.d("API CALL EXEC",server_certificat_chain);
            */

            // TODO: As the intermediate node see if I can extract the encrypted version of the answer and forward that to the search initiator
            // so along with the my own keys it can verify the integrity of the answer.
            // TODO: Ask Hongyu if here I could simply fabricate the action of the LBS signing the response it sends
            // to the intermediate node and therefore the integrity of the answer could also be proven
            // because with the HTTP connection and a BufferedReader it doesn't seem that I can returned the encrypted
            // answer. Instead it seems that it is read in plaintext.

            // from reasearch on the documentation we can't really request that the answer text we receive
            // is encrypted https://developers.google.com/maps/documentation/places/web-service/search-nearby

            int responseCode = connection.getResponseCode();
            Log.d("API CALL EXEC","HTTPS Response Code Received = " + responseCode);

            if(responseCode == HttpURLConnection.HTTP_OK ){
                Log.d("API CALL EXEC","HTTPS Response is OK!");
                BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(connection.getInputStream()) );
                StringBuilder sb = new StringBuilder();
                String line;
                while( (line=bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                answer = new JSONObject(sb.toString());
            }
            else{
                Log.d("API CALL EXEC ERROR","RESPONSE NOT OK FROM HTTP SERVER");
            }

            return answer;
        }
        catch (Exception e){
            e.printStackTrace();
            Log.d("API CALL EXEC","Could not retrieve JSONobject");
            return null;
        }

    }

}
