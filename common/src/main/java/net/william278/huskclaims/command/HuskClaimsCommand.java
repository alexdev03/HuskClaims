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
import net.william278.huskclaims.user.CommandUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HuskClaimsCommand extends Command {

    protected HuskClaimsCommand(@NotNull HuskClaims plugin) {
        super(List.of("huskclaims"), "[test|test|test]", plugin);
    }

    @Override
    public void execute(@NotNull CommandUser executor, @NotNull String[] args) {

    }

}
