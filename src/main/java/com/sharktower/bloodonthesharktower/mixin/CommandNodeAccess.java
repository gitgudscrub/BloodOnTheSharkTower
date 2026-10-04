package com.sharktower.bloodonthesharktower.mixin;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

/** Replace a command alias without widening vanilla command permissions. */
@Mixin(value = CommandNode.class, remap = false)
public interface CommandNodeAccess<S> {
    @Accessor("children") Map<String, CommandNode<S>> sharktowerChildren();
    @Accessor("literals") Map<String, LiteralCommandNode<S>> sharktowerLiterals();
}
