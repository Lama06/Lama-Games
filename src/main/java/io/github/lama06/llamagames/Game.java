package io.github.lama06.llamagames;

import io.github.lama06.llamagames.util.EventCanceler;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class Game<G extends Game<G, C>, C extends GameConfig> implements Listener {
    protected LlamaGamesPlugin plugin;
    protected GameType<G, C> type;
    protected World world;
    protected C config;
    protected boolean running = false;
    protected final Set<UUID> players = new HashSet<>();
    private BukkitTask countdownTask = null;
    protected final EventCanceler canceler;

    public Game(LlamaGamesPlugin plugin, World world, C config, GameType<G, C> type) {
        this.plugin = plugin;
        this.world = world;
        this.config = config;
        this.type = type;

        canceler = new EventCanceler(plugin, this);
        canceler.disallowAll();
    }

    public final boolean startGame() {
        if (running || !canStart() || !isConfigComplete()) {
            return false;
        }

        for (Player player : world.getPlayers()) {
            player.teleport(config.spawnPoint.asLocation(world));
        }

        running = true;
        handleGameStarted();

        return true;
    }

    public final boolean endGame(GameEndReason reason) {
        if (!running) {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
                return true;
            }

            return false;
        }

        running = false;
        handleGameEnded(reason);

        players.clear();
        addAllPlayers();

        if (reason.isShouldAttemptToStartNextGame()) {
            tryToStartAfterCountdown();
        }

        return true;
    }

    public final void loadGame() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (!isConfigComplete()) {
            plugin.getLogger().warning("The configuration for the following game is not complete: %s".formatted(world.getName()));
        }

        handleGameLoaded();

        addAllPlayers();

        tryToStartAfterCountdown();
    }

    public final void unloadGame() {
        endGame(GameEndReason.UNLOAD);

        HandlerList.unregisterAll(this);

        handleGameUnloaded();
    }

    public void handleGameLoaded() { }

    public void handleGameUnloaded() { }

    public abstract void handleGameStarted();

    public abstract void handleGameEnded(GameEndReason reason);

    public abstract boolean canStart();

    public boolean isConfigComplete() {
        return config.spawnPoint != null;
    }

    public void handlePlayerLeft(Player player) { }

    private void handlePlayerJoined(Player player) {
        if (running) {
            player.setGameMode(GameMode.SPECTATOR);
            player.showTitle(Title.title(
                    Component.text("You are now spectating"),
                    Component.empty(),
                    Title.Times.of(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            players.add(player.getUniqueId());

            tryToStartAfterCountdown();
        }

        player.teleport(config.spawnPoint == null ? world.getSpawnLocation() : config.spawnPoint.asLocation(world));
    }

    @EventHandler
    public void handlePlayerChangeWorldEvent(PlayerChangedWorldEvent event) {
        if (event.getFrom().equals(world)) {
            players.remove(event.getPlayer().getUniqueId());

            handlePlayerLeft(event.getPlayer());

            if (world.getPlayers().size() == 0) {
                endGame(GameEndReason.MISSING_REQUIREMENTS_TO_CONTINUE);
            }
        } else if (event.getPlayer().getWorld().equals(world)) {
            handlePlayerJoined(event.getPlayer());
        }
    }

    @EventHandler
    public void handlePlayerJoinEvent(PlayerJoinEvent event) {
        if (event.getPlayer().getWorld().equals(world)) {
            handlePlayerJoined(event.getPlayer());
        }
    }

    @EventHandler
    public void handlePlayerQuitEvent(PlayerQuitEvent event) {
        if (players.contains(event.getPlayer().getUniqueId())) {
            players.remove(event.getPlayer().getUniqueId());
            handlePlayerLeft(event.getPlayer());
        }
    }

    private void addAllPlayers() {
        players.addAll(world.getPlayers().stream().map(Player::getUniqueId).collect(Collectors.toList()));
    }

    private void tryToStartAfterCountdown() {
        if (!canStart() || !isConfigComplete() || countdownTask != null) return;

        startAfterCountdown(10);
    }

    private void startAfterCountdown(int countdown) {
        if (countdown == 0) {
            countdownTask = null;
            if (!startGame()) {
                getBroadcastAudience().sendMessage(Component.text("Start failed").color(NamedTextColor.RED));
            }
        } else {
            for (Player player : world.getPlayers()) {
                player.showTitle(Title.title(
                        Component.text(countdown).color(NamedTextColor.GREEN),
                        Component.empty(),
                        Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                ));
            }
            countdownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> startAfterCountdown(countdown - 1), 20);
        }
    }

    public Audience getBroadcastAudience() {
        return Audience.audience(world.getPlayers());
    }

    public LlamaGamesPlugin getPlugin() {
        return plugin;
    }

    public GameType<G, C> getType() {
        return type;
    }

    public World getWorld() {
        return world;
    }

    public C getConfig() {
        return config;
    }

    public boolean isRunning() {
        return running;
    }

    public Set<Player> getPlayers() {
        Set<Player> result = new HashSet<>(players.size());

        Iterator<UUID> iterator = players.iterator();
        while (iterator.hasNext()) {
            Player player = Bukkit.getPlayer(iterator.next());
            if (player == null || !player.getWorld().equals(world)) {
                iterator.remove();
                continue;
            }
            result.add(player);
        }

        return result;
    }

    public enum GameEndReason {
        UNLOAD(false),
        COMMAND(false),
        MISSING_REQUIREMENTS_TO_CONTINUE(false),
        ENDED(true);

        private final boolean shouldAttemptToStartNextGame;

        GameEndReason(boolean shouldAttemptToStartNextGame) {
            this.shouldAttemptToStartNextGame = shouldAttemptToStartNextGame;
        }

        public boolean isShouldAttemptToStartNextGame() {
            return shouldAttemptToStartNextGame;
        }
    }
}
