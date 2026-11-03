package org.khylo.tv.core.ui

fun interface HorizontalScrollViewListener {
	fun onScrollChanged(view: ObservableHorizontalScrollView?, x: Int, y: Int, oldx: Int, oldy: Int)
}
