package org.pokemmo.gameserver.game.move;
import lombok.AllArgsConstructor;
import org.pokemmo.gameserver.game.bin.BinFileReader;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.game.pokemon.PokemonType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
@AllArgsConstructor
public class MoveDataReader extends BinFileReader {
    private File file;
    public List<PokemonMoveData> readPokemonMoveData() throws IOException {
        List<PokemonMoveData> pokemonMoves = new java.util.ArrayList<>(809);
        try (FileInputStream fis = new FileInputStream(file)) {
            // 计算需要读取的瓦片数量
            short size = readShortLE(fis);
            for(int i = 0;i<size;i++) {
                PokemonMoveData.Builder builder = new PokemonMoveData.Builder();
                short moveIndexId = readShortLE(fis);
                builder.setMoveIndexId(moveIndexId);
                PokemonType movePokemonType = PokemonType.getByType(readByte(fis));
                builder.setMovePokemonType(movePokemonType);
                MoveDamageType moveDamageType = MoveDamageType.getByType(readByte(fis));
                builder.setMoveDamageType(moveDamageType);
                short moveBasePower = readShortLE(fis);
                builder.setMoveBasePower(moveBasePower);
                boolean isTrueDamage = readBoolean(fis);
                builder.setIsTrueDamage(isTrueDamage);
                MoveTargetType moveTargetType = MoveTargetType.getByType(readByte(fis));
                builder.setMoveTargetType(moveTargetType);
                byte moveBasePp = readByte(fis);
                builder.setMoveBasePp(moveBasePp);
                byte moveBaseAccuracy = readByte(fis);
                builder.setMoveBaseAccuracy(moveBaseAccuracy);
                byte movePriority = readByte(fis);
                builder.setMovePriority(movePriority);
                byte moveAdditionChangeStatEffectTriggerRatio = readByte(fis);
                builder.setMoveAdditionChangeStatEffectTriggerRatio(moveAdditionChangeStatEffectTriggerRatio);
                byte moveAdditionEffectChangeStatLevel = readByte(fis);
                builder.setMoveAdditionEffectChangeStatLevel(moveAdditionEffectChangeStatLevel);
                boolean isUserTriggerAdditionChangeStatEffect = readBoolean(fis);
                builder.setIsUserTriggerAdditionChangeStatEffect(isUserTriggerAdditionChangeStatEffect);
                short moveAdditionEffectChangePokemonStatArraySize = readShortLE(fis);
                for(int j = 0;j<moveAdditionEffectChangePokemonStatArraySize;j++) {
                    builder.addMoveAdditionEffectChangePokemonStat(PokemonStatType.getByType(readByte(fis)));
                }
                short moveAttachChangePokemonStatArraySize = readShortLE(fis);
                for(int j = 0;j<moveAttachChangePokemonStatArraySize;j++) {
                    builder.addMoveAttachChangePokemonStat(PokemonStatType.getByType(readByte(fis)));
                }
                byte moveChangeStatTypeArray[] = new byte[3];
                for(int j = 0;j<moveChangeStatTypeArray.length;j++) {
                    moveChangeStatTypeArray[j] = readByte(fis);
                }
                builder.setMoveChangeStatTypeArray(moveChangeStatTypeArray);
                byte moveChangeStatLevelArray[] = new byte[3];
                for(int j = 0;j<moveChangeStatLevelArray.length;j++) {
                    moveChangeStatLevelArray[j] = readByte(fis);
                }
                builder.setMoveChangeStatLevelArray(moveChangeStatLevelArray);
                byte moveChangeStatRatioArray[] = new byte[3];
                for(int j = 0;j<moveChangeStatRatioArray.length;j++) {
                    moveChangeStatRatioArray[j] = readByte(fis);
                }
                builder.setMoveChangeStatRatioArray(moveChangeStatRatioArray);
                int moveInfoFlag = readIntLE(fis);
                builder.setMoveInfoFlag(moveInfoFlag);
                byte moveBossInfoFlag = readByte(fis);
                builder.setMoveBossInfoFlag(moveBossInfoFlag);
                byte moveHpRecoverRatio = readByte(fis);
                builder.setMoveHpRecoverRatio(moveHpRecoverRatio);
                byte moveHpRecoverByDamageRatio = readByte(fis);
                builder.setMoveHpRecoverByDamageRatio(moveHpRecoverByDamageRatio);
                short moveAdditionStringDatasSize = readShortLE(fis);
                for(int j = 0;j<moveAdditionStringDatasSize;j++) {
                    int additionStringIndexId = readIntLE(fis);
                    short stringFormatDatasSize = readShortLE(fis);
                    List<MoveAdditionStringFormatData> moveAdditionStringFormatDatas = new java.util.ArrayList<>(stringFormatDatasSize);
                    for(int k = 0;k<stringFormatDatasSize;k++) {
                        MoveAdditionStringFormatData moveAdditionStringFormatData = new MoveAdditionStringFormatData();
                        moveAdditionStringFormatData.setReplaceIndex(readByte(fis));
                        moveAdditionStringFormatData.setStringType(readByte(fis));
                        short stringFormatValueSize = readShortLE(fis);
                        for(int l = 0;l<stringFormatValueSize;l++) {
                            moveAdditionStringFormatData.getStringFormatDatas().add(readShortLE(fis));
                        }
                        moveAdditionStringFormatDatas.add(moveAdditionStringFormatData);
                    }
                    MoveAdditionStringData moveAdditionStringData = new MoveAdditionStringData(additionStringIndexId,moveAdditionStringFormatDatas);
                    builder.addMoveAdditionStringData(moveAdditionStringData);
                }
                PokemonMoveData move = builder.bulid();
                pokemonMoves.add(move);
            }
        }
        return pokemonMoves;
    }
    /*public void exportToJson(List<PokemonMoveData> moves, File outputFile) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(moves, writer);
        }
    }*/
}
