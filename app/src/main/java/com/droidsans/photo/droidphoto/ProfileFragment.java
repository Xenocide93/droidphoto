package com.droidsans.photo.droidphoto;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.droidsans.photo.droidphoto.util.CircleTransform;
import com.droidsans.photo.droidphoto.util.FontTextView;
import com.droidsans.photo.droidphoto.util.GlobalSocket;
import com.droidsans.photo.droidphoto.util.PicturePack;
import com.droidsans.photo.droidphoto.util.ProfileFeedRecycleViewAdapter;
import com.droidsans.photo.droidphoto.util.SpacesItemDecoration;
import com.droidsans.photo.droidphoto.util.SquareImageView;
import com.droidsans.photo.droidphoto.util.UserPictureGridAdapter;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment {

    private ProgressBar loadingCircle;
    private LinearLayout reloadLayout;
    private RelativeLayout mainLayout;

    private ImageView profilePic;
    private FontTextView profileName, usernameTV, profileDescTV;
    private RecyclerView profileFeedPicRecyclerview;

    private FontTextView reloadText;
    private Button reloadButton;

    public static final String baseURL = "/data/avatar/";
    private String username;

    private Handler delayAction = new Handler();

    private Emitter.Listener onGetUserInfoRespond;
    private Emitter.Listener onGetUserFeedRespond;
    private Emitter.Listener onDisconnect;

//    private UserPictureGridAdapter adapter;
    private ProfileFeedRecycleViewAdapter adapter;
    ArrayList<PicturePack> packs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_profile, container, false);
        setHasOptionsMenu(true);
        loadingCircle = (ProgressBar) rootView.findViewById(R.id.loading_circle);
        reloadLayout = (LinearLayout) rootView.findViewById(R.id.reload_view);
        mainLayout = (RelativeLayout) rootView.findViewById(R.id.main_view);
        initialize();
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_profile, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_edit_profile:
                Toast.makeText(getActivity(), "edit profile", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_delete:
                toggleEditMode();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initialize() {
        findAllById();
        setupProfileFeedRecyclerView();
        setupListener();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        requestUserinfo();
        requestUserPhoto();
    }

    private void setupProfileFeedRecyclerView() {
        profileFeedPicRecyclerview.addItemDecoration(new SpacesItemDecoration(
                getActivity(),
                getResources().getInteger(R.integer.profile_feed_col_num),
                (int) getResources().getDimension(R.dimen.profile_recycleview_item_space),
                false, false, false, false
        ));
        profileFeedPicRecyclerview.setLayoutManager(
                new GridLayoutManager(getActivity(),
                        getResources().getInteger(R.integer.profile_feed_col_num)));
    }

    private void setupListener() {
        onGetUserInfoRespond = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject data = (JSONObject) args[0];
                        if(data.optBoolean("success")) {
                            loadingCircle.setVisibility(ProgressBar.GONE);
                            mainLayout.setVisibility(FrameLayout.VISIBLE);

                            JSONObject userObj = data.optJSONObject("userObj");

                            Log.d("droidphoto", userObj.optString("username") + " | " + userObj.optString("disp_name"));
                            username = userObj.optString("username");
                            usernameTV.setText(username);
                            profileName.setText(userObj.optString("disp_name"));
                            Glide.with(getActivity().getApplicationContext())
                                    .load(GlobalSocket.serverURL + baseURL + userObj.optString("avatar_url"))
//                                    .load(GlobalSocket.serverURL + baseURL + "test.jpg")
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .placeholder(R.drawable.ic_account_circle_black_48dp)
                                    .centerCrop()
                                    .transform(new CircleTransform(getActivity().getApplicationContext()))
                                    .into(profilePic);
                            profileDescTV.setText(userObj.optString("profile_desc"));
                        } else {
                            switch (data.optString("msg")) {
                                case "db error":
                                    Toast.makeText(getActivity().getApplicationContext(), "db error, please try again", Toast.LENGTH_SHORT).show();
                                    Snackbar.make(mainLayout, "db error, please try again", Snackbar.LENGTH_SHORT)
                                            .setAction("OK", null)
                                            .show();
                                    break;
                                case "token error":
                                    Toast.makeText(getActivity().getApplicationContext(), "what the fuck !!? how can you invalid your f*cking token ??", Toast.LENGTH_SHORT).show();
                                    break;
                            }
                            initReload();
                        }
                    }
                });
            }
        };
        if(!GlobalSocket.mSocket.hasListeners("userinfo_respond")) {
            GlobalSocket.mSocket.on("userinfo_respond", onGetUserInfoRespond);
        }

        onGetUserFeedRespond = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GlobalSocket.mSocket.off(Socket.EVENT_DISCONNECT);
                        JSONObject data = (JSONObject) args[0];
                        if(data.optBoolean("success")){
                            packs = new ArrayList<>();
                            JSONArray photoList = data.optJSONArray("photoList");
                            for(int i=0; i<photoList.length(); i++){
                                JSONObject jsonPhoto = photoList.optJSONObject(i);
                                PicturePack pack = new PicturePack();
                                pack.setPhotoId(jsonPhoto.optString("_id"));
                                pack.setPhotoURL(jsonPhoto.optString("photo_url"));
                                pack.setUserId(jsonPhoto.optString("user_id"));
                                pack.setUsername(username);
                                pack.setCaption(jsonPhoto.optString("caption", ""));
                                pack.setVendor(jsonPhoto.optString("vendor"));
                                pack.setModel(jsonPhoto.optString("model"));
                                pack.setEventId(jsonPhoto.optString("event_id"));
                                pack.setRank(jsonPhoto.optInt("ranking"));
                                pack.setShutterSpeed(jsonPhoto.optString("exp_time"));
                                pack.setAperture(jsonPhoto.optString("aperture"));
                                pack.setIso(jsonPhoto.optString("iso"));
                                pack.setWidth(jsonPhoto.optInt("width"));
                                pack.setHeight(jsonPhoto.optInt("height"));
                                pack.setGpsLocation(jsonPhoto.optString("gps_location"));
                                pack.setGpsLocalizedLocation(jsonPhoto.optString("gps_localized"));
                                pack.setIsEnhanced(jsonPhoto.optBoolean("is_enhanced"));
                                pack.setIsFlash(jsonPhoto.optBoolean("is_flash"));
                                pack.setSubmitDate(jsonPhoto.optString("submit_date"));

                                packs.add(pack);
                            }


                            Log.d("droidphoto", "set adapter");
                            adapter = new ProfileFeedRecycleViewAdapter(getActivity(), packs);
                            profileFeedPicRecyclerview.setAdapter(adapter);

                        } else {
                            Log.d("droidphoto", "User Feed error: " + data.optString("msg"));
                            initReload();
                        }
                    }
                });
            }
        };
        if(!GlobalSocket.mSocket.hasListeners("get_user_feed")){
            GlobalSocket.mSocket.on("get_user_feed", onGetUserFeedRespond);
        }

        onDisconnect = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("droidphoto", "ProfileFragment: disconnected");
                        GlobalSocket.mSocket.off(Socket.EVENT_DISCONNECT);
                        initReload();
                    }
                });
            }
        };

        if(!GlobalSocket.mSocket.hasListeners("remove_pic")){
            GlobalSocket.mSocket.on("remove_pic", new Emitter.Listener() {
                @Override
                public void call(final Object... args) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            GlobalSocket.mSocket.off("remove_pic");
                            JSONObject returnData = (JSONObject) args[0];
                            if(returnData.optBoolean("success")){
                                Snackbar.make(mainLayout, "Selected pictures are removed", Snackbar.LENGTH_SHORT).show();
                                Log.d("droidphoto", "Selected pictures are removed");
                            } else {
                                Snackbar.make(mainLayout, "Error: "+ returnData.optString("msg"), Snackbar.LENGTH_SHORT).show();
                                Log.d("droidphoto", "Error: "+ returnData.optString("msg"));
                            }
                        }
                    });
                }
            });
        }
    }

    private void requestUserinfo() {
        reloadButton.setClickable(false);
        mainLayout.setVisibility(View.GONE);
        reloadLayout.setVisibility(View.GONE);
        loadingCircle.setVisibility(View.VISIBLE);

        JSONObject data = new JSONObject();
        try {
            data.put("_token", getActivity().getSharedPreferences(getString(R.string.userdata), Context.MODE_PRIVATE).getString(getString(R.string.token), ""));
            data.put("_event", "userinfo_respond");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(!GlobalSocket.globalEmit("user.getuserinfo", data)) {
            //retry in 2 sec
            final JSONObject finalData = data;
            delayAction.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!GlobalSocket.globalEmit("user.getuserinfo", finalData)) {
                        //reload
                        initReload();
                    }
                }
            }, 2000);
        }
    }

    private void requestUserPhoto() {
        JSONObject data = new JSONObject();

        try {
            data.put("skip", 0);
            data.put("limit", 21);
            data.put("_event", "get_user_feed");
        } catch (JSONException e){e.printStackTrace();}

        if(!GlobalSocket.globalEmit("photo.getuserphoto", data)) {
            final JSONObject delayedData = data;
            delayAction.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!GlobalSocket.globalEmit("photo.getuserphoto", delayedData)) {
                        initReload(); //if fail twice
                    } else {
                        GlobalSocket.mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
                    }
                }
            }, 4000);
        } else {
            //can emit: detect loss on the way
            GlobalSocket.mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        }
    }

    private void initReload() {
        loadingCircle.setVisibility(View.GONE);
        reloadLayout.setVisibility(View.VISIBLE);
        reloadText.setText("Error loading user profile :(");
        if (!reloadButton.hasOnClickListeners()) {
            reloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GlobalSocket.reconnect();
                    requestUserinfo();
                }
            });
        }
        reloadButton.setClickable(true);
    }

    public void toggleEditMode(){
        adapter.isInEditMode = !adapter.isInEditMode;
        if(!adapter.isInEditMode){ //exit edit mode
            int count = 0;
            JSONArray removePicId = new JSONArray();
            for (int i=adapter.getItemCount()-1; i>=0; i--){
                if(adapter.isMarkedAsRemove[i]){
                    removePicId.put(packs.get(i).photoId);
                    packs.remove(i);
                    count++;
                    adapter.isMarkedAsRemove[i] = false;
                }
            }

            if(count>0){
                JSONObject removePicData = new JSONObject();
                try {
                    removePicData.put("photo_count", count);
                    removePicData.put("remove_photo", removePicId);
                    removePicData.put("_event", "remove_pic");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                GlobalSocket.globalEmit("photo.remove", removePicData);
            }
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onStart() {
        ProfileFeedRecycleViewAdapter.isClickOnce = false;
        super.onStart();
    }

    @Override
    public void onDestroy() {
        GlobalSocket.mSocket.off(Socket.EVENT_DISCONNECT);
        if(GlobalSocket.mSocket.hasListeners("userinfo_respond")) {
            GlobalSocket.mSocket.off("userinfo_respond");
        }
        if(GlobalSocket.mSocket.hasListeners("get_user_feed")) {
            GlobalSocket.mSocket.off("get_user_feed");
        }
        if(GlobalSocket.mSocket.hasListeners("remove_pic")){
            GlobalSocket.mSocket.off("remove_pic");
        }
        super.onDestroy();
    }

    private void findAllById() {
        profilePic = (ImageView) mainLayout.findViewById(R.id.profile_image_circle);
        profileName = (FontTextView) mainLayout.findViewById(R.id.display_name);
        profileDescTV = (FontTextView) mainLayout.findViewById(R.id.profile_desc);

        usernameTV = (FontTextView) mainLayout.findViewById(R.id.username);
        profileFeedPicRecyclerview = (RecyclerView) mainLayout.findViewById(R.id.recyclerview_profile_feed_picture);

        reloadText = (FontTextView) reloadLayout.findViewById(R.id.reload_text);
        reloadButton = (Button) reloadLayout.findViewById(R.id.reload_button);
    }
}
