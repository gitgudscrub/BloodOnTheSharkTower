package com.sharktower.bloodonthesharktower.command;

/**
 * Deprecated compatibility shim for early Sharktower development builds.
 * New code should register/use {@link BotsCommands}; the command namespace is
 * now /bots to mirror the original /botb tree with the Sharktower name.
 */
@Deprecated(forRemoval = true)
public final class SharktowerCommand {
    private SharktowerCommand() {}

    public static void register() {
        BotsCommands.register();
    }
}
