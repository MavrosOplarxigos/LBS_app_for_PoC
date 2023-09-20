package com.example.lbs_app_for_poc;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LoggingFragment extends Fragment {

    public static ScrollView MainLogSV;
    public static LinearLayout MainLogLL = null;
    public static ArrayList <TextViewDetails> tvdAL; // we just update this array list and the logs will appear when switching to the fragment
    public static final Lock mutexTvdAL = new ReentrantLock();

    public static LiveUpdateThread liveUpdateThread = null;
    public boolean stopLiveUpdate;
    public int SoFarAddedTVs;

    public static class TextViewDetails{
        public String text;
        public int color;
        public TextViewDetails(String text, int color){
            this.text = text;
            this.color = color;
        }
    }

    public class LiveUpdateThread extends Thread{

        public LiveUpdateThread(){
        }

        @Override
        public void run() {
            Log.d("LiveUpdateThread","Entered run of live update Thread!");
            try {
                Log.d("LiveUpdateThread","Try entered!");
                int prev = LoggingFragment.this.SoFarAddedTVs;
                Log.d("LiveUpdateThread","prev = " + prev);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                while (true) {
                    Thread.sleep(200);
                    while(LoggingFragment.this.tvdAL.size() > prev){
                        TextView tv = new TextView(LoggingFragment.MainLogSV.getContext());
                        tv.setText(LoggingFragment.this.tvdAL.get(prev).text);
                        tv.setTextColor(LoggingFragment.this.tvdAL.get(prev).color);

                        mainHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        LoggingFragment.this.MainLogLL.addView(tv);
                                        Log.d("LiveUpdateThread","added new view to the linear layout!");
                                    }
                                }
                        );

                        prev++;
                    }
                    //Log.d("LiveUpdateThread","new prev = " + prev);
                    if(stopLiveUpdate){
                        break;
                    }
                }
            }
            catch(Exception e){
                Log.d("LiveUpdateThread","Could not sleep!");
            }
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_logging, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvLT = view.findViewById(R.id.LoggingTitle);
        tvLT.setText("LOG: " + InterNodeCrypto.getCommonName(InterNodeCrypto.my_cert) );

        stopLiveUpdate = false;
        MainLogSV = (ScrollView) view.findViewById(R.id.MainLogSV);
        // add the linear layout if it is not already added
        MainLogLL = new LinearLayout(getContext());
        MainLogLL.setOrientation(LinearLayout.VERTICAL);
        MainLogSV.addView(MainLogLL);
        // add the text views one by one
        SoFarAddedTVs = tvdAL.size();
        for(int i=0;i<SoFarAddedTVs;i++){
            TextView tv = new TextView(getContext());
            tv.setText(tvdAL.get(i).text);
            tv.setTextColor(tvdAL.get(i).color);
            MainLogLL.addView(tv);
        }
        // scroll all the way down
        MainLogLL.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                MainLogLL.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                MainLogLL.post(() -> MainLogSV.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
        // Button for going back
        Button backToMapButt = (Button) view.findViewById(R.id.BackToMapButt);
        backToMapButt.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            if (liveUpdateThread != null && liveUpdateThread.isAlive()) {
                                stopLiveUpdate = true;
                                liveUpdateThread.join();
                            }
                            // going back to the map
                            NavHostFragment.findNavController(LoggingFragment.this)
                                    .navigate(R.id.action_loggingFragment_to_SecondFragment);
                        }
                        catch (Exception e) {
                            Log.d("backToMapButt","Can't stop the thread meant for live updates!");
                            e.printStackTrace();
                        }
                    }
                }
        );
        // start thread to update the linear layout on realtime
        liveUpdateThread = new LiveUpdateThread();
        liveUpdateThread.start();
    }

}