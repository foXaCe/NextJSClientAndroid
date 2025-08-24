package com.nextjsclient.android.ui.scamark

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextjsclient.android.R

data class SearchSuggestion(
    val text: String,
    val type: SuggestionType,
    val count: Int = 0,
    val matchedPart: String = ""
)

enum class SuggestionType {
    CLIENT_SCA,
    PRODUCT,
    BRAND,
    CATEGORY
}

class SearchSuggestionsAdapter(
    private val onSuggestionClick: (SearchSuggestion) -> Unit
) : ListAdapter<SearchSuggestion, SearchSuggestionsAdapter.SuggestionViewHolder>(SuggestionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.suggestionIcon)
        private val textView: TextView = itemView.findViewById(R.id.suggestionText)
        private val typeView: TextView = itemView.findViewById(R.id.suggestionType)
        private val countView: TextView = itemView.findViewById(R.id.suggestionCount)

        fun bind(suggestion: SearchSuggestion) {
            // Mettre en évidence la partie correspondante
            val spannableText = SpannableString(suggestion.text)
            val matchIndex = suggestion.text.lowercase().indexOf(suggestion.matchedPart.lowercase())
            if (matchIndex >= 0) {
                // Mettre en gras et en couleur la partie correspondante
                spannableText.setSpan(
                    StyleSpan(Typeface.BOLD),
                    matchIndex,
                    matchIndex + suggestion.matchedPart.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableText.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(itemView.context, R.color.md_theme_light_primary)),
                    matchIndex,
                    matchIndex + suggestion.matchedPart.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            textView.text = spannableText

            // Configurer l'icône et le type selon le type de suggestion
            when (suggestion.type) {
                SuggestionType.CLIENT_SCA -> {
                    icon.setImageResource(R.drawable.ic_analytics_24)
                    typeView.text = "Client SCA"
                    typeView.visibility = View.VISIBLE
                    if (suggestion.count > 0) {
                        countView.text = itemView.context.getString(R.string.search_products_count, suggestion.count)
                        countView.visibility = View.VISIBLE
                    }
                }
                SuggestionType.PRODUCT -> {
                    icon.setImageResource(R.drawable.ic_search)
                    typeView.text = itemView.context.getString(R.string.search_product_type)
                    typeView.visibility = View.VISIBLE
                    countView.visibility = View.GONE
                }
                SuggestionType.BRAND -> {
                    icon.setImageResource(R.drawable.ic_trophy)
                    typeView.text = itemView.context.getString(R.string.search_brand_type)
                    typeView.visibility = View.VISIBLE
                    if (suggestion.count > 0) {
                        countView.text = itemView.context.getString(R.string.search_products_count, suggestion.count)
                        countView.visibility = View.VISIBLE
                    }
                }
                SuggestionType.CATEGORY -> {
                    icon.setImageResource(R.drawable.ic_calendar)
                    typeView.text = itemView.context.getString(R.string.category_label)
                    typeView.visibility = View.VISIBLE
                    if (suggestion.count > 0) {
                        countView.text = itemView.context.getString(R.string.search_products_count, suggestion.count)
                        countView.visibility = View.VISIBLE
                    }
                }
            }

            // Gérer le clic
            itemView.setOnClickListener {
                onSuggestionClick(suggestion)
            }
        }
    }

    class SuggestionDiffCallback : DiffUtil.ItemCallback<SearchSuggestion>() {
        override fun areItemsTheSame(oldItem: SearchSuggestion, newItem: SearchSuggestion): Boolean {
            return oldItem.text == newItem.text && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: SearchSuggestion, newItem: SearchSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}