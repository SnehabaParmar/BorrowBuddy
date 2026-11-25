package com.borrowbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.borrowbuddy.app.adapter.MyListingsAdapter
import com.borrowbuddy.app.databinding.ActivityMyListingsBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.StudentProfile
import com.borrowbuddy.app.model.StudentProfileRepository
import com.borrowbuddy.app.ui.ItemDetailBottomSheetFragment

class MyListingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyListingsBinding
    private lateinit var listingsAdapter: MyListingsAdapter
    private val myItems = mutableListOf<CampusItem>()

    private val profileChangeListener: (StudentProfile) -> Unit = {
        runOnUiThread {
            refreshListings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMyListingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMyListings) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupHeader()
        setupRecyclerView()
        refreshListings()
    }

    override fun onStart() {
        super.onStart()
        StudentProfileRepository.addProfileChangeListener(profileChangeListener)
    }

    override fun onStop() {
        super.onStop()
        StudentProfileRepository.removeProfileChangeListener(profileChangeListener)
    }

    override fun onResume() {
        super.onResume()
        refreshListings()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAddListing.setOnClickListener {
            startActivity(Intent(this, ListItemActivity::class.java))
        }

        binding.btnListFirstItem.setOnClickListener {
            startActivity(Intent(this, ListItemActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        listingsAdapter = MyListingsAdapter(myItems) { clickedItem ->
            val detailSheet = ItemDetailBottomSheetFragment(clickedItem)
            detailSheet.show(supportFragmentManager, "ItemDetailFromMyListings")
        }

        binding.rvMyListings.apply {
            layoutManager = GridLayoutManager(this@MyListingsActivity, 2)
            adapter = listingsAdapter
        }
    }

    private fun refreshListings() {
        myItems.clear()
        myItems.addAll(StudentProfileRepository.getCurrentUserItems())
        listingsAdapter.notifyDataSetChanged()

        val count = myItems.size
        if (count == 0) {
            binding.rvMyListings.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvListingCountBadge.visibility = View.GONE
        } else {
            binding.rvMyListings.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            binding.tvListingCountBadge.visibility = View.VISIBLE
            binding.tvListingCountBadge.text = "$count ${if (count == 1) "Listing" else "Listings"}"
        }
    }
}
