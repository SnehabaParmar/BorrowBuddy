package com.borrowbuddy.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.ItemTrendingCardBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.util.ItemPhotoHelper

class TrendingItemAdapter(
    private var items: List<CampusItem>,
    private val onItemClick: (CampusItem) -> Unit
) : RecyclerView.Adapter<TrendingItemAdapter.TrendingViewHolder>() {

    inner class TrendingViewHolder(val binding: ItemTrendingCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val binding = ItemTrendingCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrendingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        with(holder.binding) {
            tvTrendingTitle.text = item.title
            tvTrendingSpot.text = item.pickupSpot

            val firstName = item.ownerName.split(" ").firstOrNull() ?: item.ownerName
            tvTrendingOwnerName.text = firstName
            tvTrendingOwnerAvatar.text = firstName.firstOrNull()?.toString()?.uppercase() ?: "P"
            tvTrendingRating.text = item.rating

            // Direct contextual navigation to Public Student Profile
            tvTrendingOwnerName.setOnClickListener {
                com.borrowbuddy.app.StudentProfileActivity.start(context, item.ownerUid, item.ownerName)
            }
            tvTrendingOwnerAvatar.setOnClickListener {
                com.borrowbuddy.app.StudentProfileActivity.start(context, item.ownerUid, item.ownerName)
            }

            // Badge
            if (item.isFree) {
                tvTrendingBadge.text = "FREE"
                tvTrendingBadge.setBackgroundResource(R.drawable.bg_badge_free)
                tvTrendingBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                tvTrendingBadge.text = item.priceLabel
                tvTrendingBadge.setBackgroundResource(R.drawable.bg_badge_paid)
                tvTrendingBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // Image: remote Cloudinary HTTPS via Glide, local on-device file, or category fallback
            ItemPhotoHelper.loadItemPhoto(
                context = context,
                imageView = ivTrendingImage,
                item = item,
                fallbackContainer = layoutTrendingFallback,
                fallbackIcon = ivTrendingFallbackIcon,
                targetSizeDp = 140
            )

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private fun getFallbackDrawable(item: CampusItem): Int {
        val titleLower = item.title.lowercase()
        val cat = item.category.uppercase()
        return when {
            titleLower.contains("calc") -> R.drawable.item_calculator
            titleLower.contains("dongle") || titleLower.contains("hdmi") -> R.drawable.item_dongle
            titleLower.contains("note") || titleLower.contains("dsa") -> R.drawable.item_notes
            titleLower.contains("arduino") || titleLower.contains("robot") -> R.drawable.item_arduino
            titleLower.contains("coat") || titleLower.contains("lab") -> R.drawable.item_lab_coat
            titleLower.contains("tripod") || titleLower.contains("camera") -> R.drawable.item_tripod
            titleLower.contains("cable") || titleLower.contains("charger") -> R.drawable.item_cables
            titleLower.contains("book") || titleLower.contains("dbms") -> R.drawable.item_dbms_book
            titleLower.contains("badminton") -> R.drawable.item_badminton
            titleLower.contains("clicker") -> R.drawable.item_clicker
            cat == "TECH" -> R.drawable.item_calculator
            cat == "BOOKS" -> R.drawable.item_notes
            cat == "LAB" -> R.drawable.item_lab_coat
            cat == "EVENT" -> R.drawable.item_tripod
            cat == "SPORTS" -> R.drawable.item_badminton
            else -> R.drawable.item_calculator
        }
    }

    private fun getCategoryIcon(cat: String): Int {
        return when (cat.uppercase()) {
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

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<CampusItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
