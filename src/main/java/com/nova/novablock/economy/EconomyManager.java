package com.nova.novablock.economy;

import com.nova.novablock.NovaBlock;
import com.nova.novablock.island.Island;

public class EconomyManager {

    private final NovaBlock plugin;

    public EconomyManager(NovaBlock plugin) { this.plugin = plugin; }

    public void award(Island island, long amount) {
        if (island == null) return;
        island.data().addCoins(amount);
    }

    public boolean spend(Island island, long amount) {
        if (island == null) return false;
        if (island.data().getCoins() < amount) return false;
        island.data().setCoins(island.data().getCoins() - amount);
        return true;
    }

    public long balance(Island island) {
        return island == null ? 0 : island.data().getCoins();
    }
}
