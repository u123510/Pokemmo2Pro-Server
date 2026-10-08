package org.pokemmo.gameserver.command;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.pokemmo.gameserver.command.commands.AddMonsterCommand;
import org.pokemmo.gameserver.command.commands.AddParticleCommand;
import org.pokemmo.gameserver.command.commands.CreateItemCommand;
import org.pokemmo.gameserver.command.commands.SetAlphaCommand;
import org.pokemmo.gameserver.command.commands.SetAbilityCommand;
import org.pokemmo.gameserver.command.commands.SetBallTypeCommand;
import org.pokemmo.gameserver.command.commands.SetGenderCommand;
import org.pokemmo.gameserver.command.commands.SetGiftCommand;
import org.pokemmo.gameserver.command.commands.SetHiddenAbilityCommand;
import org.pokemmo.gameserver.command.commands.SetHappinessCommand;
import org.pokemmo.gameserver.command.commands.SetIvsCommand;
import org.pokemmo.gameserver.command.commands.SetNatureCommand;
import org.pokemmo.gameserver.command.commands.SetOtCommand;
import org.pokemmo.gameserver.command.commands.PcCommand;
import org.pokemmo.gameserver.command.commands.SetRibbonsCommand;
import org.pokemmo.gameserver.command.commands.SetEvsCommand;
import org.pokemmo.gameserver.command.commands.SetSecretShinyCommand;
import org.pokemmo.gameserver.command.commands.SetShinyCommand;
import org.pokemmo.gameserver.command.commands.SpectateCommand;
import org.pokemmo.gameserver.command.commands.WinBattleCommand;
import org.pokemmo.gameserver.command.commands.MoveCloseCommand;
import org.pokemmo.gameserver.command.commands.MoveToCommand;
import org.pokemmo.gameserver.command.commands.MoveToNdsCommand;
import org.pokemmo.gameserver.command.commands.ReloadShopsCommand;
import org.pokemmo.gameserver.command.commands.SpawnNpcCommand;
import org.pokemmo.gameserver.command.commands.EventDeleteNpcCommand;
import org.pokemmo.gameserver.command.commands.EventSpawnNpcCommand;
import org.pokemmo.gameserver.command.commands.HideCommand;
import org.pokemmo.gameserver.command.commands.HealCommand;
import org.pokemmo.gameserver.command.commands.SetFormCommand;
import org.pokemmo.gameserver.command.commands.SetMoveCommand;
import org.pokemmo.gameserver.command.commands.UnlockDexCommand;

public class GameCommandModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<Command> commandBinder = Multibinder.newSetBinder(binder(), Command.class);
        commandBinder.addBinding().to(AddMonsterCommand.class);
        commandBinder.addBinding().to(AddParticleCommand.class);
        commandBinder.addBinding().to(CreateItemCommand.class);
        commandBinder.addBinding().to(SetAlphaCommand.class);
        commandBinder.addBinding().to(SetAbilityCommand.class);
        commandBinder.addBinding().to(SetBallTypeCommand.class);
        commandBinder.addBinding().to(SetGenderCommand.class);
        commandBinder.addBinding().to(SetGiftCommand.class);
        commandBinder.addBinding().to(SetHiddenAbilityCommand.class);
        commandBinder.addBinding().to(SetEvsCommand.class);
        commandBinder.addBinding().to(SetHappinessCommand.class);
        commandBinder.addBinding().to(SetIvsCommand.class);
        commandBinder.addBinding().to(SetNatureCommand.class);
        commandBinder.addBinding().to(SetOtCommand.class);
        commandBinder.addBinding().to(PcCommand.class);
        commandBinder.addBinding().to(SetRibbonsCommand.class);
        commandBinder.addBinding().to(SetShinyCommand.class);
        commandBinder.addBinding().to(SetSecretShinyCommand.class);
        commandBinder.addBinding().to(WinBattleCommand.class);
        commandBinder.addBinding().to(SpectateCommand.class);
        commandBinder.addBinding().to(MoveCloseCommand.class);
        commandBinder.addBinding().to(MoveToCommand.class);
        commandBinder.addBinding().to(MoveToNdsCommand.class);
        commandBinder.addBinding().to(ReloadShopsCommand.class);
        commandBinder.addBinding().to(SpawnNpcCommand.class);
        commandBinder.addBinding().to(EventDeleteNpcCommand.class);
        commandBinder.addBinding().to(EventSpawnNpcCommand.class);
        commandBinder.addBinding().to(HideCommand.class);
        commandBinder.addBinding().to(HealCommand.class);
        commandBinder.addBinding().to(SetFormCommand.class);
        commandBinder.addBinding().to(SetMoveCommand.class);
        commandBinder.addBinding().to(UnlockDexCommand.class);
    }
}
