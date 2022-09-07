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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.StreamEncoder
import com.bumptech.glide.load.resource.file.FileToStreamDecoder
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.AppWidgetTarget
import com.nextcloud.android.lib.resources.dashboard.DashboardButton
import com.nextcloud.client.account.CurrentAccountProvider
import com.nextcloud.client.account.UserAccountManagerImpl
import com.nextcloud.client.network.ClientFactory
import com.nextcloud.client.network.ClientFactoryImpl
import com.nextcloud.client.preferences.AppPreferences
import com.nextcloud.client.preferences.AppPreferencesImpl
import com.owncloud.android.R
import com.owncloud.android.utils.BitmapUtils
import com.owncloud.android.utils.glide.CustomGlideUriLoader
import com.owncloud.android.utils.svg.SVGorImage
import com.owncloud.android.utils.svg.SvgOrImageBitmapTranscoder
import com.owncloud.android.utils.svg.SvgOrImageDecoder
import java.io.InputStream

/**
 * Manages widgets
 */
class DashboardWidgetProvider : AppWidgetProvider() {
    private lateinit var appPreferences: AppPreferences
    private lateinit var clientFactory: ClientFactory
    private lateinit var accountProvider: CurrentAccountProvider

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appPreferences = AppPreferencesImpl.fromContext(context)
        clientFactory = ClientFactoryImpl(context)
        accountProvider = UserAccountManagerImpl.fromContext(context)

        for (appWidgetId in appWidgetIds) {
            val widgetConfiguration = appPreferences.getWidget(appWidgetId)
            updateAppWidget(
                context,
                appWidgetManager,
                clientFactory,
                accountProvider,
                appWidgetId,
                widgetConfiguration.title,
                widgetConfiguration.iconUrl,
                widgetConfiguration.addButton,
                widgetConfiguration.moreButton
            )
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)

        if (intent?.action == OPEN_INTENT) {
            val clickIntent = Intent(Intent.ACTION_VIEW, intent.data)
            clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context?.startActivity(clickIntent)
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray) {
        appPreferences = AppPreferencesImpl.fromContext(context)

        for (appWidgetId in appWidgetIds) {
            appPreferences.deleteWidget(appWidgetId)
        }
    }

    companion object {
        const val OPEN_INTENT = "open"

        @SuppressLint("UnspecifiedImmutableFlag")
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            clientFactory: ClientFactory,
            accountProvider: CurrentAccountProvider,
            appWidgetId: Int,
            title: String,
            iconUrl: String,
            moreButton: DashboardButton?,
            addButton: DashboardButton?
        ) {
            val intent = Intent(context, DashboardWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.dashboard_widget).apply {
                setRemoteAdapter(R.id.list, intent)

                setEmptyView(R.id.list, R.id.empty_view)

                setTextViewText(R.id.title, title)

                // create add button
                if (addButton == null) {
                    setViewVisibility(R.id.create, View.GONE)
                } else {
                    setViewVisibility(R.id.create, View.VISIBLE)
                    setContentDescription(R.id.create, addButton.text)

                    val clickIntent = Intent(context, DashboardWidgetProvider::class.java)
                    clickIntent.action = OPEN_INTENT
                    clickIntent.data = Uri.parse(addButton.link)

                    setOnClickPendingIntent(
                        R.id.create,
                        PendingIntent.getBroadcast(context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                }

                val intentUpdate = Intent(context, DashboardWidgetProvider::class.java)
                intentUpdate.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

                val idArray = intArrayOf(appWidgetId)
                intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, idArray)

                setOnClickPendingIntent(
                    R.id.reload,
                    PendingIntent.getBroadcast(context, appWidgetId, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT)
                )

                val clickPI = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                setPendingIntentTemplate(R.id.list, clickPI)

                val test = object : AppWidgetTarget(context, this, R.id.icon, appWidgetId) {
                    override fun onResourceReady(resource: Bitmap?, glideAnimation: GlideAnimation<in Bitmap>?) {
                        if (resource != null) {
                            val tintedBitmap = BitmapUtils.tintImage(resource, R.color.black)
                            super.onResourceReady(tintedBitmap, glideAnimation)
                        }
                    }
                }

                Glide.with(context)
                    .using(
                        CustomGlideUriLoader(accountProvider.getUser(), clientFactory),
                        InputStream::class.java
                    )
                    .from(Uri::class.java)
                    .`as`(SVGorImage::class.java)
                    .transcode(SvgOrImageBitmapTranscoder(128, 128), Bitmap::class.java)
                    .sourceEncoder(StreamEncoder())
                    .cacheDecoder(FileToStreamDecoder(SvgOrImageDecoder()))
                    .decoder(SvgOrImageDecoder())
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .load(Uri.parse(iconUrl))
                    .into(test)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list)
        }
    }
}
