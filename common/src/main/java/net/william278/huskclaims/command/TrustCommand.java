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

package net.william278.huskclaims.command;

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.TrustLevel;
import net.william278.huskclaims.claim.Trustable;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class TrustCommand extends InClaimCommand implements TrustableTabCompletable {

    private final TrustLevel level;

    protected TrustCommand(@NotNull TrustLevel level, @NotNull HuskClaims plugin) {
        super(
                level.getCommandAliases(),
                getUsageText(plugin.getSettings().getUserGroups()),
                TrustLevel.Privilege.MANAGE_TRUSTEES,
                plugin
        );
        this.level = level;
    }

    @Override
    public void execute(@NotNull OnlineUser executor, @NotNull ClaimWorld world, @NotNull Claim claim,
                        @NotNull String[] args) {
        final Optional<String> toTrust = parseStringArg(args, 0);
        if (toTrust.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
            return;
        }

        // Resolve the trustable and check the executor has access
        resolveTrustable(executor, toTrust.get(), claim)
                .flatMap(t -> checkUserHasAccess(executor, t, world, claim) ? Optional.of(t) : Optional.empty())
                .ifPresent(t -> setTrustLevel(executor, t, world, claim));
    }

    private void setTrustLevel(@NotNull OnlineUser executor, @NotNull Trustable trustable,
                               @NotNull ClaimWorld world, @NotNull Claim claim) {
        claim.setTrustLevel(trustable, world, level);
        plugin.getDatabase().updateClaimWorld(world);
        plugin.getLocales().getLocale("trust_level_set",
                        trustable.getTrustIdentifier(plugin), level.getDisplayName())
                .ifPresent(executor::sendMessage);
    }

    @Nullable
    @Override
    public UUID getGroupOwner(@NotNull OnlineUser user) {
        return plugin.getClaimAt(user.getPosition()).flatMap(Claim::getOwner).orElse(null);
    }

    @NotNull
    @Override
    public String getDescription() {
        return level.getDescription();
    }

}
