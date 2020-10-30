package me.sat7.dynamicshop.transactions;

import java.util.Arrays;
import java.util.HashMap;

import me.sat7.dynamicshop.events.ShopBuySellEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import me.sat7.dynamicshop.DynaShopAPI;
import me.sat7.dynamicshop.DynamicShop;
import me.sat7.dynamicshop.jobshook.JobsHook;
import me.sat7.dynamicshop.utilities.ItemsUtil;
import me.sat7.dynamicshop.utilities.LangUtil;
import me.sat7.dynamicshop.utilities.LogUtil;
import me.sat7.dynamicshop.utilities.ShopUtil;
import me.sat7.dynamicshop.utilities.SoundUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public final class Buy {
    private Buy() {

    }

    // 구매
    public static void buyItemCash(Player player, String shopName, String tradeIdx, ItemStack tempIS, double priceSum, double deliverycharge, boolean infiniteStock)
    {
        Economy econ = DynamicShop.getEconomy();
        double priceBuyOld = Calc.getCurrentPrice(shopName,tradeIdx,true);
        double priceSellOld = DynaShopAPI.getSellPrice(shopName, tempIS);
        int stockOld = ShopUtil.ccShop.get().getInt(shopName+"." + tradeIdx + ".stock");

        int actualAmount = 0;

        for (int i = 0; i<tempIS.getAmount(); i++)
        {
            if(!infiniteStock && stockOld <= actualAmount+1)
            {
                break;
            }

            double price = Calc.getCurrentPrice(shopName,tradeIdx,true);

            if(priceSum + price > econ.getBalance(player)) break;

            priceSum += price;

            if(!infiniteStock)
            {
                ShopUtil.ccShop.get().set(shopName+"." + tradeIdx + ".stock",
                        ShopUtil.ccShop.get().getInt(shopName+"." + tradeIdx + ".stock") - 1);
            }

            actualAmount++;
        }

        // 실 구매 가능량이 0이다 = 돈이 없다.
        if(actualAmount <= 0)
        {
            player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("NOT_ENOUGH_MONEY").replace("{bal}",econ.format(econ.getBalance(player))));
            ShopUtil.ccShop.get().set(shopName+"." + tradeIdx + ".stock", stockOld);
            return;
        }

        // 상점 재고 부족
        if(!infiniteStock && stockOld <= actualAmount)
        {
            player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("OUT_OF_STOCK"));
            ShopUtil.ccShop.get().set(shopName+"." + tradeIdx + ".stock", stockOld);
            return;
        }

        // 실 거래부-------
        if(!isPlayerInventoryFull(player, tempIS, actualAmount)) {
            if (econ.getBalance(player) >= priceSum) {
                EconomyResponse r = DynamicShop.getEconomy().withdrawPlayer(player, priceSum);

                if(r.transactionSuccess())
                {
                    int leftAmount = actualAmount;
                    while (leftAmount>0) {
                        int giveAmount = tempIS.getType().getMaxStackSize();
                        if(giveAmount > leftAmount) giveAmount = leftAmount;

                        ItemStack iStack = new ItemStack(tempIS.getType(),giveAmount);
                        iStack.setItemMeta((ItemMeta) ShopUtil.ccShop.get().get(shopName + "." + tradeIdx + ".itemStack"));

                        player.getInventory().addItem(iStack);

                        leftAmount -= giveAmount;
                    }

                    //로그 기록
                    LogUtil.addLog(shopName,tempIS.getType().toString(),actualAmount,priceSum,"vault",player.getName());

                    player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("BUY_SUCCESS")
                            .replace("{item}", ItemsUtil.getBeautifiedName(tempIS.getType()))
                            .replace("{amount}", Integer.toString(actualAmount))
                            .replace("{price}",econ.format(r.amount))
                            .replace("{bal}",econ.format(econ.getBalance((player)))));
                    SoundUtil.playerSoundEffect(player,"buy");

                    if(deliverycharge > 0)
                    {
                        player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("DELIVERYCHARGE")+": "+deliverycharge);
                    }

                    if(ShopUtil.ccShop.get().contains(shopName+".Options.Balance"))
                    {
                        ShopUtil.addShopBalance(shopName,priceSum);
                    }

                    DynaShopAPI.openItemTradeGui(player,shopName, tradeIdx);
                    ShopUtil.ccShop.save();

                    ShopBuySellEvent event = new ShopBuySellEvent(true, priceBuyOld, Calc.getCurrentPrice(shopName,tradeIdx,true), priceSellOld, DynaShopAPI.getSellPrice(shopName, tempIS), stockOld, DynaShopAPI.getStock(shopName, tempIS), DynaShopAPI.getMedian(shopName, tempIS), shopName, tempIS, player);
                    Bukkit.getPluginManager().callEvent(event);
                } else {
                    player.sendMessage(String.format("An error occured: %s", r.errorMessage));
                }
            } else {
                    player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("NOT_ENOUGH_MONEY").replace("{bal}",econ.format(econ.getBalance(player))));

            }

        } else {
            player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("INVEN_FULL"));
        }
    }

    public static int getEmptySlots(Player p) {
        PlayerInventory inventory = p.getInventory();
        ItemStack[] cont = inventory.getContents();
        int i = 0;
        for (ItemStack item : cont)
            if (item != null && item.getType() != Material.AIR) {
                i++;
            }
        return 36 - i;
    }

    public static boolean hasSpace(PlayerInventory inventory, ItemStack item, int amount, int maxStackSize) {
        int available_space = 0;
        int i = 0;
        while (i != inventory.getContents().length) {
            ItemStack[] contents = inventory.getContents();
            ItemStack stack = contents[i];

            if (stack == null) {
                i++;
                continue;
            }

            if (stack.isSimilar(item)) {

                if (stack.getAmount() == maxStackSize) {
                    i++;
                    continue;
                }

                if (stack.getAmount() < maxStackSize) {
                    available_space += maxStackSize - stack.getAmount();

                }
            }
            i++;
        }

        return !(available_space >= amount);
    }

    public static boolean isPlayerInventoryFull(Player player, ItemStack item, int amount) {
        if (item.getMaxStackSize() == 1 && amount > getEmptySlots(player)) return true;
        if (item.getMaxStackSize() == 16) {
            if (getEmptySlots(player) == 0) {
                PlayerInventory inventory = player.getInventory();
                return hasSpace(inventory, item, amount, 16);

            } else {
                int slots_needed = -1;
                if (amount <= 16) slots_needed = 1;
                if (amount <= 32 && amount > 16) slots_needed = 2;
                if (amount > 32 && amount <= 64) slots_needed = 4;
                return !(getEmptySlots(player) >= slots_needed);
            }
        }
        if (getEmptySlots(player) == 0) {
            PlayerInventory inventory = player.getInventory();
            return hasSpace(inventory, item, amount, 64);

        } else {
            return false;
        }

    }

    // 구매 jp
    public static void buyItemJobPoint(Player player, String shopName, String tradeIdx, ItemStack tempIS, double priceSum, double deliverycharge, boolean infiniteStock)
    {
        int actualAmount = 0;
        int stockOld = ShopUtil.ccShop.get().getInt(shopName+"." + tradeIdx + ".stock");
        double priceBuyOld = Calc.getCurrentPrice(shopName,tradeIdx,true);
        double priceSellOld = DynaShopAPI.getSellPrice(shopName, tempIS);

        for (int i = 0; i<tempIS.getAmount(); i++)
        {
            if(!infiniteStock && stockOld <= actualAmount+1)
            {
                break;
            }

            double price = Calc.getCurrentPrice(shopName,tradeIdx,true);

            if(priceSum + price > JobsHook.getCurJobPoints(player)) break;

            priceSum += price;

            if(!infiniteStock)
            {
                ShopUtil.ccShop.get().set(shopName+"." + tradeIdx + ".stock",
                        ShopUtil.ccShop.get().getInt(shopName+"." + tradeIdx + ".stock") - 1);
            }

            actualAmount++;
        }

        // 실 구매 가능량이 0이다 = 돈이 없다.
        if(actualAmount <= 0)
        {
            player.sendMessage(DynamicShop.dsPrefix+ LangUtil.ccLang.get().getString("NOT_ENOUGH_POINT").replace("{bal}", DynaShopAPI.df.format(JobsHook.getCurJobPoints(player))));
            ShopUtil.ccShop.get().set(shopName+"." + tradeIdx + ".stock", stockOld);
            return;
        }

        // 상점 재고 부족
        if(!infiniteStock && stockOld <= actualAmount)
        {
            player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("OUT_OF_STOCK"));
            ShopUtil.ccShop.get().set(shopName+"." + tradeIdx + ".stock", stockOld);
            return;
        }

        // 실 거래부-------
        if (isPlayerInventoryFull(player, tempIS, actualAmount)) {
            if (JobsHook.addJobsPoint(player, priceSum * -1)) {
                if (JobsHook.getCurJobPoints(player) >= priceSum) {
                    int leftAmount = actualAmount;
                    while (leftAmount > 0) {
                        int giveAmount = tempIS.getType().getMaxStackSize();
                        if (giveAmount > leftAmount) giveAmount = leftAmount;

                        ItemStack iStack = new ItemStack(tempIS.getType(), giveAmount);
                        iStack.setItemMeta((ItemMeta) ShopUtil.ccShop.get().get(shopName + "." + tradeIdx + ".itemStack"));

                        player.getInventory().addItem(iStack);

                        leftAmount -= giveAmount;
                    }

                    //로그 기록
                    LogUtil.addLog(shopName, tempIS.getType().toString(), actualAmount, priceSum, "jobpoint", player.getName());

                    player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("BUY_SUCCESS_JP")
                            .replace("{item}", ItemsUtil.getBeautifiedName(tempIS.getType()))
                            .replace("{amount}", Integer.toString(actualAmount))
                            .replace("{price}", DynaShopAPI.df.format(priceSum))
                            .replace("{bal}", DynaShopAPI.df.format(JobsHook.getCurJobPoints((player)))));
                    SoundUtil.playerSoundEffect(player, "buy");

                    if (deliverycharge > 0) {
                        player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("DELIVERYCHARGE") + ": " + deliverycharge);
                    }

                    if (ShopUtil.ccShop.get().contains(shopName + ".Options.Balance")) {
                        ShopUtil.addShopBalance(shopName, priceSum);
                    }

                    DynaShopAPI.openItemTradeGui(player, shopName, tradeIdx);
                    ShopUtil.ccShop.save();

                    ShopBuySellEvent event = new ShopBuySellEvent(true, priceBuyOld, Calc.getCurrentPrice(shopName, tradeIdx, true), priceSellOld, DynaShopAPI.getSellPrice(shopName, tempIS), stockOld, DynaShopAPI.getStock(shopName, tempIS), DynaShopAPI.getMedian(shopName, tempIS), shopName, tempIS, player);
                    Bukkit.getPluginManager().callEvent(event);
                }
            }
        } else {
            player.sendMessage(DynamicShop.dsPrefix + LangUtil.ccLang.get().getString("INVEN_FULL"));
        }
    }
}
