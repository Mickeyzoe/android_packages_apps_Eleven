/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyngn.eleven.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.cyngn.eleven.MusicStateListener;
import com.cyngn.eleven.R;
import com.cyngn.eleven.adapters.ArtistAdapter;
import com.cyngn.eleven.adapters.PagerAdapter;
import com.cyngn.eleven.loaders.ArtistLoader;
import com.cyngn.eleven.menu.DeleteDialog;
import com.cyngn.eleven.model.Artist;
import com.cyngn.eleven.recycler.RecycleHolder;
import com.cyngn.eleven.sectionadapter.SectionAdapter;
import com.cyngn.eleven.sectionadapter.SectionCreator;
import com.cyngn.eleven.sectionadapter.SectionListContainer;
import com.cyngn.eleven.ui.activities.BaseActivity;
import com.cyngn.eleven.ui.fragments.phone.MusicBrowserFragment;
import com.cyngn.eleven.utils.MusicUtils;
import com.cyngn.eleven.utils.NavUtils;
import com.cyngn.eleven.utils.PopupMenuHelper;
import com.cyngn.eleven.utils.SectionCreatorUtils;
import com.cyngn.eleven.utils.SectionCreatorUtils.IItemCompare;
import com.cyngn.eleven.widgets.IPopupMenuCallback;
import com.cyngn.eleven.widgets.NoResultsContainer;
import com.viewpagerindicator.TitlePageIndicator;

/**
 * This class is used to display all of the artists on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ArtistFragment extends MusicBrowserFragment implements
        LoaderCallbacks<SectionListContainer<Artist>>,
        OnScrollListener, OnItemClickListener, MusicStateListener {

    /**
     * Fragment UI
     */
    private ViewGroup mRootView;

    /**
     * The adapter for the grid
     */
    private SectionAdapter<Artist, ArtistAdapter> mAdapter;

    /**
     * The list view
     */
    private ListView mListView;

    /**
     * Pop up menu helper
     */
    private PopupMenuHelper mPopupMenuHelper;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public ArtistFragment() {
    }

    @Override
    public int getLoaderId() {
        return PagerAdapter.MusicFragments.ARTIST.ordinal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPopupMenuHelper = new PopupMenuHelper(getActivity(), getFragmentManager()) {
            /**
             * Represents an artist
             */
            private Artist mArtist;

            @Override
            protected PopupMenuType onPreparePopupMenu(int position) {
                // Create a new model
                mArtist = mAdapter.getTItem(position);

                return PopupMenuType.Artist;
            }

            @Override
            protected long[] getIdList() {
                return MusicUtils.getSongListForArtist(getActivity(), mArtist.mArtistId);
            }

            @Override
            protected void onDeleteClicked() {
                final String artist = mArtist.mArtistName;
                DeleteDialog.newInstance(artist, getIdList(), artist).show(
                        getFragmentManager(), "DeleteDialog");
            }
        };

        // Create the adapter
        final int layout = R.layout.list_item_normal;
        ArtistAdapter adapter = new ArtistAdapter(getActivity(), layout);
        mAdapter = new SectionAdapter<Artist, ArtistAdapter>(getActivity(), adapter);
        mAdapter.setPopupMenuClickedListener(new IPopupMenuCallback.IListener() {
            @Override
            public void onPopupMenuClicked(View v, int position) {
                mPopupMenuHelper.showPopupMenu(v, position);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        mRootView = (ViewGroup)inflater.inflate(R.layout.list_base, null);
        initListView();

        // Register the music status listener
        ((BaseActivity)getActivity()).setMusicStateListenerListener(this);

        return mRootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ((BaseActivity)getActivity()).removeMusicStateListenerListener(this);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Enable the options menu
        setHasOptionsMenu(true);
        // Start the loader
        initLoader(null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        mAdapter.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        // Pause disk cache access to ensure smoother scrolling
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING
                || scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mAdapter.getUnderlyingAdapter().setPauseDiskCache(true);
        } else {
            mAdapter.getUnderlyingAdapter().setPauseDiskCache(false);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        Artist artist = mAdapter.getTItem(position);
        NavUtils.openArtistProfile(getActivity(), artist.mArtistName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Loader<SectionListContainer<Artist>> onCreateLoader(final int id, final Bundle args) {
        final Context context = getActivity();
        IItemCompare<Artist> comparator = SectionCreatorUtils.createArtistComparison(context);
        return new SectionCreator<Artist>(getActivity(), new ArtistLoader(context), comparator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoadFinished(final Loader<SectionListContainer<Artist>> loader,
                               final SectionListContainer<Artist> data) {
        // Check for any errors
        if (data.mListResults.isEmpty()) {
            // Set the empty text
            final NoResultsContainer empty = (NoResultsContainer)mRootView.findViewById(R.id.no_results_container);
            mListView.setEmptyView(empty);
            return;
        }

        // Set the data
        mAdapter.setData(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoaderReset(final Loader<SectionListContainer<Artist>> loader) {
        // Clear the data in the adapter
        mAdapter.unload();
    }

    /**
     * Scrolls the list to the currently playing artist when the user touches
     * the header in the {@link TitlePageIndicator}.
     */
    public void scrollToCurrentArtist() {
        final int currentArtistPosition = getItemPositionByArtist();

        if (currentArtistPosition != 0) {
            mListView.setSelection(currentArtistPosition);
        }
    }

    /**
     * @return The position of an item in the list or grid based on the name of
     *         the currently playing artist.
     */
    private int getItemPositionByArtist() {
        final long artistId = MusicUtils.getCurrentArtistId();
        if (mAdapter == null) {
            return 0;
        }

        int position = mAdapter.getItemPosition(artistId);

        // if for some reason we don't find the item, just jump to the top
        if (position < 0) {
            return 0;
        }

        return position;
    }

    /**
     * Restarts the loader.
     */
    public void refresh() {
        // Wait a moment for the preference to change.
        SystemClock.sleep(10);
        restartLoader(null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onScroll(final AbsListView view, final int firstVisibleItem,
            final int visibleItemCount, final int totalItemCount) {
        // Nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restartLoader() {
        // Update the list when the user deletes any items
        restartLoader(null, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMetaChanged() {
        // Nothing to do
    }

    @Override
    public void onPlaylistChanged() {
        // Nothing to do
    }

    /**
     * Sets up various helpers for both the list and grid
     * 
     * @param list The list or grid
     */
    private void initAbsListView(final AbsListView list) {
        // Release any references to the recycled Views
        list.setRecyclerListener(new RecycleHolder());
        // Show the albums and songs from the selected artist
        list.setOnItemClickListener(this);
        // To help make scrolling smooth
        list.setOnScrollListener(this);
    }

    /**
     * Sets up the list view
     */
    private void initListView() {
        // Initialize the grid
        mListView = (ListView)mRootView.findViewById(R.id.list_base);
        // Set the data behind the list
        mListView.setAdapter(mAdapter);
        // Set up the helpers
        initAbsListView(mListView);
    }
}
