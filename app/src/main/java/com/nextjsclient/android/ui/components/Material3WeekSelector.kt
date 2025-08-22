package com.nextjsclient.android.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
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
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var weekNumberDisplay: TextView
    private lateinit var dateRangeDisplay: TextView
    private lateinit var yearDisplay: TextView
    private lateinit var previousWeekButton: FloatingActionButton
    private lateinit var nextWeekButton: FloatingActionButton
    private lateinit var weekProgress: LinearProgressIndicator
    private lateinit var weekProgressText: TextView
    private lateinit var todayButton: MaterialButton
    private lateinit var currentWeekButton: MaterialButton
    private lateinit var calendarButton: MaterialButton
    private lateinit var weekGridRecycler: RecyclerView
    private lateinit var cardView: MaterialCardView
    
    private var currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var onWeekChangeListener: ((Int, Int) -> Unit)? = null
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.week_selector_material3, this, true)
        initViews(view)
        setupListeners()
        updateWeekDisplay()
    }
    
    private fun initViews(view: View) {
        weekNumberDisplay = view.findViewById(R.id.weekNumberDisplay)
        dateRangeDisplay = view.findViewById(R.id.dateRangeDisplay)
        yearDisplay = view.findViewById(R.id.yearDisplay)
        previousWeekButton = view.findViewById(R.id.previousWeekButton)
        nextWeekButton = view.findViewById(R.id.nextWeekButton)
        weekProgress = view.findViewById(R.id.weekProgress)
        weekProgressText = view.findViewById(R.id.weekProgressText)
        todayButton = view.findViewById(R.id.todayButton)
        currentWeekButton = view.findViewById(R.id.currentWeekButton)
        calendarButton = view.findViewById(R.id.calendarButton)
        weekGridRecycler = view.findViewById(R.id.weekGridRecycler)
        cardView = view.findViewById(R.id.weekSelector)
    }
    
    private fun setupListeners() {
        previousWeekButton.setOnClickListener {
            animateWeekChange(-1)
        }
        
        nextWeekButton.setOnClickListener {
            animateWeekChange(1)
        }
        
        todayButton.setOnClickListener {
            jumpToCurrentWeek()
        }
        
        currentWeekButton.setOnClickListener {
            jumpToCurrentWeek()
        }
        
        calendarButton.setOnClickListener {
            toggleCalendarView()
        }
    }
    
    private fun animateWeekChange(direction: Int) {
        // Material 3 expressive animations
        
        // Rotate FAB buttons
        val targetButton = if (direction > 0) nextWeekButton else previousWeekButton
        targetButton.animate()
            .rotationBy(360f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .start()
        
        // Fade out current text
        dateRangeDisplay.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .setDuration(150)
            .withEndAction {
                // Update week
                changeWeek(direction)
                
                // Fade in new text with scale
                dateRangeDisplay.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
        
        // Animate week number with slide effect
        weekNumberDisplay.animate()
            .translationX(if (direction > 0) -50f else 50f)
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                weekNumberDisplay.translationX = if (direction > 0) 50f else -50f
                updateWeekDisplay()
                weekNumberDisplay.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
        
        // Pulse the card
        cardView.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(100)
            .withEndAction {
                cardView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
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
        
        val dateFormat = SimpleDateFormat("d MMMM", Locale.FRANCE)
        val startStr = dateFormat.format(startDate)
        val endStr = dateFormat.format(endDate)
        
        weekNumberDisplay.text = "SEMAINE $currentWeek"
        dateRangeDisplay.text = "$startStr - $endStr"
        yearDisplay.text = currentYear.toString()
        
        // Update progress (example: based on current day of week)
        val today = Calendar.getInstance()
        val progress = if (today.get(Calendar.WEEK_OF_YEAR) == currentWeek && 
                          today.get(Calendar.YEAR) == currentYear) {
            val dayOfWeek = today.get(Calendar.DAY_OF_WEEK)
            val adjustedDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
            (adjustedDay * 100 / 7)
        } else {
            0
        }
        
        animateProgress(progress)
    }
    
    private fun animateProgress(targetProgress: Int) {
        val animator = ValueAnimator.ofInt(weekProgress.progress, targetProgress)
        animator.duration = 500
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            weekProgress.progress = progress
            weekProgressText.text = "$progress%"
        }
        animator.start()
    }
    
    private fun jumpToCurrentWeek() {
        val today = Calendar.getInstance()
        val targetWeek = today.get(Calendar.WEEK_OF_YEAR)
        val targetYear = today.get(Calendar.YEAR)
        
        if (targetWeek != currentWeek || targetYear != currentYear) {
            // Animate jump with bounce effect
            cardView.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    currentWeek = targetWeek
                    currentYear = targetYear
                    updateWeekDisplay()
                    onWeekChangeListener?.invoke(currentWeek, currentYear)
                    
                    cardView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                }
                .start()
        }
    }
    
    private fun toggleCalendarView() {
        val isVisible = weekGridRecycler.isVisible
        
        // Rotate calendar button
        calendarButton.animate()
            .rotationBy(180f)
            .setDuration(300)
            .start()
        
        if (!isVisible) {
            weekGridRecycler.visibility = View.VISIBLE
            weekGridRecycler.alpha = 0f
            weekGridRecycler.translationY = -20f
            weekGridRecycler.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        } else {
            weekGridRecycler.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(200)
                .withEndAction {
                    weekGridRecycler.visibility = View.GONE
                }
                .start()
        }
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