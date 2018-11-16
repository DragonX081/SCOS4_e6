package es.source.code.activity2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import es.source.code.model.MessageEvent;
import es.source.code.model.User;

public class LoginOrRegister extends AppCompatActivity {
    private Button btn_login,btn_rtn,btn_reg;
    private EditText etLogin, etPsw;
    private boolean validAccount = false;
    private SCOS_Global_Application myApp;
    private SharedPreferences sharedPreferences;
    private String userNameFromSharePref;
    private String username;
    private String password;
    private String serverUrl = "http://192.168.43.63:8080/web/LoginValidator";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_or_register);
        myApp = (SCOS_Global_Application) getApplication();
        sharedPreferences = getSharedPreferences("scos_shpref",Context.MODE_PRIVATE);
        userNameFromSharePref = getLoginUserName();
        btn_login = findViewById(R.id.btn_login);
        btn_login.setOnClickListener(new loginClick());
        btn_reg = findViewById(R.id.btn_register);
        btn_reg.setOnClickListener(new registerClick());
        btn_rtn = findViewById(R.id.btn_rtn);
        btn_rtn.setOnClickListener(new retCLick());
        etLogin = findViewById(R.id.et_login_lg);
        etPsw = findViewById(R.id.et_login_psw);
        etLogin.addTextChangedListener(new userIDTextWatcher());
        EventBus.getDefault().register(this);
        setButtonForUser();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(EventBus.getDefault().isRegistered(this))EventBus.getDefault().unregister(this);
    }

    private class loginJump implements jumpToActivity {
        public void jump(String userName, String password) {
            User user = new User(userName, password, true);
            Intent intent = new Intent(LoginOrRegister.this, MainScreen.class);
            intent.putExtra("From", "LoginSuccess");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("User", (Serializable) user);
            myApp.setUserInfo(user);
            startActivity(intent);
        }
    }
    private void setButtonForUser(){
        if(userNameFromSharePref ==null){//has login
            btn_login.setVisibility(View.INVISIBLE);
            btn_reg.setVisibility(View.VISIBLE);
        }else{
            btn_login.setVisibility(View.VISIBLE);
            btn_reg.setVisibility(View.INVISIBLE);
            etLogin.setText(userNameFromSharePref);
        }
    }

    private class registerJump implements jumpToActivity{
        public void jump(String userName,String password){
            Intent intent = new Intent(LoginOrRegister.this, MainScreen.class);
            intent.putExtra("From", "RegisterSuccess");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            User user = new User(userName,password,false);
            intent.putExtra("User", (Serializable) user);
            startActivity(intent);
        }
    }

    interface jumpToActivity{
        public void jump(String userName,String password);
    }
    private void threadJump(final String userName, final String password, final jumpToActivity jumper){
        final ProgressBar prgBar = findViewById(R.id.prgBar_login);
        prgBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int curProgress = 0;
                while (curProgress < 100) {
                    try {
                        Thread.sleep(100);
                        prgBar.incrementProgressBy(5);
                        curProgress += 5;
                    } catch (Exception except) {
                        //handle the Exception
                    }
                }
                //initialize progressBar
                prgBar.setProgress(0);
                prgBar.setVisibility(View.INVISIBLE);



                /*
                if(validAccount){
                    //to shared preference
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("userNameFromSharePref",userNameFromSharePref);
                    editor.putInt("loginState",1);
                    editor.commit();
                    //need to be improved
                    Log.v("valid","true");
                    jumper.jump(userNameFromSharePref,password);
                }
                */
            }
        }).start();
        //thread for login
        new Thread(new Runnable() {
            @Override
            public void run() {
                //connection success
                MessageEvent messageEvent = null;
                if(jumper.getClass()==registerJump.class) {
                    sendJson(username,password,true);
                }
                else if(jumper.getClass()==loginJump.class){
                    sendJson(username,password,false);
                }
                /*
                ArrayList<String>paramList = new ArrayList<String>();
                paramList.add(userName);
                paramList.add(password);
                messageEvent.setParamList(paramList);
                if(messageEvent!=null&&validAccount)EventBus.getDefault().post(messageEvent);
                */
            }
        }).start();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void Event(MessageEvent messageEvent){
        SharedPreferences.Editor editor;
        switch (messageEvent.getMessage()){
            case "register_success":
                editor = sharedPreferences.edit();
                editor.putString("userNameFromSharePref",username);
                editor.putInt("loginState",1);
                editor.commit();
                new registerJump().jump(username,password);
                break;
            case "login_success":
                editor = sharedPreferences.edit();
                editor.putString("userNameFromSharePref",username);
                editor.putInt("loginState",1);
                editor.commit();
                new loginJump().jump(username,password);
                break;
            case "connection failed":
                Toast.makeText(this,"connection failed......",Toast.LENGTH_SHORT).show();
                //todo more
                break;

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(keyCode==KeyEvent.KEYCODE_BACK){
                Intent intent = new Intent(LoginOrRegister.this,MainScreen.class);
                intent.putExtra("From","Return");
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    private class retCLick implements View.OnClickListener{
        public void onClick(View v){
            Intent intent = new Intent(LoginOrRegister.this,MainScreen.class);
            intent.putExtra("From","Return");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            // suppose the userNameFromSharePref won't be changed after onCreate
            if(userNameFromSharePref !=null){
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("loginState",0);
                editor.commit();
            }
            startActivity(intent);
        }
    }
    private class loginClick implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            //for check
            String[]userInfo = buttonClick();
            threadJump(userInfo[0],userInfo[1],new loginJump());
        }
    }
    private class registerClick implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            //for check
            String[]userInfo = buttonClick();
            threadJump(userInfo[0],userInfo[1],new registerJump());
        }
    }
    private String[] buttonClick(){
        final ProgressBar prgBar = findViewById(R.id.prgBar_login);
        prgBar.setVisibility(View.VISIBLE);
        username = etLogin.getText().toString();
        password = etPsw.getText().toString();
        validAccount= false;
        //for check
        String id, psw;
        id = etLogin.getText().toString();
        psw = etPsw.getText().toString();
        String[] userInfo = {id,psw};
        if (id.isEmpty() || psw.isEmpty()) {
            if(id.isEmpty())etLogin.setError("Null Input");
            if(psw.isEmpty())etPsw.setError("Null Input");
        }else{
            String pattern = "[^A-Za-z0-9]";
            Pattern r = Pattern.compile(pattern);
            Matcher mtcId = r.matcher(id);
            Matcher mtcPsw = r.matcher(psw);
            if (mtcId.find(0) || mtcPsw.find(0)) {
                //handle error for invalid input
                if (mtcId.find(0)) etLogin.setError("Invalid Input");
                if (mtcPsw.find(0)) etPsw.setError("Invalid Input");
            } else {
                //need to be improved
                validAccount = true;
            }
        }
        return userInfo;
    }
    private String getLoginUserName(){
        String userName = sharedPreferences.getString("userNameFromSharePref",null);
        return userName;
    }
    private class userIDTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if(userNameFromSharePref !=null){
                if(s.toString().equals(userNameFromSharePref)){
                    Log.i("after_eq","eq");
                    btn_login.setVisibility(View.VISIBLE);
                    btn_reg.setVisibility(View.INVISIBLE);
                }
                else{
                    Log.i("after_eq","uneq");
                    btn_login.setVisibility(View.INVISIBLE);
                    btn_reg.setVisibility(View.VISIBLE);
                }
            }
        }

    }

    private void sendJson(String username,String password,boolean isRegister){
        try{
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("password",password);
            Log.i("json","===========json=========\n"+json.toString()+"\n=============================");


            //URL connection
            URL url = new URL(serverUrl);
            // open HttpUrlConnection
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            //setting time-out
            urlConnection.setConnectTimeout(10*1000);
            urlConnection.setReadTimeout(10*1000);
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
            urlConnection.setRequestProperty("content-type","application/json");
            //start to connection
            urlConnection.connect();
            //sending message
            String content = String.valueOf(json);
            DataOutputStream dataOutputStream = new DataOutputStream(urlConnection.getOutputStream());
            dataOutputStream.writeBytes(content);
            dataOutputStream.flush();
            dataOutputStream.close();

            //judge if the connection is successful
            if(urlConnection.getResponseCode()==HttpURLConnection.HTTP_OK){//the case successful
                Log.i("urlConnection","connect Successful");
                InputStreamReader inputStreamReader = new InputStreamReader(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String recData = null;
                String result = "";
                while((recData=bufferedReader.readLine())!=null){
                    result+=recData;
                }
                inputStreamReader.close();
                urlConnection.disconnect();

                JSONObject json_res = new JSONObject(result);
                //send message through Eventbus
                MessageEvent messageEvent;
                if(json_res.get("RESULTCODE").equals("1")){
                    if(isRegister){
                        messageEvent = new MessageEvent("register_success");
                        EventBus.getDefault().post(messageEvent);
                    }
                    else{
                        messageEvent = new MessageEvent("login_success");
                        EventBus.getDefault().post(messageEvent);
                    }

                }
                else{
                    //todo failed
                    messageEvent = new MessageEvent("connection failed");
                    EventBus.getDefault().post(messageEvent);
                }

            }

        }catch(Exception e){
            MessageEvent messageEvent = new MessageEvent("connection failed");
            Log.e("error connection","error");
            EventBus.getDefault().post(messageEvent);
        }

    }


}
