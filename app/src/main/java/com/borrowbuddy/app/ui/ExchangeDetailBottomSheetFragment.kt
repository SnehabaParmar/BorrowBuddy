package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetExchangeDetailBinding
import com.borrowbuddy.app.databinding.ItemStepperStepBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.ExchangeItem
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.model.ExchangeType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ExchangeDetailBottomSheetFragment(
    private val exchangeId: String
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetExchangeDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetExchangeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateData()
    }

    private fun populateData() {
        val item = ExchangeRepository.findById(exchangeId) ?: run {
            dismiss()
            return
        }

        with(binding) {
            tvDetailExchangeCode.text = "${item.exchangeCode} • ${item.type.name}"
            tvDetailItemTitle.text = item.title
            tvDetailPriceDuration.text = "${item.priceLabel} • ${item.duration}"

            if (item.drawableRes != 0) {
                ivDetailItemImage.setImageResource(item.drawableRes)
            } else {
                ivDetailItemImage.setImageResource(R.drawable.bg_image_fallback)
            }

            // Status Badge & Description
            tvDetailStepLabel.text = "Step ${item.currentStepIndex} of 7"
            when (item.status) {
                ExchangeStatus.AWAITING_RESPONSE -> {
                    tvDetailStatusBadge.text = "Awaiting Response"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_pending_text))
                    tvDetailStatusDesc.text = if (item.type == ExchangeType.BORROW) {
                        "Your request has been sent to ${item.ownerName}. Awaiting their confirmation."
                    } else {
                        "${item.borrowerName} has requested to borrow this item. Please review and respond."
                    }
                }
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.HANDOVER_PENDING -> {
                    tvDetailStatusBadge.text = "Handover Pending"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_active_text))
                    tvDetailStatusDesc.text = "Exchange accepted! Meet at ${item.pickupSpot} to verify handover."
                }
                ExchangeStatus.BORROWED -> {
                    tvDetailStatusBadge.text = "Borrowed (Active)"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_borrowed)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_borrowed_text))
                    tvDetailStatusDesc.text = "Item is actively borrowed. Expected return: ${item.expectedReturnDate}."
                }
                ExchangeStatus.RETURN_PENDING -> {
                    tvDetailStatusBadge.text = "Return Pending"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_return)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_return_text))
                    tvDetailStatusDesc.text = "Return initiated. Owner must verify receipt to finalize exchange."
                }
                ExchangeStatus.COMPLETED -> {
                    tvDetailStatusBadge.text = "Completed"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_completed_text))
                    tvDetailStatusDesc.text = "Exchange successfully completed and returned. Thank you for sharing!"
                }
                ExchangeStatus.DECLINED -> {
                    tvDetailStatusBadge.text = "Declined"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_declined)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_declined_text))
                    tvDetailStatusDesc.text = "This exchange request was declined."
                }
                ExchangeStatus.CANCELLED -> {
                    tvDetailStatusBadge.text = "Cancelled"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_completed_text))
                    tvDetailStatusDesc.text = "This exchange request was cancelled."
                }
                ExchangeStatus.OVERDUE -> {
                    tvDetailStatusBadge.text = "Overdue"
                    tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_badge_declined)
                    tvDetailStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_declined_text))
                    tvDetailStatusDesc.text = "This item has passed the agreed return time."
                }
                else -> {
                    tvDetailStatusBadge.text = item.status.displayName
                }
            }

            // Stepper Render (7 steps)
            renderStepper(item)

            // People details
            tvOwnerName.text = item.ownerName
            tvOwnerDept.text = item.ownerDept
            tvOwnerAvatar.text = item.ownerName.take(1).uppercase()

            tvBorrowerName.text = item.borrowerName
            tvBorrowerDept.text = item.borrowerDept
            tvBorrowerAvatar.text = item.borrowerName.take(1).uppercase()

            // Contextual navigation to Public Student Profiles
            val openOwnerProfile: (View) -> Unit = {
                val uid = if (item.ownerUid.isNotBlank()) item.ownerUid else null
                com.borrowbuddy.app.StudentProfileActivity.start(requireContext(), uid, item.ownerName)
            }
            tvOwnerName.setOnClickListener(openOwnerProfile)
            tvOwnerAvatar.setOnClickListener(openOwnerProfile)

            val openBorrowerProfile: (View) -> Unit = {
                val uid = if (item.borrowerUid.isNotBlank()) item.borrowerUid else null
                com.borrowbuddy.app.StudentProfileActivity.start(requireContext(), uid, item.borrowerName)
            }
            tvBorrowerName.setOnClickListener(openBorrowerProfile)
            tvBorrowerAvatar.setOnClickListener(openBorrowerProfile)

            // Terms
            tvDetailSpot.text = item.pickupSpot
            tvDetailReturnDate.text = if (item.expectedReturnDate.isNotEmpty()) item.expectedReturnDate else "Not specified"
            tvDetailDeposit.text = item.securityDeposit

            if (item.borrowerNote.isNotEmpty()) {
                layoutDetailNoteRow.visibility = View.VISIBLE
                tvDetailNote.text = "\"${item.borrowerNote}\""
            } else {
                layoutDetailNoteRow.visibility = View.GONE
            }

            // Action Buttons
            setupActionButtons(item)

            btnCloseDetail.setOnClickListener {
                dismiss()
            }
        }
    }

    private fun renderStepper(item: ExchangeItem) {
        val container = binding.layoutStepperContainer
        container.removeAllViews()

        val steps = listOf(
            Pair("Request Sent", "Borrow request submitted"),
            Pair("Owner Accepted", "Owner reviewed and approved request"),
            Pair("Item Handover", "Meet in person & verify exchange pass"),
            Pair("Borrowing Active", "Item in borrower's possession"),
            Pair("Return Initiated", "Borrower requested return meetup"),
            Pair("Return Confirmed", "Owner verified item returned safely"),
            Pair("Exchange Completed", "Exchange finished & feedback recorded")
        )

        val currentIdx = item.currentStepIndex // 1 to 7

        steps.forEachIndexed { index, (title, desc) ->
            val stepNum = index + 1
            val stepBinding = ItemStepperStepBinding.inflate(layoutInflater, container, false)

            stepBinding.tvStepTitle.text = title
            stepBinding.tvStepDesc.text = desc

            if (stepNum == steps.size) {
                stepBinding.viewStepLine.visibility = View.GONE
            } else {
                stepBinding.viewStepLine.visibility = View.VISIBLE
            }

            when {
                stepNum < currentIdx -> {
                    // Completed step
                    stepBinding.tvStepNumber.visibility = View.GONE
                    stepBinding.ivStepCheck.visibility = View.VISIBLE
                    stepBinding.ivStepCheck.setBackgroundResource(R.drawable.bg_stepper_circle_completed)
                    stepBinding.tvStepTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_navy))
                    stepBinding.viewStepLine.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_turquoise))
                }
                stepNum == currentIdx -> {
                    // Current active step
                    stepBinding.tvStepNumber.visibility = View.VISIBLE
                    stepBinding.ivStepCheck.visibility = View.GONE
                    stepBinding.tvStepNumber.text = stepNum.toString()
                    stepBinding.tvStepNumber.setBackgroundResource(R.drawable.bg_stepper_circle_active)
                    stepBinding.tvStepNumber.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    stepBinding.tvStepTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_blue))
                    stepBinding.tvStepDesc.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_navy))
                    stepBinding.viewStepLine.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.border_light))
                }
                else -> {
                    // Future step
                    stepBinding.tvStepNumber.visibility = View.VISIBLE
                    stepBinding.ivStepCheck.visibility = View.GONE
                    stepBinding.tvStepNumber.text = stepNum.toString()
                    stepBinding.tvStepNumber.setBackgroundResource(R.drawable.bg_stepper_circle_pending)
                    stepBinding.tvStepNumber.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_slate))
                    stepBinding.tvStepTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_slate))
                    stepBinding.tvStepDesc.setTextColor(ContextCompat.getColor(requireContext(), R.color.input_hint))
                    stepBinding.viewStepLine.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.border_light))
                }
            }

            container.addView(stepBinding.root)
        }
    }

    private fun setupActionButtons(item: ExchangeItem) {
        with(binding) {
            btnDetailPrimary.isEnabled = true
            btnDetailSecondary.isEnabled = true
            btnDetailPrimary.alpha = 1.0f
            btnDetailSecondary.alpha = 1.0f

            if (item.type == ExchangeType.BORROW) {
                when (item.status) {
                    ExchangeStatus.AWAITING_RESPONSE -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Cancel Request"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_declined_text))
                        btnDetailPrimary.setOnClickListener {
                            ExchangeRepository.cancelRequest(item.id)
                            Toast.makeText(requireContext(), "Borrow request cancelled", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                    ExchangeStatus.ACCEPTED,
                    ExchangeStatus.HANDOVER_PENDING -> {
                        btnDetailSecondary.visibility = View.VISIBLE
                        btnDetailSecondary.text = "Confirm Collection"
                        btnDetailSecondary.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_navy))
                        btnDetailSecondary.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.border_light)
                        btnDetailSecondary.setOnClickListener {
                            ExchangeRepository.confirmHandover(item.id)
                            Toast.makeText(requireContext(), "Collection confirmed! Item is now with you.", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }

                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Show Handover QR Pass"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue))
                        btnDetailPrimary.setOnClickListener {
                            dismiss()
                            val campusItem = CampusItem(
                                id = item.id,
                                title = item.title,
                                pickupSpot = item.pickupSpot,
                                ownerName = item.ownerName,
                                department = item.ownerDept,
                                isFree = item.isFree,
                                priceLabel = item.priceLabel
                            )
                            val passSheet = ExchangePassBottomSheetFragment(
                                item = campusItem,
                                returnTime = if (item.expectedReturnDate.isNotEmpty()) item.expectedReturnDate else "Today, 5:00 PM",
                                borrowerNote = item.borrowerNote
                            )
                            passSheet.show(parentFragmentManager, "ExchangePassSheet")
                        }
                    }
                    ExchangeStatus.BORROWED -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Initiate Return"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_turquoise))
                        btnDetailPrimary.setOnClickListener {
                            ExchangeRepository.initiateReturn(item.id)
                            Toast.makeText(requireContext(), "Return initiated! Meet owner to return item.", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                    ExchangeStatus.RETURN_PENDING -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Return Awaiting Confirmation"
                        btnDetailPrimary.isEnabled = false
                    }
                    else -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Close"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_navy))
                        btnDetailPrimary.setOnClickListener { dismiss() }
                    }
                }
            } else {
                // LEND Tab
                when (item.status) {
                    ExchangeStatus.AWAITING_RESPONSE -> {
                        btnDetailSecondary.visibility = View.VISIBLE
                        btnDetailSecondary.text = "Decline"
                        btnDetailSecondary.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_declined_text))
                        btnDetailSecondary.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.status_declined_text)
                        btnDetailSecondary.setOnClickListener {
                            ExchangeRepository.declineRequest(item.id)
                            Toast.makeText(requireContext(), "Request declined", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }

                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Accept Request"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue))
                        btnDetailPrimary.setOnClickListener {
                            ExchangeRepository.acceptRequest(item.id)
                            Toast.makeText(requireContext(), "Request accepted! Scheduled for handover.", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                    ExchangeStatus.ACCEPTED,
                    ExchangeStatus.HANDOVER_PENDING -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Confirm Handover to Borrower"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue))
                        btnDetailPrimary.setOnClickListener {
                            ExchangeRepository.confirmHandover(item.id)
                            Toast.makeText(requireContext(), "Handover confirmed! Active lend started.", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                    ExchangeStatus.BORROWED -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Item With Borrower"
                        btnDetailPrimary.isEnabled = false
                    }
                    ExchangeStatus.RETURN_PENDING -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Confirm Return & Complete"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_turquoise))
                        btnDetailPrimary.setOnClickListener {
                            ExchangeRepository.confirmReturn(item.id)
                            Toast.makeText(requireContext(), "Item return verified! Exchange completed.", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    }
                    else -> {
                        btnDetailSecondary.visibility = View.GONE
                        btnDetailPrimary.visibility = View.VISIBLE
                        btnDetailPrimary.text = "Close"
                        btnDetailPrimary.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_navy))
                        btnDetailPrimary.setOnClickListener { dismiss() }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
