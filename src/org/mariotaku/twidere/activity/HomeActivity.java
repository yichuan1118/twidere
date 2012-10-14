/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.activity;

import static org.mariotaku.twidere.util.Utils.cleanDatabasesByItemLimit;
import static org.mariotaku.twidere.util.Utils.getAccountIds;
import static org.mariotaku.twidere.util.Utils.getActivatedAccountIds;
import static org.mariotaku.twidere.util.Utils.getNewestMessageIdsFromDatabase;
import static org.mariotaku.twidere.util.Utils.getNewestStatusIdsFromDatabase;
import static org.mariotaku.twidere.util.Utils.getTabs;
import static org.mariotaku.twidere.util.Utils.openDirectMessagesConversation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.mariotaku.actionbarcompat.ActionBar;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.TabsAdapter;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.fragment.APIUpgradeConfirmDialog;
import org.mariotaku.twidere.fragment.AccountsFragment;
import org.mariotaku.twidere.fragment.DirectMessagesFragment;
import org.mariotaku.twidere.fragment.HomeTimelineFragment;
import org.mariotaku.twidere.fragment.MentionsFragment;
import org.mariotaku.twidere.model.TabSpec;
import org.mariotaku.twidere.provider.TweetStore.Accounts;
import org.mariotaku.twidere.provider.TweetStore.DirectMessages.Inbox;
import org.mariotaku.twidere.provider.TweetStore.DirectMessages.Outbox;
import org.mariotaku.twidere.provider.TweetStore.Mentions;
import org.mariotaku.twidere.provider.TweetStore.Statuses;
import org.mariotaku.twidere.util.ArrayUtils;
import org.mariotaku.twidere.util.ServiceInterface;
import org.mariotaku.twidere.util.SetHomeButtonEnabledAccessor;
import org.mariotaku.twidere.view.ExtendedViewPager;
import org.mariotaku.twidere.view.TabPageIndicator;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManagerTrojan;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

public class HomeActivity extends MultiSelectActivity implements OnClickListener, OnPageChangeListener {

	private SharedPreferences mPreferences;
	private ServiceInterface mService;
	private TwidereApplication mApplication;

	private ActionBar mActionBar;
	private TabsAdapter mAdapter;

	private ExtendedViewPager mViewPager;
	private ImageButton mComposeButton;
	private TabPageIndicator mIndicator;
	private ProgressBar mProgress;

	private boolean mProgressBarIndeterminateVisible = false;

	private boolean mIsNavigateToDefaultAccount = false;
	private boolean mDisplayAppIcon;

	public static final int TAB_POSITION_HOME = 0;

