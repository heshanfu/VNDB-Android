package com.booboot.vndbandroid.activity;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IdRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;

import com.booboot.vndbandroid.R;
import com.booboot.vndbandroid.api.Cache;
import com.booboot.vndbandroid.api.VNDBServer;
import com.booboot.vndbandroid.api.bean.ListType;
import com.booboot.vndbandroid.factory.PlaceholderPictureFactory;
import com.booboot.vndbandroid.util.Pixels;
import com.booboot.vndbandroid.util.SettingsManager;
import com.booboot.vndbandroid.util.Utils;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private SearchView searchView;
    private List<VNTypeFragment> activeFragments = new ArrayList<>();
    public static MainActivity instance;
    private Fragment directSubfragment;
    public static int selectedItem;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(SettingsManager.getNoActionBarTheme(this));
        setContentView(R.layout.activity_main);
        instance = this;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        if (drawer != null) {
            drawer.setDrawerListener(toggle);
        }
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked}, // selected
                new int[]{-android.R.attr.state_checked}, // not selected
        };

        int[] colors = new int[]{
                getThemeColor(this, R.attr.colorAccent),
                Color.BLACK
        };
        navigationView.setItemTextColor(new ColorStateList(states, colors));
        navigationView.setItemIconTintList(new ColorStateList(states, colors));

        navigationView.getMenu().findItem(R.id.accountTitle).setTitle(SettingsManager.getUsername(this));
        View header = navigationView.getHeaderView(0);
        final LinearLayout headerBackground = (LinearLayout) header.findViewById(R.id.headerBackground);

        if (PlaceholderPictureFactory.USE_PLACEHOLDER) {
            new Thread() {
                public void run() {
                    final Bitmap background = ImageLoader.getInstance().loadImageSync(PlaceholderPictureFactory.getPlaceholderPicture());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            headerBackground.setBackground(new BitmapDrawable(getResources(), background));
                        }
                    });
                }
            }.start();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                headerBackground.setBackground(getResources().getDrawable(SettingsManager.getWallpaper(this), getTheme()));
            } else {
                headerBackground.setBackground(getResources().getDrawable(SettingsManager.getWallpaper(this)));
            }
        }

        FloatingActionButton floatingSearchButton = (FloatingActionButton) findViewById(R.id.floatingSearchButton);
        if (floatingSearchButton != null) {
            floatingSearchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(MainActivity.this, VNSearchActivity.class));
                }
            });
        }

        if (selectedItem > 0) {
            /* When the screen rotates, MainActivity is recreated so we have to go to where we were before (correct menu item and page) ! */
            int currentPage = VNListFragment.currentPage;
            goToFragment(selectedItem);
            VNListFragment.currentPage = currentPage;
        } else {
            navigationView.getMenu().getItem(0).setChecked(true);
            onNavigationItemSelected(navigationView.getMenu().getItem(0));
        }

        updateMenuCounters();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (!searchView.isIconified()) {
            searchView.setIconified(true);
        } else {
            /* Going back to home screen (don't use super.onBackPressed() because it would redirect to the LoginActivity underneath) */
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        /* Filter */
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menu.findItem(R.id.action_filter).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setQueryHint("Filter your VN list...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String search) {
                for (VNTypeFragment activeFragment : activeFragments) {
                    activeFragment.getMaterialListView().getAdapter().getFilter().filter(search.toString());
                }
                return true;
            }
        });

        int searchIconId = searchView.getContext().getResources().getIdentifier("android:id/search_button", null, null);
        ImageView searchIcon = (ImageView) searchView.findViewById(searchIconId);
        searchIcon.setImageResource(R.drawable.ic_action_filter);

        return true;
    }

    public void onCreateSortMenu(MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout linearLayout = new LinearLayout(this);
        final CheckBox reverseCheckbox = new CheckBox(this);
        AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT);
        reverseCheckbox.setLayoutParams(layoutParams);
        reverseCheckbox.setPadding(Pixels.px(20, this), reverseCheckbox.getPaddingTop(), reverseCheckbox.getPaddingRight(), reverseCheckbox.getPaddingBottom());
        reverseCheckbox.setText("Reverse order");
        reverseCheckbox.setChecked(SettingsManager.getReverseSort(this));

        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setPadding(Pixels.px(20, this), reverseCheckbox.getPaddingTop(), reverseCheckbox.getPaddingRight(), reverseCheckbox.getPaddingBottom());
        linearLayout.addView(reverseCheckbox);

        builder.setTitle("Sort VN list by :")
                .setSingleChoiceItems(Cache.SORT_OPTIONS, SettingsManager.getSort(this), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        SettingsManager.setReverseSort(MainActivity.this, reverseCheckbox.isChecked());
                        SettingsManager.setSort(MainActivity.this, which);
                        Cache.sortAll(MainActivity.this);
                        goToFragment(MainActivity.selectedItem);
                        dialog.dismiss();
                    }
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.getListView().addFooterView(linearLayout);
        alertDialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_search:
                startActivity(new Intent(MainActivity.this, VNSearchActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        VNListFragment.currentPage = 0;
        return goToFragment(id);
    }

    public boolean goToFragment(int id) {
        Bundle args = new Bundle();
        selectedItem = id;

        if (id == R.id.nav_vnlist) {
            directSubfragment = new VNListFragment();
            args.putInt(VNListFragment.LIST_TYPE_ARG, ListType.VNLIST);
        } else if (id == R.id.nav_votelist) {
            directSubfragment = new VNListFragment();
            args.putInt(VNListFragment.LIST_TYPE_ARG, ListType.VOTELIST);
        } else if (id == R.id.nav_wishlist) {
            directSubfragment = new VNListFragment();
            args.putInt(VNListFragment.LIST_TYPE_ARG, ListType.WISHLIST);
        } else if (id == R.id.nav_stats) {
            directSubfragment = new DatabaseStatisticsFragment();
        } else if (id == R.id.nav_top) {
            directSubfragment = new RankingNewlyReleasedFragment();
        } else if (id == R.id.nav_popular) {
            directSubfragment = new RankingNewlyReleasedFragment();
        } else if (id == R.id.nav_newly_released) {
            directSubfragment = new RankingNewlyReleasedFragment();
        } else if (id == R.id.nav_newly_added) {
            directSubfragment = new RankingNewlyAddedFragment();
        }else if (id == R.id.nav_settings) {
            directSubfragment = new PreferencesFragment();
        } else if (id == R.id.nav_logout) {
            VNDBServer.close();
            Cache.clearCache(this);
            LoginActivity.instance.enableAll();
            Utils.recreate(LoginActivity.instance);
            selectedItem = 0;
            finish();
            return true;
        }

        directSubfragment.setArguments(args);
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, directSubfragment, "FRAGMENT").addToBackStack(null).commit();
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer != null)
            drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void addActiveFragment(VNTypeFragment fragment) {
        this.activeFragments.add(fragment);
    }

    public void removeActiveFragment(VNTypeFragment fragment) {
        this.activeFragments.remove(fragment);
    }

    public VNListFragment getVnlistFragment() {
        return directSubfragment instanceof VNListFragment ? (VNListFragment) directSubfragment : null;
    }

    public SearchView getSearchView() {
        return searchView;
    }

    public static int getThemeColor(Context context, int resid) {
        TypedValue colorAttribute = new TypedValue();
        context.getTheme().resolveAttribute(resid, colorAttribute, true);
        return colorAttribute.data;
    }

    public void updateMenuCounters() {
        setMenuCounter(R.id.nav_vnlist, Cache.vnlist.size());
        setMenuCounter(R.id.nav_wishlist, Cache.wishlist.size());
        setMenuCounter(R.id.nav_votelist, Cache.votelist.size());
    }

    private void setMenuCounter(@IdRes int itemId, int count) {
        TextView view = (TextView) navigationView.getMenu().findItem(itemId).getActionView();
        view.setText(count > 0 ? String.valueOf(count) : null);
    }

}