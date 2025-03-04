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

package net.william278.huskclaims;

import lombok.NoArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.william278.huskclaims.highlighter.BlockHighlighter;
import net.william278.huskclaims.highlighter.GlowHighlighter;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.BukkitUser;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

@NoArgsConstructor
public class PaperHuskClaims extends BukkitHuskClaims {

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void sendBlockUpdates(@NotNull OnlineUser user, @NotNull Map<Position, MaterialBlock> blocks) {
        ((BukkitUser) user).getBukkitPlayer().sendMultiBlockChange(Adapter.adapt(blocks));
    }

    @Override
    @NotNull
    public Audience getAudience(@NotNull UUID user) {
        final Player player = getServer().getPlayer(user);
        return player == null || !player.isOnline() ? Audience.empty() : player;
    }

    @Override
    public void loadClaimHighlighter() {
        setHighlighter(new GlowHighlighter(this));
    }

}
