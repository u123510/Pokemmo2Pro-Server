package org.pokemmo.gameserver.game.shop;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendChatMessagePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendItemShopPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShopControlPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.services.shop.ShopTransactionResult;
import org.pokemmo.gameserver.services.shop.ShopTransactions;
import org.server.Session;
import org.server.union.chat.ChatMessage;

/** Coordinates price snapshots, access, transactions and post-commit client synchronization. */
@Slf4j
public final class ShopService {
    private final ScriptManager scripts;
    private final ShopTransactions transactions;

    @Inject
    public ShopService(ScriptManager scripts, ShopTransactions transactions) {
        this.scripts = scripts;
        this.transactions = transactions;
    }

    /** A bound NPC never falls through to unrelated story scripts on shop failure. */
    public boolean tryOpen(Session session, CharacterManager manager, NpcEntity npc) {
        if (npc == null || manager == null) return false;
        ShopCatalog catalog = scripts.getShopCatalog();
        if (catalog == null) {
            if (npc.getShopId() == null || npc.getShopId().isBlank()) return false;
            message(session, "店铺目录尚未就绪");
            return true;
        }
        return catalog.withSnapshot(snapshot -> {
            synchronized (manager.getInteractManager()) {
                String shopId = snapshot.resolveShopId(currentMap(manager), npc);
                if (shopId == null) return false;
                ShopDefinition shop = snapshot.shops().get(shopId);
                if (shop == null) {
                    log.warn("NPC 绑定的店铺不存在: NPC编号={}, 店铺={}", npc.getEntityGameId(), shopId);
                    message(session, "该店铺配置不存在或未通过校验");
                    return true;
                }
                if (!session.isActive() || manager.getCharacterSession() != session
                        || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager
                        || !ShopAccess.allowed(manager, npc, InteractType.NONE)) {
                    log.debug("商店访问被拒绝: 店铺={}, NPC编号={}, 原因=角色状态或位置无效",
                            shop.shopId(), npc.getEntityGameId());
                    message(session, "当前状态或位置不能访问这家店铺");
                    return true;
                }
                if (shop.buyItems().isEmpty() && shop.sellItems().isEmpty()) {
                    message(session, "这家店铺暂未营业");
                    return true;
                }
                manager.getInteractManager().setMailWidgetOpen(false);
                if (!ShopSessions.supported(session)) {
                    if (shop.buyItems().isEmpty()) {
                        message(session, "该店铺仅提供回收服务，请使用支持商店扩展的客户端");
                        log.debug("旧客户端不支持纯回收店铺: 店铺={}", shop.shopId());
                    } else {
                        session.send(SendItemShopPacket.readOnly(shop));
                        log.debug("已发送旧客户端商店浏览响应: 店铺={}", shop.shopId());
                    }
                    return true;
                }
                long ownerId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
                ShopTransactionResult state = transactions.readState(ownerId);
                if (state.status() != ShopTransactionResult.SUCCESS) {
                    message(session, state.message());
                    return true;
                }
                if (!session.isActive() || manager.getCharacterSession() != session
                        || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager
                        || !ShopAccess.allowed(manager, npc, InteractType.NONE)) {
                    return true;
                }
                publishAssets(session, manager, state);
                ShopSession quote = new ShopSession(manager.getSnowflakeIdGenerator().nextId(),
                        snapshot.version(), shop, manager, npc);
                ShopSessions.open(session, quote);
                session.send(SendItemShopPacket.trading(shop, quote.quoteId));
                log.debug("已打开商店: 店铺={}, NPC编号={}, 报价编号={}, 配置版本={}",
                        shop.shopId(), npc.getEntityGameId(), quote.quoteId, snapshot.version());
                return true;
            }
        });
    }

    public void handle(Session session, ShopRequest request) {
        if (!ShopSessions.supported(session)) {
            log.warn("拒绝未协商协议的商店请求");
            message(session, "客户端尚未协商商店扩展协议");
            return;
        }
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        ShopCatalog catalog = scripts.getShopCatalog();
        if (!session.isActive() || manager == null || manager.getCharacterSession() != session || catalog == null
                || manager.getCharacterData() == null || manager.getCharacterData().getPlayerEntity() == null) {
            reply(session, request, ShopTransactionResult.REJECTED, "角色或店铺状态已失效");
            return;
        }
        catalog.withSnapshot(snapshot -> {
            synchronized (manager.getInteractManager()) {
                handleLocked(session, manager, snapshot, request);
            }
            return null;
        });
    }

