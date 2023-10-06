package com.example.lbs_app_for_poc;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.api.internal.ApiKey;
import com.google.android.gms.common.util.Hex;
import com.google.android.gms.maps.model.LatLng;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MapSearchItem {


    public Context context;
    public int search_diameter;
    public LatLng map_center;

    public String keyword;
    public String search_method_type = "nearbysearch";
    public String result_format = "json";
    static int MAXIMUM_SEARCH_DIAMETER = 15000;
    static String LBS_URL = "https://maps.googleapis.com/maps/api/place/";

    MapSearchItem(Context context1){
        this.search_diameter = MAXIMUM_SEARCH_DIAMETER;
        this.keyword = "";
        this.map_center = new LatLng(59.4045797, 17.950208);
        this.context = context1;
    }

    String apicall() throws PackageManager.NameNotFoundException {

        Log.d("API CALL STRING","apicall() function entered");

        String call = "";

        call += LBS_URL;
        call += search_method_type + "/";
        call += result_format + "?";


        // now specifing location
        call += "location=";
        call += Double.toString((double)( map_center.latitude ));
        Log.d("API CALL STRING","from the hexadecimal conversion we get " + Integer.toHexString((int)',') );
        call += "%" + Integer.toHexString((int)','); // Hexadecimal comma for URL
        call += Double.toString((double)( map_center.longitude ));

        Log.d("API CALL STRING","Location specified");

        // now specifing radius
        call += "&";
        call += "radius=";
        // we have the diameter (i.e. the width of the current viewport in meters
        // so we divide by 2 to get the radius of the search (we use integer division)
        // we also remove 7% for the case of edge results (still sometimes edge results are returned but this is not in our control)
        // rather it's on the API side. If resutls are returned we must render them.
        call += Integer.toString( (int)( (double)( this.search_diameter / 2.0) * 0.93 ) );
        call += "&";

        Log.d("API CALL STRING","Radius specified");

        // link to the types https://developers.google.com/maps/documentation/places/web-service/supported_types
        // call += "&";

        call += "keyword=";
        call += this.keyword;
        call += "&";

        Log.d("API CALL STRING","Keyword specified");

        // finally adding the API key from the app's metadata
        call += "key=";
        ApplicationInfo app = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        Bundle bundle = app.metaData;
        call += bundle.getString("com.google.android.geo.API_KEY");

        Log.d("API CALL STRING","API key specified");

        return call;

    }

}
