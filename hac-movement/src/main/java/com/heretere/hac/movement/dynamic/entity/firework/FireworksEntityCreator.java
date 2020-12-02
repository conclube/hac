package com.heretere.hac.movement.dynamic.entity.firework;

import com.heretere.hac.api.API;
import com.heretere.hac.api.util.reflections.types.WrappedConstructor;
import com.heretere.hac.api.util.reflections.types.WrappedField;
import com.heretere.hac.api.util.reflections.types.WrappedMethod;
import com.heretere.hac.api.util.reflections.version.ServerVersion;
import com.heretere.hac.core.util.reflections.helper.EntityHelper;
import com.heretere.hac.core.util.reflections.helper.WorldHelper;
import com.heretere.hac.movement.Movement;
import com.heretere.hac.movement.dynamic.AbstractDynamicClassCreator;
import com.heretere.hac.movement.dynamic.entity.Piston;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.commons.lang.NotImplementedException;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class FireworksEntityCreator extends AbstractDynamicClassCreator implements Listener {
    private WrappedConstructor entityFireworks;
    private WrappedField handle;
    private WrappedMethod addEntity;

    public FireworksEntityCreator(Movement movement) {
        super("EntityFireworks", movement);

        try {
            this.handle = API.INSTANCE.getReflections().getCBClass("inventory.CraftItemStack")
                    .getFieldByName("handle");
            this.addEntity = API.INSTANCE.getReflections().getNMSClass("World").getMethod("addEntity", API.INSTANCE.getReflections().getNMSClass("Entity").getRaw());
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            super.getMovement().getLog().reportFatalError(e);
        }
    }

    @Override
    public void load() {

        try {
            String methodName = null;
            switch (API.INSTANCE.getReflections().getVersion()) {
                case EIGHT_R1:
                case EIGHT_R2:
                case EIGHT_R3:
                case NINE_R1:
                case NINE_R2:
                case TEN_R1:
                    super.getMovement().getLog().info("Server version less than " + ServerVersion.ELEVEN_R1.getPackageName() + " skipping.");
                    break;
                case ELEVEN_R1:
                    methodName = "A_";
                    break;
                case TWELVE_R1:
                    methodName = "B_";
                    break;
                case THIRTEEN_R1:
                case THIRTEEN_R2:
                case FOURTEEN_R1:
                case FIFTEEN_R1:
                case SIXTEEN_R1:
                case SIXTEEN_R2:
                    methodName = "tick";
                    break;
                default:
                    throw new NotImplementedException();
            }

            if (methodName == null) {
                return;
            }

            Class<?> fireworks = API.INSTANCE.getReflections().getNMSClass("EntityFireworks").getRaw();

            this.entityFireworks = API.INSTANCE.getReflections().getClass(new ByteBuddy()
                    .subclass(fireworks)
                    .name("HACEntityFireworks")
                    .method(ElementMatchers.isDeclaredBy(fireworks)
                            .and(ElementMatchers.named(methodName)))
                    .intercept(MethodDelegation.to(new EntityFireworksInterceptor())
                            .andThen(SuperMethodCall.INSTANCE))
                    .make()
                    .load(this.getClass().getClassLoader())
                    .getLoaded())
                    .getConstructor(API.INSTANCE.getReflections().getNMSClass("World").getRaw(),
                            API.INSTANCE.getReflections().getNMSClass("ItemStack").getRaw(),
                            API.INSTANCE.getReflections().getNMSClass("EntityLiving").getRaw());

            Bukkit.getPluginManager().registerEvents(this, super.getMovement());

            //TODO: REMOVE
            Bukkit.getPluginManager().registerEvents(new Piston(), super.getMovement());
        } catch (Exception e) {
            super.getMovement().getLog().reportFatalError(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent e) {

        if (e.getItem() == null || !e.getItem().getType().name().contains("FIREWORK") || !e.getAction().equals(Action.RIGHT_CLICK_AIR) || !API.INSTANCE.getReflections().getHelpers().getHelper(EntityHelper.class).isElytraFlying(e.getPlayer())) {
            return;
        }

        if (!e.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            e.getItem().setAmount(e.getItem().getAmount() - 1);
        }

        try {
            WorldHelper worldHelper = API.INSTANCE.getReflections().getHelpers().getHelper(WorldHelper.class);
            EntityHelper helper = API.INSTANCE.getReflections().getHelpers().getHelper(EntityHelper.class);
            Object human = helper.getNMSEntity(e.getPlayer());
            Object world = worldHelper.getNMSWorld(e.getPlayer().getWorld());
            Object itemStack = this.handle.get(Object.class, e.getItem());

            this.addEntity.invoke(Object.class, world, this.entityFireworks.newInstance(Object.class, world, itemStack, human));
        } catch (Exception ex) {
            super.getMovement().getLog().severe(ex);
        }

        e.setCancelled(true);
    }

    @Override
    public void unload() {
        //Unused
    }
}