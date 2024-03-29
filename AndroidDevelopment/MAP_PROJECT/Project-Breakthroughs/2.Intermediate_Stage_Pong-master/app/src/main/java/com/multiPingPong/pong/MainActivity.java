package com.multiPingPong.pong;


import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import com.google.android.material.navigation.NavigationView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    Button buttonBot;
    Button buttonTop;
    GameView gameView;
    public static GameThread _thread;
    static int height;

    final static String BALLX = "BALLX";
    final static String BALLY = "BALLY";
    final static String BALLVX = "BALLVX";
    final static String BALLVY = "BALLVY";
    final static String BATTX = "BATTX";
    final static String BATBX = "BATBX";
    final static String RESETBUFFER = "RESETBUFFER";
    final static String SCORE_TOP = "SCORETOP";
    final static String SCORE_BOT = "SCOREBOT";

    static Bundle dataBundle;

    final static String GAME_TYPE = "TYPE";
    final static String SINGLE_PLAYER = "Single Player";
    final static String MONKEY = "Monkey in the Middle";
    final static String DOUBLE_PLAYER = "Double Player";
    final static String TWO_BALL = "Twice the Fun";
    final static String HARDCORE = "Hardcore";

    final static String PLAYER_NUM = "PNUM";

    BluetoothSocketListener bsl;
    public static BluetoothSocket bluetoothSocket;

    public static boolean isDouble;
    public static int playerNum;
    final static String SEPARATOR = "~";
    final static String POSITION = "POS";
    final static String SHAKE = "SHAKE";
    final static String SCORE = "SCORE";
    final static String PAUSE = "PAUSE";
    final static String[] AXIS = new String[]{"x","y"};

    public static int pausedPlayer;

    //Drawer
    public DrawerLayout drawerLayout;
    public TextView drawerName;
    public ActionBarDrawerToggle drawerToggle;
    public NavigationView navigationView;
    public TextView quit;
    public TextView restart;
    public TextView help;

    public Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        final String type = intent.getStringExtra(GAME_TYPE);

        bsl = null;
        bluetoothSocket = null;
        playerNum = 0;
        pausedPlayer = 0;

        isDouble = false;

        GameState.resetModes();
        switch (type){
            case DOUBLE_PLAYER:
                isDouble = true;
                bluetoothSocket = BluetoothFragment.socket;
                BluetoothSocketListener bsl = new BluetoothSocketListener(bluetoothSocket);
                Thread messageListener = new Thread(bsl);
                messageListener.start();
                playerNum = intent.getIntExtra(PLAYER_NUM, 0);
                setContentView(R.layout.activity_main_double);
                break;
            case MONKEY:
                GameState.enableMonkey();
                break;
            case TWO_BALL:
                GameState.doubleBall();
                break;
            case HARDCORE:
                GameState.hardcore();
                break;
            case SINGLE_PLAYER:
                break;
        }
        if(!type.equals(DOUBLE_PLAYER)){
            setContentView(R.layout.activity_main);
            buttonTop = (Button)findViewById(R.id.buttonTop);
        }

        //toolbar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(type);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        setDrawer();

        buttonBot = (Button)findViewById(R.id.buttonBot);
        buttonBot.setOnTouchListener(new GameTouchListener(this, 1, drawerLayout));

        gameView = (GameView)findViewById(R.id.gameView);

        gameView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                height = gameView.getHeight();
            }
        });

    }

    public void setDrawer(){
        drawerLayout = (DrawerLayout)findViewById(R.id.main_drawer_layout);
        navigationView = (NavigationView) findViewById(R.id.nvView);

        View header = navigationView.getHeaderView(0);

        drawerName = (TextView) header.findViewById(R.id.drawer_name);
        drawerName.setText("Menu");

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close){
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                buttonBot.setEnabled(true);
            }
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if(!GameState.getIsPaused() && (MainActivity.pausedPlayer == 0 || MainActivity.pausedPlayer == MainActivity.playerNum)) {
                    GameState.toggleGameState();
                }
                buttonBot.setEnabled(false);
            }
        };

        drawerLayout.addDrawerListener(drawerToggle);

        drawerToggle.syncState();

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                Log.d("string", "" + (item.getItemId()));

                Context c = getApplicationContext();
                //c.startActivity(MainActivity.chatActivity(c, uid));

                return false;
            }
        });

        quit = (TextView)drawerLayout.findViewById(R.id.quit);
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _thread.stopThread();
                GameState.reset();
                finish();
            }
        });

        restart = (TextView)drawerLayout.findViewById(R.id.restart);
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for(Ball ball:_thread._state.getBalls()) {
                    ball.resetShadows();
                }
                GameState.reset();
                GameState.toggleGameState();
                _thread.onPause();
                drawerLayout.closeDrawer(Gravity.LEFT);
            }
        });

        help = (TextView)drawerLayout.findViewById(R.id.help);
        help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, HelpActivity.class);
                startActivity(intent);
            }
        });

    }

    public static void saveData() {
        dataBundle = GameState.saveData();
    }

    public static void getData(){
        GameState.getData(dataBundle);
    }

    public static void sendPos(double xPercent, double ballVelX, double ballVelY) {
        byte[] byteString = (POSITION+SEPARATOR+xPercent+SEPARATOR+ ballVelX + SEPARATOR+ ballVelY+SEPARATOR).getBytes();
        send(byteString);
    }

    public static void sendShake(String axis, double ballVel){
        byte[] byteString = (SHAKE+SEPARATOR+axis+SEPARATOR+ ballVel + SEPARATOR).getBytes();
        send(byteString);
    }

    public static void sendScore(int score1, int score2) {
        byte[] byteString = (SCORE+SEPARATOR+score1+SEPARATOR+score2+SEPARATOR).getBytes();
        send(byteString);
    }

    public static void sendPause(){
        byte[] byteString = (PAUSE+SEPARATOR).getBytes();
        send(byteString);
    }

    public static void send(byte[] byteString){
        OutputStream outStream;
        try {
            outStream = bluetoothSocket.getOutputStream();
            outStream.write(byteString);
        } catch (IOException e) {
        }
    }

}
