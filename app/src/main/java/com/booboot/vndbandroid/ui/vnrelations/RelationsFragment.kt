package com.booboot.vndbandroid.ui.vnrelations

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.booboot.vndbandroid.R
import com.booboot.vndbandroid.model.vndb.Anime
import com.booboot.vndbandroid.model.vndb.Relation
import com.booboot.vndbandroid.model.vndb.VN
import com.booboot.vndbandroid.ui.base.BaseFragment
import com.booboot.vndbandroid.ui.vndetails.VNDetailsActivity
import com.booboot.vndbandroid.util.GridAutofitLayoutManager
import com.booboot.vndbandroid.util.Pixels
import kotlinx.android.synthetic.main.relations_fragment.*

class RelationsFragment : BaseFragment(), (Anime?, Relation?) -> Unit {
    override val layout: Int = R.layout.relations_fragment
    private lateinit var viewModel: RelationsViewModel
    private lateinit var adapter: RelationsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context?.let {
            recyclerView.layoutManager = GridAutofitLayoutManager(it, Pixels.px(300))
            adapter = RelationsAdapter(this)
            recyclerView.adapter = adapter

            val vnId = arguments?.getLong(VNDetailsActivity.EXTRA_VN_ID) ?: 0
            viewModel = ViewModelProviders.of(this).get(RelationsViewModel::class.java)
            viewModel.vnData.observe(this, Observer { showVn(it) })
            viewModel.errorData.observe(this, Observer { showError(it) })
            viewModel.loadVn(vnId, false)
        }
    }

    private fun showVn(vn: VN?) {
        vn ?: return
        adapter.anime = vn.anime
        adapter.relations = vn.relations
    }

    override fun invoke(anime: Anime?, relation: Relation?) {
        // TODO
    }
}