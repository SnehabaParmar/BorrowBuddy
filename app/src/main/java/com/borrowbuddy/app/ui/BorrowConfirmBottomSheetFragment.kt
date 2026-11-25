package com.borrowbuddy.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetBorrowConfirmBinding
import com.borrowbuddy.app.model.CampusItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BorrowConfirmBottomSheetFragment(
    private val item: CampusItem
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetBorrowConfirmBinding? = null
    private val binding get() = _binding!!

    private var selectedReturnTime = "Today, 5:00 PM"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetBorrowConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        with(binding) {
            tvConfirmItemTitle.text = item.title
            tvConfirmLenderText.text = "Lender: ${item.ownerName} (${item.department})"
            tvConfirmPickupSpot.text = item.pickupSpot

            if (item.isFree) {
                tvConfirmBadge.text = "FREE"
                tvConfirmBadge.setBackgroundResource(R.drawable.bg_badge_free)
                tvConfirmBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                tvConfirmBadge.text = item.priceLabel
                tvConfirmBadge.setBackgroundResource(R.drawable.bg_badge_paid)
                tvConfirmBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // Image
            if (item.imageUri.isNotBlank()) {
                try {
                    ivConfirmItemImage.setImageURI(Uri.parse(item.imageUri))
                } catch (_: Exception) {
                    ivConfirmItemImage.setImageResource(if (item.drawableRes != 0) item.drawableRes else R.drawable.item_calculator)
                }
            } else if (item.drawableRes != 0) {
                ivConfirmItemImage.setImageResource(item.drawableRes)
            } else {
                ivConfirmItemImage.setImageResource(R.drawable.item_calculator)
            }

            // Chips selection
            val chips = listOf(
                chipReturnToday5pm to "Today, 5:00 PM",
                chipReturnTomorrow to "Tomorrow, 12:00 PM",
                chipReturn3Days to "In 3 Days"
            )

            chips.forEach { (chipView, timeLabel) ->
                chipView.setOnClickListener {
                    selectedReturnTime = timeLabel
                    updateChipsUI(chips, timeLabel)
                }
            }

            btnCloseConfirm.setOnClickListener {
                dismiss()
            }

            btnSendBorrowRequest.setOnClickListener {
                val note = etBorrowNote.text?.toString()?.trim().orEmpty()
                dismiss()

                // Generate PIN and register live exchange item
                val pin = (1000 + kotlin.random.Random.nextInt(9000)).toString()
                com.borrowbuddy.app.model.ExchangeRepository.addBorrowRequest(
                    item = item,
                    returnTime = selectedReturnTime,
                    borrowerNote = note,
                    pin = pin
                )

                // Generate and open the Exchange Pass with Handover QR
                val passSheet = ExchangePassBottomSheetFragment(
                    item = item,
                    returnTime = selectedReturnTime,
                    borrowerNote = note
                )
                passSheet.show(parentFragmentManager, "ExchangePassSheet")
            }
        }
    }

    private fun updateChipsUI(chips: List<Pair<TextView, String>>, selected: String) {
        val context = context ?: return
        chips.forEach { (chip, label) ->
            if (label == selected) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                chip.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
