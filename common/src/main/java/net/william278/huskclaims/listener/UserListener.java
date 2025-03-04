/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskclaims.listener;

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

public interface UserListener {

    default void onUserJoin(@NotNull OnlineUser user) {
        getPlugin().loadUserData(user);
    }

    default void onUserQuit(@NotNull OnlineUser user) {

    }

    default void onUserPlayOneHour(@NotNull OnlineUser user) {
        getPlugin().runAsync(() -> getPlugin().grantHourlyClaimBlocks(user));
    }

    default void onUserSwitchHeldItem(@NotNull OnlineUser user, @NotNull String nowHolding) {
        final Settings.ClaimSettings claimSettings = getPlugin().getSettings().getClaims();
        if (!claimSettings.getClaimTool().equals(nowHolding) && !claimSettings.getInspectionTool().equals(nowHolding)) {
            getPlugin().getHighlighter().stopHighlighting(user);
            if (getPlugin().clearClaimSelection(user)) {
                getPlugin().getLocales().getLocale("claim_selection_cancelled")
                        .ifPresent(user::sendMessage);
            }
        }
    }

    @NotNull
    HuskClaims getPlugin();

}
