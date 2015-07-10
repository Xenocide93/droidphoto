package com.droidsans.photo.droidphoto;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.droidsans.photo.droidphoto.util.CircleTransform;
import com.droidsans.photo.droidphoto.util.FontTextView;
import com.droidsans.photo.droidphoto.util.GlobalSocket;
import com.github.nkzawa.emitter.Emitter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity {

    public static Context mContext;

    private Toolbar toolbar;
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle actionBarDrawerToggle;

    private RelativeLayout navigationHeader;

    private FontTextView username;
    private FontTextView displayName;
    private ImageView profile;

    private Handler delayAction = new Handler();

    private MenuItem previousMenuItem;
    private MenuItem feedMenuItem, eventMenuItem, helpMenuItem, aboutMenuItem, settingsMenuItem, evaluateMenuItem, logoutMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        initialize();
    }

    private void initialize() {
        GlobalSocket.initializeSocket();
        findAllById();
        setupUIFrame();
        attachFragment();
        setupListener();
        printDeviceInfo();
        updateVersionMappingFile();
    }

    private void setupListener() {
        if(!GlobalSocket.mSocket.hasListeners(getString(R.string.onGetCsvResponse))){
            GlobalSocket.mSocket.on(getString(R.string.onGetCsvResponse), new Emitter.Listener() {
                @Override
                public void call(final Object... args) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("droidphoto", "csv.get: response");

                            JSONObject data = (JSONObject) args[0];
                            if (data.optBoolean("success")) {
                                Log.d("droidphoto", "csv.get: success");
                                Object csvObj = data.opt("csv");
                                String version = data.optString("version");

                                writeObjToInternalStorage(csvObj, getString(R.string.csvFileName));
                                writeObjToInternalStorage(version, getString(R.string.csvVersion));

                                //try read csv from file and print
                                File file = new File(SplashLoginActivity.mContext.getExternalFilesDir(null), getString(R.string.csvFileName));
                                BufferedReader reader = null;

                                try {
                                    reader = new BufferedReader(new FileReader(file));
                                    String line;

                                    while ((line = reader.readLine()) != null) {
                                        //TODO create hashMap to store key value
                                    }
                                    reader.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                String msg = data.optString("msg");
                                Log.d("droidphoto", "Error update csv: " + msg);
                            }
                        }
                    });
                }
            });
        }
    }

    private void updateVersionMappingFile() {

        final JSONObject data = new JSONObject();
        try {
            data.put("version", getVendorModelMapVersion());
            data.put("_event", getString(R.string.onGetCsvResponse));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(!GlobalSocket.globalEmit("csv.get", data)){
//            Log.d("droidphoto", "emit 1 csv.get fail");
            delayAction.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(!GlobalSocket.globalEmit("csv.get", data)){
//                        Log.d("droidphoto", "emit 2 csv.get fail");
                    } else {
//                        Log.d("droidphoto", "emit 2 csv.get done");
                    }
                }
            }, 3000);
        } else {
//            Log.d("droidphoto", "emit 1 csv.get done");
        }


    }

    private String getVendorModelMapVersion() {
//        return getSharedPreferences(getString(R.string.cvsMapPref), Context.MODE_PRIVATE)
//                .getString(getString(R.string.csvVersion), "1.0");
        return "0.9";
    }

    private boolean parseCsvToHashMap(Object csvObject){
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);

            oos.writeObject(csvObject);
            oos.flush(); oos.close();

            InputStream is = new ByteArrayInputStream(baos.toByteArray());


        } catch (IOException e){
            e.printStackTrace();
        }


        return false;
    }


    private void printDeviceInfo(){
        String s =  "brand:" + Build.BRAND
                + "\n model:" + Build.MODEL
                + "\n manufacture:" + Build.MANUFACTURER
                + "\n device:" + Build.DEVICE
                + "\n fingerprint:" + Build.FINGERPRINT
                + "\n id:" + Build.ID;
        Log.d("droidphoto", s);
    }

    private void setupUIFrame() {
        setSupportActionBar(toolbar);
//        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
//            @Override
//            public boolean onMenuItemClick(MenuItem item) {
//                return false;
//            }
//        });
//
//        toolbar.inflateMenu(R.menu.menu_main);

        displayName.setText(getUserdata().getString(getString(R.string.display_name), "no display name ??"));
        username.setText("@" + getUserdata().getString(getString(R.string.username), "... no username ?? must be a bug"));
        Glide.with(getApplicationContext())
                .load("https://pbs.twimg.com/profile_images/596106374725021696/r2zqUbK7_400x400.jpg")
                .centerCrop()
                .transform(new CircleTransform(getApplicationContext()))
                .into(profile);

        navigationHeader.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //TODO move to profile without losing touch anim
                return false;
            }
        });

//        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
//            @Override
//            public void onBackStackChanged() {
//                if(getSupportFragmentManager().getBackStackEntryCount() == 0) {
//                    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
//                    actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
//                    actionBarDrawerToggle.syncState();
//                } else {
//                    actionBarDrawerToggle.setDrawerIndicatorEnabled(false);
//                    actionBarDrawerToggle.syncState();
//                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//                }
//            }
//        });

