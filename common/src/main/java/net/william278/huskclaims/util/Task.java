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

package net.william278.huskclaims.util;

import net.william278.huskclaims.HuskClaims;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Cross-platform task scheduling implementation
 *
 * @since 1.0
 */
public interface Task extends Runnable {

    abstract class Base implements Task {

        protected final HuskClaims plugin;
        protected final Runnable runnable;
        protected boolean cancelled = false;

        protected Base(@NotNull HuskClaims plugin, @NotNull Runnable runnable) {
            this.plugin = plugin;
            this.runnable = runnable;
        }

        public void cancel() {
            cancelled = true;
        }

        @NotNull
        @Override
        public HuskClaims getPlugin() {
            return plugin;
        }

    }

    abstract class Async extends Base {

        protected Async(@NotNull HuskClaims plugin, @NotNull Runnable runnable) {
            super(plugin, runnable);
        }

    }

    abstract class Sync extends Base {

        protected long delayTicks;

        protected Sync(@NotNull HuskClaims plugin, @NotNull Runnable runnable, long delayTicks) {
            super(plugin, runnable);
            this.delayTicks = delayTicks;
        }

    }

    abstract class Repeating extends Base {

        protected final long repeatingTicks;

        protected Repeating(@NotNull HuskClaims plugin, @NotNull Runnable runnable, long repeatingTicks) {
            super(plugin, runnable);
            this.repeatingTicks = repeatingTicks;
        }

    }

    @SuppressWarnings("UnusedReturnValue")
    interface Supplier {

        @NotNull
        Task.Sync getSyncTask(@NotNull Runnable runnable, long delayTicks);

        @NotNull
        Task.Async getAsyncTask(@NotNull Runnable runnable);

        @NotNull
        Task.Repeating getRepeatingTask(@NotNull Runnable runnable, long repeatingTicks);

        @NotNull
        default Task.Sync runSyncDelayed(@NotNull Runnable runnable, long delayTicks) {
            final Task.Sync task = getSyncTask(runnable, delayTicks);
            task.run();
            return task;
        }

        @NotNull
        default Task.Sync runSync(@NotNull Runnable runnable) {
            return runSyncDelayed(runnable, 0);
        }

        @NotNull
        default Task.Async runAsync(@NotNull Runnable runnable) {
            final Task.Async task = getAsyncTask(runnable);
            task.run();
            return task;
        }

        default <T> CompletableFuture<T> supplyAsync(@NotNull java.util.function.Supplier<T> supplier) {
            final CompletableFuture<T> future = new CompletableFuture<>();
            runAsync(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            return future;
        }

        void cancelTasks();

        @NotNull
        HuskClaims getPlugin();

    }

    @NotNull
    HuskClaims getPlugin();

}
