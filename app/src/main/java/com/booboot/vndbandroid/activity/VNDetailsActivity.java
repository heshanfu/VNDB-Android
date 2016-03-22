package com.booboot.vndbandroid.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.telecom.Call;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.booboot.vndbandroid.R;
import com.booboot.vndbandroid.adapter.CustomExpandableListAdapter;
import com.booboot.vndbandroid.api.VNDBServer;
import com.booboot.vndbandroid.api.bean.Fields;
import com.booboot.vndbandroid.api.bean.Item;
import com.booboot.vndbandroid.api.bean.Priority;
import com.booboot.vndbandroid.api.bean.Status;
import com.booboot.vndbandroid.db.DB;
import com.booboot.vndbandroid.json.JSON;
import com.booboot.vndbandroid.util.Bitmap;
import com.booboot.vndbandroid.util.Callback;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class VNDetailsActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {
    private ActionBar actionBar;
    private Item vn;
    private ImageButton image;
    private Button statusButton;
    private Button wishlistButton;
    private Button votesButton;
    private Button popupButton;

    private boolean shouldRecreateActivity = false;

    ExpandableListView expandableListView;
    ExpandableListAdapter expandableListAdapter;
    List<String> expandableListTitle;
    LinkedHashMap<String, List<String>> expandableListDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vn_details);

        vn = (Item) getIntent().getSerializableExtra(PlayingFragment.VN_ARG);
        initExpandableListView();

        image = (ImageButton) findViewById(R.id.image);
        statusButton = (Button) findViewById(R.id.statusButton);
        wishlistButton = (Button) findViewById(R.id.wishlistButton);
        votesButton = (Button) findViewById(R.id.votesButton);

        actionBar = getSupportActionBar();
        actionBar.setTitle(vn.getTitle());
        actionBar.setDisplayHomeAsUpEnabled(true);

        statusButton.setText(Status.toString(vn.getStatus()));

        new Thread() {
            public void run() {
                image.setImageDrawable(Bitmap.drawableFromUrl(vn.getImage()));
            }
        }.start();
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Dialog dialog = new Dialog(VNDetailsActivity.this);
                dialog.setContentView(R.layout.act_lightbox);
                dialog.setCancelable(true);

                final ImageView lightbox = (ImageView) dialog.findViewById(R.id.lightboxView);
                new Thread() {
                    public void run() {
                        lightbox.setImageDrawable(Bitmap.drawableFromUrl(vn.getImage()));
                    }
                }.start();

                WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                dialog.getWindow().setAttributes(params);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.setCanceledOnTouchOutside(true);
                lightbox.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                dialog.show();
            }
        });
    }

    private void initExpandableListView() {
        expandableListView = (ExpandableListView) findViewById(R.id.expandableListView);
        expandableListDetail = getExpandableListData();
        expandableListTitle = new ArrayList<>(expandableListDetail.keySet());
        expandableListAdapter = new CustomExpandableListAdapter(this, expandableListTitle, expandableListDetail);
        expandableListView.addHeaderView(getLayoutInflater().inflate(R.layout.vn_details_header, null));
        expandableListView.setAdapter(expandableListAdapter);

        expandableListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
            }
        });

        expandableListView.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {
            @Override
            public void onGroupCollapse(int groupPosition) {
            }
        });
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        popupButton.setText(item.getTitle());
        JSON.mapper.setSerializationInclusion(Include.NON_NULL);
        String type;
        Fields fields = new Fields();

        switch (item.getItemId()) {
            case R.id.item_playing:
                type = "vnlist";
                fields.setStatus(Status.PLAYING);
                break;
            case R.id.item_finished:
                type = "vnlist";
                fields.setStatus(Status.FINISHED);
                break;
            case R.id.item_stalled:
                type = "vnlist";
                fields.setStatus(Status.STALLED);
                break;
            case R.id.item_dropped:
                type = "vnlist";
                fields.setStatus(Status.DROPPED);
                break;
            case R.id.item_unknown:
                type = "vnlist";
                fields.setStatus(Status.UNKNOWN);
                break;
            case R.id.item_no_status:
                type = "vnlist";
                fields = null;
                break;
            case R.id.item_high:
                type = "wishlist";
                fields.setPriority(Priority.HIGH);
                break;
            case R.id.item_medium:
                type = "wishlist";
                fields.setPriority(Priority.MEDIUM);
                break;
            case R.id.item_low:
                type = "wishlist";
                fields.setPriority(Priority.LOW);
                break;
            case R.id.item_blacklist:
                type = "wishlist";
                fields.setPriority(Priority.BLACKLIST);
                break;
            case R.id.item_no_wishlist:
                type = "wishlist";
                fields = null;
                break;
            case R.id.item_10:
                type = "votelist";
                fields.setVote(100);
                break;
            case R.id.item_9:
                type = "votelist";
                fields.setVote(90);
                break;
            case R.id.item_8:
                type = "votelist";
                fields.setVote(80);
                break;
            case R.id.item_7:
                type = "votelist";
                fields.setVote(70);
                break;
            case R.id.item_6:
                type = "votelist";
                fields.setVote(60);
                break;
            case R.id.item_5:
                type = "votelist";
                fields.setVote(50);
                break;
            case R.id.item_4:
                type = "votelist";
                fields.setVote(40);
                break;
            case R.id.item_3:
                type = "votelist";
                fields.setVote(30);
                break;
            case R.id.item_2:
                type = "votelist";
                fields.setVote(20);
                break;
            case R.id.item_1:
                type = "votelist";
                fields.setVote(10);
                break;
            case R.id.item_no_vote:
                type = "votelist";
                fields = null;
                break;
            default:
                return false;
        }

        VNDBServer.set(type, vn.getId(), fields, this, null, Callback.errorCallback(this));
        shouldRecreateActivity = true;
        return true;
    }

    public void showStatusPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.status, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popupButton = statusButton;
        popup.show();
    }

    public void showWishlistPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.wishlist, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popupButton = wishlistButton;
        popup.show();
    }

    public void showVotesPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.votes, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popupButton = votesButton;
        popup.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (shouldRecreateActivity) {
                    DB.loadData(getApplicationContext(), new Callback() {
                        @Override
                        protected void config() {
                            VNDetailsActivity.this.finish();
                            startActivity(new Intent(getApplicationContext(), MainActivity.class));
                        }
                    });
                } else {
                    finish();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public LinkedHashMap<String, List<String>> getExpandableListData() {
        LinkedHashMap<String, List<String>> expandableListDetail = new LinkedHashMap<>();

        List<String> description = new ArrayList<>();
        description.add(vn.getDescription());

        List<String> platforms = new ArrayList<>();
        for (String platform : vn.getPlatforms())
            platforms.add(platform);

        List<String> languages = new ArrayList<>();
        for (String language : vn.getLanguages())
            languages.add(language);

        expandableListDetail.put("Description", description);
        expandableListDetail.put("Platforms", platforms);
        expandableListDetail.put("Languages", languages);

        return expandableListDetail;
    }
}