//        previousMenuItem = navigationView.getMenu().getItem(0);
//        evaluateMenuItem.setVisible(true);

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(final MenuItem menuItem) {
                if (previousMenuItem != null) previousMenuItem.setChecked(false);
//                menuItem.setChecked(true);
                String selectedMenu = menuItem.getTitle().toString();
                getSupportFragmentManager().popBackStack();
                FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
//                fragmentTransaction.setCustomAnimations(android.R.anim.slide_out_right, android.R.anim.slide_in_left);
                if (selectedMenu.equals(getString(R.string.drawer_feed))) {
//                    GlobalSocket.reconnect();
                    feedMenuItem.setChecked(true);
                    fragmentTransaction.replace(R.id.main_fragment, new FeedFragment());
                    fragmentTransaction.commit();
                    toolbar.setTitle("Feed");
                    previousMenuItem = feedMenuItem;
                } else if (selectedMenu.equals(getString(R.string.drawer_event))) {
//                    GlobalSocket.reconnect();
                    eventMenuItem.setChecked(true);
                    fragmentTransaction.replace(R.id.main_fragment, new EventFragment());
                    fragmentTransaction.commit();
                    toolbar.setTitle("Events");
                    previousMenuItem = eventMenuItem;
                } else if (selectedMenu.equals(getString(R.string.drawer_help))) {
//                    GlobalSocket.reconnect();
                    helpMenuItem.setChecked(true);
                    fragmentTransaction.replace(R.id.main_fragment, new ProfileFragment());
                    fragmentTransaction.commit();
                    toolbar.setTitle(getUserdata().getString(getString(R.string.username), "???"));
                    previousMenuItem = helpMenuItem;
                } else if (selectedMenu.equals(getString(R.string.drawer_about))) {
                    aboutMenuItem.setChecked(true);
                    fragmentTransaction.replace(R.id.main_fragment, new AboutFragment());
                    fragmentTransaction.commit();
                    toolbar.setTitle("About");
                    previousMenuItem = aboutMenuItem;
                } else if (selectedMenu.equals(getString(R.string.drawer_settings))) {
                    settingsMenuItem.setChecked(true);
                    evaluateMenuItem.setVisible(true);
                    fragmentTransaction.replace(R.id.main_fragment, new SettingsFragment());
                    fragmentTransaction.commit();
                    toolbar.setTitle("Settings");
                    previousMenuItem = menuItem;
                } else if (selectedMenu.equals(getString(R.string.drawer_logout))) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Logout ?")
                            .setMessage("are you sure ?")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getUserdata().edit().clear().apply(); //clear userdata from sharedprefs.
                                    Intent login = new Intent(getApplicationContext(), SplashLoginActivity.class);
                                    startActivity(login);
                                    finish();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    logoutMenuItem.setChecked(false);
                                    previousMenuItem.setChecked(true);
                                }
                            })
//                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                } else {
                    Toast.makeText(getApplicationContext(), "bug ?", Toast.LENGTH_SHORT).show();
                }
                drawerLayout.closeDrawers();
                return false;
            }
        });

        navigationView.getMenu().getItem(4).getSubMenu().removeItem(R.id.drawer_evaluate);
        drawerLayout.requestLayout();
        previousMenuItem = feedMenuItem;
        feedMenuItem.setChecked(true);

        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout,toolbar, R.string.drawer_open, R.string.drawer_close){
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
//                actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
//                actionBarDrawerToggle.setDrawerIndicatorEnabled(getSupportFragmentManager().getBackStackEntryCount() == 0);
            }
        };
        drawerLayout.setDrawerListener(actionBarDrawerToggle);

//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        getSupportActionBar().setDisplayShowHomeEnabled(true);

        actionBarDrawerToggle.syncState();
    }



    private void attachFragment() {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.main_fragment, new FeedFragment());
        fragmentTransaction.commit();
        toolbar.setTitle("Feed");
        getSupportFragmentManager().findFragmentById(R.id.main_fragment);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onPostCreate(savedInstanceState, persistentState);
        actionBarDrawerToggle.syncState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //outState.put(tag, data);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        finish();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
//        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.action_search:
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
                if(fragment instanceof FeedFragment){
                    ((FeedFragment)fragment).launchAddFilterPopup();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void findAllById() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        navigationView = (NavigationView) findViewById(R.id.navigation_view);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        navigationHeader = (RelativeLayout) findViewById(R.id.navigation_head);

        username = (FontTextView) findViewById(R.id.username);
        displayName = (FontTextView) findViewById(R.id.display_name);
        profile = (ImageView) findViewById(R.id.profile_image_circle);

        feedMenuItem = navigationView.getMenu().getItem(0);
        eventMenuItem = navigationView.getMenu().getItem(1);
        helpMenuItem = navigationView.getMenu().getItem(2);
        aboutMenuItem = navigationView.getMenu().getItem(3);
        settingsMenuItem = navigationView.getMenu().getItem(4).getSubMenu().getItem(0);
        evaluateMenuItem = navigationView.getMenu().getItem(4).getSubMenu().getItem(1);
        logoutMenuItem = navigationView.getMenu().getItem(4).getSubMenu().getItem(2);
    }

    private SharedPreferences getUserdata() {
        return getSharedPreferences(getString(R.string.userdata), Context.MODE_PRIVATE);
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(b);
        o.writeObject(obj);
        return b.toByteArray();
    }

    private void writeObjToInternalStorage(Object obj, String filename){
        File file = new File(SplashLoginActivity.mContext.getExternalFilesDir(null), filename);

        try {
            InputStream is = new ByteArrayInputStream(serialize(obj));
            OutputStream os = new FileOutputStream(file);
            byte[] writeData = new byte[is.available()];
            is.read(writeData);
            os.write(writeData);
            is.close();
            os.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
