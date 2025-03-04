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

import com.google.common.collect.Lists;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.TrustLevel;
import net.william278.huskclaims.claim.Trustable;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.group.UserGroup;
import net.william278.huskclaims.user.CommandUser;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class UnTrustCommand extends InClaimCommand implements TabCompletable {

    protected UnTrustCommand(@NotNull HuskClaims plugin) {
        super(
                List.of("untrust"),
                getUsageText(plugin.getSettings().getUserGroups()),
                TrustLevel.Privilege.MANAGE_TRUSTEES,
                plugin
        );
    }

    @Override
    public void execute(@NotNull OnlineUser executor, @NotNull ClaimWorld world,
                        @NotNull Claim claim, @NotNull String[] args) {
        final Optional<String> toUnTrust = parseStringArg(args, 0);
        if (toUnTrust.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
            return;
        }

        // Resolve the trustable and check the executor has access
        resolveTrustable(executor, toUnTrust.get(), claim)
                .flatMap(t -> checkUserHasAccess(executor, t, world, claim) ? Optional.of(t) : Optional.empty())
                .ifPresent(trustable -> removeTrust(executor, trustable, world, claim));
    }

    // We allow the resolution of deleted user groups as we need to be able to remove them from the claim
    @Override
    protected Optional<UserGroup> resolveGroup(@NotNull OnlineUser user, @NotNull String name, @NotNull Claim claim,
                                               @NotNull Settings.UserGroupSettings groups) {
        if (!claim.getTrustedGroups().containsKey(name)) {
            return Optional.empty();
        }
        return claim.getOwner().map(o -> new UserGroup(o, name, List.of()));
    }

    @Blocking
    private void removeTrust(@NotNull OnlineUser executor, @NotNull Trustable toUntrust,
                             @NotNull ClaimWorld world, @NotNull Claim claim) {
        boolean removed = false;
        if (toUntrust instanceof User user) {
            removed = claim.getTrustedUsers().remove(user.getUuid()) != null;
        } else if (toUntrust instanceof UserGroup group) {
            removed = claim.getTrustedGroups().remove(group.name()) != null;
        }

        if (!removed) {
            plugin.getLocales().getLocale("error_not_trusted", toUntrust.getTrustIdentifier(plugin))
                    .ifPresent(executor::sendMessage);
            return;
        }
        plugin.getLocales().getLocale("trust_level_removed", toUntrust.getTrustIdentifier(plugin))
                .ifPresent(executor::sendMessage);
        plugin.getDatabase().updateClaimWorld(world);
    }

    @Nullable
    @Override
    public List<String> suggest(@NotNull CommandUser user, @NotNull String[] args) {
        return user instanceof OnlineUser online ? getGroupEntries(online) : null;
    }

    @Nullable
    public List<String> getGroupEntries(@NotNull OnlineUser user) {
        return plugin.getClaimWorld(user.getWorld())
                .flatMap(world -> world.getClaimAt(user.getPosition()).map(claim -> {
                    final List<String> names = Lists.newArrayList();
                    claim.getTrustedUsers().keySet().stream()
                            .map(uuid -> world.getUser(uuid).map(User::getName))
                            .forEach(optionalName -> optionalName.ifPresent(names::add));
                    claim.getTrustedGroups().keySet().stream()
                            .map(group -> plugin.getSettings().getUserGroups().getGroupSpecifierPrefix() + group)
                            .forEach(names::add);
                    return names;
                }))
                .orElse(null);
    }

}
