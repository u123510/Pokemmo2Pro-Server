package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.gtl.GtlListType;
import org.pokemmo.gameserver.game.gtl.GtlListingPage;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGTLPacket;
import org.pokemmo.gameserver.services.gtl.GtlQueryService;
import org.pokemmo.gameserver.services.gtl.GtlSearchRequest;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Handles GTL request lifecycle; wire parsing lives in {@link GtlRequestDecoder}. */
@Slf4j
public class RequestGTLPacket extends IncomingPacket {
    private static final int PAGE_SIZE = 10;

    private final GtlRequestDecoder decoder = new GtlRequestDecoder();
    private GtlRequestState requestState;

    @Inject
    private GtlQueryService gtlQueryService;

    @Override
    public void decode(ByteBufEx buffer) {
        requestState = decoder.decode(buffer).toState();
    }

    @Override
    public void handle(Session session) {
        GtlRequestState state = requestState;
        if (state == null || state.listType() == null || state.filterType() == null
                || state.pageIndex() < 0) {
            log.warn("忽略无效交易行请求");
            return;
        }
        session.attr(GameProtocol.ATTRIBUTE_GTL_REQUEST_STATE).set(state);
        GtlListingPage resultPage = loadPage(session, gtlQueryService, state);
        if (resultPage == null) {
            return;
        }
        session.send(new SendGTLPacket(
                state.requestSequence(), state.listType(), state.pageIndex(),
                resultPage.totalListings(), resultPage.listings()));
    }

    /** Rebuilds the last page after a GTL mutation. */
    public static SendGTLPacket createCurrentPageRefresh(
            Session session, GtlQueryService gtlQueryService) {
        GtlRequestState state = session.attr(GameProtocol.ATTRIBUTE_GTL_REQUEST_STATE).get();
        if (state == null) {
            return null;
        }
        GtlListingPage resultPage = loadPage(session, gtlQueryService, state);
        return resultPage == null ? null : new SendGTLPacket(
                state.requestSequence(), state.listType(), state.pageIndex(),
                resultPage.totalListings(), resultPage.listings());
    }

    private static GtlListingPage loadPage(
            Session session, GtlQueryService gtlQueryService, GtlRequestState state) {
        Long sellerId = null;
        if (state.listType() == GtlListType.OWN_LISTINGS) {
            CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (manager == null || manager.getCharacterData() == null
                    || manager.getCharacterData().getPlayerEntity() == null) {
                log.warn("无法刷新个人交易行列表：角色上下文不存在");
                return null;
            }
            sellerId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        }
        return gtlQueryService.search(GtlSearchRequest.fromState(state, sellerId, PAGE_SIZE));
    }
}
