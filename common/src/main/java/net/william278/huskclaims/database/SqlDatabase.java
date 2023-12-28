package net.william278.huskclaims.database;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.gson.JsonSyntaxException;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.group.UserGroup;
import net.william278.huskclaims.position.ServerWorld;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.user.Preferences;
import net.william278.huskclaims.user.SavedUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public abstract class SqlDatabase extends Database {

    public SqlDatabase(@NotNull HuskClaims plugin) {
        super(plugin);
    }

    public abstract Connection getConnection() throws SQLException;

    @Override
    protected void executeScript(@NotNull Connection connection, @NotNull String name) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String schemaStatement : getScript(name)) {
                statement.execute(schemaStatement);
            }
        }
    }

    @Override
    public boolean isCreated() {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`
                FROM `%user_data%`
                LIMIT 1;"""))) {
            statement.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public int getSchemaVersion() {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `schema_version`
                FROM `%meta_data%`
                LIMIT 1;"""))) {
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("schema_version");
            }
        } catch (SQLException e) {
            plugin.log(Level.WARNING, "The database schema version could not be fetched; migrations will be carried out.");
        }
        return -1;
    }

    @Override
    public void setSchemaVersion(int version) {
        if (getSchemaVersion() == -1) {
            try (PreparedStatement insertStatement = getConnection().prepareStatement(format("""
                    INSERT INTO `%meta_data%` (`schema_version`)
                    VALUES (?);"""))) {
                insertStatement.setInt(1, version);
                insertStatement.executeUpdate();
            } catch (SQLException e) {
                plugin.log(Level.SEVERE, "Failed to insert schema version in table", e);
            }
            return;
        }

        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                UPDATE `%meta_data%`
                SET `schema_version` = ?;"""))) {
            statement.setInt(1, version);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to update schema version in table", e);
        }
    }

    @Override
    public Optional<SavedUser> getUser(@NotNull UUID uuid) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `username`, `last_login`, `claim_blocks`, `hours_played`, `preferences`
                FROM `%user_data%`
                WHERE uuid = ?"""))) {
            statement.setString(1, uuid.toString());
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                final String name = resultSet.getString("username");
                final String preferences = new String(resultSet.getBytes("preferences"), StandardCharsets.UTF_8);
                return Optional.of(new SavedUser(
                        User.of(uuid, name),
                        plugin.getPreferencesFromJson(preferences),
                        resultSet.getTimestamp("last_login").toLocalDateTime()
                                .atOffset(OffsetDateTime.now().getOffset()),
                        resultSet.getLong("claim_blocks"),
                        resultSet.getInt("hours_played")
                ));
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch user data from table by UUID", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<SavedUser> getUser(@NotNull String username) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `username`, `last_login`, `claim_blocks`, `hours_played`, `preferences`
                FROM `%user_data%`
                WHERE `username` = ?"""))) {
            statement.setString(1, username);
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                final String name = resultSet.getString("username");
                final String preferences = new String(resultSet.getBytes("preferences"), StandardCharsets.UTF_8);
                return Optional.of(new SavedUser(
                        User.of(uuid, name),
                        plugin.getPreferencesFromJson(preferences),
                        resultSet.getTimestamp("last_login").toLocalDateTime()
                                .atOffset(OffsetDateTime.now().getOffset()),
                        resultSet.getLong("claim_blocks"),
                        resultSet.getInt("hours_played")
                ));
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch user data from table by username", e);
        }
        return Optional.empty();
    }

    @Override
    public List<SavedUser> getInactiveUsers(long daysInactive) {
        final List<SavedUser> inactiveUsers = Lists.newArrayList();
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `username`, `last_login`, `preferences`, `claim_blocks`, `hours_played`
                FROM `%user_data%`
                WHERE datetime(`last_login` / 1000, 'unixepoch') < datetime('now', ?);"""))) {
            statement.setString(1, String.format("-%d days", daysInactive));
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                final String name = resultSet.getString("username");
                final String preferences = new String(resultSet.getBytes("preferences"), StandardCharsets.UTF_8);
                inactiveUsers.add(new SavedUser(
                        User.of(uuid, name),
                        plugin.getPreferencesFromJson(preferences),
                        resultSet.getTimestamp("last_login").toLocalDateTime()
                                .atOffset(OffsetDateTime.now().getOffset()),
                        resultSet.getLong("claim_blocks"),
                        resultSet.getInt("hours_played")
                ));
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch list of inactive users", e);
            inactiveUsers.clear(); // Clear for safety to prevent any accidental data being returned
        }
        return inactiveUsers;
    }

    @Override
    public void createUser(@NotNull User user, long claimBlocks, @NotNull Preferences preferences) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                INSERT INTO `%user_data%` (`uuid`, `username`, `last_login`, `claim_blocks`, `preferences`)
                VALUES (?, ?, ?, ?, ?)"""))) {
            statement.setString(1, user.getUuid().toString());
            statement.setString(2, user.getName());
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(4, claimBlocks);
            statement.setBytes(5, plugin.getGson().toJson(preferences).getBytes(StandardCharsets.UTF_8));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create user in table", e);
        }
    }

    @Override
    public void updateUser(@NotNull SavedUser user) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                UPDATE `%user_data%`
                SET `claim_blocks` = ?, `hours_played` = ?, `preferences` = ?
                WHERE `uuid` = ?"""))) {
            statement.setLong(1, user.getClaimBlocks());
            statement.setInt(2, user.getHoursPlayed());
            statement.setBytes(3, plugin.getGson().toJson(user.getPreferences())
                    .getBytes(StandardCharsets.UTF_8));
            statement.setString(4, user.getUser().getUuid().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to update Saved User data in table", e);
        }
    }

    @NotNull
    @Override
    public ConcurrentLinkedQueue<UserGroup> getUserGroups(@NotNull UUID uuid) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `name`, `members`
                FROM `%user_group_data%`
                WHERE `uuid` = ?"""))) {
            statement.setString(1, uuid.toString());
            final ResultSet resultSet = statement.executeQuery();
            final ConcurrentLinkedQueue<UserGroup> userGroups = Queues.newConcurrentLinkedQueue();
            while (resultSet.next()) {
                userGroups.add(new UserGroup(
                        uuid,
                        resultSet.getString("name"),
                        plugin.getUserListFromJson(new String(
                                resultSet.getBytes("members"), StandardCharsets.UTF_8
                        ))
                ));
            }
            return userGroups;
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch user groups from table", e);
        }
        return Queues.newConcurrentLinkedQueue();
    }

    @NotNull
    @Override
    public ConcurrentLinkedQueue<UserGroup> getAllUserGroups() {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `name`, `members`
                FROM `%user_group_data%`"""))) {
            final ResultSet resultSet = statement.executeQuery();
            final ConcurrentLinkedQueue<UserGroup> userGroups = Queues.newConcurrentLinkedQueue();
            while (resultSet.next()) {
                userGroups.add(new UserGroup(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("name"),
                        plugin.getUserListFromJson(new String(
                                resultSet.getBytes("members"), StandardCharsets.UTF_8
                        ))
                ));
            }
            return userGroups;
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch user groups from table", e);
        }
        return Queues.newConcurrentLinkedQueue();
    }

    @Override
    public void addUserGroup(@NotNull UserGroup group) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                INSERT INTO `%user_group_data%` (`uuid`, `name`, `members`)
                VALUES (?, ?, ?)"""))) {
            statement.setString(1, group.groupOwner().toString());
            statement.setString(2, group.name());
            statement.setBytes(3, plugin.getGson().toJson(group.members()).getBytes(StandardCharsets.UTF_8));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create user group in table", e);
        }
    }

    @Override
    public void updateUserGroup(@NotNull UUID owner, @NotNull String name, @NotNull UserGroup newGroup) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                UPDATE `%user_group_data%`
                SET `name` = ?, `members` = ?
                WHERE `uuid` = ? AND `name` = ?"""))) {
            statement.setString(1, newGroup.name());
            statement.setBytes(2, plugin.getGson().toJson(newGroup.members()).getBytes(StandardCharsets.UTF_8));
            statement.setString(3, owner.toString());
            statement.setString(4, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to update user group in table", e);
        }
    }

    @Override
    public void deleteUserGroup(@NotNull UserGroup group) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                DELETE FROM `%user_group_data%`
                WHERE `uuid` = ? AND `name` = ?"""))) {
            statement.setString(1, group.groupOwner().toString());
            statement.setString(2, group.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to remove user group from table", e);
        }
    }

    @NotNull
    @Override
    public Map<World, ClaimWorld> getClaimWorlds(@NotNull String server) throws IllegalStateException {
        final Map<World, ClaimWorld> worlds = new HashMap<>();
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `id`, `world_uuid`, `world_name`, `world_environment`, `data`
                FROM `%claim_data%`
                WHERE `server_name` = ?"""))) {
            statement.setString(1, server);
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                final World world = World.of(
                        resultSet.getString("world_name"),
                        UUID.fromString(resultSet.getString("world_uuid"))
                );
                final ClaimWorld claimWorld = plugin.getClaimWorldFromJson(
                        new String(resultSet.getBytes("data"), StandardCharsets.UTF_8)
                );
                claimWorld.updateId(resultSet.getInt("id"));
                if (!plugin.getSettings().getClaims().isWorldUnclaimable(world)) {
                    worlds.put(world, claimWorld);
                }
            }
        } catch (SQLException | JsonSyntaxException e) {
            throw new IllegalStateException(String.format("Failed to fetch claim world map for %s", server), e);
        }
        return worlds;
    }

    @NotNull
    @Override
    public Map<ServerWorld, ClaimWorld> getAllClaimWorlds() throws IllegalStateException {
        final Map<ServerWorld, ClaimWorld> worlds = new HashMap<>();
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `id`, `server_name`, `world_uuid`, `world_name`, `world_environment`, `data`
                FROM `%claim_data%`"""))) {
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                final World world = World.of(
                        resultSet.getString("world_name"),
                        UUID.fromString(resultSet.getString("world_uuid"))
                );
                final ClaimWorld claimWorld = plugin.getClaimWorldFromJson(
                        new String(resultSet.getBytes("data"), StandardCharsets.UTF_8)
                );
                claimWorld.updateId(resultSet.getInt("id"));
                worlds.put(new ServerWorld(resultSet.getString("server_name"), world), claimWorld);
            }
        } catch (SQLException | JsonSyntaxException e) {
            throw new IllegalStateException("Failed to fetch map of all claim worlds", e);
        }
        return worlds;
    }


    @Override
    @NotNull
    public ClaimWorld createClaimWorld(@NotNull World world) {
        final ClaimWorld claimWorld = ClaimWorld.create(plugin);
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                INSERT INTO `%claim_data%` (`world_uuid`, `world_name`, `world_environment`, `server_name`, `data`)
                VALUES (?, ?, ?, ?, ?)"""))) {
            statement.setString(1, world.getUuid().toString());
            statement.setString(2, world.getName());
            statement.setString(3, world.getEnvironment());
            statement.setString(4, plugin.getServerName());
            statement.setBytes(5, plugin.getGson().toJson(claimWorld).getBytes(StandardCharsets.UTF_8));
            claimWorld.updateId(statement.executeUpdate());
        } catch (SQLException | JsonSyntaxException e) {
            plugin.log(Level.SEVERE, "Failed to create claim world in table", e);
        }
        return claimWorld;
    }

    @Override
    public void updateClaimWorld(@NotNull ClaimWorld claimWorld) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                UPDATE `%claim_data%`
                SET `data` = ?
                WHERE `id` = ?"""))) {
            statement.setBytes(1, plugin.getGson().toJson(claimWorld).getBytes(StandardCharsets.UTF_8));
            statement.setInt(2, claimWorld.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to update claim world in table", e);
        }
    }

}
