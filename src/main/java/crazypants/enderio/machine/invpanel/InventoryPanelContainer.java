package crazypants.enderio.machine.invpanel;

import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import crazypants.enderio.machine.gui.AbstractMachineContainer;
import crazypants.enderio.machine.invpanel.server.ChangeLog;
import crazypants.enderio.machine.invpanel.server.InventoryDatabaseServer;
import crazypants.enderio.machine.invpanel.server.ItemEntry;
import crazypants.enderio.network.PacketHandler;
import crazypants.util.ItemUtil;

public class InventoryPanelContainer extends AbstractMachineContainer implements ChangeLog {

  public static final int CRAFTING_GRID_X = 7;
  public static final int CRAFTING_GRID_Y = 16;

  public static final int RETURN_INV_X = 7;
  public static final int RETURN_INV_Y = 82;

  public static final int FILTER_SLOT_X = 233;
  public static final int FILTER_SLOT_Y = 7;

  private final HashSet<ItemEntry> changedItems;

  private Slot slotFilter;

  private int slotCraftResult;
  @SuppressWarnings("unused")
  private int indexFilterSlot;
  private int firstSlotReturn;
  private int lastSlotReturn;
  private int firstSlotCraftingGrid;
  private int lastSlotCraftingGrid;

  public InventoryPanelContainer(InventoryPlayer playerInv, TileInventoryPanel te) {
    super(playerInv, te);
    te.eventHandler = this;

    if(te.getWorldObj().isRemote) {
      changedItems = null;
    } else {
      changedItems = new HashSet<ItemEntry>();
    }
  }

