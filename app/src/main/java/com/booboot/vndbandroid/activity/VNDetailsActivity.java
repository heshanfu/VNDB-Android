package com.booboot.vndbandroid.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import com.booboot.vndbandroid.R;
import com.booboot.vndbandroid.adapter.vndetails.VNDetailsElement;
import com.booboot.vndbandroid.adapter.vndetails.VNDetailsListener;
import com.booboot.vndbandroid.adapter.vndetails.VNExpandableListAdapter;
import com.booboot.vndbandroid.api.Cache;
import com.booboot.vndbandroid.api.DB;
import com.booboot.vndbandroid.api.VNDBServer;
import com.booboot.vndbandroid.api.VNStatServer;
import com.booboot.vndbandroid.bean.vndb.Item;
import com.booboot.vndbandroid.bean.vndb.Links;
import com.booboot.vndbandroid.bean.vndb.Options;
import com.booboot.vndbandroid.bean.vndbandroid.Priority;
import com.booboot.vndbandroid.bean.vndbandroid.Status;
import com.booboot.vndbandroid.bean.vndbandroid.Theme;
import com.booboot.vndbandroid.bean.vndbandroid.VNlistItem;
import com.booboot.vndbandroid.bean.vndbandroid.Vote;
import com.booboot.vndbandroid.bean.vndbandroid.VotelistItem;
import com.booboot.vndbandroid.bean.vndbandroid.WishlistItem;
import com.booboot.vndbandroid.bean.vnstat.SimilarNovel;
import com.booboot.vndbandroid.factory.PlaceholderPictureFactory;
import com.booboot.vndbandroid.factory.PopupMenuFactory;
import com.booboot.vndbandroid.factory.VNDetailsFactory;
import com.booboot.vndbandroid.util.BitmapTransformation;
import com.booboot.vndbandroid.util.Callback;
import com.booboot.vndbandroid.util.Lightbox;
import com.booboot.vndbandroid.util.Pixels;
import com.booboot.vndbandroid.util.SettingsManager;
import com.booboot.vndbandroid.util.Utils;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class VNDetailsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    /* Attributes that need to be retrieved when this activity is recreated */
    public int spoilerLevel = -1;
    public int nsfwLevel = -1;

    /* If true, the activity must finish() on resume, so the user can go back to their VN list */
    public static boolean goBackToVnlist;
    private ActionBar actionBar;
    private SwipeRefreshLayout refreshLayout;
    private PopupWindow spoilerPopup;

    private Item vn;
    private VNlistItem vnlistVn;
    private WishlistItem wishlistVn;
    private VotelistItem votelistVn;

    private List<Item> characters;
    private LinkedHashMap<String, List<Item>> releases;
    private List<Item> releasesList;
    private List<SimilarNovel> similarNovels;

    private ImageView image;
    private Button statusButton;
    private Button wishlistButton;
    private Button votesButton;
    private TextView notesTextView;
    private ImageButton notesEditButton;

    private VNDetailsListener listener;
    private ExpandableListView expandableListView;
    private ExpandableListAdapter expandableListAdapter;

    private VNDetailsElement informationSubmenu;
    private VNDetailsElement charactersSubmenu;
    private VNDetailsElement releasesSubmenu;
    private VNDetailsElement languagesSubmenu;
    private VNDetailsElement platformsSubmenu;
    private VNDetailsElement animesSubmenu;
    private VNDetailsElement relationsSubmenu;
    private VNDetailsElement similarNovelsSubmenu;
    private VNDetailsElement tagsSubmenu;
    private VNDetailsElement genresSubmenu;
    private VNDetailsElement screensSubmenu;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(Theme.THEMES.get(SettingsManager.getTheme(this)).getStyle());
        setContentView(R.layout.vn_details);

        vn = Cache.vns.get(getIntent().getIntExtra(VNTypeFragment.VN_ARG, -1));
        assert vn != null;

        if (savedInstanceState != null) {
            spoilerLevel = savedInstanceState.getInt("SPOILER_LEVEL");
            nsfwLevel = savedInstanceState.getInt("NSFW_LEVEL");
        }

        if (spoilerLevel < 0) {
            if (SettingsManager.getSpoilerCompleted(this) && vn.getStatus() == Status.FINISHED)
                spoilerLevel = 2;
            else
                spoilerLevel = SettingsManager.getSpoilerLevel(this);
        }
        if (nsfwLevel < 0) {
            nsfwLevel = SettingsManager.getNSFW(this) ? 1 : 0;
        }

        init();
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private void init() {
        vnlistVn = Cache.vnlist.get(vn.getId());
        wishlistVn = Cache.wishlist.get(vn.getId());
        votelistVn = Cache.votelist.get(vn.getId());

        if (Cache.characters.get(vn.getId()) != null) {
            characters = Cache.characters.get(vn.getId());
        }
        if (Cache.releases.get(vn.getId()) != null) {
            groupReleasesByLanguage(Cache.releases.get(vn.getId()));
        }
        if (Cache.similarNovels.get(vn.getId()) != null) {
            similarNovels = Cache.similarNovels.get(vn.getId());
        }

        initExpandableListView();

        notesTextView.setHintTextColor(Utils.getTextColorFromBackground(this, R.color.secondaryText, R.color.light_gray, isNsfw()));
        notesTextView.setTextColor(Utils.getTextColorFromBackground(this, R.color.primaryText, R.color.white, isNsfw()));
        notesTextView.setText(vnlistVn != null ? vnlistVn.getNotes() : "");
        listener = new VNDetailsListener(this, vn, notesTextView);

        notesEditButton = (ImageButton) findViewById(R.id.notesEditButton);
        notesEditButton.setColorFilter(Utils.getThemeColor(this, R.attr.colorPrimary), PorterDuff.Mode.SRC_ATOP);
        notesEditButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View arg0, MotionEvent arg1) {
                switch (arg1.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        notesEditButton.setBackgroundColor(getResources().getColor(R.color.buttonPressed));
                        notesEditButton.setAlpha(0.4f);
                        break;

                    case MotionEvent.ACTION_UP:
                        notesEditButton.setBackgroundColor(getResources().getColor(android.R.color.transparent));
                        notesEditButton.setAlpha(1.0f);
                        AlertDialog.Builder builder = new AlertDialog.Builder(VNDetailsActivity.this);
                        builder.setTitle("Notes");
                        final LinearLayout params = new LinearLayout(VNDetailsActivity.this);
                        params.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        params.setPadding(Pixels.px(15, VNDetailsActivity.this), 0, Pixels.px(15, VNDetailsActivity.this), 0);
                        final EditText input = new EditText(VNDetailsActivity.this);
                        input.setSingleLine();
                        input.setText(notesTextView.getText());
                        input.setSelection(input.getText().length());
                        input.setMaxHeight(Pixels.px(200, VNDetailsActivity.this));
                        params.addView(input, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        builder.setView(params);
                        listener.setNotesInput(input);
                        listener.setPopupButton(statusButton);
                        builder.setPositiveButton("Save", listener);
                        builder.setNegativeButton("Cancel", listener);
                        builder.setNeutralButton("Clear", listener);

                        final AlertDialog dialog = builder.create();
                        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                            @Override
                            public void onFocusChange(View v, boolean hasFocus) {
                                if (hasFocus) {
                                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                                }
                            }
                        });
                        dialog.show();
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        notesEditButton.setBackgroundColor(getResources().getColor(android.R.color.transparent));
                        notesEditButton.setAlpha(1.0f);
                        break;
                }
                return true;
            }
        });

        actionBar = getSupportActionBar();
        actionBar.setTitle(vn.getTitle());
        actionBar.setDisplayHomeAsUpEnabled(true);

        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(this);
        refreshLayout.setColorSchemeColors(Utils.getThemeColor(this, R.attr.colorAccent));

        statusButton.setText(Status.toString(vnlistVn != null ? vnlistVn.getStatus() : -1));
        wishlistButton.setText(Priority.toString(wishlistVn != null ? wishlistVn.getPriority() : -1));
        votesButton.setText(Vote.toString(votelistVn != null ? votelistVn.getVote() : -1));
        toggleButtons();

        Utils.setButtonColor(this, statusButton);
        Utils.setButtonColor(this, wishlistButton);
        Utils.setButtonColor(this, votesButton);
    }

    private void initExpandableListView() {
        expandableListView = (ExpandableListView) findViewById(R.id.expandableListView);
        LinkedHashMap<String, VNDetailsElement> expandableListDetail = VNDetailsFactory.getData(this);
        List<String> expandableListTitle = new ArrayList<>(expandableListDetail.keySet());
        expandableListAdapter = new VNExpandableListAdapter(this, expandableListTitle, expandableListDetail);

        final View header = getLayoutInflater().inflate(R.layout.vn_details_header, null);
        image = (ImageView) header.findViewById(R.id.image);
        statusButton = (Button) header.findViewById(R.id.statusButton);
        wishlistButton = (Button) header.findViewById(R.id.wishlistButton);
        votesButton = (Button) header.findViewById(R.id.votesButton);
        notesTextView = (TextView) header.findViewById(R.id.notesTextView);

        /* Setting the header and the background image according to the preferences */
        final ImageView blurBackground = ((ImageView) VNDetailsActivity.this.findViewById(R.id.blurBackground));
        final boolean coverBackground = SettingsManager.getCoverBackground(this);
        String imageUrl = PlaceholderPictureFactory.USE_PLACEHOLDER ? PlaceholderPictureFactory.getPlaceholderPicture() : vn.getImage();
        if (isNsfw()) {
            image.setImageResource(R.drawable.ic_nsfw);
            blurBackground.setImageResource(0);
        } else if (coverBackground) {
            blurBackground.setImageResource(R.drawable.blur_background_placeholder);

            Target picassoTarget = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    Bitmap blurredImage = BitmapTransformation.darkBlur(VNDetailsActivity.this, bitmap);
                    image.setImageBitmap(bitmap);
                    blurBackground.setImageBitmap(blurredImage);
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                }
            };
            image.setTag(picassoTarget);
            Picasso.with(this).load(imageUrl).into(picassoTarget);
        } else {
            Picasso.with(this).load(imageUrl).into(image);
        }
        Lightbox.set(VNDetailsActivity.this, image, vn.getImage());

        expandableListView.addHeaderView(header);
        expandableListView.setAdapter(expandableListAdapter);

        expandableListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (view.getId() == R.id.list_item_text_layout) {
                    TextView itemLeftText = (TextView) view.findViewById(R.id.itemLeftText);
                    TextView itemRightText = (TextView) view.findViewById(R.id.itemRightText);

                    String copiedText = itemLeftText.getText().toString();
                    if (!itemRightText.getText().toString().isEmpty())
                        copiedText += " : " + itemRightText.getText().toString();

                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("CLIPBOARD", copiedText);
                    clipboard.setPrimaryClip(clip);

                    Callback.showToast(VNDetailsActivity.this, "Element copied to clipboard.");
                }
                return false;
            }
        });

        expandableListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            public boolean onGroupClick(ExpandableListView parent, View groupView, int groupPosition, long id) {
                String groupName = (String) parent.getExpandableListAdapter().getGroup(groupPosition);
                boolean handledAsynchronously = initSubmenu(groupView, groupPosition, groupName);
                boolean hasChildren = parent.getExpandableListAdapter().getChildrenCount(groupPosition) > 0;

                if (!handledAsynchronously && !hasChildren) {
                    Callback.showToast(VNDetailsActivity.this, "Nothing to show here...");
                }
                return handledAsynchronously || !hasChildren;
            }
        });
    }

    /**
     * Loads and displays a submenu's content.
     * Pattern: check if the variables are already init, otherwise check the content in database, otherwise send an API query.
     *
     * @param groupView     submenu's group view (to display an loader icon)
     * @param groupPosition submenu's group position
     * @param groupName     submenu's group title to identify which content has to be fetched
     * @return true if the data is fetched asynchronously. The submenu will then be expanded in a callback.
     */
    private boolean initSubmenu(final View groupView, final int groupPosition, final String groupName) {
        boolean alreadyInit = true, hasChildren = false;
        switch (groupName) {
            case VNDetailsFactory.TITLE_CHARACTERS:
                alreadyInit = Cache.characters.get(vn.getId()) != null;
                break;
            case VNDetailsFactory.TITLE_INFORMATION:
                alreadyInit = Cache.releases.get(vn.getId()) != null;
                hasChildren = expandableListAdapter.getChildrenCount(groupPosition) > 0;
                if (alreadyInit && !hasChildren) VNDetailsFactory.setInformationSubmenu(this);
                break;
            case VNDetailsFactory.TITLE_RELEASES:
                alreadyInit = Cache.releases.get(vn.getId()) != null;
                hasChildren = expandableListAdapter.getChildrenCount(groupPosition) > 0;
                if (alreadyInit && !hasChildren) VNDetailsFactory.setReleasesSubmenu(this);
                break;
            case VNDetailsFactory.TITLE_SIMILAR_NOVELS:
                alreadyInit = Cache.similarNovels.get(vn.getId()) != null;
                break;
            case VNDetailsFactory.TITLE_LANGUAGES:
                alreadyInit = vn.getLanguages() != null;
                break;
            case VNDetailsFactory.TITLE_PLATFORMS:
                alreadyInit = vn.getPlatforms() != null;
                break;
            case VNDetailsFactory.TITLE_ANIME:
                alreadyInit = vn.getAnime() != null;
                break;
            case VNDetailsFactory.TITLE_RELATIONS:
                alreadyInit = vn.getRelations() != null;
                break;
            case VNDetailsFactory.TITLE_TAGS:
                alreadyInit = vn.getTags() != null;
                hasChildren = expandableListAdapter.getChildrenCount(groupPosition) > 0;
                if (alreadyInit && !hasChildren) VNDetailsFactory.setTagsSubmenu(this);
                break;
            case VNDetailsFactory.TITLE_GENRES:
                alreadyInit = vn.getTags() != null;
                hasChildren = expandableListAdapter.getChildrenCount(groupPosition) > 0;
                if (alreadyInit && !hasChildren) VNDetailsFactory.setGenresSubmenu(this);
                break;
            case VNDetailsFactory.TITLE_SCREENSHOTS:
                alreadyInit = vn.getScreens() != null;
                break;
        }

        if (!alreadyInit) {
            /* Variables not init yet: going to fetch data asynchronously */
            showGroupLoader(groupView);

            new Thread() {
                public void run() {
                    boolean alreadyInDatabase = false;
                    switch (groupName) {
                        case VNDetailsFactory.TITLE_CHARACTERS:
                            characters = DB.loadCharacters(VNDetailsActivity.this, vn.getId());
                            alreadyInDatabase = characters.size() > 0;
                            break;
                        case VNDetailsFactory.TITLE_INFORMATION:
                        case VNDetailsFactory.TITLE_RELEASES:
                            releasesList = DB.loadReleases(VNDetailsActivity.this, vn.getId());
                            alreadyInDatabase = releasesList.size() > 0;
                            break;
                        case VNDetailsFactory.TITLE_SIMILAR_NOVELS:
                            similarNovels = DB.loadSimilarNovels(VNDetailsActivity.this, vn.getId());
                            alreadyInDatabase = similarNovels.size() > 0;
                            break;
                        case VNDetailsFactory.TITLE_LANGUAGES:
                            vn.setLanguages(DB.loadLanguages(VNDetailsActivity.this, vn.getId()));
                            alreadyInDatabase = true;
                            break;
                        case VNDetailsFactory.TITLE_PLATFORMS:
                            vn.setPlatforms(DB.loadPlatforms(VNDetailsActivity.this, vn.getId()));
                            alreadyInDatabase = true;
                            break;
                        case VNDetailsFactory.TITLE_ANIME:
                            vn.setAnime(DB.loadAnimes(VNDetailsActivity.this, vn.getId()));
                            alreadyInDatabase = true;
                            break;
                        case VNDetailsFactory.TITLE_RELATIONS:
                            vn.setRelations(DB.loadRelations(VNDetailsActivity.this, vn.getId()));
                            alreadyInDatabase = true;
                            break;
                        case VNDetailsFactory.TITLE_TAGS:
                        case VNDetailsFactory.TITLE_GENRES:
                            vn.setTags(DB.loadTags(VNDetailsActivity.this, vn.getId()));
                            alreadyInDatabase = true;
                            break;
                        case VNDetailsFactory.TITLE_SCREENSHOTS:
                            vn.setScreens(DB.loadScreens(VNDetailsActivity.this, vn.getId()));
                            alreadyInDatabase = true;
                            break;
                    }

                    if (alreadyInDatabase) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                switch (groupName) {
                                    case VNDetailsFactory.TITLE_CHARACTERS:
                                        Cache.characters.put(vn.getId(), characters);
                                        groupCharactersByRole();
                                        VNDetailsFactory.setCharactersSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_INFORMATION:
                                        Cache.releases.put(vn.getId(), releasesList);
                                        groupReleasesByLanguage(releasesList);
                                        VNDetailsFactory.setInformationSubmenu(VNDetailsActivity.this);
                                    case VNDetailsFactory.TITLE_RELEASES:
                                        Cache.releases.put(vn.getId(), releasesList);
                                        groupReleasesByLanguage(releasesList);
                                        VNDetailsFactory.setReleasesSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_SIMILAR_NOVELS:
                                        Cache.similarNovels.put(vn.getId(), similarNovels);
                                        groupSimilarNovelsBySimilarity();
                                        VNDetailsFactory.setSimilarNovelsSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_LANGUAGES:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setLanguagesSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_PLATFORMS:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setPlatformsSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_ANIME:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setAnimesSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_RELATIONS:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setRelationsSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_TAGS:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setTagsSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_GENRES:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setGenresSubmenu(VNDetailsActivity.this);
                                        break;
                                    case VNDetailsFactory.TITLE_SCREENSHOTS:
                                        Cache.vns.put(vn.getId(), vn);
                                        VNDetailsFactory.setScreensSubmenu(VNDetailsActivity.this);
                                        break;
                                }

                                hideGroupLoader(groupView, groupPosition);
                            }
                        });
                    } else {
                        /* Database tables not init yet: going to send the API query */
                        switch (groupName) {
                            case VNDetailsFactory.TITLE_CHARACTERS:
                                VNDBServer.get("character", Cache.CHARACTER_FLAGS, "(vn = " + vn.getId() + ")", Options.create(true, 0), 0, VNDetailsActivity.this, new Callback() {
                                    @Override
                                    protected void config() {
                                        if (results.getItems().isEmpty()) {
                                            Cache.characters.put(vn.getId(), new ArrayList<Item>());
                                            characters = Cache.characters.get(vn.getId());
                                        } else {
                                            characters = results.getItems();
                                            Cache.characters.put(vn.getId(), characters);
                                            DB.saveCharacters(VNDetailsActivity.this, characters, vn.getId());
                                        }

                                        if (characters == null) {
                                            hideGroupLoader(groupView, groupPosition);
                                            return;
                                        }

                                        groupCharactersByRole();
                                        VNDetailsFactory.setCharactersSubmenu(VNDetailsActivity.this);
                                        hideGroupLoader(groupView, groupPosition);
                                    }
                                }, getSubmenuCallback(groupView, groupPosition));
                                break;

                            case VNDetailsFactory.TITLE_INFORMATION:
                            case VNDetailsFactory.TITLE_RELEASES:
                                VNDBServer.get("release", Cache.RELEASE_FLAGS, "(vn = " + vn.getId() + ")", Options.create(1, 25, "released", false, true, 0), 1, VNDetailsActivity.this, new Callback() {
                                    @Override
                                    protected void config() {
                                        List<Item> releasesList;
                                        if (results.getItems().isEmpty()) {
                                            Cache.releases.put(vn.getId(), new ArrayList<Item>());
                                            releasesList = Cache.releases.get(vn.getId());
                                        } else {
                                            releasesList = results.getItems();
                                            Cache.releases.put(vn.getId(), releasesList);
                                            DB.saveReleases(VNDetailsActivity.this, releasesList, vn.getId());
                                        }

                                        if (releasesList == null) {
                                            hideGroupLoader(groupView, groupPosition);
                                            return;
                                        }

                                        groupReleasesByLanguage(releasesList);
                                        if (groupName.equals(VNDetailsFactory.TITLE_RELEASES)) {
                                            VNDetailsFactory.setReleasesSubmenu(VNDetailsActivity.this);
                                        } else {
                                            VNDetailsFactory.setInformationSubmenu(VNDetailsActivity.this);
                                        }
                                        hideGroupLoader(groupView, groupPosition);
                                    }
                                }, getSubmenuCallback(groupView, groupPosition));
                                break;

                            case VNDetailsFactory.TITLE_SIMILAR_NOVELS:
                                VNStatServer.get("novel", "similar", vn.getId(), new Callback() {
                                    @Override
                                    protected void config() {
                                        if (vnStatResults.getSimilar().isEmpty()) {
                                            Cache.similarNovels.put(vn.getId(), new ArrayList<SimilarNovel>());
                                            similarNovels = Cache.similarNovels.get(vn.getId());
                                        } else {
                                            similarNovels = vnStatResults.getSimilar();
                                            Cache.similarNovels.put(vn.getId(), similarNovels);

                                            DB.saveSimilarNovels(VNDetailsActivity.this, similarNovels, vn.getId());
                                        }

                                        if (similarNovels == null) {
                                            hideGroupLoader(groupView, groupPosition);
                                            return;
                                        }

                                        VNDetailsFactory.setSimilarNovelsSubmenu(VNDetailsActivity.this);
                                        hideGroupLoader(groupView, groupPosition);
                                    }
                                }, getSubmenuCallback(groupView, groupPosition));
                                break;
                        }

                    }
                }
            }.start();
            return true;
        }
        return false;
    }

    private Callback getSubmenuCallback(final View groupView, final int groupPosition) {
        return new Callback() {
            @Override
            public void config() {
                showToast(VNDetailsActivity.this, message);
                hideGroupLoader(groupView, groupPosition);
            }
        };
    }

    private void hideGroupLoader(final View groupView, final int groupPosition) {
        expandableListView.expandGroup(groupPosition, true);
        ImageView groupLoader = (ImageView) groupView.findViewById(R.id.groupLoader);
        groupLoader.clearAnimation();
        groupLoader.setVisibility(View.INVISIBLE);
        groupView.setEnabled(true);

        boolean hasChildren = expandableListAdapter.getChildrenCount(groupPosition) > 0;
        if (!hasChildren) {
            Callback.showToast(VNDetailsActivity.this, "Nothing to show here...");
        }
    }

    private void showGroupLoader(View groupView) {
        groupView.setEnabled(false);
        ImageView groupLoader = (ImageView) groupView.findViewById(R.id.groupLoader);
        Utils.tintImage(this, groupLoader, R.attr.colorPrimaryDark, true);
        groupLoader.startAnimation(AnimationUtils.loadAnimation(VNDetailsActivity.this, R.anim.infinite_rotation));
        groupLoader.setVisibility(View.VISIBLE);
    }

    /**
     * Cache.releases just gives the set of all the releases for the VN.
     * That's not what we want though : like it is displayed on VNDB.org, we want to group these
     * VNs by language. That means that we somehow want a data structure "Language" -> "List of releases"
     * in which a release may appear several times for different languages!
     *
     * @param releasesList the releases list for the VN, where each release appear a single time.
     */
    private void groupReleasesByLanguage(List<Item> releasesList) {
        releases = new LinkedHashMap<>();
        for (Item release : releasesList) {
            if (release.getLanguages() == null) continue;
            for (String language : release.getLanguages()) {
                if (releases.get(language) == null)
                    releases.put(language, new ArrayList<Item>());
                releases.get(language).add(release);
            }
        }
    }

    /**
     * Sorting the characters with their role (Protagonist then main characters etc.)
     */
    private void groupCharactersByRole() {
        Collections.sort(characters, new Comparator<Item>() {
            @Override
            public int compare(Item lhs, Item rhs) {
                if (lhs.getVns() == null || lhs.getVns().size() < 1 || lhs.getVns().get(0).length < 1 || rhs.getVns() == null || rhs.getVns().size() < 1 || rhs.getVns().get(0).length < 1)
                    return 0;
                String leftRole = (String) lhs.getVns().get(0)[Item.ROLE_INDEX];
                String rightRole = (String) rhs.getVns().get(0)[Item.ROLE_INDEX];
                return Integer.valueOf(Item.ROLES_KEY.indexOf(leftRole)).compareTo(Item.ROLES_KEY.indexOf(rightRole));
            }
        });
    }

    /**
     * Sorting the similar novels by similarity
     */
    private void groupSimilarNovelsBySimilarity() {
        Collections.sort(similarNovels, new Comparator<SimilarNovel>() {
            @Override
            public int compare(SimilarNovel lhs, SimilarNovel rhs) {
                return Double.valueOf(rhs.getSimilarity()).compareTo(lhs.getSimilarity());
            }
        });
    }

    public void showStatusPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.status, popup.getMenu());
        popup.setOnMenuItemClickListener(listener);
        listener.setPopupButton(statusButton);
        popup.show();
    }

    public void showWishlistPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.wishlist, popup.getMenu());
        popup.setOnMenuItemClickListener(listener);
        listener.setPopupButton(wishlistButton);
        popup.show();
    }

    public void showVotesPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.votes, popup.getMenu());
        popup.setOnMenuItemClickListener(listener);
        listener.setPopupButton(votesButton);
        popup.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vn_details_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                overridePendingTransition(R.anim.slide_back_in, R.anim.slide_back_out);
                break;

            case R.id.action_view_on_vndb:
                /* #78 : Disabling the deep linking feature temporarily : the user obviously wants to go to the web version if they click on this button! */
                getPackageManager().setComponentEnabledSetting(new ComponentName(this, VNDBURLActivity.class), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Utils.openURL(this, Links.VNDB_PAGE + vn.getId());
                getPackageManager().setComponentEnabledSetting(new ComponentName(this, VNDBURLActivity.class), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
                break;

            case R.id.action_go_back_to_list:
                finish();
                if (MainActivity.mainActivityExists) {
                    goBackToVnlist = true;
                    overridePendingTransition(R.anim.slide_back_in, R.anim.slide_back_out);
                } else {
                    startActivity(new Intent(this, LoginActivity.class));
                }
                break;

            case R.id.action_spoiler:
                spoilerPopup = PopupMenuFactory.get(this, R.layout.spoiler_menu, findViewById(R.id.action_spoiler), spoilerPopup, new PopupMenuFactory.Callback() {
                    @Override
                    public void create(View content) {
                        RadioButton itemSpoil0 = (RadioButton) content.findViewById(R.id.item_spoil_0);
                        RadioButton itemSpoil1 = (RadioButton) content.findViewById(R.id.item_spoil_1);
                        RadioButton itemSpoil2 = (RadioButton) content.findViewById(R.id.item_spoil_2);
                        CheckBox checkNsfw = (CheckBox) content.findViewById(R.id.check_nsfw);
                        itemSpoil0.setOnClickListener(listener);
                        itemSpoil1.setOnClickListener(listener);
                        itemSpoil2.setOnClickListener(listener);
                        checkNsfw.setOnClickListener(listener);
                        itemSpoil0.setChecked(spoilerLevel == 0);
                        itemSpoil1.setChecked(spoilerLevel == 1);
                        itemSpoil2.setChecked(spoilerLevel == 2);
                        checkNsfw.setChecked(nsfwLevel == 1);
                        listener.setPopupButton(null);
                    }
                });
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public void toggleButtons() {
        vnlistVn = Cache.vnlist.get(vn.getId());
        wishlistVn = Cache.wishlist.get(vn.getId());
        votelistVn = Cache.votelist.get(vn.getId());

        boolean hideWish = votelistVn != null && wishlistVn == null;
        boolean hideVote = votelistVn == null && wishlistVn != null;
        wishlistButton.setVisibility(hideWish ? View.GONE : View.VISIBLE);
        votesButton.setVisibility(hideVote ? View.GONE : View.VISIBLE);
    }

    public boolean isNsfw() {
        return vn.isImage_nsfw() && nsfwLevel <= 0;
    }

    @Override
    public void onRefresh() {
        VNDBServer.get("vn", Cache.VN_FLAGS, "(id = " + vn.getId() + ")", Options.create(false, 1), 0, this, new Callback() {
            @Override
            protected void config() {
                if (results.getItems().size() > 0) {
                    vn = results.getItems().get(0);
                    Cache.vns.put(vn.getId(), vn);

                    /* Deleting all saved data related to the VN, so we can replace it */
                    DB.deleteVN(VNDetailsActivity.this, vn.getId(), true, false);
                    Cache.characters.remove(vn.getId());
                    Cache.releases.remove(vn.getId());
                    Cache.similarNovels.remove(vn.getId());
                    characters = null;
                    releases = null;
                    similarNovels = null;

                    DB.saveVNs(VNDetailsActivity.this, false, true);

                    /* Collapsing all submenus */
                    int count = expandableListAdapter.getGroupCount();
                    for (int i = 0; i < count; i++)
                        expandableListView.collapseGroup(i);

                    refreshLayout.setRefreshing(false);
                    Utils.recreate(VNDetailsActivity.this);
                } else {
                    Callback.showToast(VNDetailsActivity.this, "No matching VN has been found to refresh the data. Please try again later.");
                }
            }
        }, Callback.errorCallback(this));
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("SPOILER_LEVEL", spoilerLevel);
        savedInstanceState.putInt("NSFW_LEVEL", nsfwLevel);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_back_in, R.anim.slide_back_out);
    }

    @Override
    protected void onDestroy() {
        DB.saveVnlist(this, true, false);
        DB.saveVotelist(this, false, false);
        DB.saveWishlist(this, false, false);
        DB.saveVNs(this, false, true);
        Lightbox.dismiss();
        if (spoilerPopup != null && spoilerPopup.isShowing()) spoilerPopup.dismiss();
        super.onDestroy();
    }

    public VNDetailsElement getInformationSubmenu() {
        return informationSubmenu;
    }

    public void setInformationSubmenu(VNDetailsElement informationSubmenu) {
        this.informationSubmenu = informationSubmenu;
    }

    public void setCharactersSubmenu(VNDetailsElement characterElement) {
        this.charactersSubmenu = characterElement;
    }

    public VNDetailsElement getReleasesSubmenu() {
        return releasesSubmenu;
    }

    public void setReleasesSubmenu(VNDetailsElement releasesSubmenu) {
        this.releasesSubmenu = releasesSubmenu;
    }

    public VNDetailsElement getCharactersSubmenu() {
        return charactersSubmenu;
    }

    public Item getVn() {
        return vn;
    }

    public List<Item> getCharacters() {
        return characters;
    }

    public LinkedHashMap<String, List<Item>> getReleases() {
        return releases;
    }

    public void setLanguagesSubmenu(VNDetailsElement languagesSubmenu) {
        this.languagesSubmenu = languagesSubmenu;
    }

    public VNDetailsElement getLanguagesSubmenu() {
        return languagesSubmenu;
    }

    public VNDetailsElement getPlatformsSubmenu() {
        return platformsSubmenu;
    }

    public void setPlatformsSubmenu(VNDetailsElement platformsSubmenu) {
        this.platformsSubmenu = platformsSubmenu;
    }

    public VNDetailsElement getAnimesSubmenu() {
        return animesSubmenu;
    }

    public void setAnimesSubmenu(VNDetailsElement animesSubmenu) {
        this.animesSubmenu = animesSubmenu;
    }

    public VNDetailsElement getRelationsSubmenu() {
        return relationsSubmenu;
    }

    public void setRelationsSubmenu(VNDetailsElement relationsSubmenu) {
        this.relationsSubmenu = relationsSubmenu;
    }

    public VNDetailsElement getTagsSubmenu() {
        return tagsSubmenu;
    }

    public void setTagsSubmenu(VNDetailsElement tagsSubmenu) {
        this.tagsSubmenu = tagsSubmenu;
    }

    public VNDetailsElement getScreensSubmenu() {
        return screensSubmenu;
    }

    public void setScreensSubmenu(VNDetailsElement screensSubmenu) {
        this.screensSubmenu = screensSubmenu;
    }

    public VNDetailsElement getGenresSubmenu() {
        return genresSubmenu;
    }

    public void setGenresSubmenu(VNDetailsElement genresSubmenu) {
        this.genresSubmenu = genresSubmenu;
    }

    public VNDetailsElement getSimilarNovelsSubmenu() {
        return similarNovelsSubmenu;
    }

    public void setSimilarNovelsSubmenu(VNDetailsElement similarNovelsSubmenu) {
        this.similarNovelsSubmenu = similarNovelsSubmenu;
    }

    public List<SimilarNovel> getSimilarNovels() {
        return similarNovels;
    }

    public void setSimilarNovels(List<SimilarNovel> similarNovels) {
        this.similarNovels = similarNovels;
    }

    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("VN Details Page")
                .setUrl(Uri.parse(Links.VNDB))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (goBackToVnlist) {
            finish();
            overridePendingTransition(R.anim.slide_back_in, R.anim.slide_back_out);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();
    }
}