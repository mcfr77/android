/*
 *
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2022 Tobias Kaminsky
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.client.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bumptech.glide.Glide
import com.nextcloud.android.lib.resources.dashboard.DashboardGetWidgetItemsRemoteOperation
import com.nextcloud.android.lib.resources.dashboard.DashboardWidgetItem
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.network.ClientFactory
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.R
import com.owncloud.android.lib.common.utils.Log_OC
import com.owncloud.android.utils.glide.CustomGlideStreamLoader
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class DashboardWidgetService : RemoteViewsService() {
    @Inject
    lateinit var userAccountManager: UserAccountManager

    @Inject
    lateinit var clientFactory: ClientFactory

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return StackRemoteViewsFactory(
            this.applicationContext,
            userAccountManager,
            clientFactory,
            intent,
            appPreferences
        )
    }
}

class StackRemoteViewsFactory(
    private val context: Context,
    val userAccountManager: UserAccountManager,
    val clientFactory: ClientFactory,
    val intent: Intent,
    val appPreferences: AppPreferences
) : RemoteViewsService.RemoteViewsFactory {

    private lateinit var widgetConfiguration: WidgetConfiguration
    private var widgetItems: List<DashboardWidgetItem> = emptyList()
    private var hasLoadMore = true

    override fun onCreate() {
        Log_OC.d(this, "onCreate")
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        widgetConfiguration = appPreferences.getWidget(appWidgetId)

        if (!widgetConfiguration.user.isPresent) {
            // TODO show error
            Log_OC.e(this, "No user found!")
        }

        hasLoadMore = widgetConfiguration.moreButton != null

        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = clientFactory.createNextcloudClient(widgetConfiguration.user.get())
                val result = DashboardGetWidgetItemsRemoteOperation(widgetConfiguration.widgetId).execute(client)
                widgetItems = result.resultData[widgetConfiguration.widgetId] ?: emptyList()
            } catch (e: Exception) {
                Log_OC.e(this, "Error updating widget", e)
            }
        }

        Log_OC.d("WidgetService", "onDataSetChanged")
    }

    override fun onDestroy() {
        Log_OC.d("WidgetService", "onDestroy")

        widgetItems = emptyList()
    }

    override fun getCount(): Int {
        return if (hasLoadMore && widgetItems.isNotEmpty()) {
            widgetItems.size + 1
        } else {
            widgetItems.size
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position == widgetItems.size) {
            return RemoteViews(context.packageName, R.layout.widget_item_load_more).apply {
                val clickIntent = Intent(Intent.ACTION_VIEW, Uri.parse(widgetConfiguration.moreButton?.link))
                setTextViewText(R.id.load_more, widgetConfiguration.moreButton?.text)
                setOnClickFillInIntent(R.id.load_more_container, clickIntent)
            }
        } else {
            return RemoteViews(context.packageName, R.layout.widget_item).apply {
                val widgetItem = widgetItems[position]

                // icon
                if (widgetItem.iconUrl.isNotEmpty()) {
                    val test = Glide.with(context)
                        .using(CustomGlideStreamLoader(widgetConfiguration.user.get(), clientFactory))
                        .load(widgetItem.iconUrl)
                        .asBitmap()
                        .into(256, 256)
                    try {
                        setImageViewBitmap(R.id.icon, test.get())
                    } catch (e: Exception) {
                        Log_OC.d(this, "Error setting icon", e)
                        setImageViewResource(R.id.icon, R.drawable.ic_dashboard)
                    }
                }
                // if (widgetItem.userName != null) {
                //     val avatarRadius: Float = context.resources.getDimension(R.dimen.widget_avatar_icon_radius)
                //     val avatarBitmap =
                //         BitmapUtils.drawableToBitmap(TextDrawable.createNamedAvatar(widgetItem.userName, avatarRadius))
                //
                //     // val avatar = BitmapUtils.createAvatarWithStatus(
                //     //     avatarBitmap,
                //     //     widgetItem.statusType,
                //     //     widgetItem.icon ?: "",
                //     //     context
                //     // )
                //
                //     setImageViewBitmap(R.id.icon, avatarBitmap)
                // } else {
                //     setImageViewResource(R.id.icon, R.drawable.ic_group)
                // }

                // text
                setTextViewText(R.id.title, widgetItem.title)

                if (widgetItem.subtitle.isNotEmpty()) {
                    setViewVisibility(R.id.subtitle, View.VISIBLE)
                    setTextViewText(R.id.subtitle, widgetItem.subtitle)
                } else {
                    setViewVisibility(R.id.subtitle, View.GONE)
                }

                if (widgetItem.link.isNotEmpty()) {
                    val clickIntent = Intent(Intent.ACTION_VIEW, Uri.parse(widgetItem.link))
                    setOnClickFillInIntent(R.id.text_container, clickIntent)
                }
            }
        }
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return if (hasLoadMore) {
            2
        } else {
            1
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
