/*******************************************************************************
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco Mobile for Android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.alfresco.mobile.android.ui.fragments;

import java.io.Serializable;
import java.util.Map.Entry;

import org.alfresco.mobile.android.api.model.ListingContext;
import org.alfresco.mobile.android.api.model.ListingFilter;
import org.alfresco.mobile.android.async.LoaderResult;
import org.alfresco.mobile.android.foundation.R;
import org.alfresco.mobile.android.platform.AlfrescoNotificationManager;
import org.alfresco.mobile.android.platform.utils.BundleUtils;
import org.alfresco.mobile.android.ui.GridFragment;
import org.alfresco.mobile.android.ui.ListingModeFragment;
import org.alfresco.mobile.android.ui.RefreshFragment;
import org.alfresco.mobile.android.ui.template.ListingTemplate;
import org.alfresco.mobile.android.ui.utils.UIUtils;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public abstract class CommonGridFragment extends AlfrescoFragment implements RefreshFragment, GridFragment,
        ListingModeFragment, ListingTemplate
{
    // /////////////////////////////////////////////////////////////
    // CONSTANTS
    // ////////////////////////////////////////////////////////////
    public static final String LOAD_STATE = "loadState";

    /** Load the number of items defined by the ListingContext. No Paging */
    public static final int LOAD_NONE = 0;

    /**
     * Load items until it displayed everything. Paging defined by the
     * ListingContext
     */
    public static final int LOAD_AUTO = 1;

    /** Load More items requires a user interactions. */
    public static final int LOAD_MANUAL = 2;

    /** Load more items when user reaches the bottom of the list. */
    public static final int LOAD_VISIBLE = 3;

    // /////////////////////////////////////////////////////////////
    // MEMBERS
    // ////////////////////////////////////////////////////////////
    /** Principal GridView of the fragment */
    protected GridView gv;

    /**
     * Principal progress indicator displaying during loading of data in
     * background
     */
    protected View pb;

    /** View displaying if no result inside the listView */
    protected View ev;

    /** Max Items for a single call */
    protected int maxItems;

    /** Skip count paramater during loading */
    protected int skipCount;

    /**
     * Indicator to retain position of first item currently display during
     * scrolling
     */
    protected int selectedPosition;

    /** Indicator to retain if everything has been loaded */
    protected boolean isFullLoad = Boolean.FALSE;

    /** Flag to retrieve date when fragment during onActivityCreate. */
    protected boolean retrieveDataOnCreation = true;

    private boolean isLockVisibleLoader = Boolean.FALSE;

    protected Boolean hasmore = Boolean.FALSE;

    protected int loadState = LOAD_AUTO;

    protected Bundle bundle;

    protected int emptyListMessageId;

    protected View footer;

    protected int titleId = -1;

    protected String mTitle = "";

    protected RefreshHelper refreshHelper;

    protected ListingContext originListing;

    protected ListingContext currentListing;

    protected boolean enableTitle = false;

    protected BaseAdapter adapter;

    protected boolean displayAsList = true;

    protected int mode = MODE_LISTING;

    // /////////////////////////////////////////////////////////////
    // BUNDLE CONSTRUCTOR
    // ////////////////////////////////////////////////////////////
    public static Bundle createBundleArgs(ListingContext lc, int loadState)
    {
        Bundle args = new Bundle();
        BundleUtils.addIfNotNull(args, LOAD_STATE, loadState);
        BundleUtils.addIfNotNull(args, ARGUMENT_LISTING, lc);
        return args;
    }

    protected static Bundle createListingBundleArgs(Bundle b)
    {
        if (b == null || b.isEmpty()) { return null; }

        Bundle args = new Bundle();
        BundleUtils.addIfNotNull(args, LOAD_STATE, LOAD_AUTO);
        BundleUtils.addIfNotNull(args, ARGUMENT_LISTING, parse(b));

        return args;
    }

    // /////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ////////////////////////////////////////////////////////////
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (container == null && getDialog() == null) { return null; }
        setRootView(inflater.inflate(R.layout.sdk_grid, container, false));

        init(getRootView(), emptyListMessageId);

        return getRootView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        // Retrieve arguments
        if (getArguments() != null)
        {
            bundle = getArguments();
            originListing = (ListingContext) bundle.getSerializable(ARGUMENT_LISTING);
            loadState = bundle.containsKey(LOAD_STATE) ? bundle.getInt(LOAD_STATE) : loadState;
            if (bundle.containsKey(ARGUMENT_MODE))
            {
                mode = bundle.getInt(ARGUMENT_MODE);
            }
            onRetrieveParameters(bundle);
        }

        if (originListing == null)
        {
            originListing = new ListingContext();
        }

        currentListing = copyListing(originListing);

        //
        if (getRootView() != null)
        {
            refreshHelper = new RefreshHelper(getActivity(), this, getRootView());
        }
        else
        {
            refreshHelper = new RefreshHelper(getActivity(), this, null);
        }

        // Perform the request on creation to populate gridView
        if (retrieveDataOnCreation && adapter == null)
        {
            performRequest(currentListing);
        }
        else if (retrieveDataOnCreation && adapter != null)
        {
            setListShown(true);
            gv.setAdapter(adapter);
        }
        else
        {
            // Display Empty view
            setListShown(true);
            adapter = onAdapterCreation();
            gv.setAdapter(adapter);
        }
    }

    /** Called this method to retrieve argument parameters. */
    protected void onRetrieveParameters(Bundle bundle)
    {
        // Can be implemented by the derived class.
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        savePosition();
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart()
    {
        super.onStart();
        setListShown(!(adapter == null));
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (enableTitle)
        {
            UIUtils.displayTitle(getActivity(), onCreateTitle(mTitle));
        }
    }

    // /////////////////////////////////////////////////////////////
    // SESSION MANAGEMENT
    // ////////////////////////////////////////////////////////////
    @Override
    protected void onSessionMissing()
    {
        super.onSessionMissing();
        setListShown(true);
        gv.setEmptyView(ev);
    }

    // /////////////////////////////////////////////////////////////
    // TITLE
    // ////////////////////////////////////////////////////////////
    /** Title is displayed during onResume. */
    protected String onCreateTitle(String title)
    {
        return TextUtils.isEmpty(title) ? (titleId == -1) ? mTitle : getString(titleId) : title;
    }

    // /////////////////////////////////////////////////////////////
    // ITEMS MANAGEMENT
    // ////////////////////////////////////////////////////////////
    /**
     * Called when the adapter is created for the first time.
     */
    protected BaseAdapter onAdapterCreation()
    {
        return adapter;
    }

    protected void init(View v, Integer estring)
    {
        pb = v.findViewById(R.id.progressbar);
        gv = (GridView) v.findViewById(R.id.gridview);
        ev = v.findViewById(R.id.empty);
        footer = v.findViewById(R.id.load_more);
        TextView evt = (TextView) v.findViewById(R.id.empty_text);
        if (estring != null && estring > 0)
        {
            evt.setText(estring);
        }

        if (adapter != null)
        {
            if (adapter.getCount() == 0)
            {
                gv.setEmptyView(ev);
            }
            else
            {
                gv.setAdapter(adapter);
                gv.setSelection(selectedPosition);
            }

        }

        gv.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> l, View v, int position, long id)
            {
                savePosition();
                CommonGridFragment.this.onListItemClick((GridView) l, v, position, id);
            }
        });

        gv.setOnItemLongClickListener(new OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> l, View v, int position, long id)
            {
                return CommonGridFragment.this.onListItemLongClick((GridView) l, v, position, id);
            }
        });

        gv.setOnScrollListener(new OnScrollListener()
        {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState)
            {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
            {
                savePosition();
                if (totalItemCount > 0 && firstVisibleItem + visibleItemCount == totalItemCount
                        && loadState == LOAD_VISIBLE && !isLockVisibleLoader)
                {
                    loadMore();
                    isLockVisibleLoader = Boolean.TRUE;
                }

                if (refreshHelper != null && view != null && view.getChildCount() > 0)
                {
                    boolean firstItemVisible = view.getFirstVisiblePosition() == 0;
                    boolean topOfFirstItemVisible = view.getChildAt(0).getTop() == 0;
                    refreshHelper.setEnabled(firstItemVisible && topOfFirstItemVisible);
                }
            }
        });
    }

    /**
     * Control whether the list is being displayed.
     * 
     * @param shown : If true, the list view is shown; if false, the progress
     *            indicator. The initial value is true.
     */
    protected void setListShown(Boolean shown)
    {
        if (shown)
        {
            gv.setVisibility(View.VISIBLE);
            pb.setVisibility(View.GONE);
        }
        else
        {
            ev.setVisibility(View.GONE);
            gv.setVisibility(View.GONE);
            pb.setVisibility(View.VISIBLE);
        }
    }

    protected void displayEmptyView()
    {
        if (!isVisible()) { return; }
        gv.setEmptyView(ev);
        isFullLoad = Boolean.TRUE;
        if (adapter != null)
        {
            gv.setAdapter(null);
        }
    }

    protected void displayDataView()
    {
        if (adapter != null && !adapter.isEmpty())
        {
            setListShown(true);
            gv.setAdapter(adapter);
        }
    }

    protected final void savePosition()
    {
        if (gv != null)
        {
            selectedPosition = gv.getFirstVisiblePosition();
        }
    }

    // /////////////////////////////////////////////////////////////
    // ITEMS SELECTION
    // ////////////////////////////////////////////////////////////
    /**
     * Affect a clickListener to the principal GridView.
     */
    public void setOnItemClickListener(OnItemClickListener clickListener)
    {
        gv.setOnItemClickListener(clickListener);
    }

    public void onListItemClick(GridView l, View v, int position, long id)
    {
        // Can be implemented on children
    }

    public boolean onListItemLongClick(GridView l, View v, int position, long id)
    {
        // Can be implemented on children
        return false;
    }

    // /////////////////////////////////////////////////////////////
    // LOAD MORE
    // ////////////////////////////////////////////////////////////
    protected abstract void performRequest(ListingContext lcorigin);

    @SuppressWarnings("unchecked")
    protected void calculateSkipCount(ListingContext lc)
    {
        if (lc != null)
        {
            skipCount = lc.getSkipCount();
            maxItems = lc.getMaxItems();
            if (hasmore)
            {
                skipCount = (adapter != null) ? (((ArrayAdapter<Object>) adapter)).getCount() : lc.getSkipCount()
                        + lc.getMaxItems();
            }
            lc.setSkipCount(skipCount);
        }
    }

    protected boolean doesLoadMore()
    {
        boolean loadMore = Boolean.FALSE;

        switch (loadState)
        {
            case LOAD_MANUAL:
                isFullLoad = Boolean.FALSE;
                if (!hasmore)
                {
                    isFullLoad = Boolean.TRUE;
                }
                break;
            case LOAD_AUTO:
                if (hasmore)
                {
                    loadMore = Boolean.TRUE;
                    isFullLoad = Boolean.FALSE;
                }
                else
                {
                    loadMore = Boolean.FALSE;
                    isFullLoad = Boolean.TRUE;
                }
                break;
            case LOAD_NONE:
                isFullLoad = Boolean.TRUE;
                break;
            case LOAD_VISIBLE:
                isFullLoad = Boolean.FALSE;
                if (!hasmore)
                {
                    isFullLoad = Boolean.TRUE;
                }
                else
                {
                    isLockVisibleLoader = Boolean.FALSE;
                }
                break;
            default:
                break;
        }
        return loadMore;
    }

    protected void loadMore()
    {
        if (isFullLoad) { return; }
        onPrepareRefresh();
        calculateSkipCount(currentListing);
        performRequest(currentListing);
        Crouton.showText(getActivity(), R.string.load_more_progress, Style.INFO, (ViewGroup) footer);
    }

    protected static ListingContext copyListing(ListingContext lco)
    {
        if (lco == null) { return null; }
        ListingContext lci = new ListingContext();
        lci.setIsSortAscending(lco.isSortAscending());
        lci.setMaxItems(lco.getMaxItems());
        lci.setSkipCount(lco.getSkipCount());
        lci.setSortProperty(lco.getSortProperty());
        if (lco.getFilter() != null)
        {
            ListingFilter lf = new ListingFilter();
            for (Entry<String, Serializable> filter : lco.getFilter().getFilters().entrySet())
            {
                lf.addFilter(filter.getKey(), filter.getValue());
            }
            lci.setFilter(lf);
        }
        return lci;
    }

    // /////////////////////////////////////////////////////////////
    // EXCEPTION
    // ////////////////////////////////////////////////////////////
    protected boolean checkException(LoaderResult<?> result)
    {
        return (result.getException() != null);
    }

    /**
     * Override this method to handle an exception coming back from the server.
     * 
     * @param e : exception raised by the client API.
     */
    public void onResultError(Exception e)
    {
        AlfrescoNotificationManager.getInstance(getActivity()).showToast(e.getMessage());
        setListShown(true);
    }

    public void refreshListView()
    {
        gv.setAdapter(adapter);
    }

    // /////////////////////////////////////////////////////////////
    // ITEMS SIZE
    // ////////////////////////////////////////////////////////////
    public void setColumnWidth(int value)
    {
        gv.setColumnWidth(value);
    }

    // //////////////////////////////////////////////////////////////////////
    // INTERNALS UTILS
    // //////////////////////////////////////////////////////////////////////
    protected static ListingContext parse(Bundle b)
    {
        ListingContext lc = new ListingContext();
        if (b.containsKey(ARGUMENT_MAX_ITEMS))
        {
            lc.setMaxItems(b.getInt(ARGUMENT_MAX_ITEMS));
        }
        if (b.containsKey(ARGUMENT_ORDER_BY))
        {
            String[] orderBy = b.getString(ARGUMENT_ORDER_BY).split(" ");
            if (!TextUtils.isEmpty(orderBy[0]))
            {
                lc.setSortProperty(orderBy[0]);
            }
            if (!TextUtils.isEmpty(orderBy[1]))
            {
                if ("ASC".equals(orderBy[1].toUpperCase()))
                {
                    lc.setIsSortAscending(true);
                }
                else if ("DESC".equals(orderBy[1].toUpperCase()))
                {
                    lc.setIsSortAscending(false);
                }
            }
        }
        if (b.containsKey(ARGUMENT_SKIP_COUNT))
        {
            lc.setSkipCount(b.getInt(ARGUMENT_SKIP_COUNT));
        }
        return lc;
    }

    public static int getDPI(DisplayMetrics dm, int sizeInDp)
    {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeInDp, dm);
    }

    // //////////////////////////////////////////////////////////////////////
    // REFRESH
    // //////////////////////////////////////////////////////////////////////
    protected void onPrepareRefresh()
    {
        refreshHelper.setRefreshing();
    }

    // //////////////////////////////////////////////////////////////////////
    // MODE
    // //////////////////////////////////////////////////////////////////////
    public int getMode()
    {
        return mode;
    }
}
