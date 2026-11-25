package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

data class FilterCriteria(
    val price: String = "ALL", // ALL, FREE, PAID
    val department: String = "ALL", // ALL, IMBA, IMSC
    val zone: String = "ALL" // ALL, LIB, IMBA, LAB, CANTEEN
)

class FilterBottomSheetFragment(
    private val currentCriteria: FilterCriteria,
    private val onApply: (FilterCriteria) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial state
        when (currentCriteria.price) {
            "FREE" -> binding.rbPriceFree.isChecked = true
            "PAID" -> binding.rbPricePaid.isChecked = true
            else -> binding.rbPriceAll.isChecked = true
        }

        when (currentCriteria.department) {
            "IMBA" -> binding.rbDeptIMBA.isChecked = true
            "IMSC" -> binding.rbDeptIMSC.isChecked = true
            else -> binding.rbDeptAll.isChecked = true
        }

        when (currentCriteria.zone) {
            "LIB" -> binding.rbZoneLib.isChecked = true
            "IMBA" -> binding.rbZoneIMBA.isChecked = true
            "LAB" -> binding.rbZoneLab.isChecked = true
            "CANTEEN" -> binding.rbZoneCanteen.isChecked = true
            else -> binding.rbZoneAll.isChecked = true
        }

        binding.btnApplyFilters.setOnClickListener {
            val selectedPrice = when (binding.rgFilterPrice.checkedRadioButtonId) {
                R.id.rbPriceFree -> "FREE"
                R.id.rbPricePaid -> "PAID"
                else -> "ALL"
            }

            val selectedDept = when (binding.rgFilterDept.checkedRadioButtonId) {
                R.id.rbDeptIMBA -> "IMBA"
                R.id.rbDeptIMSC -> "IMSC"
                else -> "ALL"
            }

            val selectedZone = when (binding.rgFilterZone.checkedRadioButtonId) {
                R.id.rbZoneLib -> "LIB"
                R.id.rbZoneIMBA -> "IMBA"
                R.id.rbZoneLab -> "LAB"
                R.id.rbZoneCanteen -> "CANTEEN"
                else -> "ALL"
            }

            onApply(FilterCriteria(selectedPrice, selectedDept, selectedZone))
            dismiss()
        }

        binding.btnResetFilters.setOnClickListener {
            binding.rbPriceAll.isChecked = true
            binding.rbDeptAll.isChecked = true
            binding.rbZoneAll.isChecked = true
            onApply(FilterCriteria("ALL", "ALL", "ALL"))
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
