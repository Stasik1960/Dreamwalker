package dev.dreamwalker.bloodbornedw.visual;

import java.lang.reflect.Field;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publishes only the session belonging to the model set actually uploaded. */
public final class NativeSessionPublication {
    private static final Logger LOG=LoggerFactory.getLogger("dw-native-session");
    private static final Class<?> PHYSICAL;
    private static final Field ACTIVE, LOADING, GETTER;
    static {
        try {
            Class<?> owner=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridClient");
            Class<?> session=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridClient$Session");
            PHYSICAL=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridClient$PhysicalModel");
            ACTIVE=owner.getDeclaredField("session"); LOADING=PHYSICAL.getDeclaredField("loading"); GETTER=session.getDeclaredField("textureGetter");
            ACTIVE.setAccessible(true); LOADING.setAccessible(true); GETTER.setAccessible(true);
            if(!java.lang.reflect.Modifier.isVolatile(ACTIVE.getModifiers()))throw new IllegalStateException("Native session field is not volatile");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot access native session lifecycle",failure); }
    }
    private NativeSessionPublication() {}
    public static void prepare(Object pending) {
        if(pending==null)throw new IllegalStateException("Native pending session is null");
        LOG.info("Native session prepared: {} (not yet published)",identity(pending));
    }
    public static void publish(Map<?,?> models) {
        try {
            Object selected=null;
            for(Object model:models.values()) {
                if(!PHYSICAL.isInstance(model))continue;
                Object loading=LOADING.get(model);
                if(loading==null)throw new IllegalStateException("Native physical model has no loading session");
                if(selected!=null && selected!=loading)throw new IllegalStateException("Applied native models contain multiple sessions");
                selected=loading;
            }
            if(selected==null) {
                if(ACTIVE.get(null)!=null)throw new IllegalStateException("Applied models have no native physical session");
                return; // Native catalog is absent: its model plugin did not register.
            }
            if(GETTER.get(selected)==null)throw new IllegalStateException("Applied native session has no atlas getter");
            ACTIVE.set(null,selected); // Field is volatile: one publication after successful upload.
            LOG.info("Native session committed: {} (atlas getter ready)",identity(selected));
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot publish native session",failure); }
    }
    private static String identity(Object value) { return Integer.toHexString(System.identityHashCode(value)); }
}
