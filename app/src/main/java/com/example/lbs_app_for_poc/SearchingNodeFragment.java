package com.example.lbs_app_for_poc;

import static java.lang.Math.min;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;

import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.lbs_app_for_poc.databinding.FragmentSecondBinding;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.Feature;
import com.google.maps.android.data.Layer;
import com.google.maps.android.data.geojson.GeoJsonFeature;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonPoint;
import com.google.maps.android.geometry.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.HttpsURLConnection;

public class SearchingNodeFragment extends Fragment implements OnMapReadyCallback {

    private FragmentSecondBinding binding;
    private MapView mMapView;
    private GoogleMap mMap=null;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    private Button search_button;
    private EditText search_keyword_input;
    public GeoJsonLayer results_layer = null;
    private MapSearchItem msi; // The fundamental object that defines the search as soon as the search button is pressed

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        mMap = googleMap;

        // now let's configure the map style
        // First we set the map style: This is used to hide features (POIs and Transit)
        // that doesn't mean that the GoogleMap doesn't load them in the background proactively
        // Google could track where the user looks at the map since in order for the map tiles to appear
        // the GoogleMap carries out API calls in the background to download the maps tiles
        // From reading the documentation there was no way to cache a certain region and the load it to the GoogleMap object
        // as one can do in Google Maps (the default maps application of Google).
        // Since this application is just designed for PoC we can make the argument that somebody could find another tiling
        // api which allows for controlled caching of a certain area. Also the search keyword and request are not visible to the GoogleMap
        // rather it's the GeoJSON layer that is applied on to it. Still one can make the case that Google could infer the search based on
        // on the GeoJSON layer applied but for the sake of PoC we don't really care about that since as mentioned before one can use a different
        // map object to achieve the same (and if that map object allows for controlled caching and ensures that the GeoJSON layer applied is not
        // leaked back to the tiling service then that's a solution one can use).
        try {
            boolean style_set = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.no_poi));
            if(!style_set){
                Log.d("MapConfig","The map style could not be loaded");
            }
        } catch (Resources.NotFoundException e){
            Log.d("MapConfig","Map style file was not found to apply!");
        }

        search_keyword_input.setVisibility(View.VISIBLE);
        search_keyword_input.setHint("Search keyword");
        search_keyword_input.setText("");
        search_keyword_input.setBackgroundColor(Color.WHITE);
        search_keyword_input.setSingleLine();

        search_keyword_input.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        if(i == EditorInfo.IME_ACTION_DONE || i==EditorInfo.IME_ACTION_GO){
                            msi.keyword = search_keyword_input.getText().toString();
                            // removing whitespaces
                            msi.keyword = msi.keyword.replaceAll("\\s","");
                            search_keyword_input.clearFocus();
                        }
                        return false;
                    }
                }
        );

        search_keyword_input.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        msi.keyword = search_keyword_input.getText().toString();
                        // removing whitespaces
                        msi.keyword = msi.keyword.replaceAll("\\s","");
                    }
                }
        );

        // Move Camera to the initial position and zoom in
        LatLng kth_kista = new LatLng(59.4045797, 17.950208);
        CameraPosition initial_camera_position = new CameraPosition.Builder()
                .target(kth_kista)
                .zoom((float)(13))
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(initial_camera_position));

        mMap.setOnCameraMoveListener(
                new GoogleMap.OnCameraMoveListener() {
                    @Override
                    /*
                    Check out: https://developers.google.com/maps/documentation/android-sdk/views
                    for better understanding of the terminology used.
                     */
                    public void onCameraMove() {

                        // in case there is a zoom or unzoom the search diameter must be updated
                        update_search_diameter();

                        // updating the search location based on the new target of the map
                        msi.map_center = mMap.getCameraPosition().target;

                    }
                }
        );

        mMap.setOnMapClickListener(
                new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng latLng) {
                        Log.d("ON MAP CLICK","entered function");
                        if(search_keyword_input.hasFocus()){
                            Log.d("ON MAP CLICK","the edit text has focus!");
                            // search_keyword_input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.onEditorAction(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.clearFocus();
                        }
                    }
                }
        );

        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setMyLocationButtonEnabled(false);
        uiSettings.setMapToolbarEnabled(false);

        update_search_diameter();

    }

    void update_search_diameter(){

        VisibleRegion viewPort = mMap.getProjection().getVisibleRegion();
        double viewport_width = SphericalUtil.computeDistanceBetween(viewPort.nearLeft,viewPort.nearRight);

        msi.search_diameter = min(15000,(int)(viewport_width));
        if(viewport_width <= MapSearchItem.MAXIMUM_SEARCH_DIAMETER){
            search_button.setActivated(true);
            search_button.setClickable(true);
            search_button.setEnabled(true);
        }
        else {
            search_button.setActivated(false);
            search_button.setClickable(false);
            search_button.setEnabled(false);
        }

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonSecond.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(SearchingNodeFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        });

        Button back_button = view.findViewById(R.id.button_second);
        back_button.setVisibility(View.INVISIBLE);
        back_button.setActivated(false);

        // allowing for HTTPS connections
        network_permit();

        search_button = view.findViewById(R.id.button_search);
        search_button.setActivated(false);
        search_button = view.findViewById(R.id.button_search);
        search_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if(mMap == null){
                            return;
                        }

                        // we must have a peer to forward the request to
                        if(ConnectivityConfiguration.current_peer_cert == null){
                            Log.d("SEARCH CLICK","No peer to forward the request to!");
                            Toast.makeText(getContext(), "No peer to forward the request to!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if(ConnectivityConfiguration.my_client_socket.isClosed()){
                            Log.d("SEARCH CLICK","The peer closed the connection!");
                            Toast.makeText(getContext(), "The peer closed the connection!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if(msi.keyword.isEmpty()){
                            Log.d("SEARCH CLICK","The search keyword is undefined");
                            Toast.makeText(getContext(), "Please specify a keyword first", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if(search_keyword_input.hasFocus()){
                            search_keyword_input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.clearFocus();
                        }

                        // OK so the map is already prepared so now we can carry out the search
                        // TODO: Reduce the number of bytes that are send to the server by sending a string that only contains the fields needed
                        // TODO: rather than the entire URL (modify MapSearchItem class)
                        String api_call_string = "";
                        try {
                            api_call_string = msi.apicall();
                            Log.d("API CALL STRING CREATION SUCCESS",api_call_string);
                        }
                        catch (PackageManager.NameNotFoundException e){
                            e.printStackTrace();
                            Log.d("API CALL STRING CREATION ERROR","Can't get meta-data and thus Places API key!");
                            return;
                        }

                        // Now we need to instead of carrying out the request ourselves establish connectivity to the server and send msi as a byte array or string
                        // 3) CLIENT MESSAGE: SENT MESSAGE TO THE SERVER (QUERY)

                        // In this case we are sending a query so the format is the following:
                        // [QUERY] | [API_CALL_ENC_BYTES_LENGTH] | [API_CALL_ENC_BYTES] | [API_CALL_SIGNED_BYTES]

                        byte [] queryBytesClientQuery = "QUERY".getBytes();
                        byte [] APICallBytesClientQuery = api_call_string.getBytes();

                        byte [] APICallEncryptedBytesClientQuery;
                        try {
                            APICallEncryptedBytesClientQuery = InterNodeCrypto.encryptWithPeerKey(APICallBytesClientQuery,ConnectivityConfiguration.current_peer_cert);
                        } catch (NoSuchPaddingException e) {
                            throw new RuntimeException(e);
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        } catch (InvalidKeyException e) {
                            throw new RuntimeException(e);
                        } catch (IllegalBlockSizeException e) {
                            throw new RuntimeException(e);
                        } catch (BadPaddingException e) {
                            throw new RuntimeException(e);
                        }

                        byte [] APICallBytesSignedClientQuery;
                        try {
                            APICallBytesSignedClientQuery = InterNodeCrypto.signPrivateKeyByteArray(APICallEncryptedBytesClientQuery);
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        } catch (NoSuchProviderException e) {
                            throw new RuntimeException(e);
                        } catch (InvalidKeyException e) {
                            throw new RuntimeException(e);
                        } catch (SignatureException e) {
                            throw new RuntimeException(e);
                        }

                        byte [] APICallEncryptedBytesClientQueryLength = ("" + APICallEncryptedBytesClientQuery.length).getBytes();
                        // 256
                        Log.d("TCP client","The size of the query bytes should be " + new String(APICallEncryptedBytesClientQueryLength,StandardCharsets.UTF_8) );

                        ByteArrayOutputStream baosClientQuery = new ByteArrayOutputStream();
                        try {
                            baosClientQuery.write(queryBytesClientQuery);
                            baosClientQuery.write(TCPServerThread.transmission_del);
                            baosClientQuery.write(APICallEncryptedBytesClientQueryLength);
                            baosClientQuery.write(TCPServerThread.transmission_del);
                            baosClientQuery.write(APICallEncryptedBytesClientQuery);
                            baosClientQuery.write(TCPServerThread.transmission_del);
                            baosClientQuery.write(APICallBytesSignedClientQuery);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        byte [] ClientQuery = baosClientQuery.toByteArray();
                        // 523
                        Log.d("TCP client","The size of the entire query message in bytes should be " + ClientQuery.length );

                        try {
                            ConnectivityConfiguration.outputStream.write(ClientQuery);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        Log.d("TCP CLIENT","The query has been sent to the Server!");

                        // ---------------------------- POINT OF DISASTER -------------------------------------

                        // 4.1) SERVER RESPONSE DECLARATION: RECEIVE THE RESPONSE SIZE IN BYTES
                        // [RESPONSE] | [RESPONSE SIZE IN BYTES]

                        ByteArrayOutputStream baosServerResponseDeclaration;
                        try {
                            baosServerResponseDeclaration = new ByteArrayOutputStream();
                            byte[] bufferServerResponseDeclaration = new byte[1000];
                            int bytesReadServerResponseDeclaration;
                            int total_bytesServerResponseDeclaration = 0;
                            while( (bytesReadServerResponseDeclaration = ConnectivityConfiguration.inputStream.read(bufferServerResponseDeclaration)) != -1 ) {
                                Log.d("TCP CLIENT","Now read " + bytesReadServerResponseDeclaration + " bytes!");
                                baosServerResponseDeclaration.write(bufferServerResponseDeclaration, 0, bytesReadServerResponseDeclaration);
                                total_bytesServerResponseDeclaration += bytesReadServerResponseDeclaration;
                                if (bytesReadServerResponseDeclaration < bufferServerResponseDeclaration.length) {
                                    break; // The buffer is not filled up that means we have reached the EOF
                                }
                                if (total_bytesServerResponseDeclaration > TCPServerThread.max_transmission_cutoff) {
                                    Log.d("TCP CLIENT","The maximum transmission cutoff is reached!");
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        byte [] bytesServerResponseDeclartion = baosServerResponseDeclaration.toByteArray();

                        Log.d("TCP CLIENT","Received Server Response Declaration!");

                        // SEPARATING THE FIELDS OF SERVER RESPONSE DECLARATION

                        // First let's read the prefix RESPONSE
                        int ci = 0; // current index bytesServerResponseDeclaration
                        int tempci = ci;

                        // [RESPONSE]
                        ByteArrayOutputStream baosServerResponseDeclarationResponse = new ByteArrayOutputStream();
                        for(int i=ci;(i < bytesServerResponseDeclartion.length) && ((char)( bytesServerResponseDeclartion[i] ) != TCPServerThread.transmission_del);i++){
                            baosServerResponseDeclarationResponse.write( (byte) bytesServerResponseDeclartion[i] );
                            ci=i;
                        }
                        // check that the message has the prefix RESPONSE
                        if( !( "RESPONSE".equals( new String(baosServerResponseDeclarationResponse.toByteArray(), StandardCharsets.UTF_8) ) ) ){
                            Log.d("TCP CLIENT","ERROR: The prefix of the received message is " + new String(baosServerResponseDeclarationResponse.toByteArray(), StandardCharsets.UTF_8) );
                            Toast.makeText(null, "Invalid message. Prefix not equal to RESPONSE!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Log.d("TCP CLIENT","SUCCESS: the prefix of the received message from the intermediate node is " + new String(baosServerResponseDeclarationResponse.toByteArray(), StandardCharsets.UTF_8) );
                        ci++; // Now must be on delimiter
                        if( (char)( bytesServerResponseDeclartion[ci] ) != TCPServerThread.transmission_del ){
                            Log.d("TCP client","Expected " + TCPServerThread.transmission_del +" after the RESPONSE bytes. Found " + bytesServerResponseDeclartion[ci]);
                            Toast.makeText(null, "Invalid answer from intermediate node!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ci++;

                        // Then let's read how many bytes the response will be
                        // [RESPONSE SIZE IN BYTES]
                        String ResponseSizeInBytesString = "";
                        for(int i=ci;(i< bytesServerResponseDeclartion.length) && ((char)( bytesServerResponseDeclartion[i] ) != TCPServerThread.transmission_del); i++){
                            ResponseSizeInBytesString += (char)( bytesServerResponseDeclartion[i] );
                            ci = i;
                        }
                        Log.d("TCP CLIENT","The ResponseSizeInBytesString is " + ResponseSizeInBytesString);
                        int ResponseSizeInBytes = Integer.parseInt(ResponseSizeInBytesString);

                        // 4.2) CLIENT DECLARATION ACCEPT
                        try {
                            ConnectivityConfiguration.outputStream.write("ACK".getBytes());
                            Log.d("TCP CLIENT","ACKNOWLEDGMENT OF SERVER RESPONSE DECLARATION SENT SUCCESSFULLY");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        // 4.3) SERVER RESPONSE: RECEIVE THE ACTUAL RESPONSE BYTES FROM THE SERVER
                        // [JSONObjectAnswerByteArraySize] | [JSONObjectAnswerByteArray] | [JSONObjectAnswerByteArraySigned]
                        // NOW BASED ON KNOWING HOW MUCH BYTES WE SHOULD EXPECT WE CAN READ THE ACTUAL RESPONSE

                        ByteArrayOutputStream baosServerResponse;
                        try {

                            baosServerResponse = new ByteArrayOutputStream();
                            byte[] bufferServerResponse = new byte[1000]; // we will attempt using a bigger buffer here
                            int bytesReadServerResponse;
                            int total_bytesServerResponse = 0;
                            int times_waited = 0;
                            while( total_bytesServerResponse < ResponseSizeInBytes ) {
                                bytesReadServerResponse = ConnectivityConfiguration.inputStream.read(bufferServerResponse);
                                total_bytesServerResponse += bytesReadServerResponse;
                                if( (bytesReadServerResponse == -1) && (total_bytesServerResponse < ResponseSizeInBytes) ){
                                    if(times_waited == 0) {
                                        Log.d("TCP CLIENT", "Waiting for bytes from intermediate node!");
                                    }
                                    if(times_waited == 100) {
                                        Log.d("TCP CLIENT", "Waited more thatn 100 times for bytes to reach the client! " +
                                                "So far we have read only " + total_bytesServerResponse + " bytes!");
                                    }
                                    times_waited++;
                                    continue;
                                }
                                Log.d("TCP CLIENT","Now read " + bytesReadServerResponse + " bytes!");
                                baosServerResponse.write(bufferServerResponse, 0, bytesReadServerResponse);
                                if (bytesReadServerResponse < bufferServerResponse.length) {
                                    Log.d("TCP CLIENT","A segment was read that had only " + bytesReadServerResponse + " bytes!" +
                                            " So far " + total_bytesServerResponse + "have been read!");
                                    if(total_bytesServerResponse < ResponseSizeInBytes){
                                        Log.d("TCP CLIENT","We won't brake the loop because not all of the expected bytes were read!");
                                        continue;
                                    }
                                    else {
                                        Log.d("TCP CLIENT","Since it seems that we have read all the bytes we will break the loop!");
                                        break; // The buffer is not filled up that means we have reached the EOF
                                    }
                                }
                                if (total_bytesServerResponse > TCPServerThread.max_transmission_cutoff) {
                                    Log.d("TCP CLIENT","ERROR: The maximum transmission cutoff is reached! We did not expect messages more than " + TCPServerThread.max_transmission_cutoff + " bytes!");
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        byte [] bytesServerResponse = baosServerResponse.toByteArray();
                        Log.d("TCP CLIENT","SUCCESS: Received ServerResponse. Size of byte array is " + bytesServerResponse.length);

                        // SEPARATING THE FIELDS
                        // [EncryptedJSONObjectAnswerByteArraySize] | [EncryptedJSONObjectAnswerByteArray] | [EncryptedJSONObjectAnswerByteArraySigned]
                        byte [][] fieldsServerResponse = new byte[2][]; // [EncryptedJSONObjectAnswerByteArray] | [EncryptedJSONObjectAnswerByteArraySigned]
                        ci = 0; // current index bytesServerResponse
                        tempci = ci;

                        // EncryptedJSONObjectAnswerByteArraySize
                        String EncryptedJSONObjectAnswerByteArraySizeString = "";
                        for(int i=ci;(char)( bytesServerResponse[i] ) != TCPServerThread.transmission_del; i++){
                            EncryptedJSONObjectAnswerByteArraySizeString += (char)( bytesServerResponse[i] );
                            ci = i;
                        }
                        Log.d("TCP CLIENT","The EncryptedJSONObjectAnswerByteArraySizeString is " + EncryptedJSONObjectAnswerByteArraySizeString);
                        int EncryptedJSONObjectAnswerByteArraySize = Integer.parseInt(EncryptedJSONObjectAnswerByteArraySizeString);

                        ci++; // Now must be on delimiter
                        if( (char)(bytesServerResponse[ci]) != TCPServerThread.transmission_del ){
                            Log.d("TCP client","Expected " + TCPServerThread.transmission_del +" after the server response json object ansewr bytes array size. Found " + bytesServerResponse[ci]);
                            Toast.makeText(null, "Invalid answer from intermediate node!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ci++;

                        // EncryptedJSONObjectAnswerByteArray
                        tempci = ci;
                        ByteArrayOutputStream baosServerResponseJSONObjectAnswerByteArray = new ByteArrayOutputStream();
                        for(int i=ci;i<ci+EncryptedJSONObjectAnswerByteArraySize;i++){
                            baosServerResponseJSONObjectAnswerByteArray.write((byte)bytesServerResponse[i]);
                            tempci = i;
                        }
                        fieldsServerResponse[0] = baosServerResponseJSONObjectAnswerByteArray.toByteArray();
                        ci = tempci;

                        ci++; // Now must be on delimiter
                        if( (char)(bytesServerResponse[ci]) != TCPServerThread.transmission_del ){
                            Log.d("TCP client","Expected " + TCPServerThread.transmission_del +" after the server response json object ansewr bytes. Found " + bytesServerResponse[ci]);
                            Toast.makeText(null, "Invalid answer from intermediate node!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ci++;

                        // JSONObjectAnswerByteArraySigned
                        ByteArrayOutputStream baosServerResponseJSONObjectAnswerByteArraySigned = new ByteArrayOutputStream();
                        for(int i=ci;i< bytesServerResponse.length;i++){
                            baosServerResponseJSONObjectAnswerByteArraySigned.write((byte)(bytesServerResponse[i]));
                        }
                        fieldsServerResponse[1] = baosServerResponseJSONObjectAnswerByteArraySigned.toByteArray();

                        Log.d("TCP client","The response has been received by the intermediate node! Now performing checks!");

                        // Client: Success/Failure ← Verpub_server(Er,Sr)
                        // Check that the JSON array is indeed signed by the peer server
                        try {
                            if( !CryptoChecks.isSignedByCert(fieldsServerResponse[0],fieldsServerResponse[1],ConnectivityConfiguration.current_peer_cert) ){
                                Log.d("TCP client","The received response signature is not correct!");
                                Toast.makeText(null, "Invalid answer from intermediate node!", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        } catch (NoSuchProviderException e) {
                            throw new RuntimeException(e);
                        } catch (InvalidKeyException e) {
                            throw new RuntimeException(e);
                        } catch (SignatureException e) {
                            throw new RuntimeException(e);
                        }

                        Log.d("TCP client","SUCCESS: The received response's signature is correct!");

                        // Now we will decrypt the encrypted JSON object
                        byte [] decryptedJSON;
                        try {
                            decryptedJSON = InterNodeCrypto.decryptWithOwnKey(fieldsServerResponse[0]);
                        } catch (NoSuchPaddingException e) {
                            throw new RuntimeException(e);
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        } catch (InvalidKeyException e) {
                            throw new RuntimeException(e);
                        } catch (IllegalBlockSizeException e) {
                            throw new RuntimeException(e);
                        } catch (BadPaddingException e) {
                            throw new RuntimeException(e);
                        }

                        Log.d("TCP client","SUCCESS: The decryption of the response has finished!");

                        // Now make the decrypted JSON byte array to JSONObject again
                        String JSONObjectAnswer = new String(decryptedJSON,StandardCharsets.UTF_8);
                        JSONObject answerJSON;
                        try {
                            answerJSON = new JSONObject(JSONObjectAnswer);
                            Log.d("TCP client","SUCCESS: The response produced a JSON object successfully!");
                        } catch (JSONException e) {
                            Log.d("TCP client","ERROR: The response doesn't produce a JSON object as expected!");
                            Toast.makeText(null, "Invalid answer from intermediate node!", Toast.LENGTH_SHORT).show();
                            throw new RuntimeException(e);
                        }

                        if( answerJSON.toString().contains("\"status\":\"ZERO_RESULTS\"") ) {
                            Toast.makeText(getContext(), "ERROR: No results for given keyword!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Log.d("JSON PROCESSING","The transmission of data has finished! Now on to processing the JSON");
                        JSONObject answer_geojsoned = json_to_geojson( answerJSON );
                        Log.d("JSON PROCESSING","Modified answer from JSON to GEO_JSON format!");
                        apply_result_layer(answer_geojsoned);
                        Log.d("MAP UPDATE","The GeoJSON object has been added to the map layer for display!");

                    }
                }
        );

        // we are making the search edit text invisible until the map is loaded
        search_keyword_input = view.findViewById(R.id.search_keyword_input);
        search_keyword_input.setVisibility(View.INVISIBLE);

        // we can now initialize the map search item
        msi = new MapSearchItem(getContext());

    }

    void apply_result_layer(JSONObject geojson){
        // now rendering the resulting GeoJSON into the map after
        if(results_layer != null) {
            // we remove the previous results layer if it exists
            results_layer.removeLayerFromMap();
        }
        results_layer = new GeoJsonLayer(mMap, geojson);

        // setting the listener to show the names of the locations
        results_layer.setOnFeatureClickListener(
                new GeoJsonLayer.GeoJsonOnFeatureClickListener() {
                    @Override
                    public void onFeatureClick(Feature feature) {
                        String name = feature.getProperty("name");
                        GeoJsonPoint geoJsonPoint = (GeoJsonPoint) feature.getGeometry();
                        LatLng latLng = geoJsonPoint.getCoordinates();

                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(latLng)
                                .title(name);

                        mMap.addMarker(markerOptions).showInfoWindow();
                    }
                }
        );

        results_layer.addLayerToMap();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        mMapView = (MapView) v.findViewById(R.id.mapViewforSecond);
        mMapView.onCreate(mapViewBundle);
        mMapView.getMapAsync(this);

        return v;

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    /*
    Function to transform the data we have received from the API call in the JSON format
    defined by the Places API into the GeoJSON format: https://geojson.org/ which is what
    GoogleMap can understand to render the results as markers on the map.
     */
    JSONObject json_to_geojson(JSONObject response){

        JSONObject full_geoJson = new JSONObject();

        try {

            full_geoJson.put("type","FeatureCollection");

            JSONArray features = new JSONArray();

            JSONArray arr = response.getJSONArray("results");
            for (int i=0; i < arr.length(); i++){

                JSONObject single_point_geojson = new JSONObject();

                // collecting the fields that we are interested in
                String name = arr.getJSONObject(i).getString("name");
                String lat = arr.getJSONObject(i).getJSONObject("geometry").getJSONObject("location").getString("lat");
                String lng = arr.getJSONObject(i).getJSONObject("geometry").getJSONObject("location").getString("lng");
                Log.d("JSON TO GEOjson",""+name);
                Log.d("JSON TO GEOjson",""+lat+" , "+lng);

                // constructing the geoJson to return
                // based on https://geojson.org/

                // type
                single_point_geojson.put("type","Feature");

                // geometry
                JSONObject geometry = new JSONObject();
                geometry.put("type","Point");
                JSONArray coords = new JSONArray();
                double lng_double = round_decimals( Double.parseDouble(lng) , 10 );
                double lat_double = round_decimals( Double.parseDouble(lat) , 10 );

                // DecimalFormat df = new DecimalFormat("0.00");
                // df.setMaximumFractionDigits(6);

                coords.put(lng_double);
                coords.put(lat_double);
                geometry.put("coordinates",coords);
                single_point_geojson.put("geometry",geometry);

                // properties
                JSONObject prop = new JSONObject();
                prop.put("name",name);
                single_point_geojson.put("properties",prop);

                String result = single_point_geojson.toString();
                Log.d("JSON TO GEOjson","Single resulting geoJSON: " + result);

                features.put(i,single_point_geojson);

            }

            full_geoJson.put("features",features);

            Log.d("JSON TO GEOjson","Returning the following GEOJSON: " + full_geoJson);

            return full_geoJson;

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    // from: https://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
    public static double round_decimals(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    // The following functions is to allow us establish HTTP connections
    public void network_permit(){
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

}