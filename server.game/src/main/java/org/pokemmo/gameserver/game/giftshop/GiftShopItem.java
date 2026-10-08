package org.pokemmo.gameserver.game.giftshop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 礼品商城商品条目模型，对应客户端 f.HV
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftShopItem {
    /** 商城条目唯一 ID */
    private int id;

    /** 目标分类：1 为物品/时装，3 为特殊/质子，5 为染料 */
    @Builder.Default
    private byte targetType = 1;

    /** 分类 ID (0..8)，对应 GiftShopCategory */
    private byte category;

    /** 物品图鉴/数据索引 ID (Item.bin/Item.jsonc 中的 itemIndexId) */
    private short itemIndexId;

    /** 奖励点购买价格 */
    private int price;

    /** 原始价格 (若大于 price 则客户端显示折扣与划线原价) */
    private int originalPrice;

    /** 每次购买数量 / 堆叠数量 (默认 1，大于 1 时显示 "xN") */
    @Builder.Default
    private int quantity = 1;

    /** 保留参数 1 (默认为 0) */
    @Builder.Default
    private int param1 = 0;

    /** 上架时间戳 (秒，若当前时间与其差值小于 7 天则客户端展示 NEW 标签) */
    @Builder.Default
    private int releaseTime = 0;

    /** 季节限定标记 (1 为季节限定，0 为普通) */
    @Builder.Default
    private byte seasonal = 0;

    /** 保留标记 2 (默认为 0) */
    @Builder.Default
    private byte flag2 = 0;

    /** 是否有限时可购条件 */
    @Builder.Default
    private boolean limitedTime = false;

    /** 限时起始月份 (1-12) */
    @Builder.Default
    private byte startMonth = 1;

    /** 限时起始日期 (1-31) */
    @Builder.Default
    private byte startDay = 1;

    /** 限时结束月份 (1-12) */
    @Builder.Default
    private byte endMonth = 12;

    /** 限时结束日期 (1-31) */
    @Builder.Default
    private byte endDay = 31;

    /** 精选推荐位序号 (>= 0 则出现在首页精选标签页，-1 为不推荐) */
    @Builder.Default
    private int featuredOrder = -1;
}
