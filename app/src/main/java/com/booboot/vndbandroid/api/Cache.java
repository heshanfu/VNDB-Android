package com.booboot.vndbandroid.api;

import android.content.Context;

import com.booboot.vndbandroid.api.bean.DbStats;
import com.booboot.vndbandroid.api.bean.Item;
import com.booboot.vndbandroid.api.bean.Options;
import com.booboot.vndbandroid.util.Callback;
import com.booboot.vndbandroid.util.IPredicate;
import com.booboot.vndbandroid.util.JSON;
import com.booboot.vndbandroid.util.Predicate;
import com.booboot.vndbandroid.util.SettingsManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Created by od on 15/03/2016.
 */
public class Cache {
    public static LinkedHashMap<Integer, Item> vnlist = new LinkedHashMap<>();
    public static LinkedHashMap<Integer, Item> votelist = new LinkedHashMap<>();
    public static LinkedHashMap<Integer, Item> wishlist = new LinkedHashMap<>();
    public static LinkedHashMap<Integer, List<Item>> characters = new LinkedHashMap<>();
    public static LinkedHashMap<Integer, List<Item>> releases = new LinkedHashMap<>();

    public final static String VN_FLAGS = "basic,details,screens,tags,stats,relations,anime";
    public final static String CHARACTER_FLAGS = "basic,details,meas,traits,vns";
    public final static String RELEASE_FLAGS = "basic,details,producers";

    public final static String VNLIST_CACHE = "vnlist.data";
    public final static String VOTELIST_CACHE = "votelist.data";
    public final static String WISHLIST_CACHE = "wishlist.data";
    public final static String CHARACTERS_CACHE = "characters.data";
    public final static String RELEASES_CACHE = "releases.data";
    public final static String DBSTATS_CACHE = "dbstats.data";
    public static boolean loadedFromCache = false;

    public final static String[] SORT_OPTIONS = new String[]{
            "ID",
            "Title",
            "Release date",
            "Length",
            "Popularity",
            "Rating",
            "Status",
            "Vote",
            "Wish"
    };

    public static DbStats dbstats;
    private static String mergedIdsString;
    public static boolean shouldRefreshView;

    public static boolean pipeliningError;