    private void handleLocked(Session session, CharacterManager manager,
                              ShopCatalog.Snapshot snapshot, ShopRequest request) {
        ShopSession quote = ShopSessions.get(session);
        if (!session.isActive() || quote == null || quote.manager != manager || quote.quoteId != request.quoteId()) {
            reply(session, request, ShopTransactionResult.REJECTED, "报价已失效，请重新打开商店");
            return;
        }
        NpcEntity npc = currentNpc(manager, quote.npcId);
        if (quote.catalogVersion != snapshot.version() || !quote.unchangedPosition()
                || npc == null || !quote.definition.shopId().equals(snapshot.resolveShopId(currentMap(manager), npc))
                || !ShopAccess.allowed(manager, npc, InteractType.SHOP)) {
            reply(session, request, ShopTransactionResult.REJECTED, "商店价格、位置或状态已变化，请重新打开");
            ShopSessions.close(session, "商店报价已失效", true);
            return;
        }
        ShopSession.RequestState state = quote.classify(request);
        if (state == ShopSession.RequestState.DUPLICATE) {
            // Do not replay an old asset snapshot over subsequent inventory changes.
            reply(session, request, quote.lastReply().status(), quote.lastReply().message());
            return;
        }
        if (state == ShopSession.RequestState.INVALID) {
            reply(session, request, ShopTransactionResult.REJECTED, "请求序号无效，请重新打开商店");
            ShopSessions.close(session, "商店请求序号已失效", true);
            return;
        }
        ShopTransactionResult result = transactions.execute(quote.ownerId, quote.definition, request);
        quote.remember(request, result.status(), result.message());
        if (result.status() == ShopTransactionResult.SUCCESS) {
            publishAssets(session, manager, result);
        }
        reply(session, request, result.status(), result.message());
        log.debug("商店请求处理完成: 角色编号={}, 店铺={}, 请求序号={}, 操作={}, 数量={}, 结果={}",
                quote.ownerId, quote.definition.shopId(), request.requestId(),
                request.action() == ShopRequest.BUY ? "购买" : "出售", request.amount(), result.message());
        if (result.status() == ShopTransactionResult.UNCERTAIN) {
            ShopSessions.close(session, "交易结果待核对，请重新打开商店刷新资产", true);
        }
    }

    public ShopCatalog.ReloadResult reload() {
        ShopCatalog catalog = scripts.getShopCatalog();
        if (catalog == null) return new ShopCatalog.ReloadResult(false, 0, 0, "店铺目录尚未就绪");
        ShopCatalog.ReloadResult result = catalog.reload();
        if (result.success()) {
            int closed = catalog.withSnapshot(snapshot -> ShopSessions.closeOutdated(snapshot.version()));
            log.info("店铺重载完成，已关闭旧报价窗口: 数量={}", closed);
        }
        return result;
    }

    private static NpcEntity currentNpc(CharacterManager manager, long npcId) {
        MapData map = currentMap(manager);
        return map == null ? null : map.getNpcEntityByGameId(npcId);
    }

    private static MapData currentMap(CharacterManager manager) {
        MapData[] maps = manager.getCurrentMapDatas();
        return maps == null || maps.length == 0 ? null : maps[0];
    }

    private static void publishAssets(Session session, CharacterManager manager, ShopTransactionResult result) {
        manager.getCharacterData().setMoney(result.money());
        UpdateCharacterSelector update = new UpdateCharacterSelector.Builder()
                .setRefreshMoney(true).setCharacterData(manager.getCharacterData()).build();
        session.send(new SendInventoryPacket(result.inventory(), result.items()), new SendUpdatePlayerInfo(update));
    }

    private static void reply(Session session, ShopRequest request, int status, String message) {
        session.send(SendShopControlPacket.result(request, status, message));
    }

    private static void message(Session session, String message) {
        session.send(new SendChatMessagePacket(ChatMessage.gameNotification(message)));
    }
}
