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
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.nextcloud.android.lib.resources.dashboard.DashboardGetWidgetItemsRemoteOperation
import com.nextcloud.android.lib.resources.dashboard.DashboardWidgetItem
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.network.ClientFactory
import com.nextcloud.client.preferences.AppPreferences
import com.owncloud.android.R
import com.owncloud.android.lib.common.utils.Log_OC
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Random
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

private const val REMOTE_VIEW_COUNT: Int = 10

class StackRemoteViewsFactory(
    private val context: Context,
    val userAccountManager: UserAccountManager,
    val clientFactory: ClientFactory,
    val intent: Intent,
    val appPreferences: AppPreferences
) : RemoteViewsService.RemoteViewsFactory {

    private lateinit var widgetConfiguration: WidgetConfiguration
    private lateinit var widgetItems: List<DashboardWidgetItem>

    override fun onCreate() {
        Log_OC.d(this, "onCreate")
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        widgetConfiguration = appPreferences.getWidget(appWidgetId)

        if (!widgetConfiguration.user.isPresent) {
            // TODO show error
            Log_OC.e(this, "No user found!")
        }

        //widgetApp = "recommendations" // TODO change me

        widgetItems = emptyList()
    }

    override fun onDataSetChanged() {
        CoroutineScope(Dispatchers.IO).launch {
            val client = clientFactory.createNextcloudClient(widgetConfiguration.user.get())
            val result = DashboardGetWidgetItemsRemoteOperation(widgetConfiguration.widgetId).execute(client)
            widgetItems = result.resultData[widgetConfiguration.widgetId] ?: emptyList()
        }

        Log_OC.d("WidgetService", "onDataSetChanged")
    }

    override fun onDestroy() {
        Log_OC.d("WidgetService", "onDestroy")

        widgetItems = emptyList()
    }

    override fun getCount(): Int {
        return widgetItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            val widgetItem = widgetItems[position]

            val target1: SimpleTarget<Bitmap> = object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap?, glideAnimation: GlideAnimation<in Bitmap>?) {
                    // setImageViewResource(R.id.icon, resource)
                    setImageViewBitmap(R.id.icon, resource)
                    // imageView.setImageDrawable(resource)
                }
            }

            // icon
            if (widgetItem.iconUrl.isNotEmpty()) {
                val test = Glide.with(context)
                    .load("https://www.creavea.com/produits/82320-l/image-3d-divers-zen-n2-30-x-30-cm-l.jpg")
                    .asBitmap()
                    .into(256, 256)

                setImageViewBitmap(R.id.icon, test.get())

                // Glide.with(context)
                //     .load(widgetItem.iconUrl)
                //     .asBitmap()
                //     .diskCacheStrategy(DiskCacheStrategy.ALL)
                //     .into(target1)

                //
                // Glide.with(context)
                //     .using(CustomGlideStreamLoader(widgetConfiguration.user.get(), clientFactory))
                //     .load(widgetItem.iconUrl)
                //     .asBitmap()
                //     .placeholder(R.id.icon)
                //     .error(R.id.icon)
                //     //.diskCacheStrategy(DiskCacheStrategy.NONE)
                //     //.skipMemoryCache(true)
                //     .crossFade()
                //
                //    
                //     //.into(target1)
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
            setTextViewText(R.id.subtitle, widgetItem.subtitle)

            if (widgetItem.link != null) {
                val clickIntent = Intent(Intent.ACTION_VIEW, Uri.parse(widgetItem.link))
                setOnClickFillInIntent(R.id.text_container, clickIntent)
            }
        }
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true // TODO check me
    }

    private fun randomList(): List<DashboardWidgetItem> {
        if (Random().nextInt(3) == 1) {
            return emptyList()
        } else {
            return List(REMOTE_VIEW_COUNT) {
                when (Random().nextInt(10)) {
                    0 -> DashboardWidgetItem("⚙️ Sysadmin", "You were mentioned", "https://sysadmin.de", "")
                    1 -> DashboardWidgetItem(
                        "👩‍⚖️ Support!",
                        "You were mentioned",
                        "https://support.nextcloud.com",
                        ""
                    )
                    2 -> DashboardWidgetItem(
                        "Andy Scherzinger",
                        "Please reply",
                        "",
                        "Andy",
                    )
                    3 -> DashboardWidgetItem(
                        "Christoph Wurst",
                        "See you next week!",
                        "",
                        "Christoph Wurst",
                    )
                    4 -> DashboardWidgetItem("\uD83D\uDEE0️ Engineering", "You were mentioned", "", "")
                    5 -> DashboardWidgetItem("\uD83D\uDCF1 Mobile apps public", "Please see link above.", "", "")
                    else -> DashboardWidgetItem(
                        "Jos Poortvliet",
                        "Haha, funny",
                        "",
                        "Jos Poortvliet",
                    )
                }
            }
        }
    }
}
