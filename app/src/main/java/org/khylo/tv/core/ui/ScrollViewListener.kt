package org.khylo.tv.core.ui

fun interface ScrollViewListener {
	fun onScrollChanged(scrollView: ObservableScrollView?, x: Int, y: Int, oldx: Int, oldy: Int)
}
