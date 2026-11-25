package com.borrowbuddy.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetItemDetailBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.util.ItemPhotoHelper
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ItemDetailBottomSheetFragment(
    private val item: CampusItem
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetItemDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        with(binding) {
            // Section 1: Media & Title
            tvDetailTitle.text = item.title
            tvDetailCategoryDept.text = "${formatCategoryName(item.category)} • ${item.department} Department"

            if (item.isFree) {
                tvDetailBadge.text = "FREE"
                tvDetailBadge.setBackgroundResource(R.drawable.bg_badge_free)
                tvDetailBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                tvDetailBadge.text = item.priceLabel
                tvDetailBadge.setBackgroundResource(R.drawable.bg_badge_paid)
                tvDetailBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // Multi-image display support: show thumbnails if > 1 remote photo available
            val imageUrls = item.validImageUrls
            if (imageUrls.size > 1) {
                layoutDetailThumbnails.visibility = View.VISIBLE
                val thumbViews = listOf(ivThumb0, ivThumb1, ivThumb2)

                fun selectImage(index: Int) {
                    if (index !in imageUrls.indices) return
                    val selectedUrl = imageUrls[index]
                    ItemPhotoHelper.clearGlide(context, ivDetailImage)
                    Glide.with(context)
                        .load(selectedUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.bg_image_fallback)
                        .into(ivDetailImage)

                    for ((i, thumbView) in thumbViews.withIndex()) {
                        if (i == index) {
                            thumbView.strokeColor = ContextCompat.getColorStateList(context, R.color.brand_blue)
                            thumbView.strokeWidth = 2f * context.resources.displayMetrics.density
                        } else {
                            thumbView.strokeColor = ContextCompat.getColorStateList(context, R.color.btn_secondary_border)
                            thumbView.strokeWidth = 1f * context.resources.displayMetrics.density
                        }
                    }
                }

                // Initial image selection
                selectImage(0)

                // Configure thumbnails
                for ((i, thumbView) in thumbViews.withIndex()) {
                    if (i < imageUrls.size) {
                        thumbView.visibility = View.VISIBLE
                        Glide.with(context)
                            .load(imageUrls[i])
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(thumbView)
                        thumbView.setOnClickListener { selectImage(i) }
                    } else {
                        thumbView.visibility = View.GONE
                    }
                }
            } else {
                layoutDetailThumbnails.visibility = View.GONE
                ItemPhotoHelper.loadItemPhoto(
                    context = context,
                    imageView = ivDetailImage,
                    item = item,
                    targetSizeDp = 240
                )
            }

            // Section 2: Lender's Description & Specs
            tvDetailDescription.text = if (item.description.isNotBlank()) item.description else "Available for campus peer lending. Please handle with care and return on schedule."
            tvSpecCondition.text = item.condition
            tvSpecDuration.text = item.maxDuration
            tvSpecDeposit.text = item.securityDeposit

            // Section 3: Lender Card (Trust)
            val initial = item.ownerName.firstOrNull()?.toString()?.uppercase() ?: "P"
            tvLenderAvatar.text = initial
            tvLenderName.text = item.ownerName
            tvLenderCourse.text = item.ownerCourse
            tvLenderRating.text = item.rating
            tvLenderLendsCount.text = "${item.completedLends} campus lends"

            // Direct contextual navigation to Public Student Profile
            val openStudentProfile: (View) -> Unit = {
                com.borrowbuddy.app.StudentProfileActivity.start(context, item.ownerUid, item.ownerName)
            }
            tvLenderAvatar.setOnClickListener(openStudentProfile)
            tvLenderName.setOnClickListener(openStudentProfile)
            tvLenderCourse.setOnClickListener(openStudentProfile)

            btnMessageLender.setOnClickListener {
                Toast.makeText(context, "Opening direct chat with ${item.ownerName}...", Toast.LENGTH_SHORT).show()
            }

            // Section 4: Pickup & Settlement Info
            tvDetailSpot.text = item.pickupSpot

            if (item.isFree) {
                tvSettlementNote.text = "Free Peer Lending (₹0) — Free for students. No platform fees or deposit required."
            } else {
                tvSettlementNote.text = "${item.priceLabel} — Settle directly with lender at pickup (Cash / UPI). No platform checkout fee."
            }

            // Section 5: Sticky Bottom CTA -> Opens Borrow Request Confirmation
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: com.borrowbuddy.app.model.StudentProfileRepository.getCurrentUserProfile().id
            val isMyItem = item.ownerUid.isNotBlank() && item.ownerUid == currentUid
            val isAvailable = com.borrowbuddy.app.model.ExchangeRepository.isItemAvailable(item.id)

            if (isMyItem) {
                btnRequestBorrow.text = "Your Listing"
                btnRequestBorrow.isEnabled = false
                btnRequestBorrow.alpha = 0.6f
            } else if (!isAvailable) {
                btnRequestBorrow.text = "Currently Unavailable (Lent Out)"
                btnRequestBorrow.isEnabled = false
                btnRequestBorrow.alpha = 0.6f
            } else {
                btnRequestBorrow.isEnabled = true
                btnRequestBorrow.alpha = 1.0f
                btnRequestBorrow.setOnClickListener {
                    dismiss()
                    val confirmSheet = BorrowConfirmBottomSheetFragment(item)
                    confirmSheet.show(parentFragmentManager, "BorrowConfirmSheet")
                }
            }

            btnCloseDetail.setOnClickListener {
                dismiss()
            }
        }
    }

    private fun formatCategoryName(cat: String): String {
        return when (cat.uppercase()) {
            "TECH" -> "Tech & Gadgets"
            "BOOKS" -> "Books & Notes"
            "LAB" -> "Lab & Study"
            "EVENT" -> "Events & Audio/Video"
            "STATIONERY" -> "Stationery & Tools"
            "SPORTS" -> "Sports & Fitness"
            "BUSINESS" -> "Business & Management"
            "CAMPUS" -> "Campus & Admin"
            else -> "Campus Utilities"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
