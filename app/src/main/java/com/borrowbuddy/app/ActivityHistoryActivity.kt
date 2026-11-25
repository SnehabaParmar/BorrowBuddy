package com.borrowbuddy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityHistoryBinding
import com.borrowbuddy.app.databinding.ItemProfileActivityBinding
import com.borrowbuddy.app.model.ExchangeItem
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.ui.ExchangesFragment
import com.borrowbuddy.app.util.ActivityHelper

class ActivityHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    private val exchangeChangeListener: () -> Unit = {
        runOnUiThread {
            refreshActivities()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootActivityHistory) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupHeader()
        refreshActivities()
    }

    override fun onStart() {
        super.onStart()
        ExchangeRepository.registerListener(exchangeChangeListener)
    }

    override fun onStop() {
        super.onStop()
        ExchangeRepository.unregisterListener(exchangeChangeListener)
    }

    override fun onResume() {
        super.onResume()
        refreshActivities()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun refreshActivities() {
        val container = binding.layoutActivityHistoryContainer
        container.removeAllViews()

        val allExchanges = ExchangeRepository.getAll()

        if (allExchanges.isEmpty()) {
            binding.cardActivityHistory.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvActivityCountBadge.visibility = View.GONE
            return
        }

        binding.cardActivityHistory.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
        binding.tvActivityCountBadge.visibility = View.VISIBLE
        binding.tvActivityCountBadge.text = "${allExchanges.size} ${if (allExchanges.size == 1) "Activity" else "Activities"}"

        // Sort newest activities first
        val sortedActivities = allExchanges.sortedByDescending {
            ActivityHelper.parseActivityTimestamp(ActivityHelper.getActivityDate(it))
        }

        val inflater = LayoutInflater.from(this)
        sortedActivities.forEachIndexed { index, exchange ->
            val rowBinding = ItemProfileActivityBinding.inflate(inflater, container, false)

            rowBinding.tvActivityItemTitle.text = exchange.title
            rowBinding.tvActivityDescription.text = ActivityHelper.getActivityDescription(exchange)
            rowBinding.tvActivityDate.text = ActivityHelper.getActivityDate(exchange)

            // Thumbnail / Category Icon
            var imageLoaded = false
            if (exchange.imageUri.isNotBlank()) {
                try {
                    rowBinding.ivActivityThumb.setImageURI(Uri.parse(exchange.imageUri))
                    rowBinding.ivActivityThumb.visibility = View.VISIBLE
                    rowBinding.layoutFallback.visibility = View.GONE
                    imageLoaded = true
                } catch (_: Exception) {
                    imageLoaded = false
                }
            }

            if (!imageLoaded) {
                if (exchange.drawableRes != 0) {
                    rowBinding.ivActivityThumb.setImageResource(exchange.drawableRes)
                    rowBinding.ivActivityThumb.visibility = View.VISIBLE
                    rowBinding.layoutFallback.visibility = View.GONE
                } else {
                    rowBinding.ivActivityThumb.visibility = View.GONE
                    rowBinding.layoutFallback.visibility = View.VISIBLE
                    rowBinding.ivFallbackIcon.setImageResource(ActivityHelper.getCategoryIcon(exchange.category))
                }
            }

            // Divider: hide for last item
            rowBinding.dividerActivity.visibility = if (index == sortedActivities.size - 1) View.GONE else View.VISIBLE

            // Click interaction: navigate to Exchanges tab & relevant category in MainActivity
            rowBinding.root.setOnClickListener {
                val isHistory = exchange.status in listOf(
                    ExchangeStatus.COMPLETED,
                    ExchangeStatus.DECLINED,
                    ExchangeStatus.CANCELLED
                )
                val category = if (isHistory) "HISTORY" else "ACTIVE"
                ExchangesFragment.navigateTo(exchange.type, category)

                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("EXTRA_NAVIGATE_TAB", 3)
                }
                startActivity(intent)
                finish()
            }

            container.addView(rowBinding.root)
        }
    }
}
