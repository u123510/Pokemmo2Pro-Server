package org.pokemmo.gameserver.game.script;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
@Getter @Setter
public class Script {
    private String name;
    private ArrayList<ScrpitNode> scrpitNodes = new ArrayList<>();
    private ArrayList<String> nextNodeIdMapper = new ArrayList<>();
    public Script(String name) {
        this.name = name;
    }
    public void addScrpitNode(ScrpitNode scrpitNode) {
        scrpitNodes.add(scrpitNode);
    }
    public void addNextNodeId(String nextNodeName) {
        nextNodeIdMapper.add(nextNodeName);
    }
    public boolean isEnd() {
        return nextNodeIdMapper.isEmpty();
    }


}
