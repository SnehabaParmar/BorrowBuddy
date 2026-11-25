package com.borrowbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityNotificationsBinding
import com.borrowbuddy.app.databinding.ItemNotificationBinding
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.model.NotificationItem
import com.borrowbuddy.app.model.NotificationRepository
import com.borrowbuddy.app.ui.ExchangesFragment
import com.borrowbuddy.app.util.ActivityHelper

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding

    private val dataChangeListener: () -> Unit = {
        runOnUiThread {
            refreshNotifications()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootNotifications) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupHeader()
        refreshNotifications()
    }

    override fun onStart() {
        super.onStart()
        NotificationRepository.registerListener(dataChangeListener)
        ExchangeRepository.registerListener(dataChangeListener)
    }

    override fun onStop() {
        super.onStop()
        NotificationRepository.unregisterListener(dataChangeListener)
        ExchangeRepository.unregisterListener(dataChangeListener)
    }

    override fun onResume() {
        super.onResume()
        refreshNotifications()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMarkAllRead.setOnClickListener {
            NotificationRepository.markAllAsRead(this)
            refreshNotifications()
        }
    }

    private fun refreshNotifications() {
        val container = binding.layoutNotificationsContainer
        container.removeAllViews()

        val notifs = NotificationRepository.getNotifications(this)
        val unreadCount = notifs.count { !it.isRead }

        if (notifs.isEmpty()) {
            binding.cardNotifications.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvUnreadBadge.visibility = View.GONE
            binding.btnMarkAllRead.visibility = View.GONE
            return
        }

        binding.cardNotifications.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
        binding.btnMarkAllRead.visibility = View.VISIBLE

        if (unreadCount > 0) {
            binding.tvUnreadBadge.visibility = View.VISIBLE
            binding.tvUnreadBadge.text = "$unreadCount Unread"
            binding.tvUnreadBadge.setBackgroundResource(R.drawable.bg_badge_active)
            binding.tvUnreadBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_blue))
            binding.btnMarkAllRead.isEnabled = true
            binding.btnMarkAllRead.alpha = 1.0f
        } else {
            binding.tvUnreadBadge.visibility = View.VISIBLE
            binding.tvUnreadBadge.text = "0 Unread"
            binding.tvUnreadBadge.setBackgroundResource(R.drawable.bg_badge_completed)
            binding.tvUnreadBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_slate))
            binding.btnMarkAllRead.isEnabled = false
            binding.btnMarkAllRead.alpha = 0.5f
        }

        val inflater = LayoutInflater.from(this)
        notifs.forEachIndexed { index, notif ->
            val rowBinding = ItemNotificationBinding.inflate(inflater, container, false)

            rowBinding.tvNotificationTitle.text = notif.title
            rowBinding.tvNotificationDescription.text = notif.description
            rowBinding.tvNotificationDate.text = notif.dateDisplay

            // Category Icon
            val iconRes = ActivityHelper.getCategoryIcon(notif.category)
            rowBinding.ivNotificationIcon.setImageResource(iconRes)

            // Unread Blue Dot
            rowBinding.dotUnreadIndicator.visibility = if (notif.isRead) View.GONE else View.VISIBLE

            // Divider: Hide for last element
            rowBinding.dividerNotification.visibility = if (index == notifs.size - 1) View.GONE else View.VISIBLE

            rowBinding.rowNotificationContent.setOnClickListener {
                NotificationRepository.markAsRead(this, notif.id)

                if (notif.targetExchangeType != null && notif.targetCategory != null) {
                    ExchangesFragment.navigateTo(notif.targetExchangeType, notif.targetCategory)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("EXTRA_NAVIGATE_TAB", notif.targetTab)
                    }
                    startActivity(intent)
                    finish()
                } else if (notif.targetTab >= 0) {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("EXTRA_NAVIGATE_TAB", notif.targetTab)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    refreshNotifications()
                }
            }

            container.addView(rowBinding.root)
        }
    }
}
