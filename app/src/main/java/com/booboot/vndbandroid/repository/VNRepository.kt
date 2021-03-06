package com.booboot.vndbandroid.repository

import android.text.TextUtils
import com.booboot.vndbandroid.api.VNDBServer
import com.booboot.vndbandroid.dao.VNDao
import com.booboot.vndbandroid.extensions.get
import com.booboot.vndbandroid.extensions.save
import com.booboot.vndbandroid.model.vndb.Options
import com.booboot.vndbandroid.model.vndb.VN
import com.booboot.vndbandroid.model.vndbandroid.FLAGS_BASIC
import com.booboot.vndbandroid.model.vndbandroid.FLAGS_DETAILS
import com.booboot.vndbandroid.model.vndbandroid.FLAGS_FULL
import com.booboot.vndbandroid.model.vndbandroid.FLAGS_NOT_EXISTS
import com.booboot.vndbandroid.util.type
import com.squareup.moshi.Moshi
import io.objectbox.BoxStore
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VNRepository @Inject constructor(var boxStore: BoxStore, var vndbServer: VNDBServer, var moshi: Moshi) : Repository<VN>() {
    override fun getItems(cachePolicy: CachePolicy<Map<Long, VN>>): Single<Map<Long, VN>> = Single.fromCallable {
        cachePolicy
            .fetchFromMemory { items }
            .fetchFromDatabase {
                boxStore.get<VNDao, Map<Long, VN>> { it.all.map { it.toBo() }.associateBy { it.id } }
            }
            .putInMemory { items.putAll(it) }
            .get()
    }

    override fun getItems(ids: Set<Long>, flags: Int, cachePolicy: CachePolicy<Map<Long, VN>>): Single<Map<Long, VN>> = Single.fromCallable {
        cachePolicy
            .fetchFromMemory { items }
            .fetchFromDatabase {
                boxStore.get<VNDao, Map<Long, VN>> { it.get(ids).map { it.toBo(flags) }.associateBy { it.id } }
            }
            .fetchFromNetwork {
                val dbVns = it?.toMutableMap() ?: mutableMapOf()
                val flagsList = linkedSetOf<String>()
                val newIds = linkedSetOf<Long>()

                ids.forEach {
                    val dbFlags = dbVns[it]?.flags ?: FLAGS_NOT_EXISTS
                    if (FLAGS_BASIC in (dbFlags + 1)..flags) {
                        newIds.add(it)
                        flagsList.add("basic")
                    }
                    if (FLAGS_DETAILS in (dbFlags + 1)..flags) {
                        newIds.add(it)
                        flagsList.addAll(listOf("details", "stats"))
                    }
                    if (FLAGS_FULL in (dbFlags + 1)..flags) {
                        newIds.add(it)
                        flagsList.addAll(listOf("screens", "tags", "anime", "relations"))
                    }
                }

                val mergedIdsString = TextUtils.join(",", newIds)
                val numberOfPages = Math.ceil(newIds.size * 1.0 / 25).toInt()

                val apiVns = vndbServer.get<VN>("vn", TextUtils.join(",", flagsList), "(id = [$mergedIdsString])",
                    Options(fetchAllPages = true, numberOfPages = numberOfPages), type())
                    .blockingGet()
                    .items

                apiVns.forEach {
                    val dbFlags = dbVns[it.id]?.flags ?: FLAGS_NOT_EXISTS

                    if (FLAGS_BASIC in (dbFlags + 1)..flags) {
                        dbVns[it.id] = it
                    } else {
                        if (FLAGS_DETAILS in (dbFlags + 1)..flags) {
                            dbVns[it.id]?.aliases = it.aliases
                            dbVns[it.id]?.length = it.length
                            dbVns[it.id]?.description = it.description
                            dbVns[it.id]?.links = it.links
                            dbVns[it.id]?.image = it.image
                            dbVns[it.id]?.image_nsfw = it.image_nsfw
                            dbVns[it.id]?.popularity = it.popularity
                            dbVns[it.id]?.rating = it.rating
                            dbVns[it.id]?.votecount = it.votecount
                        }
                        if (FLAGS_FULL in (dbFlags + 1)..flags) {
                            dbVns[it.id]?.screens = it.screens
                            dbVns[it.id]?.tags = it.tags
                            dbVns[it.id]?.anime = it.anime
                            dbVns[it.id]?.relations = it.relations
                        }
                    }

                    dbVns[it.id]?.flags = flags
                }
                dbVns
            }
            .isEmpty { cache ->
                ids.any {
                    if (it !in cache) true
                    else {
                        val dbFlags = cache[it]?.flags ?: FLAGS_NOT_EXISTS
                        FLAGS_BASIC in (dbFlags + 1)..flags ||
                            FLAGS_DETAILS in (dbFlags + 1)..flags ||
                            FLAGS_FULL in (dbFlags + 1)..flags
                    }
                }
            }
            .putInMemory {
                if (cachePolicy.enabled) items.putAll(it.filter { it.value.flags > items[it.key]?.flags ?: FLAGS_NOT_EXISTS })
            }
            .putInDatabase {
                if (cachePolicy.enabled) boxStore.save {
                    it.mapNotNull {
                        if (it.value.flags > items[it.key]?.flags ?: FLAGS_NOT_EXISTS)
                            VNDao(it.value, boxStore)
                        else null
                    }
                }
            }
            .get()
    }

    override fun setItems(items: Map<Long, VN>): Completable = Completable.fromAction {
        this.items.putAll(items)
        boxStore.save { items.map { VNDao(it.value, boxStore) } }
    }

    override fun getItem(id: Long, cachePolicy: CachePolicy<VN>): Single<VN> = Single.fromCallable {
        getItems(setOf(id), FLAGS_FULL, cachePolicy.copy()).blockingGet()[id]
    }
}