package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.borrowbuddy.app.MainActivity
import com.borrowbuddy.app.databinding.FragmentBottomSheetExchangePassBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.util.QRCodeHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.random.Random

class ExchangePassBottomSheetFragment(
    private val item: CampusItem,
    private val returnTime: String = "Today, 5:00 PM",
    private val borrowerNote: String = ""
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetExchangePassBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetExchangePassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // Generate a 4-digit PIN (e.g. 4892)
            val pin = 1000 + Random.nextInt(9000)
            tvHandoverPin.text = "PIN: $pin"

            // Generate crisp Handover QR bitmap
            val qrSeed = "BORROW_${item.id}_${item.ownerName}_$pin"
            val qrBitmap = QRCodeHelper.generateHandoverQR(qrSeed, size = 400)
            ivHandoverQR.setImageBitmap(qrBitmap)

            val zoneName = item.pickupSpot.split("•").first().trim()
            tvHandoverInstructions.text =
                "Show this QR to ${item.ownerName} at $zoneName during pickup & return to verify handover."

            tvPassItemTitle.text = item.title
            tvPassDueTime.text = "Due: $returnTime"
            tvPassSpot.text = "Handover Spot: $zoneName"

            btnClosePass.setOnClickListener {
                dismiss()
            }

            // "View in Exchanges" switches to the 4th bottom tab (Exchanges) and highlights Pending requests
            btnViewInExchanges.setOnClickListener {
                dismiss()
                ExchangesFragment.navigateTo(com.borrowbuddy.app.model.ExchangeType.BORROW, "PENDING")
                (activity as? MainActivity)?.navigateToTab(3)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
