package org.pokemmo.gameserver.game.trade;

import org.pokemmo.gameserver.game.character.CharacterManager;

import java.util.List;

public final class TradeSession {
    public static final int MAX_POKEMON = 6;
    public static final int MAX_ITEMS = 6;

    public static final class Offer {
        private int money;
        private final TradePokemonOffer[] pokemons = new TradePokemonOffer[MAX_POKEMON];
        private final TradeItemOffer[] items = new TradeItemOffer[MAX_ITEMS];
        private boolean locked;
        private boolean confirmed;

        public int getMoney() {
            return money;
        }

        public List<Long> getPokemonIds() {
            return java.util.Arrays.stream(pokemons)
                    .filter(pokemon -> pokemon != null)
                    .map(TradePokemonOffer::pokemonId)
                    .toList();
        }

        public TradePokemonOffer getPokemon(int slot) {
            if (slot < 0 || slot >= MAX_POKEMON) {
                throw new IllegalArgumentException("交易宝可梦槽位越界: " + slot);
            }
            return pokemons[slot];
        }

        public List<TradePokemonOffer> getPokemonOffers() {
            return java.util.Arrays.stream(pokemons)
                    .filter(pokemon -> pokemon != null)
                    .toList();
        }

        TradePokemonOffer[] copyPokemonSlots() {
            return java.util.Arrays.copyOf(pokemons, pokemons.length);
        }

        void replacePokemonSlots(TradePokemonOffer[] replacement) {
            if (replacement == null || replacement.length != MAX_POKEMON) {
                throw new IllegalArgumentException("交易宝可梦槽位数量非法");
            }
            System.arraycopy(replacement, 0, pokemons, 0, pokemons.length);
        }

        public int findPokemonSlot(long pokemonId) {
            for (int slot = 0; slot < MAX_POKEMON; slot++) {
                if (pokemons[slot] != null && pokemons[slot].pokemonId() == pokemonId) {
                    return slot;
                }
            }
            return -1;
        }

        public TradeItemOffer getItem(int slot) {
            if (slot < 0 || slot >= MAX_ITEMS) {
                throw new IllegalArgumentException("交易道具槽位越界: " + slot);
            }
            return items[slot];
        }

        public boolean containsItem(long itemId, int exceptSlot) {
            for (int slot = 0; slot < MAX_ITEMS; slot++) {
                if (slot != exceptSlot && items[slot] != null && items[slot].itemId() == itemId) {
                    return true;
                }
            }
            return false;
        }

        public List<TradeItemOffer> getItems() {
            return java.util.Arrays.stream(items)
                    .filter(item -> item != null && !item.isEmpty())
                    .toList();
        }

        public boolean isLocked() {
            return locked;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        void setMoney(int money) {
            this.money = money;
        }

        void setItem(int slot, TradeItemOffer item) {
            if (slot < 0 || slot >= MAX_ITEMS) {
                throw new IllegalArgumentException("交易道具槽位越界: " + slot);
            }
            items[slot] = item;
        }

        void setPokemon(int slot, TradePokemonOffer pokemon) {
            if (slot < 0 || slot >= MAX_POKEMON) {
                throw new IllegalArgumentException("交易宝可梦槽位越界: " + slot);
            }
            pokemons[slot] = pokemon;
        }

        void removePokemon(int slot) {
            if (slot < 0 || slot >= MAX_POKEMON) {
                throw new IllegalArgumentException("交易宝可梦槽位越界: " + slot);
            }
            pokemons[slot] = null;
        }

        void setLocked(boolean locked) {
            this.locked = locked;
        }

        void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }

        void clearConfirmation() {
            locked = false;
            confirmed = false;
        }
    }

    private final CharacterManager requester;
    private final CharacterManager target;
    private final Offer[] offers = {new Offer(), new Offer()};
    private TradeState state = TradeState.OPEN;
    private boolean closed;

    public TradeSession(CharacterManager requester, CharacterManager target) {
        this.requester = requester;
        this.target = target;
    }

    public CharacterManager getRequester() {
        return requester;
    }

    public CharacterManager getTarget() {
        return target;
    }

    public synchronized TradeState getState() {
        return state;
    }

    public synchronized void setState(TradeState state) {
        this.state = state;
    }

    public CharacterManager other(CharacterManager manager) {
        if (manager == requester) {
            return target;
        }
        if (manager == target) {
            return requester;
        }
        return null;
    }

    public int sideOf(CharacterManager manager) {
        if (manager == requester) {
            return 0;
        }
        if (manager == target) {
            return 1;
        }
        return -1;
    }

    public boolean contains(CharacterManager manager) {
        return manager == requester || manager == target;
    }

    public synchronized Offer offer(CharacterManager manager) {
        int side = sideOf(manager);
        if (side < 0) {
            throw new IllegalArgumentException("角色不属于当前交易会话");
        }
        return offers[side];
    }

    public synchronized Offer offer(int side) {
        return offers[side];
    }

    public synchronized void resetConfirmations() {
        offers[0].clearConfirmation();
        offers[1].clearConfirmation();
    }

    public synchronized boolean bothConfirmed() {
        return offers[0].isConfirmed() && offers[1].isConfirmed();
    }

    public synchronized boolean markClosed() {
        if (closed) {
            return false;
        }
        closed = true;
        return true;
    }
}
