package stargatetech2.enemy.tileentity;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import stargatetech2.api.StargateTechAPI;
import stargatetech2.api.bus.IBusDevice;
import stargatetech2.api.bus.IBusDriver;
import stargatetech2.api.bus.IBusInterface;
import stargatetech2.automation.bus.BusDriver;
import stargatetech2.core.api.ParticleIonizerRecipes;
import stargatetech2.core.api.ParticleIonizerRecipes.Recipe;
import stargatetech2.core.base.BaseTileEntity;
import stargatetech2.enemy.util.IonizedParticles;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;

public class TileParticleIonizer extends BaseTileEntity implements IFluidHandler, IEnergyHandler, IInventory, IBusDevice{
	private FluidTank tank = new FluidTank(4000);
	private ItemStack[] inventory = new ItemStack[9];
	public ItemStack consuming = null;
	EnergyStorage capacitor = new EnergyStorage(12000, 40);
	public int workTicks;
	public Recipe recipe;
	private IBusDriver networkDriver = new BusDriver();
	private IBusInterface[] interfaces = new IBusInterface[]{
			StargateTechAPI.api().getFactory().getIBusInterface(this, networkDriver)
	};
	
	@Override
	public void invalidate(){
		super.invalidate();
		if(worldObj.isRemote == false){
			double x = ((double)xCoord) + 0.5D;
			double y = ((double)yCoord) + 0.5D;
			double z = ((double)zCoord) + 0.5D;
			for(ItemStack stack : inventory){
				if(stack != null){
					worldObj.spawnEntityInWorld(new EntityItem(worldObj, x, y, z,stack));
				}
			}
		}
	}
	
	@Override
	public void updateEntity(){
		if(worldObj.isRemote == false){
			if(consuming == null){
				for(int slot = 0; slot < inventory.length; slot++){
					ItemStack item = inventory[slot];
					recipe = ParticleIonizerRecipes.getRecipe(item);
					if(item != null && recipe != null){
						consuming = item.copy();
						item.stackSize--;
						consuming.stackSize = 1;
						if(item.stackSize == 0){
							inventory[slot] = null;
						}
						workTicks = recipe.ticks;
						break;
					}
				}
			}else{
				FluidStack fs = new FluidStack(IonizedParticles.fluid, recipe.ions);
				int fill = tank.fill(fs, false);
				if(fill < fs.amount || capacitor.extractEnergy(recipe.power, true) < recipe.power){
					return;
				}
				capacitor.extractEnergy(recipe.power, false);
				tank.fill(fs, true);
				workTicks--;
				if(workTicks == 0){
					consuming = null;
				}
			}
		}
	}
	
	public int getIonAmount(){
		FluidStack fs = tank.getInfo().fluid;
		return (fs == null) ? 0 : fs.amount;
	}
	
	public void setIonAmount(int value){
		tank.setFluid(new FluidStack(IonizedParticles.fluid, value));
	}
	
	public void setPower(int value){
		capacitor.setEnergyStored(value);
	}
	
	@Override
	protected void readNBT(NBTTagCompound nbt) {
		tank.readFromNBT(nbt);
		capacitor.readFromNBT(nbt.getCompoundTag("power"));
		for(int slot = 0; slot < inventory.length; slot++){
			if(nbt.hasKey("stack" + slot)){
				inventory[slot] = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("stack" + slot));
			}else{
				inventory[slot] = null;
			}
		}
		if(nbt.hasKey("consuming")){
			consuming = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("consuming"));
			recipe = ParticleIonizerRecipes.getRecipe(consuming);
			if(recipe == null) consuming = null;
		}
		workTicks = nbt.getInteger("workTicks");
	}
	
	@Override
	protected void writeNBT(NBTTagCompound nbt) {
		tank.writeToNBT(nbt);
		NBTTagCompound power = new NBTTagCompound();
		capacitor.writeToNBT(power);
		nbt.setCompoundTag("power", power);
		for(int slot = 0; slot < inventory.length; slot++){
			NBTTagCompound stack = new NBTTagCompound();
			if(inventory[slot] != null){
				inventory[slot].writeToNBT(stack);
			}
			nbt.setCompoundTag("stack" + slot, stack);
		}
		if(consuming != null){
			NBTTagCompound stack = new NBTTagCompound();
			consuming.writeToNBT(stack);
			nbt.setCompoundTag("consuming", stack);
		}
		nbt.setInteger("workTicks", workTicks);
	}
	
	//##################################################################################
	// IEnergyHandler
	
	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
		return capacitor.receiveEnergy(maxReceive, simulate);
	}
	
	@Override
	public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
		return capacitor.extractEnergy(maxExtract, simulate);
	}
	
	@Override
	public boolean canInterface(ForgeDirection from) {
		return from.ordinal() != getBlockMetadata();
	}
	
	@Override
	public int getEnergyStored(ForgeDirection from) {
		return capacitor.getEnergyStored();
	}
	
	@Override
	public int getMaxEnergyStored(ForgeDirection from) {
		return capacitor.getMaxEnergyStored();
	}
	
	//##################################################################################
	// IFluidHandler
	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
		if(resource.fluidID == IonizedParticles.fluid.getID()){
			return tank.drain(resource.amount, doDrain);
		}else{
			return null;
		}
	}
	
	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
		return tank.drain(maxDrain, doDrain);
	}
	
	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid) {
		return fluid.getID() == IonizedParticles.fluid.getID();
	}
	
	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) {
		return new FluidTankInfo[]{tank.getInfo()};
	}
	
	@Override public boolean canFill(ForgeDirection from, Fluid fluid){ return false; }
	@Override public int fill(ForgeDirection from, FluidStack resource, boolean doFill){ return 0; }
	
	//##################################################################################
	// IInventory
	@Override
	public ItemStack getStackInSlot(int slot) {
		return inventory[slot];
	}
	
	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		if(inventory[slot] != null){
			ItemStack stack;
			if(inventory[slot].stackSize <= amount){
				stack = inventory[slot];
				inventory[slot] = null;
			}else{
				stack = inventory[slot].splitStack(amount);
				if(inventory[slot].stackSize == 0){
					inventory[slot] = null;
				}
			}
			return stack;
		}else{
			return null;
		}
	}
	
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		inventory[slot] = stack;
	}
	
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return ParticleIonizerRecipes.getRecipe(stack) != null;
	}
	
	@Override
	public ItemStack getStackInSlotOnClosing(int slot){
		ItemStack stack = inventory[slot];
		inventory[slot] = null;
		return stack;
	}
	
	@Override public String getInvName(){ return "Particle Ionizer"; }
	@Override public boolean isInvNameLocalized(){ return true; }
	@Override public int getInventoryStackLimit(){ return 64; }
	@Override public boolean isUseableByPlayer(EntityPlayer entityplayer){ return true; }
	@Override public int getSizeInventory(){ return inventory.length; }
	@Override public void openChest(){}
	@Override public void closeChest(){}
	
	//##################################################################################
	// IBusDevice
	
	@Override
	public IBusInterface[] getInterfaces(int side) {
		return getBlockMetadata() == side ? null : interfaces;
	}
	
	@Override
	public World getWorld(){
		return worldObj;
	}
	
	@Override
	public int getXCoord() {
		return xCoord;
	}

	@Override
	public int getYCoord() {
		return yCoord;
	}

	@Override
	public int getZCoord() {
		return zCoord;
	}
}