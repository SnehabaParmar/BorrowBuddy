package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.borrowbuddy.app.databinding.FragmentBottomSheetActionChoiceBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ActionChoiceBottomSheetFragment(
    private val onChooseLend: () -> Unit,
    private val onChooseRequest: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetActionChoiceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetActionChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardOptionLend.setOnClickListener {
            dismiss()
            onChooseLend()
        }

        binding.cardOptionRequest.setOnClickListener {
            dismiss()
            onChooseRequest()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
