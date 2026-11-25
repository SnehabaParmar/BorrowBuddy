package com.borrowbuddy.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.ItemCampusCardBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.util.ItemPhotoHelper

class CampusItemAdapter(
    private var items: List<CampusItem>,
    private val onItemClick: (CampusItem) -> Unit
) : RecyclerView.Adapter<CampusItemAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(val binding: ItemCampusCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemCampusCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        with(holder.binding) {
            tvItemTitle.text = item.title
            tvItemSpot.text = item.pickupSpot
            
            // First name only for clean footer row (e.g. "Priya" from "Priya Patel")
            val firstName = item.ownerName.split(" ").firstOrNull() ?: item.ownerName
            tvOwnerName.text = firstName
            tvRating.text = item.rating
            tvOwnerAvatar.text = firstName.firstOrNull()?.toString()?.uppercase() ?: "S"

            // Direct contextual navigation to Public Student Profile
            tvOwnerName.setOnClickListener {
                com.borrowbuddy.app.StudentProfileActivity.start(context, item.ownerUid, item.ownerName)
            }
            tvOwnerAvatar.setOnClickListener {
                com.borrowbuddy.app.StudentProfileActivity.start(context, item.ownerUid, item.ownerName)
            }

            // Badge styling: "FREE" in #16C7BC or "₹[amount]/day" in #0B2A6F (bold white text)
            if (item.isFree) {
                tvBadge.text = "FREE"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_free)
                tvBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                tvBadge.text = item.priceLabel
                tvBadge.setBackgroundResource(R.drawable.bg_badge_paid)
                tvBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // Image Rendering: remote Cloudinary HTTPS via Glide, local on-device file, or category fallback
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

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<CampusItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
