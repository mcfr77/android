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

package com.owncloud.android.ui.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.nextcloud.android.lib.resources.dashboard.DashboardWidget
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.network.ClientFactory
import com.nextcloud.client.widget.DashboardWidgetConfigurationInterface
import com.owncloud.android.R
import com.owncloud.android.databinding.WidgetListItemBinding
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.theme.ThemeDrawableUtils

class WidgetListItemViewHolder(
    val binding: WidgetListItemBinding,
    val themeDrawableUtils: ThemeDrawableUtils,
    val accountManager: UserAccountManager,
    val clientFactory: ClientFactory,
    val context: Context
) :
    RecyclerView.ViewHolder(binding.root) {
    fun bind(
        dashboardWidget: DashboardWidget,
        dashboardWidgetConfigurationInterface: DashboardWidgetConfigurationInterface
    ) {
        binding.layout.setOnClickListener { dashboardWidgetConfigurationInterface.onItemClicked(dashboardWidget) }

        val target = object : SimpleTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable?, glideAnimation: GlideAnimation<in Drawable>?) {
                // binding.icon.setImageDrawable(themeDrawableUtils.tintDrawable(resource, R.color.black))

                binding.icon.setImageDrawable(resource)
                binding.icon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            }

            override fun onLoadFailed(e: java.lang.Exception?, errorDrawable: Drawable?) {
                super.onLoadFailed(e, errorDrawable)
                binding.icon.setImageDrawable(errorDrawable)
            }
        }

        DisplayUtils.downloadIcon(
            accountManager,
            clientFactory,
            context,
            dashboardWidget.iconUrl,
            target,
            R.drawable.ic_dashboard,
            binding.icon.width,
            binding.icon.height
        )
        binding.name.text = dashboardWidget.title
    }
}