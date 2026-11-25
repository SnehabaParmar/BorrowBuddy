package com.borrowbuddy.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.ItemMyListingGridCardBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.util.ItemPhotoHelper

class MyListingsAdapter(
    private var items: List<CampusItem>,
    private val onItemClick: (CampusItem) -> Unit
) : RecyclerView.Adapter<MyListingsAdapter.ListingViewHolder>() {

    inner class ListingViewHolder(val binding: ItemMyListingGridCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListingViewHolder {
        val binding = ItemMyListingGridCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ListingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListingViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        with(holder.binding) {
            tvItemTitle.text = item.title
            tvItemCategoryCondition.text = "${item.category} • ${item.condition}"
            tvItemSpot.text = item.pickupSpot
            tvItemDuration.text = item.maxDuration
            tvRating.text = item.rating

            // Price badge
            if (item.isFree) {
                tvPriceBadge.text = "FREE"
                tvPriceBadge.setBackgroundResource(R.drawable.bg_badge_free)
                tvPriceBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                tvPriceBadge.text = item.priceLabel
                tvPriceBadge.setBackgroundResource(R.drawable.bg_badge_paid)
                tvPriceBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // Availability badge
            val isAvail = ExchangeRepository.isItemAvailable(item.id)
            if (isAvail) {
                tvAvailabilityBadge.text = "Available"
                tvAvailabilityBadge.setBackgroundResource(R.drawable.bg_badge_free)
                tvAvailabilityBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                tvAvailabilityBadge.text = "Lent Out"
                tvAvailabilityBadge.setBackgroundResource(R.drawable.bg_badge_active)
                tvAvailabilityBadge.setTextColor(ContextCompat.getColor(context, R.color.status_active_text))
            }

            // Image handling: remote Cloudinary HTTPS via Glide, local on-device file, or category fallback
            ItemPhotoHelper.loadItemPhoto(
                context = context,
                imageView = ivItemImage,
                item = item,
                fallbackContainer = layoutFallback,
                fallbackIcon = ivFallbackIcon,
                targetSizeDp = 140
            )

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<CampusItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun getFallbackDrawable(item: CampusItem): Int {
        val titleLower = item.title.lowercase()
        val descLower = item.description.lowercase()
        val cat = item.category.uppercase()

        return when {
            titleLower.contains("calc") || descLower.contains("calc") -> R.drawable.item_calculator
            titleLower.contains("dongle") || titleLower.contains("hdmi") || titleLower.contains("hub") || descLower.contains("type-c") -> R.drawable.item_dongle
            titleLower.contains("note") || titleLower.contains("dsa") || titleLower.contains("study") -> R.drawable.item_notes
            titleLower.contains("arduino") || titleLower.contains("robot") || titleLower.contains("sensor") -> R.drawable.item_arduino
            titleLower.contains("coat") || titleLower.contains("apron") || titleLower.contains("lab") -> R.drawable.item_lab_coat
            titleLower.contains("tripod") || titleLower.contains("camera") || titleLower.contains("mount") -> R.drawable.item_tripod
            titleLower.contains("cable") || titleLower.contains("charger") || titleLower.contains("usb") -> R.drawable.item_cables
            titleLower.contains("book") || titleLower.contains("dbms") || titleLower.contains("concept") -> R.drawable.item_dbms_book
            titleLower.contains("badminton") || titleLower.contains("racket") || titleLower.contains("sport") -> R.drawable.item_badminton
            titleLower.contains("clicker") || titleLower.contains("presenter") || titleLower.contains("remote") -> R.drawable.item_clicker
            cat == "BOOKS" -> R.drawable.item_notes
            cat == "LAB" -> R.drawable.item_lab_coat
            cat == "EVENT" -> R.drawable.item_tripod
            cat == "SPORTS" -> R.drawable.item_badminton
            cat == "STATIONERY" -> R.drawable.item_calculator
            else -> R.drawable.item_calculator
        }
    }

    private fun getCategoryIcon(category: String): Int {
        return when (category.uppercase()) {
            "TECH" -> R.drawable.ic_cat_tech
            "BOOKS" -> R.drawable.ic_cat_books
            "LAB" -> R.drawable.ic_cat_lab
            "EVENT" -> R.drawable.ic_cat_event
            "STATIONERY" -> R.drawable.ic_cat_stationery
            "SPORTS" -> R.drawable.ic_cat_sports
            "BUSINESS" -> R.drawable.ic_cat_business
            "CAMPUS" -> R.drawable.ic_cat_campus
            else -> R.drawable.ic_cat_tech
        }
    }
}
