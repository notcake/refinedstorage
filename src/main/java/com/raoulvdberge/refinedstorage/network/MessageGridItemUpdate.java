package com.raoulvdberge.refinedstorage.network;

import com.raoulvdberge.refinedstorage.RSUtils;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetworkMaster;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.GridStackItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class MessageGridItemUpdate implements IMessage, IMessageHandler<MessageGridItemUpdate, IMessage> {
    private INetworkMaster network;
    private boolean canCraft;
    private List<GridStackItem> stacks = new ArrayList<>();

    public MessageGridItemUpdate() {
    }

    public MessageGridItemUpdate(INetworkMaster network, boolean canCraft) {
        this.network = network;
        this.canCraft = canCraft;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        canCraft = buf.readBoolean();

        int items = buf.readInt();

        for (int i = 0; i < items; ++i) {
            this.stacks.add(new GridStackItem(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(canCraft);

        int size = network.getItemStorageCache().getList().getStacks().size();

        for (ICraftingPattern pattern : network.getCraftingManager().getPatterns()) {
            size += pattern.getOutputs().stream().filter(o -> o != null).count();
        }

        buf.writeInt(size);

        for (ItemStack stack : network.getItemStorageCache().getList().getStacks()) {
            RSUtils.writeItemStack(buf, stack, network, false);
        }

        for (ICraftingPattern pattern : network.getCraftingManager().getPatterns()) {
            for (ItemStack output : pattern.getOutputs()) {
                if (output != null) {
                    RSUtils.writeItemStack(buf, output, network, true);
                }
            }
        }
    }

    @Override
    public IMessage onMessage(MessageGridItemUpdate message, MessageContext ctx) {
        GuiGrid.CAN_CRAFT = message.canCraft;

        GuiGrid.ITEMS.clear();

        for (GridStackItem item : message.stacks) {
            boolean canAdd = true;

            if (item.doesDisplayCraftText()) {
                // This is an output from a pattern being sent. Only add it if it hasn't been added before.
                for (GridStackItem otherItem : GuiGrid.ITEMS.get(item.getStack().getItem())) {
                    if (API.instance().getComparer().isEqualNoQuantity(item.getStack(), otherItem.getStack())) {
                        canAdd = false;

                        break;
                    }
                }
            }

            if (canAdd) {
                GuiGrid.ITEMS.put(item.getStack().getItem(), item);
            }
        }

        GuiGrid.markForSorting();

        return null;
    }
}
