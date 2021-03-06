package com.booboot.vndbandroid.ui.vnlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.booboot.vndbandroid.R
import com.booboot.vndbandroid.diff.VNDiffCallback
import com.booboot.vndbandroid.model.vndb.AccountItems
import com.booboot.vndbandroid.model.vndb.VN

class VNAdapter(
    private val onVnClicked: (View, VN) -> Unit,
    private val showFullDate: Boolean = false,
    private val showRank: Boolean = false,
    private val showRating: Boolean = false,
    private val showPopularity: Boolean = false,
    private val showVoteCount: Boolean = false
) : RecyclerView.Adapter<VNHolder>(), Filterable {
    var items = AccountItems()
        set(value) {
            field = value
            filter.filter(filterString)
        }
    private var filteredVns = items
    private val mFilter = ItemFilter()
    var filterString: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VNHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.vn_card, parent, false)
        return VNHolder(v, onVnClicked)
    }

    override fun onBindViewHolder(holder: VNHolder, position: Int) {
        val vn = filteredVns.vns.values.toList()[position]
        holder.onBind(
            vn,
            filteredVns.vnlist[vn.id],
            filteredVns.votelist[vn.id],
            filteredVns.wishlist[vn.id],
            showFullDate,
            showRank,
            showRating,
            showPopularity,
            showVoteCount
        )
    }

    override fun getItemCount() = filteredVns.vns.values.size

    override fun getFilter(): Filter = mFilter

    private inner class ItemFilter : Filter() {
        override fun performFiltering(constraint: CharSequence): Filter.FilterResults {
            filterString = constraint.toString().trim().toLowerCase()
            val results = Filter.FilterResults()
            val newVns = mutableMapOf<Long, VN>()

            items.vns.forEach {
                if (it.value.title.trim().toLowerCase().contains(filterString)) {
                    newVns[it.key] = it.value
                }
            }

            val newItems = items.copy()
            newItems.vns = newVns

            val diffResult = DiffUtil.calculateDiff(VNDiffCallback(filteredVns, newItems))
            results.values = VNFilterResults(newItems, diffResult)

            return results
        }

        override fun publishResults(constraint: CharSequence, results: Filter.FilterResults) {
            val filterResults = results.values as? VNFilterResults ?: return
            filteredVns = filterResults.items
            filterResults.diffResult.dispatchUpdatesTo(this@VNAdapter)
        }
    }

    data class VNFilterResults(
        val items: AccountItems,
        val diffResult: DiffUtil.DiffResult
    )
}