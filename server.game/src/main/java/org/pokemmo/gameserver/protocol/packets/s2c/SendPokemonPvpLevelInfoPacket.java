package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.util.HashMap;

public class SendPokemonPvpLevelInfoPacket extends OutgoingPacket {
    private byte[] data = {(byte)0xB7,(byte)0x00,(byte)0x95,(byte)0x02,(byte)0x02,(byte)0x92,(byte)0x02,(byte)0x01,(byte)0x91,(byte)0x02,
            (byte)0x02,(byte)0x90,(byte)0x02,(byte)0x00,(byte)0x8F,(byte)0x02,(byte)0x00,(byte)0x8C,(byte)0x02,(byte)0x00,
            (byte)0x89,(byte)0x02,(byte)0x00,(byte)0x88,(byte)0x02,(byte)0x00,(byte)0x87,(byte)0x02,(byte)0x00,(byte)0x86,
            (byte)0x02,(byte)0x00,(byte)0x85,(byte)0x02,(byte)0x00,(byte)0x84,(byte)0x02,(byte)0x00,(byte)0x83,(byte)0x02,
            (byte)0x00,(byte)0x82,(byte)0x02,(byte)0x00,(byte)0x81,(byte)0x02,(byte)0x00,(byte)0x80,(byte)0x02,(byte)0x00,
            (byte)0x7F,(byte)0x02,(byte)0x00,(byte)0x7E,(byte)0x02,(byte)0x00,(byte)0x7D,(byte)0x02,(byte)0x01,(byte)0x7B,
            (byte)0x02,(byte)0x01,(byte)0x78,(byte)0x02,(byte)0x03,(byte)0x76,(byte)0x02,(byte)0x02,(byte)0x74,(byte)0x02,
            (byte)0x03,(byte)0x71,(byte)0x02,(byte)0x02,(byte)0x6D,(byte)0x02,(byte)0x03,(byte)0x6C,(byte)0x02,(byte)0x01,
            (byte)0x64,(byte)0x02,(byte)0x02,(byte)0x61,(byte)0x02,(byte)0x01,(byte)0x5C,(byte)0x02,(byte)0x03,(byte)0x56,
            (byte)0x02,(byte)0x01,(byte)0x51,(byte)0x02,(byte)0x01,(byte)0x4F,(byte)0x02,(byte)0x01,(byte)0x43,(byte)0x02,
            (byte)0x01,(byte)0x3D,(byte)0x02,(byte)0x02,(byte)0x3B,(byte)0x02,(byte)0x03,(byte)0x33,(byte)0x02,(byte)0x02,
            (byte)0x31,(byte)0x02,(byte)0x02,(byte)0x2B,(byte)0x02,(byte)0x02,(byte)0x29,(byte)0x02,(byte)0x02,(byte)0x21,
            (byte)0x02,(byte)0x02,(byte)0x19,(byte)0x02,(byte)0x02,(byte)0x16,(byte)0x02,(byte)0x01,(byte)0x12,(byte)0x02,
            (byte)0x01,(byte)0x0E,(byte)0x02,(byte)0x02,(byte)0xF1,(byte)0x01,(byte)0x01,(byte)0xEE,(byte)0x01,(byte)0x00,
            (byte)0xED,(byte)0x01,(byte)0x00,(byte)0xEC,(byte)0x01,(byte)0x02,(byte)0xEB,(byte)0x01,(byte)0x00,(byte)0xEA,
            (byte)0x01,(byte)0x00,(byte)0xE9,(byte)0x01,(byte)0x00,(byte)0xE8,(byte)0x01,(byte)0x00,(byte)0xE7,(byte)0x01,
            (byte)0x00,(byte)0xE6,(byte)0x01,(byte)0x00,(byte)0xE5,(byte)0x01,(byte)0x01,(byte)0xE4,(byte)0x01,(byte)0x00,
            (byte)0xE3,(byte)0x01,(byte)0x00,(byte)0xE2,(byte)0x01,(byte)0x00,(byte)0xE1,(byte)0x01,(byte)0x00,(byte)0xE0,
            (byte)0x01,(byte)0x00,(byte)0xDF,(byte)0x01,(byte)0x03,(byte)0xDE,(byte)0x01,(byte)0x03,(byte)0xDB,(byte)0x01,
            (byte)0x03,(byte)0xDA,(byte)0x01,(byte)0x02,(byte)0xD9,(byte)0x01,(byte)0x01,(byte)0xD8,(byte)0x01,(byte)0x01,
            (byte)0xD5,(byte)0x01,(byte)0x02,(byte)0xD4,(byte)0x01,(byte)0x01,(byte)0xD1,(byte)0x01,(byte)0x02,(byte)0xD0,
            (byte)0x01,(byte)0x02,(byte)0xCE,(byte)0x01,(byte)0x01,(byte)0xCD,(byte)0x01,(byte)0x01,(byte)0xC6,(byte)0x01,
            (byte)0x03,(byte)0xC4,(byte)0x01,(byte)0x03,(byte)0xC2,(byte)0x01,(byte)0x02,(byte)0xC0,(byte)0x01,(byte)0x01,
            (byte)0xBD,(byte)0x01,(byte)0x01,(byte)0xBA,(byte)0x01,(byte)0x03,(byte)0xB5,(byte)0x01,(byte)0x01,(byte)0xB4,
            (byte)0x01,(byte)0x03,(byte)0xB3,(byte)0x01,(byte)0x03,(byte)0xAE,(byte)0x01,(byte)0x03,(byte)0xAD,(byte)0x01,
            (byte)0x02,(byte)0xA8,(byte)0x01,(byte)0x02,(byte)0xA7,(byte)0x01,(byte)0x02,(byte)0x97,(byte)0x01,(byte)0x02,
            (byte)0x8E,(byte)0x01,(byte)0x01,(byte)0x8B,(byte)0x01,(byte)0x02,(byte)0x88,(byte)0x01,(byte)0x01,(byte)0x85,
            (byte)0x01,(byte)0x03,(byte)0x82,(byte)0x01,(byte)0x00,(byte)0x81,(byte)0x01,(byte)0x01,(byte)0x80,(byte)0x01,
            (byte)0x00,(byte)0x7F,(byte)0x01,(byte)0x00,(byte)0x7E,(byte)0x01,(byte)0x00,(byte)0x7D,(byte)0x01,(byte)0x00,
            (byte)0x7C,(byte)0x01,(byte)0x00,(byte)0x7B,(byte)0x01,(byte)0x00,(byte)0x7A,(byte)0x01,(byte)0x00,(byte)0x79,
            (byte)0x01,(byte)0x00,(byte)0x78,(byte)0x01,(byte)0x01,(byte)0x75,(byte)0x01,(byte)0x02,(byte)0x64,(byte)0x01,
            (byte)0x03,(byte)0x5E,(byte)0x01,(byte)0x03,(byte)0x56,(byte)0x01,(byte)0x01,(byte)0x4E,(byte)0x01,(byte)0x03,
            (byte)0x4A,(byte)0x01,(byte)0x02,(byte)0x44,(byte)0x01,(byte)0x02,(byte)0x3F,(byte)0x01,(byte)0x03,(byte)0x34,
            (byte)0x01,(byte)0x02,(byte)0x2E,(byte)0x01,(byte)0x02,(byte)0x29,(byte)0x01,(byte)0x03,(byte)0x23,(byte)0x01,
            (byte)0x03,(byte)0x1E,(byte)0x01,(byte)0x01,(byte)0x17,(byte)0x01,(byte)0x01,(byte)0x10,(byte)0x01,(byte)0x01,
            (byte)0x04,(byte)0x01,(byte)0x03,(byte)0x01,(byte)0x01,(byte)0x03,(byte)0xFE,(byte)0x00,(byte)0x03,(byte)0xFB,
            (byte)0x00,(byte)0x00,(byte)0xFA,(byte)0x00,(byte)0x00,(byte)0xF9,(byte)0x00,(byte)0x00,(byte)0xF8,(byte)0x00,
            (byte)0x01,(byte)0xF5,(byte)0x00,(byte)0x01,(byte)0xF4,(byte)0x00,(byte)0x02,(byte)0xF3,(byte)0x00,(byte)0x01,
            (byte)0xF2,(byte)0x00,(byte)0x02,(byte)0xED,(byte)0x00,(byte)0x03,(byte)0xE9,(byte)0x00,(byte)0x02,(byte)0xE8,
            (byte)0x00,(byte)0x02,(byte)0xE6,(byte)0x00,(byte)0x01,(byte)0xE3,(byte)0x00,(byte)0x01,(byte)0xE2,(byte)0x00,
            (byte)0x03,(byte)0xDD,(byte)0x00,(byte)0x03,(byte)0xD6,(byte)0x00,(byte)0x02,(byte)0xD4,(byte)0x00,(byte)0x01,
            (byte)0xD3,(byte)0x00,(byte)0x03,(byte)0xD0,(byte)0x00,(byte)0x03,(byte)0xCF,(byte)0x00,(byte)0x02,(byte)0xCD,
            (byte)0x00,(byte)0x02,(byte)0xC5,(byte)0x00,(byte)0x02,(byte)0xC4,(byte)0x00,(byte)0x01,(byte)0xC3,(byte)0x00,
            (byte)0x02,(byte)0xBA,(byte)0x00,(byte)0x02,(byte)0xB8,(byte)0x00,(byte)0x03,(byte)0xB2,(byte)0x00,(byte)0x02,
            (byte)0xAB,(byte)0x00,(byte)0x03,(byte)0xA9,(byte)0x00,(byte)0x02,(byte)0xA0,(byte)0x00,(byte)0x02,(byte)0x97,
            (byte)0x00,(byte)0x00,(byte)0x96,(byte)0x00,(byte)0x00,(byte)0x95,(byte)0x00,(byte)0x01,(byte)0x92,(byte)0x00,
            (byte)0x03,(byte)0x91,(byte)0x00,(byte)0x01,(byte)0x8F,(byte)0x00,(byte)0x02,(byte)0x8D,(byte)0x00,(byte)0x02,
            (byte)0x87,(byte)0x00,(byte)0x03,(byte)0x86,(byte)0x00,(byte)0x02,(byte)0x82,(byte)0x00,(byte)0x01,(byte)0x79,
            (byte)0x00,(byte)0x01,(byte)0x71,(byte)0x00,(byte)0x01,(byte)0x6E,(byte)0x00,(byte)0x01,(byte)0x6B,(byte)0x00,
            (byte)0x03,(byte)0x65,(byte)0x00,(byte)0x03,(byte)0x5E,(byte)0x00,(byte)0x01,(byte)0x5B,(byte)0x00,(byte)0x02,
            (byte)0x52,(byte)0x00,(byte)0x03,(byte)0x50,(byte)0x00,(byte)0x02,(byte)0x4C,(byte)0x00,(byte)0x03,(byte)0x49,
            (byte)0x00,(byte)0x02,(byte)0x44,(byte)0x00,(byte)0x02,(byte)0x41,(byte)0x00,(byte)0x03,(byte)0x3E,(byte)0x00,
            (byte)0x02,(byte)0x3B,(byte)0x00,(byte)0x03,(byte)0x37,(byte)0x00,(byte)0x02,(byte)0x31,(byte)0x00,(byte)0x03,
            (byte)0x2A,(byte)0x00,(byte)0x03,(byte)0x26,(byte)0x00,(byte)0x02,(byte)0x24,(byte)0x00,(byte)0x03,(byte)0x22,
            (byte)0x00,(byte)0x02,(byte)0x1F,(byte)0x00,(byte)0x02,(byte)0x09,(byte)0x00,(byte)0x03,(byte)0x03,(byte)0x00,
            (byte)0x02,(byte)0x01,(byte)0x06,(byte)0x00,(byte)0x00};
    private HashMap<Short, Byte> pvpLevelInfo = new HashMap<Short, Byte>() {{
        put((short) 661, (byte) 2);
        put((short) 658, (byte) 1);
        put((short) 657, (byte) 2);
        put((short) 656, (byte) 0);
        put((short) 655, (byte) 0);
        put((short) 652, (byte) 0);
        put((short) 649, (byte) 0);
        put((short) 648, (byte) 0);
        put((short) 647, (byte) 0);
        put((short) 646, (byte) 0);
        put((short) 645, (byte) 0);
        put((short) 644, (byte) 0);
        put((short) 643, (byte) 0);
        put((short) 642, (byte) 0);
        put((short) 641, (byte) 0);
        put((short) 640, (byte) 0);
        put((short) 639, (byte) 0);
        put((short) 638, (byte) 0);
        put((short) 637, (byte) 1);
        put((short) 635, (byte) 1);
        put((short) 632, (byte) 3);
        put((short) 630, (byte) 2);
        put((short) 628, (byte) 3);
        put((short) 625, (byte) 2);
        put((short) 621, (byte) 3);
        put((short) 620, (byte) 1);
        put((short) 612, (byte) 2);
        put((short) 609, (byte) 1);
        put((short) 604, (byte) 3);
        put((short) 598, (byte) 1);
        put((short) 593, (byte) 1);
        put((short) 591, (byte) 1);
        put((short) 579, (byte) 1);
        put((short) 573, (byte) 2);
        put((short) 571, (byte) 3);
        put((short) 563, (byte) 2);
        put((short) 561, (byte) 2);
        put((short) 555, (byte) 2);
        put((short) 553, (byte) 2);
        put((short) 545, (byte) 2);
        put((short) 537, (byte) 2);
        put((short) 534, (byte) 1);
        put((short) 530, (byte) 1);
        put((short) 526, (byte) 2);
        put((short) 497, (byte) 1);
        put((short) 494, (byte) 0);
        put((short) 493, (byte) 0);
        put((short) 492, (byte) 2);
        put((short) 491, (byte) 0);
        put((short) 490, (byte) 0);
        put((short) 489, (byte) 0);
        put((short) 488, (byte) 0);
        put((short) 487, (byte) 0);
        put((short) 486, (byte) 0);
        put((short) 485, (byte) 1);
        put((short) 484, (byte) 0);
        put((short) 483, (byte) 0);
        put((short) 482, (byte) 0);
        put((short) 481, (byte) 0);
        put((short) 480, (byte) 0);
        put((short) 479, (byte) 3);
        put((short) 478, (byte) 3);
        put((short) 475, (byte) 3);
        put((short) 474, (byte) 2);
        put((short) 473, (byte) 1);
        put((short) 472, (byte) 1);
        put((short) 469, (byte) 2);
        put((short) 468, (byte) 1);
        put((short) 465, (byte) 2);
        put((short) 464, (byte) 2);
        put((short) 462, (byte) 1);
        put((short) 461, (byte) 1);
        put((short) 454, (byte) 3);
        put((short) 452, (byte) 3);
        put((short) 450, (byte) 2);
        put((short) 448, (byte) 1);
        put((short) 445, (byte) 1);
        put((short) 442, (byte) 3);
        put((short) 437, (byte) 1);
        put((short) 436, (byte) 3);
        put((short) 435, (byte) 3);
        put((short) 430, (byte) 3);
        put((short) 429, (byte) 2);
        put((short) 424, (byte) 2);
        put((short) 423, (byte) 2);
        put((short) 407, (byte) 2);
        put((short) 398, (byte) 1);
        put((short) 395, (byte) 2);
        put((short) 392, (byte) 1);
        put((short) 389, (byte) 3);
        put((short) 386, (byte) 0);
        put((short) 385, (byte) 1);
        put((short) 384, (byte) 0);
        put((short) 383, (byte) 0);
        put((short) 382, (byte) 0);
        put((short) 381, (byte) 0);
        put((short) 380, (byte) 0);
        put((short) 379, (byte) 0);
        put((short) 378, (byte) 0);
        put((short) 377, (byte) 0);
        put((short) 376, (byte) 1);
        put((short) 373, (byte) 2);
        put((short) 356, (byte) 3);
        put((short) 350, (byte) 3);
        put((short) 342, (byte) 1);
        put((short) 334, (byte) 3);
        put((short) 330, (byte) 2);
        put((short) 324, (byte) 2);
        put((short) 319, (byte) 3);
        put((short) 308, (byte) 2);
        put((short) 302, (byte) 2);
        put((short) 297, (byte) 3);
        put((short) 291, (byte) 3);
        put((short) 286, (byte) 1);
        put((short) 279, (byte) 1);
        put((short) 272, (byte) 1);
        put((short) 260, (byte) 3);
        put((short) 257, (byte) 3);
        put((short) 254, (byte) 3);
        put((short) 251, (byte) 0);
        put((short) 250, (byte) 0);
        put((short) 249, (byte) 0);
        put((short) 248, (byte) 1);
        put((short) 245, (byte) 1);
        put((short) 244, (byte) 2);
        put((short) 243, (byte) 1);
        put((short) 242, (byte) 2);
        put((short) 237, (byte) 3);
        put((short) 233, (byte) 2);
        put((short) 232, (byte) 2);
        put((short) 230, (byte) 1);
        put((short) 227, (byte) 1);
        put((short) 226, (byte) 3);
        put((short) 221, (byte) 3);
        put((short) 214, (byte) 2);
        put((short) 212, (byte) 1);
        put((short) 211, (byte) 3);
        put((short) 208, (byte) 3);
        put((short) 207, (byte) 2);
        put((short) 205, (byte) 2);
        put((short) 197, (byte) 2);
        put((short) 196, (byte) 1);
        put((short) 195, (byte) 2);
        put((short) 186, (byte) 2);
        put((short) 184, (byte) 3);
        put((short) 178, (byte) 2);
        put((short) 171, (byte) 3);
        put((short) 169, (byte) 2);
        put((short) 160, (byte) 2);
        put((short) 151, (byte) 0);
        put((short) 150, (byte) 0);
        put((short) 149, (byte) 1);
        put((short) 146, (byte) 3);
        put((short) 145, (byte) 1);
        put((short) 143, (byte) 2);
        put((short) 141, (byte) 2);
        put((short) 135, (byte) 3);
        put((short) 134, (byte) 2);
        put((short) 130, (byte) 1);
        put((short) 121, (byte) 1);
        put((short) 113, (byte) 1);
        put((short) 110, (byte) 1);
        put((short) 107, (byte) 3);
        put((short) 101, (byte) 3);
        put((short) 94, (byte) 1);
        put((short) 91, (byte) 2);
        put((short) 82, (byte) 3);
        put((short) 80, (byte) 2);
        put((short) 76, (byte) 3);
        put((short) 73, (byte) 2);
        put((short) 68, (byte) 2);
        put((short) 65, (byte) 3);
        put((short) 62, (byte) 2);
        put((short) 59, (byte) 3);
        put((short) 55, (byte) 2);
        put((short) 49, (byte) 3);
        put((short) 42, (byte) 3);
        put((short) 38, (byte) 2);
        put((short) 36, (byte) 3);
        put((short) 34, (byte) 2);
        put((short) 31, (byte) 2);
        put((short) 9, (byte) 3);
        put((short) 3, (byte) 2);
    }};
    //private byte pvpLevelAmount = 0;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeBytes(data);
        /*buffer.writeShort(pvpLevelInfo.size());
        for (Map.Entry<Short, Byte> entry : pvpLevelInfo.entrySet()) {
            buffer.writeShort(entry.getKey());
            buffer.writeByte(entry.getValue());
        }
        buffer.writeByte(0);//size*/
    }
}