    public static void loadData(final Context context, final Callback successCallback, final Callback errorCallback) {
        new Thread() {
            public void run() {
                shouldRefreshView = false;
                final Map<Integer, Item> vnlistIds = new HashMap<>(), votelistIds = new HashMap<>(), wishlistIds = new HashMap<>();

                /* Initializing multi-threading variables */
                pipeliningError = false;
                Callback.countDownLatch = new CountDownLatch(3);

                VNDBServer.get("vnlist", "basic", "(uid = 0)", Options.create(1, 100, null, false, true, true, 0), 0, context, new Callback() {
                    @Override
                    public void config() {
                        for (Item vnlistItem : results.getItems()) {
                            vnlistIds.put(vnlistItem.getVn(), vnlistItem);
                        }
                        if (countDownLatch != null) countDownLatch.countDown();
                    }
                }, errorCallback);

                VNDBServer.get("votelist", "basic", "(uid = 0)", Options.create(1, 100, null, false, true, true, 0), 1, context, new Callback() {
                    @Override
                    protected void config() {
                        for (Item votelistItem : results.getItems()) {
                            votelistIds.put(votelistItem.getVn(), votelistItem);
                        }
                        if (countDownLatch != null) countDownLatch.countDown();
                    }
                }, errorCallback);

                VNDBServer.get("wishlist", "basic", "(uid = 0)", Options.create(1, 100, null, false, true, true, 0), 2, context, new Callback() {
                    @Override
                    protected void config() {
                        for (Item wishlistItem : results.getItems()) {
                            wishlistIds.put(wishlistItem.getVn(), wishlistItem);
                        }
                        if (countDownLatch != null) countDownLatch.countDown();
                    }
                }, errorCallback);

                try {
                    Callback.countDownLatch.await();
                } catch (InterruptedException E) {
                    errorCallback.message = "An unexpected error occurred while loading your lists. Please try again later.";
                    errorCallback.call();
                    return;
                }

                Callback.countDownLatch = null;
                if (pipeliningError) return;

                Set<Integer> mergedIds = new HashSet<>(vnlistIds.keySet());
                mergedIds.addAll(votelistIds.keySet());
                mergedIds.addAll(wishlistIds.keySet());

                if (mergedIds.isEmpty() || !shouldSendGetVn(context, vnlistIds, votelistIds, wishlistIds, mergedIds)) {
                    successCallback.call();
                    return;
                }

                try {
                    mergedIdsString = JSON.mapper.writeValueAsString(mergedIds);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                int numberOfPages = (int) Math.ceil(mergedIds.size() * 1.0 / 25);
                VNDBServer.get("vn", VN_FLAGS, "(id = " + mergedIdsString + ")", Options.create(true, true, numberOfPages), 0, context, new Callback() {
                    @Override
                    protected void config() {
                        for (Item vn : results.getItems()) {
                            Item vnlistItem = vnlistIds.get(vn.getId());
                            Item votelistItem = votelistIds.get(vn.getId());
                            Item wishlistItem = wishlistIds.get(vn.getId());

                            if (vnlistItem != null) {
                                vn.setStatus(vnlistItem.getStatus());
                                Cache.vnlist.put(vn.getId(), vn);
                            }
                            if (votelistItem != null) {
                                vn.setVote(votelistItem.getVote());
                                Cache.votelist.put(vn.getId(), vn);
                            }
                            if (wishlistItem != null) {
                                vn.setPriority(wishlistItem.getPriority());
                                Cache.wishlist.put(vn.getId(), vn);
                            }
                        }

                        sortAll(context);
                        saveToCache(context, VNLIST_CACHE, vnlist);
                        saveToCache(context, VOTELIST_CACHE, votelist);
                        saveToCache(context, WISHLIST_CACHE, wishlist);
                        shouldRefreshView = true;
                        successCallback.call();
                    }
                }, errorCallback);
            }
        }.start();
    }

    /**
     * @return true if the up-to-date lists (directly fetched from the API) are different from the cache content.
     */
    private static boolean shouldSendGetVn(Context context, Map<Integer, Item> vnlistIds, Map<Integer, Item> votelistIds, Map<Integer, Item> wishlistIds, Set<Integer> mergedIds) {
        /* 1 - Checking for VNs that have been removed overtime */
        boolean vnlistHasChanged = false;
        for (int id : new HashSet<>(vnlist.keySet())) {
            if (vnlistIds.get(id) == null) {
                vnlist.remove(id);
                vnlistHasChanged = true;
            }
        }
        boolean votelistHasChanged = false;
        for (int id : new HashSet<>(votelist.keySet())) {
            if (votelistIds.get(id) == null) {
                votelist.remove(id);
                votelistHasChanged = true;
            }
        }
        boolean wishlistHasChanged = false;
        for (int id : new HashSet<>(wishlist.keySet())) {
            if (wishlistIds.get(id) == null) {
                wishlist.remove(id);
                wishlistHasChanged = true;
            }
        }

        Set<Integer> filteredMergedIds = new HashSet<>();
        for (Integer id : mergedIds) {
            Item vnlistItem = vnlistIds.get(id);
            Item votelistItem = votelistIds.get(id);
            Item wishlistItem = wishlistIds.get(id);

            /* 2 - Checking for VNs that have been added or modified overtime */
            if (vnlistItem != null) {
                if (vnlist.get(id) == null || vnlistItem.getStatus() != vnlist.get(id).getStatus())
                    filteredMergedIds.add(id);
            }
            if (votelistItem != null) {
                if (votelist.get(id) == null || votelistItem.getVote() != votelist.get(id).getVote())
                    filteredMergedIds.add(id);
            }
            if (wishlistItem != null) {
                if (wishlist.get(id) == null || wishlistItem.getPriority() != wishlist.get(id).getPriority())
                    filteredMergedIds.add(id);
            }
        }

        if (!filteredMergedIds.isEmpty()) {
            /* VNs have been added or modified: updating the ids we're going to query */
            mergedIds.clear();
            mergedIds.addAll(filteredMergedIds);
            return true;
        } else {
            /* Updating persistent cache if VNs have been removed */
            if (vnlistHasChanged) saveToCache(context, VNLIST_CACHE, vnlist);
            if (votelistHasChanged) saveToCache(context, VOTELIST_CACHE, votelist);
            if (wishlistHasChanged) saveToCache(context, WISHLIST_CACHE, wishlist);
            if (vnlistHasChanged || votelistHasChanged || wishlistHasChanged) shouldRefreshView = true;
        }

        return false;
    }

    public static void saveToCache(Context context, String filename, Object object) {
        File file = new File(context.getFilesDir(), filename);
        try {
            JSON.mapper.writeValue(file, object);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean loadFromCache(Context context) {
        if (loadedFromCache) return true;
        File vnlistFile = new File(context.getFilesDir(), VNLIST_CACHE);
        File votelistFile = new File(context.getFilesDir(), VOTELIST_CACHE);
        File wishlistFile = new File(context.getFilesDir(), WISHLIST_CACHE);
        File charactersFile = new File(context.getFilesDir(), CHARACTERS_CACHE);
        File releasesFile = new File(context.getFilesDir(), RELEASES_CACHE);

        if (!vnlistFile.exists() || !votelistFile.exists() || !wishlistFile.exists())
            return false;

        try {
            vnlist = JSON.mapper.readValue(vnlistFile, new TypeReference<LinkedHashMap<Integer, Item>>() {
            });
            votelist = JSON.mapper.readValue(votelistFile, new TypeReference<LinkedHashMap<Integer, Item>>() {
            });
            wishlist = JSON.mapper.readValue(wishlistFile, new TypeReference<LinkedHashMap<Integer, Item>>() {
            });
            if (charactersFile.exists()) {
                characters = JSON.mapper.readValue(charactersFile, new TypeReference<LinkedHashMap<Integer, List<Item>>>() {
                });
            }
            if (releasesFile.exists()) {
                releases = JSON.mapper.readValue(releasesFile, new TypeReference<LinkedHashMap<Integer, List<Item>>>() {
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        sortAll(context);
        loadedFromCache = true;
        return true;
    }

    public static void loadStatsFromCache(Context context) {
        File dbstatsFile = new File(context.getFilesDir(), DBSTATS_CACHE);
        if (dbstatsFile.exists()) {
            try {
                dbstats = JSON.mapper.readValue(dbstatsFile, DbStats.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void clearCache(Context context) {
        File vnlistFile = new File(context.getFilesDir(), VNLIST_CACHE);
        File votelistFile = new File(context.getFilesDir(), VOTELIST_CACHE);
        File wishlistFile = new File(context.getFilesDir(), WISHLIST_CACHE);
        File charactersFile = new File(context.getFilesDir(), CHARACTERS_CACHE);
        File releasesFile = new File(context.getFilesDir(), RELEASES_CACHE);

        if (vnlistFile.exists()) vnlistFile.delete();
        if (votelistFile.exists()) votelistFile.delete();
        if (wishlistFile.exists()) wishlistFile.delete();
        if (charactersFile.exists()) charactersFile.delete();
        if (releasesFile.exists()) releasesFile.delete();

        vnlist = new LinkedHashMap<>();
        votelist = new LinkedHashMap<>();
        wishlist = new LinkedHashMap<>();
        loadedFromCache = false;
    }

    public static void loadStats(final Context context, final Callback successCallback, boolean forceRefresh) {
        if (Cache.dbstats != null && !forceRefresh) {
            successCallback.call();
            return;
        }

        VNDBServer.dbstats(new Callback() {
            @Override
            protected void config() {
                Cache.dbstats = dbstats;
                saveToCache(context, DBSTATS_CACHE, dbstats);
                successCallback.call();
            }
        }, Callback.errorCallback(context));
    }

    public static void sortAll(Context context) {
        sort(context, vnlist);
        sort(context, votelist);
        sort(context, wishlist);
    }

    public static void sort(final Context context, LinkedHashMap<Integer, Item> list) {
        List<Map.Entry<Integer, Item>> entries = new ArrayList<>(list.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<Integer, Item>>() {
            public int compare(Map.Entry<Integer, Item> a, Map.Entry<Integer, Item> b) {
                Map.Entry<Integer, Item> first = a, second = b;
                if (SettingsManager.getReverseSort(context)) {
                    // Reverse sort : swapping a and b
                    first = b;
                    second = a;
                }

                switch (SettingsManager.getSort(context)) {
                    case 1:
                        return first.getValue().getTitle().compareTo(second.getValue().getTitle());
                    case 2:
                        String releasedA = first.getValue().getReleased();
                        String releasedB = second.getValue().getReleased();
                        if (releasedA == null) return -1;
                        if (releasedB == null) return 1;
                        return releasedA.compareTo(releasedB);
                    case 3:
                        return Integer.valueOf(first.getValue().getLength()).compareTo(second.getValue().getLength());
                    case 4:
                        return Double.valueOf(first.getValue().getPopularity()).compareTo(second.getValue().getPopularity());
                    case 5:
                        return Double.valueOf(first.getValue().getRating()).compareTo(second.getValue().getRating());
                    case 6:
                        Item vnlistA = Cache.vnlist.get(first.getKey());
                        Item vnlistB = Cache.vnlist.get(second.getKey());
                        if (vnlistA == null && vnlistB == null) return 0;
                        if (vnlistA == null) return -1;
                        if (vnlistB == null) return 1;
                        return Integer.valueOf(vnlistA.getStatus()).compareTo(vnlistB.getStatus());
                    case 7:
                        Item votelistA = Cache.votelist.get(first.getKey());
                        Item votelistB = Cache.votelist.get(second.getKey());
                        if (votelistA == null && votelistB == null) return 0;
                        if (votelistA == null) return -1;
                        if (votelistB == null) return 1;
                        return Integer.valueOf(votelistA.getVote()).compareTo(votelistB.getVote());
                    case 8:
                        Item wishlistA = Cache.wishlist.get(first.getKey());
                        Item wishlistB = Cache.wishlist.get(second.getKey());
                        if (wishlistA == null && wishlistB == null) return 0;
                        if (wishlistA == null) return -1;
                        if (wishlistB == null) return 1;
                        return Integer.valueOf(wishlistA.getPriority()).compareTo(wishlistB.getPriority());
                    default:
                        return Integer.valueOf(first.getValue().getId()).compareTo(second.getValue().getId());
                }
            }
        });
        list.clear();
        for (Map.Entry<Integer, Item> entry : entries) {
            list.put(entry.getKey(), entry.getValue());
        }
    }

    public static int getStatusNumber(final int status) {
        return Predicate.filter(vnlist.values(), new IPredicate<Item>() {
            @Override
            public boolean apply(Item element) {
                return element.getStatus() == status;
            }
        }).size();
    }

    public static int getWishNumber(final int priority) {
        return Predicate.filter(wishlist.values(), new IPredicate<Item>() {
            @Override
            public boolean apply(Item element) {
                return element.getPriority() == priority;
            }
        }).size();
    }

    public static int getVoteNumber(final int vote) {
        return Predicate.filter(votelist.values(), new IPredicate<Item>() {
            @Override
            public boolean apply(Item element) {
                return element.getVote() / 10 == vote || element.getVote() / 10 == vote - 1;
            }
        }).size();
    }
}