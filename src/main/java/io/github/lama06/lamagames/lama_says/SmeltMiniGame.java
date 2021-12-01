package io.github.lama06.lamagames.lama_says;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public class SmeltMiniGame extends CompeteMiniGame<SmeltMiniGame> {
    private static final List<Material> INGREDIENTS = List.of(
            Material.RAW_COPPER,
            Material.RAW_IRON,
            Material.RAW_GOLD,
            Material.BEEF
    );

    private static final List<Material> FURNACE_TYPES = List.of(
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER
    );

    private final Material ingredient;

    public SmeltMiniGame(LamaSaysGame game, Consumer<SmeltMiniGame> callback) {
        super(game, callback);
        ingredient = INGREDIENTS.get(game.getRandom().nextInt(INGREDIENTS.size()));
    }


    @Override
    public Component getTitle() {
        return Component.text("Smelt ").append(Component.translatable(ingredient));
    }

    @Override
    public void handleGameStarted() {
        for (int i = 0; i < FURNACE_TYPES.size(); i++) {
            Material furnaceType = FURNACE_TYPES.get(i);
            game.getWorld().setBlockData(game.getConfig().spawnPoint.asLocation().add(0, 1 + i, 0), furnaceType.createBlockData());
        }

        for (Player player : game.getPlayers()) {
            player.getInventory().setItem(0, new ItemStack(Material.LAVA_BUCKET));

            for (int slot = 1, ingredient = 0; slot <= 8 && ingredient < INGREDIENTS.size(); slot++, ingredient++) {
                player.getInventory().setItem(slot, new ItemStack(INGREDIENTS.get(ingredient)));
            }
        }
    }

    @Override
    public void handleGameEnded() {
        for (int i = 0; i < FURNACE_TYPES.size(); i++) {
            game.getWorld().setBlockData(game.getConfig().spawnPoint.asLocation().add(0, 1 + i, 0), Material.AIR.createBlockData());
        }
    }

    @EventHandler
    public void handleFurnaceExtractEvent(FurnaceExtractEvent event) {
        if (!game.getPlayers().contains(event.getPlayer())) {
            return;
        }

        if (event.getItemType() == ingredient) {
            addSuccessfulPlayer(event.getPlayer());
        } else {
            addFailedPlayer(event.getPlayer());
        }
    }
}
