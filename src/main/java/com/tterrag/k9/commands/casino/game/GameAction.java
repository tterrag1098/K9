package com.tterrag.k9.commands.casino.game;

import java.util.function.Consumer;

public interface GameAction<T extends Game<T>> extends Consumer<T> {

    String getName();

}
