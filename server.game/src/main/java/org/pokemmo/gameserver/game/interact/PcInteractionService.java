package org.pokemmo.gameserver.game.interact;

import java.util.ArrayList;

import com.google.inject.Inject;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.gtl.GtlListingPage;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.game.player.InteractManager;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.game.script.LocalFormatStringScript;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBoxInfoPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailListPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGTLPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameMailPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInteractPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPcStatePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonContainerPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.gtl.GtlQueryService;
import org.pokemmo.gameserver.services.gtl.GtlSearchRequest;
import org.server.Session;

/** The confirmed scene PC menu and its PC, GTL and mailbox entry points. */
public final class PcInteractionService {
    private final GtlQueryService gtlQueryService;
    private final GameServerService gameServerService;

    @Inject
    public PcInteractionService(GtlQueryService gtlQueryService,
                                GameServerService gameServerService) {
        this.gtlQueryService = gtlQueryService;
        this.gameServerService = gameServerService;
    }

    public static boolean isPcTarget(PlayerEntity player) {
        if (player.getRegionIndexId() != 0
                || player.getMapHeaderIdOrGbaMapGroupId() != 5
                || player.getGbaMapId() != 4
                || player.getToward() != 1
                || player.getX() != 11) {
            return false;
        }
        // Preserve the confirmed login/movement coordinate representations.
        return (player.getY() == 2 && (player.getZ() == 0 || player.getZ() == 2))
                || (player.getY() == 1 && player.getZ() == 2);
    }

    void openMenu(Session session, CharacterManager manager) {
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        InteractManager interaction = manager.getInteractManager();
        interaction.addInteractTimes();
        interaction.setMailWidgetOpen(false);
        InteractScript menu = createMenu(player.getPlayerName());
        interaction.setLastInteractorEntityId(-1L);
        interaction.setCurrentInteractScript(menu);
        interaction.setCurrentScript(null);
        interaction.setInteractType(InteractType.PC_MENU);
        session.send(new SendInteractPacket(-1L, interaction.getInteractTimes(), menu));
    }

    private static InteractScript createMenu(String playerName) {
        ArrayList<LocalFormatStringScript> formats = new ArrayList<>(1);
        formats.add(new LocalFormatStringScript(
                1, 5, 0, 0L, 0, new ArrayList<>(0),
                playerName == null ? "" : playerName));
        return new InteractScript("Scene", GameInteractionType.MULTICHOICE,
                2350, 0, formats, (byte) 10, (byte) 3, (byte) 0);
    }

    public void openPc(Session session, CharacterManager manager) {
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        GameServerService.PcData pcData = gameServerService.getPcData(characterId);
        if (pcData == null) {
            throw new IllegalStateException("电脑宝可梦容器不可用");
        }
        session.send(
                new SendPcStatePacket(false),
                new SendPokemonContainerPacket(pcData.container(), pcData.pokemons()),
                new SendBoxInfoPacket(manager.getCharacterData().getPcBoxExpansionNumber()),
                new SendPcStatePacket(true));
    }

    public void openGtl(Session session) {
        GtlRequestState state = GtlRequestState.defaultMonsterPage();
        session.attr(GameProtocol.ATTRIBUTE_GTL_REQUEST_STATE).set(state);
        GtlListingPage page = gtlQueryService.search(GtlSearchRequest.fromState(state, null, 10));
        session.send(new SendGTLPacket(state.requestSequence(), state.listType(),
                state.pageIndex(), page.totalListings(), page.listings()));
    }

    public void openEmail(Session session, CharacterManager manager) {
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        var counts = gameServerService.getMailCounts(characterId);
        var page = gameServerService.getMailList(characterId, false, 0);
        manager.getInteractManager().setMailWidgetOpen(true);
        session.send(
                new SendGameMailPacket(counts.received(), counts.unread(), counts.sent()),
                new SendEmailListPacket((short) 0, false, page.entries()));
    }
}
