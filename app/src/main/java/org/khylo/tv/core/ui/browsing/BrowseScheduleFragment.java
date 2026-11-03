package org.khylo.tv.core.ui.browsing;

import org.khylo.tv.core.ui.livetv.TvManager;
import org.khylo.tv.core.ui.presentation.CardPresenter;

public class BrowseScheduleFragment extends EnhancedBrowseFragment {

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void setupQueries(final RowLoader rowLoader) {
        TvManager.getScheduleRowsAsync(this, null, new CardPresenter(true), mRowsAdapter);
    }
}
