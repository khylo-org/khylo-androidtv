package org.khylo.tv.core.ui.presentation

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import org.khylo.tv.core.ui.card.ChannelCardView
import org.khylo.sdk.model.api.BaseItemDto

class ChannelCardPresenter : Presenter() {
	class ViewHolder(
		private val cardView: ChannelCardView,
	) : Presenter.ViewHolder(cardView) {
		fun setItem(item: BaseItemDto?) = cardView.setItem(item)
	}

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = ChannelCardView(parent.context).apply {
			isFocusable = true
			isFocusableInTouchMode = true
		}

		return ViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (item !is BaseItemDto) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
}
