package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetExploreFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

enum class ExploreSortOption(val label: String) {
    RECENTLY_ADDED("Recently Added"),
    NEAREST_FIRST("Nearest First"),
    LOWEST_PRICE("Lowest Price"),
    HIGHEST_PRICE("Highest Price"),
    HIGHEST_RATED("Highest Rated")
}

data class ExploreFilterCriteria(
    val sortBy: ExploreSortOption = ExploreSortOption.RECENTLY_ADDED,
    val priceType: String = "ALL", // ALL, FREE, PAID
    val zone: String = "ALL", // ALL, LIB, IMBA, LAB, CANTEEN
    val condition: String = "ALL" // ALL, LIKE_NEW
) {
    fun isFiltered(): Boolean {
        return sortBy != ExploreSortOption.RECENTLY_ADDED ||
                priceType != "ALL" ||
                zone != "ALL" ||
                condition != "ALL"
    }
}

class ExploreFilterBottomSheetFragment(
    private val currentCriteria: ExploreFilterCriteria,
    private val onApply: (ExploreFilterCriteria) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetExploreFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetExploreFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial sort option
        when (currentCriteria.sortBy) {
            ExploreSortOption.RECENTLY_ADDED -> binding.rbSortRecent.isChecked = true
            ExploreSortOption.NEAREST_FIRST -> binding.rbSortNearest.isChecked = true
            ExploreSortOption.LOWEST_PRICE -> binding.rbSortLowestPrice.isChecked = true
            ExploreSortOption.HIGHEST_PRICE -> binding.rbSortHighestPrice.isChecked = true
            ExploreSortOption.HIGHEST_RATED -> binding.rbSortHighestRated.isChecked = true
        }

        // Set price type
        when (currentCriteria.priceType) {
            "FREE" -> binding.rbPriceFree.isChecked = true
            "PAID" -> binding.rbPricePaid.isChecked = true
            else -> binding.rbPriceAll.isChecked = true
        }

        // Set campus zone
        when (currentCriteria.zone) {
            "LIB" -> binding.rbZoneLibrary.isChecked = true
            "IMBA" -> binding.rbZoneIMBA.isChecked = true
            "LAB" -> binding.rbZoneLab.isChecked = true
            "CANTEEN" -> binding.rbZoneCanteen.isChecked = true
            else -> binding.rbZoneAll.isChecked = true
        }

        // Set condition
        when (currentCriteria.condition) {
            "LIKE_NEW" -> binding.rbCondLikeNew.isChecked = true
            else -> binding.rbCondAll.isChecked = true
        }

        binding.btnApplyFilters.setOnClickListener {
            val selectedSort = when (binding.rgSortBy.checkedRadioButtonId) {
                R.id.rbSortNearest -> ExploreSortOption.NEAREST_FIRST
                R.id.rbSortLowestPrice -> ExploreSortOption.LOWEST_PRICE
                R.id.rbSortHighestPrice -> ExploreSortOption.HIGHEST_PRICE
                R.id.rbSortHighestRated -> ExploreSortOption.HIGHEST_RATED
                else -> ExploreSortOption.RECENTLY_ADDED
            }

            val selectedPrice = when (binding.rgPriceType.checkedRadioButtonId) {
                R.id.rbPriceFree -> "FREE"
                R.id.rbPricePaid -> "PAID"
                else -> "ALL"
            }

            val selectedZone = when (binding.rgZone.checkedRadioButtonId) {
                R.id.rbZoneLibrary -> "LIB"
                R.id.rbZoneIMBA -> "IMBA"
                R.id.rbZoneLab -> "LAB"
                R.id.rbZoneCanteen -> "CANTEEN"
                else -> "ALL"
            }

            val selectedCond = when (binding.rgCondition.checkedRadioButtonId) {
                R.id.rbCondLikeNew -> "LIKE_NEW"
                else -> "ALL"
            }

            onApply(
                ExploreFilterCriteria(
                    sortBy = selectedSort,
                    priceType = selectedPrice,
                    zone = selectedZone,
                    condition = selectedCond
                )
            )
            dismiss()
        }

        binding.btnResetFilters.setOnClickListener {
            binding.rbSortRecent.isChecked = true
            binding.rbPriceAll.isChecked = true
            binding.rbZoneAll.isChecked = true
            binding.rbCondAll.isChecked = true
            onApply(ExploreFilterCriteria())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
