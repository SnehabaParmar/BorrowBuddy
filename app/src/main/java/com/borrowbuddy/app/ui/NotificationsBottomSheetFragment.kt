package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.borrowbuddy.app.databinding.FragmentBottomSheetNotificationsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NotificationsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
