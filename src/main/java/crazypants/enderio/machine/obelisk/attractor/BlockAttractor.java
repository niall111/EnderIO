package crazypants.enderio.machine.obelisk.attractor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.ModObject;
import crazypants.enderio.machine.obelisk.BlockObeliskAbstract;

public class BlockAttractor extends BlockObeliskAbstract<TileAttractor> {
  
  public static BlockAttractor create() {
    BlockAttractor res = new BlockAttractor();
    res.init();
    return res;
  }
  
  protected BlockAttractor() {
    super(ModObject.blockAttractor, TileAttractor.class);
  }

  @Override
  public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileAttractor) {
      return new ContainerAttractor(player.inventory, (TileAttractor)te);
    }
    return null;
  }
  
  @SideOnly(Side.CLIENT)
  public IIcon getOnIcon() {
    return iconBuffer[0][6];
  }

  @Override
  public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileAttractor) {
      return new GuiAttractor(player.inventory, (TileAttractor)te);
    }
    return null;
  }

  @Override
  protected int getGuiId() {    
    return GuiHandler.GUI_ID_ATTRACTOR;
  }
}
