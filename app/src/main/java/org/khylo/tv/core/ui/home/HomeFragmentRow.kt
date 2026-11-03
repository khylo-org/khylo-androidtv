package org.khylo.tv.core.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.khylo.tv.core.ui.presentation.CardPresenter
import org.khylo.tv.core.ui.presentation.MutableObjectAdapter

interface HomeFragmentRow {
	fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>)
}
