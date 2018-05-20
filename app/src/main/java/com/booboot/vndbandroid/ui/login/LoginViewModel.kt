package com.booboot.vndbandroid.ui.login

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.text.TextUtils
import com.booboot.vndbandroid.App
import com.booboot.vndbandroid.BuildConfig
import com.booboot.vndbandroid.api.VNDBServer
import com.booboot.vndbandroid.di.Schedulers
import com.booboot.vndbandroid.model.vndb.Options
import com.booboot.vndbandroid.model.vndb.Results
import com.booboot.vndbandroid.model.vndb.VN
import com.booboot.vndbandroid.model.vndbandroid.*
import com.booboot.vndbandroid.store.VnlistRepository
import com.booboot.vndbandroid.store.VotelistRepository
import com.booboot.vndbandroid.store.WishlistRepository
import com.booboot.vndbandroid.util.type
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function3
import javax.inject.Inject

class LoginViewModel constructor(application: Application) : AndroidViewModel(application) {
    @Inject lateinit var vndbServer: VNDBServer
    @Inject lateinit var schedulers: Schedulers
    @Inject lateinit var vnlistRepository: VnlistRepository
    @Inject lateinit var votelistRepository: VotelistRepository
    @Inject lateinit var wishlistRepository: WishlistRepository
    val vnData: MutableLiveData<Results<VN>> = MutableLiveData()
    val loadingData: MutableLiveData<Boolean> = MutableLiveData()
    val errorData: MutableLiveData<String> = MutableLiveData()
    private val disposables: MutableMap<String, Disposable> = mutableMapOf()

    private lateinit var items: AccountItems
    private var result: Results<VN>? = null

    init {
        (application as App).appComponent.inject(this)
    }

    fun login() {
        if (disposables.contains(DISPOSABLE_LOGIN)) return

        val vnlistIds = vndbServer.get<Vnlist>("vnlist", "basic", "(uid = 0)",
                Options(results = 100, fetchAllPages = true), type())
        val votelistIds = vndbServer.get<Votelist>("votelist", "basic", "(uid = 0)",
                Options(results = 100, fetchAllPages = true, socketIndex = 1), type())
        val wishlistIds = vndbServer.get<Wishlist>("wishlist", "basic", "(uid = 0)",
                Options(results = 100, fetchAllPages = true, socketIndex = 2), type())

        disposables[DISPOSABLE_LOGIN] = VNDBServer.closeAll()
                .observeOn(schedulers.ui())
                .doOnSubscribe { loadingData.value = true }
                .observeOn(schedulers.io())
                .andThen(Single.zip(vnlistIds, votelistIds, wishlistIds,
                        Function3<Results<Vnlist>, Results<Votelist>, Results<Wishlist>, AccountItems> { vni, vti, wsi ->
                            AccountItems(vni.items, vti.items, wsi.items)
                        }))
                .observeOn(schedulers.io())
                .flatMapMaybe<Results<VN>> { _items: AccountItems ->
                    items = _items

                    val allIds = _items.vnlist.map { it.vn }
                            .union(_items.votelist.map { it.vn })
                            .union(_items.wishlist.map { it.vn })

                    val newIds = allIds.minus(vnlistRepository.getItems().blockingGet().map { it.vn })
                            .minus(votelistRepository.getItems().blockingGet().map { it.vn })
                            .minus(wishlistRepository.getItems().blockingGet().map { it.vn })

                    when {
                        allIds.isEmpty() -> Maybe.just(Results()) // empty account
                        newIds.isNotEmpty() -> { // should send get vn
                            val mergedIdsString = TextUtils.join(",", newIds)
                            val numberOfPages = Math.ceil(newIds.size * 1.0 / 25).toInt()

                            vndbServer.get<VN>("vn", "basic,details", "(id = [$mergedIdsString])",
                                    Options(fetchAllPages = true, numberOfPages = numberOfPages), type()).toMaybe()
                        }
                        else -> Maybe.empty() // nothing new: skipping DB update with an empty result
                    }
                }
                .observeOn(schedulers.io())
                .flatMapCompletable {
                    result = it
                    Completable.merge(listOf(
                            vnlistRepository.setItems(items.vnlist),
                            votelistRepository.setItems(items.votelist),
                            wishlistRepository.setItems(items.wishlist)
                    ))
                }
                .observeOn(schedulers.ui())
                .doFinally {
                    loadingData.value = false
                    disposables.remove(DISPOSABLE_LOGIN)
                }
                .subscribe(::onNext, ::onError)
    }

    private fun onNext() {
        Preferences.loggedIn = true
        vnData.value = result
    }

    private fun onError(throwable: Throwable) {
        if (BuildConfig.DEBUG) throwable.printStackTrace()
        errorData.value = throwable.localizedMessage
        errorData.value = null
    }

    companion object {
        private const val DISPOSABLE_LOGIN = "DISPOSABLE_LOGIN"
    }
}