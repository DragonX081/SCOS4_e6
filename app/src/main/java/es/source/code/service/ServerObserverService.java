package es.source.code.service;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import es.source.code.activity2.SCOS_Global_Application;
import es.source.code.model.FoodInfo;
import es.source.code.model.FoodStockInfo;
import es.source.code.model.MessageEvent;

public class ServerObserverService extends Service {
    private ArrayList<FoodInfo>foodInfos;
    private GetStockThread getStockThread;
    private int mainPid;
    private String mainPackageName;
    private SCOS_Global_Application myApp;
    private Messenger msger_client;
    private Messenger mMessenger;
    private boolean isDataUpdated=true;
    private String serverUrl = "http://192.168.43.63:8080/web/FoodUpdateService";
    private JSONObject jsonBuffer;
    private JSONObject latestJson;
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("onCreate","oncreate");
        EventBus.getDefault().register(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        myApp = (SCOS_Global_Application) getApplication();
        mHandler cMessageHandler = new mHandler();
        mMessenger = new Messenger(cMessageHandler);
        Log.v("onbind","onbind");
        return mMessenger.getBinder();
    }
    /*
    private ArrayList<FoodStockInfo> getFoodStockInfos(){
        //todo this is simulation
        ArrayList<FoodStockInfo> foodStockInfoList = new ArrayList<FoodStockInfo>();
        SCOS_Global_Application myapp = (SCOS_Global_Application) getApplication();
        ArrayList<FoodInfo> foodInfos = myapp.getFoodInfos();
        for(FoodInfo foodInfo:foodInfos){
            //number control
            int baseStock = 5;
            FoodStockInfo mfoodStockInfo = new FoodStockInfo(foodInfo.getFoodName(),baseStock);
            foodStockInfoList.add(mfoodStockInfo);
        }
        return foodStockInfoList;
    }
    */
    private class mHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            msger_client = msg.replyTo;
            switch (msg.what){
                case 1:
                    isDataUpdated=true;
                    getStockThread = new GetStockThread(this);
                    getStockThread.start();
                    Log.e("case1","case1");
                    break;
                case 0:
                    //close thread
                    if(getStockThread!=null) getStockThread.isInterrupt = true;
                    break;



            }
        }
    }

    private class GetStockThread extends Thread{
        mHandler mhandler;
        public GetStockThread(mHandler mhandler){
            this.mhandler = mhandler;
        }
        public boolean isInterrupt=false;
        @Override
        public void run() {
            Log.e("run","run");
            super.run();
            while(!isInterrupt) {//to close
                Message msg = new Message();
                msg.replyTo = new Messenger(mhandler);
                msg.what = 10;
                //

                sendJson("requestFoodInfos");

                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //

                try {
                    sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

    }
    private boolean isAppRunning(Context context){
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String packageName = context.getPackageName();
        List<ActivityManager.RunningAppProcessInfo> processInfos = am.getRunningAppProcesses();
        if(processInfos!=null){
            if(processInfos.get(0).processName.contains(packageName)) return true;
        }
        return false;
    }
    private boolean checkDataUpdated(){
        //to modify
        if(latestJson ==null){
            if(jsonBuffer==null) return false;
            else return true;
        }
        return !jsonBuffer.toString().equals(latestJson.toString());
    }
    private void sendJson(String requestCode) {

        try {
            JSONObject json = new JSONObject();
            json.put("requestCode", "requestFoodInfos");
            Log.i("json", "===========json=========\n" + json.toString() + "\n=============================");

            //URL connection
            URL url = new URL(serverUrl);
            // open HttpUrlConnection
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            //setting time-out
            urlConnection.setConnectTimeout(10 * 1000);
            urlConnection.setReadTimeout(10 * 1000);
            //Post must has an output
            urlConnection.setDoOutput(true);
            //allow input
            urlConnection.setDoInput(true);
            //post can't use cache
            urlConnection.setUseCaches(false);
            //set to POST
            urlConnection.setRequestMethod("POST");
            //setting of redirection
            urlConnection.setInstanceFollowRedirects(true);
            //configure request content type
            urlConnection.setRequestProperty("content-type", "application/json");
            //start to connection
            Log.i("time_to_Start","sending_message");
            urlConnection.connect();
            //sending message
            String content = String.valueOf(json);
            DataOutputStream dataOutputStream = new DataOutputStream(urlConnection.getOutputStream());
            dataOutputStream.writeBytes(content);
            dataOutputStream.flush();
            dataOutputStream.close();

            //judge if the connection is successful
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {//the case successful
                Log.i("urlConnection", "connect Successful");
                InputStreamReader inputStreamReader = new InputStreamReader(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String recData = null;
                String result = "";
                while ((recData = bufferedReader.readLine()) != null) {
                    result += recData;
                }
                Log.i("=======json======",result);
                inputStreamReader.close();
                urlConnection.disconnect();

                jsonBuffer = new JSONObject(result);

                //send message through Eventbus
                MessageEvent messageEvent;
                if (jsonBuffer.length()>=1&&jsonBuffer.get("RESULTCODE").equals("1")) {
                    EventBus.getDefault().post(new MessageEvent("connection succeed"));
                } else {
                    //todo failed
                    messageEvent = new MessageEvent("connection failed");
                    EventBus.getDefault().post(messageEvent);
                }

            }

        } catch (Exception e) {
            MessageEvent messageEvent = new MessageEvent("connection failed");
            Log.e("error connection", "error");
            EventBus.getDefault().post(messageEvent);
            Thread.interrupted();
        }
        Log.i("Transfer_Complete","tansfer_complete");
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void Event(MessageEvent event){
        Message message = new Message();
        switch (event.getMessage()){

            case "connection failed":
                //todo return 404 error

                message.what = 404;
                message.replyTo = new Messenger(new mHandler());
                try {
                    msger_client.send(message);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;

            case "connection succeed":
                //todo check update

                //check updated
                foodInfos = FoodInfo.getFoodInfoFromJson(jsonBuffer);
                Log.i("foodinfos_size",String.valueOf(foodInfos.size()));
                myApp.setFoodInfos(foodInfos);
                isDataUpdated = checkDataUpdated();
                if(isDataUpdated) latestJson = jsonBuffer;

                //todo send message,what = 10

                message.what = 10;
                message.replyTo = new Messenger(new mHandler());
                Bundle bundle = new Bundle();
                bundle.putSerializable("foodInfos",foodInfos);
                message.setData(bundle);
                //todo send msg
                Log.e("isRunning",isAppRunning(myApp.getApplicationContext())?"true":"false");
                if(isAppRunning(myApp.getApplicationContext())) {
                    try {
                        Log.i("isDateUpdated",isDataUpdated?"true":"false");
                        if(isDataUpdated){
                            //start update notification service
                            Intent UpdateNotiIntent = new Intent(ServerObserverService.this,UpdateService.class);
                            UpdateNotiIntent.putExtra("action","PROVIDE_NEW_FOOD");
                            UpdateNotiIntent.putExtra("newFoodInfoList",foodInfos);
                            startService(UpdateNotiIntent);
                            //send message to mainScreen
                            msger_client.send(message);
                            isDataUpdated = false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
        }

    }

}
