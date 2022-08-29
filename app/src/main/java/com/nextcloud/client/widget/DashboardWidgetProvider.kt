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
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.StreamEncoder
import com.bumptech.glide.load.resource.file.FileToStreamDecoder
import com.bumptech.glide.request.target.AppWidgetTarget
import com.nextcloud.client.preferences.AppPreferences
import com.nextcloud.client.preferences.AppPreferencesImpl
import com.owncloud.android.R
import com.owncloud.android.utils.svg.SVGorImage
import com.owncloud.android.utils.svg.SvgOrImageBitmapTranscoder
import com.owncloud.android.utils.svg.SvgOrImageDecoder
import java.io.InputStream

/**
 * Manages widgets
 */
class DashboardWidgetProvider : AppWidgetProvider() {
    private lateinit var appPreferences: AppPreferences

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appPreferences = AppPreferencesImpl.fromContext(context)

        for (appWidgetId in appWidgetIds) {
            val widgetConfiguration = appPreferences.getWidget(appWidgetId)
            updateAppWidget(
                context,
                appWidgetManager,
                appWidgetId,
                widgetConfiguration.title,
                widgetConfiguration.iconUrl
            )
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray) {
        appPreferences = AppPreferencesImpl.fromContext(context)

        for (appWidgetId in appWidgetIds) {
            appPreferences.deleteWidget(appWidgetId)
        }
    }

    companion object {
        @SuppressLint("UnspecifiedImmutableFlag")
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            title: String,
            iconUrl: String
        ) {
            val intent = Intent(context, DashboardWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.dashboard_widget).apply {
                setRemoteAdapter(R.id.list, intent)

                setEmptyView(R.id.list, R.id.empty_view)

                setTextViewText(R.id.title, title)

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

                val appWidgetTarget = AppWidgetTarget(context, this, R.id.icon, appWidgetId)

                Glide.with(context)
                    .using(
                        Glide.buildStreamModelLoader(Uri::class.java, context),
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
                    .into(appWidgetTarget)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list)
        }
    }
}
