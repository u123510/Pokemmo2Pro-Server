package org.pokemmo.gameserver.game.script;

public class RenderScreenScript {
    private boolean renderScreen;
    public RenderScreenScript(boolean renderScreen) {
        this.renderScreen = renderScreen;
    }
    public boolean isRenderScreen() {
        return renderScreen;
    }
}
