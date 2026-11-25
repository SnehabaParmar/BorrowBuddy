package com.borrowbuddy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.borrowbuddy.app.R

class CurvedBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val notchRadius = 38f * density
    private val notchDepth = 24f * density
    private val barTopY = 14f * density

    private var selectedIndex = 0

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f * density, 0f, -3f * density, Color.parseColor("#15000000"))
    }
    private val bgPath = Path()

    // Permanent Elevated Center Action FAB (+) with Turquoise-to-Blue Gradient
    private lateinit var fabCenterAdd: FrameLayout
    private lateinit var ivCenterPlus: ImageView

    // 4 Navigation Tab Slots (0: Home, 1: Explore, 3: Exchanges, 4: Profile)
    private val tabIcons = mutableMapOf<Int, ImageView>()
    private val tabLabels = mutableMapOf<Int, TextView>()

    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onCenterAddClickListener: (() -> Unit)? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setLayerType(LAYER_TYPE_SOFTWARE, bgPaint)
        setupViews()
    }

    private fun setupViews() {
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.BOTTOM
            setPadding(0, (14 * density).toInt(), 0, (6 * density).toInt())
            clipChildren = false
            clipToPadding = false
        }

        val tabData = listOf(
            Triple(R.drawable.ic_nav_home, "Home", 0),
            Triple(R.drawable.ic_nav_explore, "Explore", 1),
            Triple(0, "", 2), // Spacer slot for center FAB
            Triple(R.drawable.ic_nav_exchanges, "Exchanges", 3),
            Triple(R.drawable.ic_nav_profile, "Profile", 4)
        )

        tabData.forEach { (iconRes, label, index) ->
            val slot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                clipChildren = false
                isClickable = index != 2
                isFocusable = index != 2
            }

            if (index != 2) {
                val icon = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
                    setImageResource(iconRes)
                    setColorFilter(
                        ContextCompat.getColor(
                            context,
                            if (index == 0) R.color.brand_blue else R.color.brand_slate
                        )
                    )
                }

                val text = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (2 * density).toInt()
                    }
                    this.text = label
                    textSize = 10.5f
                    typeface = if (index == 0) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (index == 0) R.color.brand_blue else R.color.brand_slate
                        )
                    )
                }

                slot.addView(icon)
                slot.addView(text)

                slot.setOnClickListener {
                    selectTab(index)
                }

                tabIcons[index] = icon
                tabLabels[index] = text
            } else {
                // Empty spacer for the center button
                val emptyView = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (34 * density).toInt())
                }
                slot.addView(emptyView)
            }

            tabLayout.addView(slot)
        }

        addView(tabLayout)

        // Permanent Elevated Center Action Button (+)
        val fabSize = (48 * density).toInt()
        fabCenterAdd = FrameLayout(context).apply {
            layoutParams = LayoutParams(fabSize, fabSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (2 * density).toInt()
            }
            background = ContextCompat.getDrawable(context, R.drawable.bg_fab_gradient)
            elevation = 8f * density
            isClickable = true
            isFocusable = true

            ivCenterPlus = ImageView(context).apply {
                val iconSize = (24 * density).toInt()
                layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
                setImageResource(R.drawable.ic_plus)
                setColorFilter(Color.WHITE)
            }
            addView(ivCenterPlus)

            setOnClickListener {
                animatePulse()
                onCenterAddClickListener?.invoke()
            }
        }
        addView(fabCenterAdd)
    }

    private fun animatePulse() {
        fabCenterAdd.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                fabCenterAdd.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        if (widthF == 0f) return

        bgPath.reset()
        bgPath.moveTo(0f, barTopY)

        val cx = widthF / 2f
        val p0x = cx - notchRadius
        val p6x = cx + notchRadius

        bgPath.lineTo(p0x, barTopY)

        // Smooth cubic scoop down to notchDepth
        bgPath.cubicTo(
            cx - notchRadius * 0.55f, barTopY,
            cx - notchRadius * 0.45f, barTopY + notchDepth,
            cx, barTopY + notchDepth
        )

        // Smooth cubic scoop rise back to barTopY
        bgPath.cubicTo(
            cx + notchRadius * 0.45f, barTopY + notchDepth,
            cx + notchRadius * 0.55f, barTopY,
            p6x, barTopY
        )

        bgPath.lineTo(widthF, barTopY)
        bgPath.lineTo(widthF, heightF)
        bgPath.lineTo(0f, heightF)
        bgPath.close()

        canvas.drawPath(bgPath, bgPaint)
    }

    fun selectTab(index: Int) {
        if (index == 2) {
            onCenterAddClickListener?.invoke()
            return
        }
        if (index !in listOf(0, 1, 3, 4)) return
        selectedIndex = index

        val activeColor = ContextCompat.getColor(context, R.color.brand_blue)
        val inactiveColor = ContextCompat.getColor(context, R.color.brand_slate)

        listOf(0, 1, 3, 4).forEach { i ->
            val icon = tabIcons[i]
            val label = tabLabels[i]

            if (i == index) {
                icon?.setColorFilter(activeColor)
                label?.setTextColor(activeColor)
                label?.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                icon?.setColorFilter(inactiveColor)
                label?.setTextColor(inactiveColor)
                label?.typeface = android.graphics.Typeface.DEFAULT
            }
        }

        onTabSelectedListener?.invoke(index)
    }

    fun setOnTabSelectedListener(listener: (Int) -> Unit) {
        this.onTabSelectedListener = listener
    }

    fun setOnCenterAddClickListener(listener: () -> Unit) {
        this.onCenterAddClickListener = listener
    }

    fun getSelectedTab(): Int = selectedIndex
}
