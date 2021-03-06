/**
 * Flym
 * <p/>
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ru.yanus171.feedexfork.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.yanus171.feedexfork.Constants;
import ru.yanus171.feedexfork.R;
import ru.yanus171.feedexfork.adapter.DrawerAdapter;
import ru.yanus171.feedexfork.adapter.EntriesCursorAdapter;
import ru.yanus171.feedexfork.fragment.EntriesListFragment;
import ru.yanus171.feedexfork.parser.OPML;
import ru.yanus171.feedexfork.provider.FeedData.EntryColumns;
import ru.yanus171.feedexfork.provider.FeedData.FeedColumns;
import ru.yanus171.feedexfork.service.AutoRefreshService;
import ru.yanus171.feedexfork.service.FetcherService;
import ru.yanus171.feedexfork.utils.PrefUtils;
import ru.yanus171.feedexfork.utils.UiUtils;

public class HomeActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String STATE_CURRENT_DRAWER_POS = "STATE_CURRENT_DRAWER_POS";

    private static final String FEED_UNREAD_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ')';

    private static final int LOADER_ID = 0;
    private static final int PERMISSIONS_REQUEST_IMPORT_FROM_OPML = 1;

    private EntriesListFragment mEntriesFragment;
    private DrawerLayout mDrawerLayout;
    private View mLeftDrawer;
    private ListView mDrawerList;
    private DrawerAdapter mDrawerAdapter = null;
    private ActionBarDrawerToggle mDrawerToggle;
    private CharSequence mTitle;
    private int mCurrentDrawerPos;
    private Handler mHandler = null;
    public static Boolean mFeedSetupChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        mEntriesFragment = (EntriesListFragment) getSupportFragmentManager().findFragmentById(R.id.entries_list_fragment);

        mTitle = getTitle();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLeftDrawer = findViewById(R.id.left_drawer);
        mDrawerList = (ListView) findViewById(R.id.drawer_list);
        mDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mDrawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerItem(position);
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.closeDrawer(mLeftDrawer);
                        }
                    }, 50);
                }
            }
        });
        mDrawerList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (id > 0) {
                    startActivity(new Intent(Intent.ACTION_EDIT).setData(FeedColumns.CONTENT_URI(id)));
                    return true;
                }
                return false;
            }
        });

        if (savedInstanceState != null)
            mCurrentDrawerPos = savedInstanceState.getInt(STATE_CURRENT_DRAWER_POS);
        else
            mCurrentDrawerPos = PrefUtils.getInt(STATE_CURRENT_DRAWER_POS, 1);


        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
            mDrawerLayout.setDrawerListener(mDrawerToggle);
        }

        if (!PrefUtils.getBoolean(PrefUtils.REMEBER_LAST_ENTRY, true))
            selectDrawerItem(0);

        getLoaderManager().initLoader(LOADER_ID, null, this);

        AutoRefreshService.initAutoRefresh(this);

        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ON_OPEN_ENABLED, false)) {
            if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
                startService(new Intent(HomeActivity.this, FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS));
            }
        }

        // Ask the permission to import the feeds if there is already one backup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && new File(OPML.BACKUP_OPML).exists()) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.storage_request_explanation).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ActivityCompat.requestPermissions(HomeActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_IMPORT_FROM_OPML);
                    }
                });
                builder.show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_IMPORT_FROM_OPML);
            }
        }

        mHandler = new Handler();
        FetcherService.getObservable().setHandler(mHandler);

        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onPause() {
        synchronized ( EntriesCursorAdapter.mMarkAsReadList ) {
            EntriesCursorAdapter.mMarkAsReadList.clear();//SetIsReadMakredList();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent.getScheme() != null && intent.getScheme().startsWith("http")) {

        } else if (PrefUtils.getBoolean(PrefUtils.REMEBER_LAST_ENTRY, true)) {
            String lastUri = PrefUtils.getString(PrefUtils.LAST_ENTRY_URI, "");
            if (!lastUri.isEmpty() && !lastUri.contains("-1"))
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lastUri)));
        }

        //getLoaderManager().restartLoader(LOADER_ID, null, this);
        //if ( mDrawerAdapter != null  )
        //    selectDrawerItem( mCurrentDrawerPos );
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_DRAWER_POS, mCurrentDrawerPos);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // We reset the current drawer position
        // selectDrawerItem(0);

        if ( intent.getAction() != null && intent.getAction().equals(Intent.ACTION_SEND) && intent.hasExtra(Intent.EXTRA_TEXT) ) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            Pattern p = Pattern.compile( "(?<![\\>https?://|href=\"'])(?<http>(https?:[/][/]|www.)([a-z]|[-_%]|[A-Z]|[0-9]|[/.]|[~])*)" );
            Matcher m = p.matcher( text );
            if ( m.find() )
                OpenExternalLink( text.substring(m.start(), m.end()), text.substring( 0, m.start() ) );

        } else if (intent.getScheme() != null && intent.getScheme().startsWith("http"))
            OpenExternalLink(intent.getDataString(), intent.getDataString());

    }

    private void OpenExternalLink(final String url, final String title) {
        new Thread() {
            @Override
            public void run() {
                int status = FetcherService.getObservable().Start( getString(R.string.loadingLink) );

                Uri entryUri;

                Cursor cursor = getContentResolver().query( EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(GetExtrenalLinkFeedID()),
                                            new String[] { EntryColumns._ID },
                                            EntryColumns.LINK + "='" + url +"'",
                                            null,
                                            null);
                if ( cursor.moveToFirst() ) {
                    entryUri = EntryColumns.CONTENT_URI( cursor.getLong( 0 ) );
                } else {

                    ContentValues values = new ContentValues();
                    values.put(EntryColumns.TITLE, title);
                    //values.put(EntryColumns.ABSTRACT, NULL);
                    //values.put(EntryColumns.IMAGE_URL, NULL);
                    //values.put(EntryColumns.AUTHOR, NULL);
                    //values.put(EntryColumns.ENCLOSURE, NULL);
                    values.put(EntryColumns.DATE, (new Date()).getTime());
                    values.put(EntryColumns.LINK, url);

                    //values.put(EntryColumns.MOBILIZED_HTML, enclosureString);
                    //values.put(EntryColumns.ENCLOSURE, enclosureString);
                    entryUri = getContentResolver().insert(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(GetExtrenalLinkFeedID()), values);

                    FetcherService.mobilizeEntry(getContentResolver(), Long.parseLong(entryUri.getLastPathSegment()));
                }
                cursor.close();

                //startActivity(new Intent(Intent.ACTION_VIEW, entryUri));
                PrefUtils.putString( PrefUtils.LAST_ENTRY_URI, entryUri.toString() );
                startActivity( new Intent( HomeActivity.this, HomeActivity.class ) );
                FetcherService.getObservable().End( status );
            }

        }.start();
    }

    long GetExtrenalLinkFeedID() {
        long result = 0;

        Cursor cursor = getContentResolver().query( FeedColumns.CONTENT_URI,
                                                    FeedColumns.PROJECTION_ID,
                                                    FeedColumns.FETCH_MODE + "=" + FetcherService.FETCHMODE_EXERNAL_LINK,
                                                    null,
                                                    null);
        if ( cursor.moveToFirst() )
            result = cursor.getLong( 0 );
        cursor.close();

        if ( result == 0 ) {
            ContentValues values = new ContentValues();
            values.put( FeedColumns.FETCH_MODE, FetcherService.FETCHMODE_EXERNAL_LINK );
            values.put( FeedColumns.NAME, getString(R.string.externalLinks) );
            result = Long.parseLong( getContentResolver().insert(FeedColumns.CONTENT_URI, values).getLastPathSegment() );
        }
        return result;
    }

    public void onBackPressed() {
        // Before exiting from app the navigation drawer is opened
        if (mDrawerLayout != null && !mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.openDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    public void onClickEditFeeds(View view) {
        startActivity(new Intent(this, EditFeedsListActivity.class));
    }

    public void onClickAdd(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_add_feed)
                .setItems(new CharSequence[]{getString(R.string.add_custom_feed), getString(R.string.google_news_title)}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            startActivity(new Intent(Intent.ACTION_INSERT).setData(FeedColumns.CONTENT_URI));
                        } else {
                            startActivity(new Intent(HomeActivity.this, AddGoogleNewsActivity.class));
                        }
                    }
                });
        builder.show();
    }

    public void onClickSettings(View view) {
        startActivity(new Intent(this, GeneralPrefsActivity.class));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        CursorLoader cursorLoader =
                new CursorLoader(this,
                                 FeedColumns.GROUPED_FEEDS_CONTENT_URI,
                                 new String[]{FeedColumns._ID, FeedColumns.URL, FeedColumns.NAME,
                                              FeedColumns.IS_GROUP, FeedColumns.ICON, FeedColumns.LAST_UPDATE,
                                              FeedColumns.ERROR, FEED_UNREAD_NUMBER, FeedColumns.SHOW_TEXT_IN_ENTRY_LIST,
                                              FeedColumns.IS_GROUP_EXPANDED  },
                                         FeedColumns.IS_GROUP + Constants.DB_IS_TRUE + Constants.DB_OR +
                                         FeedColumns.GROUP_ID + Constants.DB_IS_NULL  + Constants.DB_OR +
                                         FeedColumns.GROUP_ID + " IN (SELECT " + FeedColumns._ID +
                                                                     " FROM " + FeedColumns.TABLE_NAME +
                                                                     " WHERE " + FeedColumns.IS_GROUP_EXPANDED + Constants.DB_IS_TRUE + ")",
                                 null,
                                 null );
        cursorLoader.setUpdateThrottle(Constants.UPDATE_THROTTLE_DELAY);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {

        synchronized (mFeedSetupChanged) {
            if (mDrawerAdapter != null && !mFeedSetupChanged) {
                mDrawerAdapter.setCursor(cursor);
            } else {
                mFeedSetupChanged = false;

                mDrawerAdapter = new DrawerAdapter(this, cursor);
                mDrawerList.setAdapter(mDrawerAdapter);
                // We don't have any menu yet, we need to display it

                mDrawerList.post(new Runnable() {
                    @Override
                    public void run() {
                            selectDrawerItem(mCurrentDrawerPos);
                        }
                });
            }
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mDrawerAdapter.setCursor(null);
    }

    private void selectDrawerItem(int position) {


        mCurrentDrawerPos = position;

        Uri newUri;
        boolean showFeedInfo = true;

        switch (position) {
            case 0:
                newUri = EntryColumns.UNREAD_ENTRIES_CONTENT_URI;
                break;
            case 1:
                newUri = EntryColumns.CONTENT_URI;
                break;
            case 2:
                newUri = EntryColumns.FAVORITES_CONTENT_URI;
                break;
            default:
                long feedOrGroupId = mDrawerAdapter.getItemId(position);
                if ( feedOrGroupId != -1 ) {
                    if (mDrawerAdapter.isItemAGroup(position)) {
                        newUri = EntryColumns.ENTRIES_FOR_GROUP_CONTENT_URI(feedOrGroupId);
                    } else {
                        newUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedOrGroupId);
                        showFeedInfo = false;
                    }
                } else
                    newUri = EntryColumns.UNREAD_ENTRIES_CONTENT_URI;
                mTitle = mDrawerAdapter.getItemName(position);

                break;
        }

        //if (!newUri.equals(mEntriesFragment.getUri()))
             mEntriesFragment.setData(newUri,
                                      showFeedInfo,
                                      false,
                                      mDrawerAdapter == null ? false : mDrawerAdapter.isShowTextInEntryList(position));


        mDrawerList.setItemChecked(position, true);

        // First open => we open the drawer for you
        if (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) {
            PrefUtils.putBoolean(PrefUtils.FIRST_OPEN, false);
            if (mDrawerLayout != null) {
                mDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(mLeftDrawer);
                    }
                }, 500);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.welcome_title)
                    .setItems(new CharSequence[]{getString(R.string.google_news_title), getString(R.string.add_custom_feed)}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 1) {
                                startActivity(new Intent(Intent.ACTION_INSERT).setData(FeedColumns.CONTENT_URI));
                            } else {
                                startActivity(new Intent(HomeActivity.this, AddGoogleNewsActivity.class));
                            }
                        }
                    });
            builder.show();
        }

        // Set title & icon
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            switch (mCurrentDrawerPos) {
                case 0:
                    getSupportActionBar().setTitle(R.string.unread_entries);
                    break;
                case 1:
                    getSupportActionBar().setTitle(R.string.all_entries);
                    break;
                case 2:
                    getSupportActionBar().setTitle(R.string.favorites);
                    break;
                default:
                    getSupportActionBar().setTitle(mTitle);
                    break;
            }
        }

        PrefUtils.putInt(STATE_CURRENT_DRAWER_POS, mCurrentDrawerPos);

        // Put the good menu
        invalidateOptionsMenu();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_IMPORT_FROM_OPML: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    new Thread(new Runnable() { // To not block the UI
                        @Override
                        public void run() {
                            try {
                                // Perform an automated import of the backup
                                OPML.importFromFile(OPML.BACKUP_OPML);
                            } catch (Exception ignored) {
                            }
                        }
                    }).start();
                }
                return;
            }
        }
    }
}