	public static final int TAB_POSITION_MENTIONS = 1;
	public static final int TAB_POSITION_MESSAGES = 2;
	private final ArrayList<TabSpec> mCustomTabs = new ArrayList<TabSpec>();

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_REFRESHSTATE_CHANGED.equals(action)) {
				setSupportProgressBarIndeterminateVisibility(mProgressBarIndeterminateVisible);
			}
		}

	};

	public boolean checkDefaultAccountSet() {
		boolean result = true;
		final long[] activated_ids = getActivatedAccountIds(this);
		final long default_account_id = mPreferences.getLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, -1);
		if (default_account_id == -1 || !ArrayUtils.contains(activated_ids, default_account_id)) {
			if (activated_ids.length == 1) {
				mPreferences.edit().putLong(PREFERENCE_KEY_DEFAULT_ACCOUNT_ID, activated_ids[0]).commit();
				mIndicator.setPagingEnabled(true);
				mIsNavigateToDefaultAccount = false;
			} else if (activated_ids.length > 1) {
				final int count = mAdapter.getCount();
				if (count > 0) {
					mViewPager.setCurrentItem(count - 1, false);
				}
				mIndicator.setPagingEnabled(false);
				if (!mIsNavigateToDefaultAccount) {
					Toast.makeText(this, R.string.set_default_account_hint, Toast.LENGTH_LONG).show();
				}
				mIsNavigateToDefaultAccount = true;
				result = false;
			}
		} else {
			mIndicator.setPagingEnabled(true);
			mIsNavigateToDefaultAccount = false;
		}
		return result;
	}

	@Override
	public void onBackStackChanged() {
		super.onBackStackChanged();
		if (!isDualPaneMode()) return;
		final FragmentManager fm = getSupportFragmentManager();
		final Fragment left_pane_fragment = fm.findFragmentById(PANE_LEFT);
		final boolean left_pane_used = left_pane_fragment != null && left_pane_fragment.isAdded();
		setPagingEnabled(!left_pane_used);
		final int count = fm.getBackStackEntryCount();
		if (count == 0) {
			bringLeftPaneToFront();
		}
	}

	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
			case R.id.compose:
			case R.id.button_compose:
				if (mViewPager == null) return;
				final int position = mViewPager.getCurrentItem();
				if (position == mAdapter.getCount() - 1) {
					final Intent intent = new Intent(INTENT_ACTION_TWITTER_LOGIN);
					intent.setClass(this, SignInActivity.class);
					startActivity(intent);
				} else {
					switch (position) {
						case TAB_POSITION_MESSAGES:
							openDirectMessagesConversation(this, -1, -1);
							break;
						default:
							startActivity(new Intent(INTENT_ACTION_COMPOSE));
					}
				}
				break;
		}
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mViewPager = (ExtendedViewPager) findViewById(R.id.pager);
		mComposeButton = (ImageButton) findViewById(R.id.button_compose);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		mApplication = getTwidereApplication();
		mService = mApplication.getServiceInterface();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		super.onCreate(savedInstanceState);
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONCREATE));
		final Resources res = getResources();
		mDisplayAppIcon = res.getBoolean(R.bool.home_display_icon);
		final long[] account_ids = getAccountIds(this);
		if (account_ids.length <= 0) {
			final Intent intent = new Intent(INTENT_ACTION_TWITTER_LOGIN);
			intent.setClass(this, SignInActivity.class);
			startActivity(intent);
			finish();
			return;
		}
		final boolean refresh_on_start = mPreferences.getBoolean(PREFERENCE_KEY_REFRESH_ON_START, false);
		final Bundle bundle = getIntent().getExtras();
		int initial_tab = -1;
		if (bundle != null) {
			final long[] refreshed_ids = bundle.getLongArray(INTENT_KEY_IDS);
			if (refreshed_ids != null && !refresh_on_start && savedInstanceState == null) {
				mService.getHomeTimeline(refreshed_ids, null);
				mService.getMentions(refreshed_ids, null);
				mService.getReceivedDirectMessages(account_ids, null);
				mService.getSentDirectMessages(account_ids, null);
			}
			initial_tab = bundle.getInt(INTENT_KEY_INITIAL_TAB, -1);
			switch (initial_tab) {
				case TAB_POSITION_HOME: {
					mService.clearNotification(NOTIFICATION_ID_HOME_TIMELINE);
					break;
				}
				case TAB_POSITION_MENTIONS: {
					mService.clearNotification(NOTIFICATION_ID_MENTIONS);
					break;
				}
				case TAB_POSITION_MESSAGES: {
					mService.clearNotification(NOTIFICATION_ID_DIRECT_MESSAGES);
					break;
				}
			}
		}
		mActionBar = getSupportActionBar();
		mActionBar.setCustomView(R.layout.base_tabs);
		mActionBar.setDisplayShowTitleEnabled(false);
		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setDisplayShowHomeEnabled(mDisplayAppIcon);
		if (mDisplayAppIcon && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			SetHomeButtonEnabledAccessor.setHomeButtonEnabled(this, true);
		}
		final View view = mActionBar.getCustomView();

		mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
		mIndicator = (TabPageIndicator) view.findViewById(android.R.id.tabs);
		final boolean tab_display_label = res.getBoolean(R.bool.tab_display_label);
		mAdapter = new TabsAdapter(this, getSupportFragmentManager(), mIndicator);
		mAdapter.setDisplayLabel(tab_display_label);
		initTabs(getTabs(this));
		mViewPager.setAdapter(mAdapter);
		mViewPager.setOffscreenPageLimit(3);
		mIndicator.setViewPager(mViewPager);
		mIndicator.setOnPageChangeListener(this);
		getSupportFragmentManager().addOnBackStackChangedListener(this);

		final boolean remember_position = mPreferences.getBoolean(PREFERENCE_KEY_REMEMBER_POSITION, true);
		final long[] activated_ids = getActivatedAccountIds(this);
		if (activated_ids.length <= 0) {
			startActivityForResult(new Intent(INTENT_ACTION_SELECT_ACCOUNT), REQUEST_SELECT_ACCOUNT);
		} else if (checkDefaultAccountSet() && (remember_position || initial_tab >= 0)) {
			final int position = initial_tab >= 0 ? initial_tab : mPreferences.getInt(
					PREFERENCE_KEY_SAVED_TAB_POSITION, TAB_POSITION_HOME);
			if (position >= 0 || position < mViewPager.getChildCount()) {
				mViewPager.setCurrentItem(position);
			}
		}
		if (refresh_on_start && savedInstanceState == null) {
			mService.getHomeTimelineWithSinceIds(activated_ids, null,
					getNewestStatusIdsFromDatabase(this, Statuses.CONTENT_URI));
			if (mPreferences.getBoolean(PREFERENCE_KEY_HOME_REFRESH_MENTIONS, false)) {
				mService.getMentionsWithSinceIds(account_ids, null,
						getNewestStatusIdsFromDatabase(this, Mentions.CONTENT_URI));
			}
			if (mPreferences.getBoolean(PREFERENCE_KEY_HOME_REFRESH_DIRECT_MESSAGES, false)) {
				mService.getReceivedDirectMessagesWithSinceIds(account_ids, null,
						getNewestMessageIdsFromDatabase(this, Inbox.CONTENT_URI));
				mService.getSentDirectMessagesWithSinceIds(account_ids, null,
						getNewestMessageIdsFromDatabase(this, Outbox.CONTENT_URI));
			}
		}
		if (!mPreferences.getBoolean(PREFERENCE_KEY_API_UPGRADE_CONFIRMED, false)) {
			final FragmentManager fm = getSupportFragmentManager();
			if (fm.findFragmentByTag(FRAGMENT_TAG_API_UPGRADE_NOTICE) == null
					|| !fm.findFragmentByTag(FRAGMENT_TAG_API_UPGRADE_NOTICE).isAdded()) {
				new APIUpgradeConfirmDialog().show(getSupportFragmentManager(), "api_upgrade_notice");
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_home, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				final FragmentManager fm = getSupportFragmentManager();
				if (isDualPaneMode() && !FragmentManagerTrojan.isStateSaved(fm)) {
					final int count = fm.getBackStackEntryCount();
					for (int i = 0; i < count; i++) {
						fm.popBackStackImmediate();
					}
					setSupportProgressBarIndeterminateVisibility(false);
				}
				break;
			}
			case MENU_COMPOSE: {
				if (mComposeButton != null) {
					onClick(mComposeButton);
				}
				break;
			}
			case MENU_SEARCH: {
				onSearchRequested();
				break;
			}
			case MENU_SELECT_ACCOUNT: {
				startActivityForResult(new Intent(INTENT_ACTION_SELECT_ACCOUNT), REQUEST_SELECT_ACCOUNT);
				break;
			}
			case MENU_SETTINGS: {
				startActivity(new Intent(INTENT_ACTION_SETTINGS));
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {

	}

	@Override
	public void onPageScrollStateChanged(final int state) {

	}

	@Override
	public void onPageSelected(final int position) {
		switch (position) {
			case TAB_POSITION_HOME: {
				mService.clearNotification(NOTIFICATION_ID_HOME_TIMELINE);
				break;
			}
			case TAB_POSITION_MENTIONS: {
				mService.clearNotification(NOTIFICATION_ID_MENTIONS);
				break;
			}
			case TAB_POSITION_MESSAGES: {
				mService.clearNotification(NOTIFICATION_ID_DIRECT_MESSAGES);
				break;
			}
		}
		invalidateSupportOptionsMenu();
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final boolean bottom_actions = mPreferences.getBoolean(PREFERENCE_KEY_COMPOSE_BUTTON, false);
		final boolean leftside_compose_button = mPreferences.getBoolean(PREFERENCE_KEY_LEFTSIDE_COMPOSE_BUTTON, false);
		int icon = R.drawable.ic_menu_tweet, title = R.string.compose;
		if (mViewPager != null && mAdapter != null) {
			final int position = mViewPager.getCurrentItem();
			if (position == mAdapter.getCount() - 1) {
				icon = R.drawable.ic_menu_add;
				title = R.string.add_account;
			} else {
				title = R.string.compose;
				switch (position) {
					case TAB_POSITION_MESSAGES:
						icon = R.drawable.ic_menu_compose;
						break;
					default:
						icon = R.drawable.ic_menu_tweet;
				}
			}
		}
		final MenuItem composeItem = menu.findItem(MENU_COMPOSE);
		if (composeItem != null) {
			composeItem.setIcon(icon);
			composeItem.setTitle(title);
			composeItem.setVisible(!bottom_actions);
		}
		if (mComposeButton != null) {
			mComposeButton.setImageResource(icon);
			mComposeButton.setVisibility(bottom_actions ? View.VISIBLE : View.GONE);
			if (bottom_actions) {
				final FrameLayout.LayoutParams compose_lp = (FrameLayout.LayoutParams) mComposeButton.getLayoutParams();
				compose_lp.gravity = Gravity.BOTTOM | (leftside_compose_button ? Gravity.LEFT : Gravity.RIGHT);
				mComposeButton.setLayoutParams(compose_lp);
			}
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onResume() {
		super.onResume();
		invalidateSupportOptionsMenu();
	}

	@Override
	public void setSupportProgressBarIndeterminateVisibility(final boolean visible) {
		mProgressBarIndeterminateVisible = visible;
		mProgress.setVisibility(visible || mService.hasActivatedTask() ? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
		final ContentResolver resolver = getContentResolver();
		ContentValues values;
		switch (requestCode) {
			case REQUEST_SELECT_ACCOUNT: {
				if (resultCode == RESULT_OK) {
					if (intent == null || intent.getExtras() == null) {
						break;
					}
					final Bundle bundle = intent.getExtras();
					if (bundle == null) {
						break;
					}
					final long[] account_ids = bundle.getLongArray(INTENT_KEY_IDS);
					if (account_ids != null) {
						values = new ContentValues();
						values.put(Accounts.IS_ACTIVATED, 0);
						resolver.update(Accounts.CONTENT_URI, values, null, null);
						values = new ContentValues();
						values.put(Accounts.IS_ACTIVATED, 1);
						for (final long account_id : account_ids) {
							final String where = Accounts.ACCOUNT_ID + " = " + account_id;
							resolver.update(Accounts.CONTENT_URI, values, where, null);
						}
					}
					checkDefaultAccountSet();
				} else if (resultCode == RESULT_CANCELED) {
					if (getActivatedAccountIds(this).length <= 0) {
						finish();
					} else {
						checkDefaultAccountSet();
					}
				}
				break;
			}
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	protected void onDefaultAccountSet() {
		mIsNavigateToDefaultAccount = false;
	}

	@Override
	protected void onDestroy() {
		// Delete unused items in databases.
		cleanDatabasesByItemLimit(this);
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONDESTROY));
		super.onDestroy();
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		final Bundle bundle = intent.getExtras();
		if (bundle != null) {
			final long[] refreshed_ids = bundle.getLongArray(INTENT_KEY_IDS);
			if (refreshed_ids != null) {
				mService.getHomeTimelineWithSinceIds(refreshed_ids, null,
						getNewestStatusIdsFromDatabase(this, Statuses.CONTENT_URI));
				mService.getMentionsWithSinceIds(refreshed_ids, null,
						getNewestStatusIdsFromDatabase(this, Mentions.CONTENT_URI));
			}
			final int initial_tab = bundle.getInt(INTENT_KEY_INITIAL_TAB, -1);
			if (initial_tab != -1 && mViewPager != null) {
				switch (initial_tab) {
					case TAB_POSITION_HOME: {
						mService.clearNotification(NOTIFICATION_ID_HOME_TIMELINE);
						break;
					}
					case TAB_POSITION_MENTIONS: {
						mService.clearNotification(NOTIFICATION_ID_MENTIONS);
						break;
					}
					case TAB_POSITION_MESSAGES: {
						mService.clearNotification(NOTIFICATION_ID_DIRECT_MESSAGES);
						break;
					}
				}
				if (initial_tab >= 0 || initial_tab < mViewPager.getChildCount()) {
					mViewPager.setCurrentItem(initial_tab);
				}
			}
		}
		super.onNewIntent(intent);
	}

	@Override
	protected void onStart() {
		super.onStart();
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONSTART));
		setSupportProgressBarIndeterminateVisibility(mProgressBarIndeterminateVisible);
		final IntentFilter filter = new IntentFilter(BROADCAST_REFRESHSTATE_CHANGED);
		registerReceiver(mStateReceiver, filter);

		final List<TabSpec> tabs = getTabs(this);
		if (isTabsChanged(tabs)) {
			restart();
		}
		/**
		 * UCD 
		 */ 
		edu.ucdavis.earlybird.Util.profile(this, -1,
				"App.csv", "App onStart");
		/*
		 *
		 **/
	}

	@Override
	protected void onStop() {
		unregisterReceiver(mStateReceiver);
		mPreferences.edit().putInt(PREFERENCE_KEY_SAVED_TAB_POSITION, mViewPager.getCurrentItem()).commit();
		sendBroadcast(new Intent(BROADCAST_HOME_ACTIVITY_ONSTOP));
		/**
		 * UCD 
		 */ 
		edu.ucdavis.earlybird.Util.profile(this, -1,
				"App.csv", "App onStop");
		/*
		 *
		 **/
		super.onStop();
	}

	protected void setPagingEnabled(final boolean enabled) {
		if (mIndicator != null) {
			mIndicator.setPagingEnabled(enabled);
			mIndicator.setEnabled(enabled);
		}
	}

	private void initTabs(final Collection<? extends TabSpec> tabs) {
		mCustomTabs.clear();
		mCustomTabs.addAll(tabs);
		mAdapter.clear();
		mAdapter.addTab(HomeTimelineFragment.class, null, getString(R.string.home), R.drawable.ic_tab_home,
				TAB_POSITION_HOME);
		mAdapter.addTab(MentionsFragment.class, null, getString(R.string.mentions), R.drawable.ic_tab_mention,
				TAB_POSITION_MENTIONS);
		mAdapter.addTab(DirectMessagesFragment.class, null, getString(R.string.direct_messages),
				R.drawable.ic_tab_message, TAB_POSITION_MESSAGES);
		mAdapter.addTabs(tabs);
		mAdapter.addTab(AccountsFragment.class, null, getString(R.string.accounts), R.drawable.ic_tab_accounts,
				mAdapter.getCount());

	}

	private boolean isTabsChanged(final List<TabSpec> tabs) {
		if (mCustomTabs.size() == 0 && tabs == null) return false;
		if (mCustomTabs.size() != tabs.size()) return true;
		final int size = mCustomTabs.size();
		for (int i = 0; i < size; i++) {
			if (!mCustomTabs.get(i).equals(tabs.get(i))) return true;
		}
		return false;
	}

	@Override
	int getDualPaneLayoutRes() {
		return R.layout.home_dual_pane;
	}

	@Override
	int getNormalLayoutRes() {
		return R.layout.home;
	}

}