  @Override
  protected void addMachineSlots(InventoryPlayer playerInv) {
    slotCraftResult = inventorySlots.size();
    addSlotToContainer(new SlotCrafting(playerInv.player, tileEntity, tileEntity, TileInventoryPanel.SLOT_CRAFTING_RESULT, CRAFTING_GRID_X + 59,
        CRAFTING_GRID_Y + 18) {
      @Override
      public void onPickupFromSlot(EntityPlayer player, ItemStack p_82870_2_) {
        FMLCommonHandler.instance().firePlayerCraftingEvent(player, p_82870_2_, tileEntity);
        for (int i = TileInventoryPanel.SLOT_CRAFTING_START; i < TileInventoryPanel.SLOT_CRAFTING_RESULT; i++) {
          ItemStack itemstack = tileEntity.getStackInSlot(i);
          if(itemstack == null)
            continue;

          tileEntity.decrStackSize(i, 1);
          if(!itemstack.getItem().hasContainerItem(itemstack))
            continue;

          ItemStack containerIS = itemstack.getItem().getContainerItem(itemstack);
          if(containerIS != null && containerIS.isItemStackDamageable() && containerIS.getItemDamage() > containerIS.getMaxDamage()) {
            MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, containerIS));
          } else {
            if(itemstack.getItem().doesContainerItemLeaveCraftingGrid(itemstack)) {
              if(ItemUtil.doInsertItem(tileEntity, 10, 20, itemstack) > 0)
                continue;
              if(player.inventory.addItemStackToInventory(containerIS))
                continue;
            }
            if(tileEntity.getStackInSlot(i) == null) {
              tileEntity.setInventorySlotContents(i, containerIS);
            } else {
              player.dropPlayerItemWithRandomChoice(containerIS, false);
            }
          }
        }
      }
    });

    firstSlotCraftingGrid = inventorySlots.size();
    for (int y = 0, i = TileInventoryPanel.SLOT_CRAFTING_START; y < 3; y++) {
      for (int x = 0; x < 3; x++, i++) {
        addSlotToContainer(new Slot(tileEntity, i, CRAFTING_GRID_X + x * 18, CRAFTING_GRID_Y + y * 18));
      }
    }
    lastSlotCraftingGrid = inventorySlots.size() - 1;

    indexFilterSlot = lastSlotCraftingGrid + 1;
    slotFilter = addSlotToContainer(new Slot(tileEntity, TileInventoryPanel.SLOT_VIEW_FILTER, FILTER_SLOT_X, FILTER_SLOT_Y) {
      @Override
      public int getSlotStackLimit() {
        return 1;
      }
    });

    firstSlotReturn = inventorySlots.size();
    for (int y = 0, i = TileInventoryPanel.SLOT_RETURN_START; y < 2; y++) {
      for (int x = 0; x < 5; x++, i++) {
        addSlotToContainer(new Slot(tileEntity, i, RETURN_INV_X + x * 18, RETURN_INV_Y + y * 18));
      }
    }
    lastSlotReturn = inventorySlots.size() - 1;
  }

  @Override
  public Point getPlayerInventoryOffset() {
    return new Point(39, 130);
  }

  @Override
  public void onContainerClosed(EntityPlayer player) {
    super.onContainerClosed(player);
    if(!tileEntity.getWorldObj().isRemote) {
      ((TileInventoryPanel) tileEntity).eventHandler = null;
    }
    removeChangeLog();
  }

  private TileInventoryPanel getInventoryPanel() {
    return (TileInventoryPanel) tileEntity;
  }

  public Slot getSlotFilter() {
    return slotFilter;
  }

  @SuppressWarnings("unchecked")
  public List<Slot> getCraftingGridSlots() {
    return inventorySlots.subList(firstSlotCraftingGrid, lastSlotCraftingGrid);
  }

  @SuppressWarnings("unchecked")
  public List<Slot> getReturnAreaSlots() {
    return inventorySlots.subList(firstSlotReturn, lastSlotReturn);
  }

  @SuppressWarnings("unchecked")
  public List<Slot> getPlayerInventorySlots() {
    return inventorySlots.subList(startPlayerSlot, endPlayerSlot);
  }

  private void removeChangeLog() {
    if(changedItems != null) {
      InventoryDatabaseServer db = getInventoryPanel().getDatabaseServer();
      if(db != null) {
        db.removeChangeLog(this);
      }
    }
  }

  @Override
  public void removeCraftingFromCrafters(ICrafting crafting) {
    super.removeCraftingFromCrafters(crafting);
    removeChangeLog();
  }

  @Override
  public void addCraftingToCrafters(ICrafting crafting) {
    if(changedItems != null) {
      sendChangeLog();
    }
    super.addCraftingToCrafters(crafting);
    if(changedItems != null) {
      InventoryDatabaseServer db = getInventoryPanel().getDatabaseServer();
      if(db != null) {
        db.addChangeLog(this);
        if(crafting instanceof EntityPlayerMP) {
          try {
            byte[] compressed = db.compressItemList();
            PacketItemList pil = new PacketItemList(getInventoryPanel(), db.getGeneration(), compressed);
            PacketHandler.sendTo(pil, (EntityPlayerMP) crafting);
          } catch (IOException ex) {
            Logger.getLogger(InventoryPanelContainer.class.getName()).log(Level.SEVERE, "Exception while compressing item list", ex);
          }
        }
      }
    }
  }

  @Override
  public void onCraftMatrixChanged(IInventory inv) {
    InventoryCrafting tmp = new InventoryCrafting(new Container() {
      @Override
      public boolean canInteractWith(EntityPlayer ep) {
        return false;
      }
    }, 3, 3);

    for (int i = 0; i < 9; i++) {
      tmp.setInventorySlotContents(i, tileEntity.getStackInSlot(i));
    }

    tileEntity.setInventorySlotContents(9, CraftingManager.getInstance().findMatchingRecipe(tmp, tileEntity.getWorldObj()));
  }

  @Override
  public boolean func_94530_a(ItemStack par1, Slot slot) {
    return !(slot instanceof SlotCrafting) && super.func_94530_a(par1, slot);
  }

  public boolean clearCraftingGrid() {
    boolean cleared = true;
    for (Slot slot : getCraftingGridSlots()) {
      if(slot.getHasStack()) {
        moveItemsToReturnArea(slot.slotNumber);
        if(slot.getHasStack()) {
          cleared = false;
        }
      }
    }
    return cleared;
  }

  @Override
  protected List<SlotRange> getTargetSlotsForTransfer(int slotIndex, Slot slot) {
    if(slotIndex == slotCraftResult) {
      return Collections.singletonList(getPlayerInventorySlotRange(true));
    }
    if(slotIndex >= firstSlotReturn && slotIndex <= lastSlotReturn) {
      return Collections.singletonList(new SlotRange(firstSlotReturn, lastSlotReturn, false));
    }
    if((slotIndex >= firstSlotCraftingGrid && slotIndex <= lastSlotCraftingGrid) || slotIndex >= startPlayerSlot) {
      ArrayList<SlotRange> res = new ArrayList<SlotRange>();
      res.add(new SlotRange(firstSlotReturn, lastSlotReturn, false));
      addPlayerSlotRanges(res, slotIndex);
      return res;
    }
    return Collections.emptyList();
  }

  @Override
  public void entryChanged(ItemEntry entry) {
    changedItems.add(entry);
  }

  @Override
  public void databaseReset() {
    changedItems.clear();

  }

  @Override
  public void sendChangeLog() {
    if(!changedItems.isEmpty() && !crafters.isEmpty()) {
      InventoryDatabaseServer db = getInventoryPanel().getDatabaseServer();
      if(db != null) {
        try {
          byte[] compressed = db.compressChangedItems(changedItems);
          PacketItemList pil = new PacketItemList(getInventoryPanel(), db.getGeneration(), compressed);
          for (Object crafting : crafters) {
            if(crafting instanceof EntityPlayerMP) {
              PacketHandler.sendTo(pil, (EntityPlayerMP) crafting);
            }
          }
        } catch (IOException ex) {
          Logger.getLogger(InventoryPanelContainer.class.getName()).log(Level.SEVERE, "Exception while compressing changed items", ex);
        }
      }
    }
    changedItems.clear();
  }

  public int getSlotIndex(IInventory inv, int index) {
    for (int i = 0; i < inventorySlots.size(); i++) {
      Slot slot = (Slot) inventorySlots.get(i);
      if(slot.isSlotInInventory(inv, index)) {
        return i;
      }
    }
    return -1;
  }

  public void executeFetchItems(EntityPlayerMP player, int generation, int dbID, int targetSlot, int count) {
    TileInventoryPanel te = getInventoryPanel();
    InventoryDatabaseServer db = te.getDatabaseServer();
    if(db == null || db.getGeneration() != generation || !db.isCurrent()) {
      return;
    }
    ItemEntry entry = db.getExistingItem(dbID);
    if(entry != null) {
      ItemStack targetStack;
      Slot slot;
      int maxStackSize;

      if(targetSlot < 0) {
        slot = null;
        targetStack = player.inventory.getItemStack();
        maxStackSize = player.inventory.getInventoryStackLimit();
      } else {
        slot = getSlot(targetSlot);
        targetStack = slot.getStack();
        maxStackSize = slot.getSlotStackLimit();
      }

      ItemStack tmpStack = new ItemStack(entry.getItem(), 0, entry.meta);
      tmpStack.stackTagCompound = entry.nbt;
      maxStackSize = Math.min(maxStackSize, tmpStack.getMaxStackSize());

      if(targetStack != null && targetStack.stackSize > 0) {
        if(!ItemUtil.areStackMergable(tmpStack, targetStack)) {
          return;
        }
      } else {
        targetStack = tmpStack.copy();
      }

      count = Math.min(count, maxStackSize - targetStack.stackSize);
      if(count > 0) {
        int extracted = db.extractItems(entry, count, te);
        if(extracted > 0) {
          targetStack.stackSize += extracted;

          sendChangeLog();

          if(slot != null) {
            slot.putStack(targetStack);
          } else {
            player.inventory.setItemStack(targetStack);
            player.updateHeldItem();
          }
        }
      }
    }
  }

  public boolean moveItemsToReturnArea(int fromSlot) {
    return moveItems(fromSlot, firstSlotReturn, lastSlotReturn, Short.MAX_VALUE);
  }

  public boolean moveItems(int fromSlot, int toSlotStart, int toSlotEnd, int amount) {
    if(!executeMoveItems(fromSlot, toSlotStart, toSlotEnd, amount)) {
      return false;
    }
    if(getTileEntity().getWorldObj().isRemote) {
      PacketHandler.INSTANCE.sendToServer(new PacketMoveItems(fromSlot, toSlotStart, toSlotEnd, amount));
    }
    return true;
  }

  public boolean executeMoveItems(int fromSlot, int toSlotStart, int toSlotEnd, int amount) {
    if((fromSlot >= toSlotStart && fromSlot < toSlotEnd) || toSlotEnd <= toSlotStart || amount <= 0) {
      return false;
    }

    Slot srcSlot = getSlot(fromSlot);
    ItemStack src = srcSlot.getStack();
    if(src != null) {
      ItemStack toMove = src.copy();
      toMove.stackSize = Math.min(src.stackSize, amount);
      int remaining = src.stackSize - toMove.stackSize;
      if(mergeItemStack(toMove, toSlotStart, toSlotEnd, false)) {
        remaining += toMove.stackSize;
        if(remaining == 0) {
          srcSlot.putStack(null);
        } else {
          src.stackSize = remaining;
          srcSlot.onSlotChanged();
        }
        return true;
      }
    }
    return false;
  }
}
