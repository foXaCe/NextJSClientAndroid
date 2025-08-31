package com.nextjsclient.android.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nextjsclient.android.R
import java.text.SimpleDateFormat
import java.util.*

class Material3WeekSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var weekNumberDisplay: MaterialButton
    private lateinit var dateRangeDisplay: TextView
    private lateinit var yearDisplay: TextView
    private lateinit var previousWeekButton: MaterialButton
    private lateinit var nextWeekButton: MaterialButton
    private lateinit var weekGridRecycler: RecyclerView
    
    private var currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var onWeekChangeListener: ((Int, Int) -> Unit)? = null
    
    init {
        // Configurer la MaterialCardView
        radius = 28.dpToPx()
        cardElevation = 0f
        setCardBackgroundColor(context.getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
        
        // Inflater le contenu
        LayoutInflater.from(context).inflate(R.layout.week_selector_content_material3, this, true)
        initViews()
        setupListeners()
        updateWeekDisplay()
    }
    
    private fun Int.dpToPx(): Float = this * context.resources.displayMetrics.density
    
    private fun Context.getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
    
    private fun initViews() {
        weekNumberDisplay = findViewById(R.id.weekNumberDisplay)
        dateRangeDisplay = findViewById(R.id.dateRangeDisplay)
        yearDisplay = findViewById(R.id.yearDisplay)
        previousWeekButton = findViewById(R.id.previousWeekButton)
        nextWeekButton = findViewById(R.id.nextWeekButton)
        weekGridRecycler = findViewById(R.id.weekGridRecycler)
    }
    
    private fun setupListeners() {
        previousWeekButton.setOnClickListener {
            animateWeekChange(-1)
        }
        
        nextWeekButton.setOnClickListener {
            animateWeekChange(1)
        }
        
        // Week Number Button - Affiche la liste des semaines
        weekNumberDisplay.setOnClickListener {
            // Animation de feedback
            weekNumberDisplay.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    weekNumberDisplay.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                }
                .start()
            onWeekHistoryRequested?.invoke()
        }
        
    }
    
    private fun animateWeekChange(direction: Int) {
        changeWeek(direction)
        updateWeekDisplay()
    }
    
    private fun changeWeek(direction: Int) {
        currentWeek += direction
        
        // Handle year transition
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, currentYear)
        calendar.set(Calendar.WEEK_OF_YEAR, currentWeek)
        
        if (currentWeek > calendar.getActualMaximum(Calendar.WEEK_OF_YEAR)) {
            currentWeek = 1
            currentYear++
        } else if (currentWeek < 1) {
            currentYear--
            calendar.set(Calendar.YEAR, currentYear)
            currentWeek = calendar.getActualMaximum(Calendar.WEEK_OF_YEAR)
        }
        
        onWeekChangeListener?.invoke(currentWeek, currentYear)
    }
    
    private fun updateWeekDisplay() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, currentYear)
        calendar.set(Calendar.WEEK_OF_YEAR, currentWeek)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val startDate = calendar.time
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = calendar.time
        
        val currentLocale = context.resources.configuration.locales[0]
        val dateFormat = SimpleDateFormat("d MMMM", currentLocale)
        val startStr = dateFormat.format(startDate)
        val endStr = dateFormat.format(endDate)
        val separator = context.getString(R.string.date_range_separator)
        
        weekNumberDisplay.text = context.getString(R.string.week_with_number, currentWeek)
        dateRangeDisplay.text = "$startStr $separator $endStr"
        yearDisplay.text = currentYear.toString()
        
    }
    
    private fun showWeekListDialog() {
        // Get available weeks from ViewModel through callback
        onWeekHistoryRequested?.invoke()
    }
    
    private var onWeekListRequested: (() -> Unit)? = null
    private var onWeekHistoryRequested: (() -> Unit)? = null
    
    fun setOnWeekListRequestedListener(listener: () -> Unit) {
        onWeekListRequested = listener
    }
    
    fun setOnWeekHistoryRequestedListener(listener: () -> Unit) {
        onWeekHistoryRequested = listener
    }
    
    fun setOnWeekChangeListener(listener: (week: Int, year: Int) -> Unit) {
        onWeekChangeListener = listener
    }
    
    fun setWeek(week: Int, year: Int) {
        currentWeek = week
        currentYear = year
        updateWeekDisplay()
    }
}