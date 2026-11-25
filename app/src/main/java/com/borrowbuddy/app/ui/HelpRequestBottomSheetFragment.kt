package com.borrowbuddy.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.borrowbuddy.app.databinding.FragmentBottomSheetHelpRequestBinding
import com.borrowbuddy.app.model.GlanceRequest
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HelpRequestBottomSheetFragment(
    private val request: GlanceRequest,
    private val onMarkFulfilled: ((GlanceRequest) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetHelpRequestBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetHelpRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindRequestDetails()
        setupActions()
    }

    private fun bindRequestDetails() {
        binding.tvHelpAvatar.text = request.avatarLetter
        binding.tvHelpRequesterName.text = request.name
        binding.tvHelpItemName.text = request.item
        binding.tvHelpLocation.text = "Needed at: ${request.location}"
        binding.tvHelpNeededBy.text = "Needed by: ${request.neededBy}"

        if (request.description.isNotBlank()) {
            binding.tvHelpDescription.text = request.description
            binding.tvHelpDescription.visibility = View.VISIBLE
        } else {
            binding.tvHelpDescription.visibility = View.GONE
        }

        binding.btnCloseHelpSheet.setOnClickListener {
            dismiss()
        }
    }

    private fun setupActions() {
        binding.btnConfirmHelp.setOnClickListener {
            binding.layoutHelpSuccessState.visibility = View.VISIBLE
            binding.tvHelpSuccessMsg.text =
                "Awesome! We've notified ${request.name} that you can help with \"${request.item}\" at ${request.location}."
            binding.btnConfirmHelp.text = "Assistance Confirmed ✓"
            binding.btnConfirmHelp.isEnabled = false

            Toast.makeText(
                requireContext(),
                "Thank you for helping ${request.name}!",
                Toast.LENGTH_SHORT
            ).show()

            Handler(Looper.getMainLooper()).postDelayed({
                if (isAdded) dismiss()
            }, 2000)
        }

        binding.btnMarkFulfilled.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Request marked as fulfilled!",
                Toast.LENGTH_SHORT
            ).show()
            onMarkFulfilled?.invoke(request.copy(state = "FULFILLED"))
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
