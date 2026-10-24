import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Temporary shell client: normal input-service APIs, never direct system-file edits. */
public final class LayoutCtl {
    static final String PREFIX="com.lzchyi.tvkeys/com.lzchyi.tvkeys.Layouts/";
    static final String FORWARD=PREFIX+"apps_global", REVERSE=PREFIX+"apps_original";
    static Object manager, identifier;
    static Class<?> api, idClass;
    static InputDevice remote;
    static void init() throws Exception {
        Class<?> serviceManager=Class.forName("android.os.ServiceManager");
        IBinder binder=(IBinder)serviceManager.getMethod("getService",String.class).invoke(null,"input");
        api=Class.forName("android.hardware.input.IInputManager");
        manager=Class.forName("android.hardware.input.IInputManager$Stub")
            .getMethod("asInterface",IBinder.class).invoke(null,binder);
        idClass=Class.forName("android.hardware.input.InputDeviceIdentifier");
        for(int id:InputDevice.getDeviceIds()) {
            InputDevice candidate=InputDevice.getDevice(id);
            if(candidate!=null && !candidate.isVirtual() && candidate.getVendorId()==0x054c
                && candidate.getProductId()==0x0c99 && "SONY TV VRC 001".equals(candidate.getName())) {
                if(remote!=null)throw new IllegalStateException("More than one matching remote");
                remote=candidate;
            }
        }
        if(remote==null)throw new IllegalStateException("Sony remote not found");
        identifier=idClass.getConstructor(String.class,int.class,int.class)
            .newInstance(remote.getDescriptor(),remote.getVendorId(),remote.getProductId());
    }
    static Object call(String name,Object...args) throws Exception {
        Class<?>[] types=new Class<?>[args.length];
        for(int i=0;i<args.length;i++)types[i]=i==0?idClass:String.class;
        return api.getMethod(name,types).invoke(manager,args);
    }
    static String current() throws Exception {return (String)call("getCurrentKeyboardLayoutForInputDevice",identifier);}
    static String[] enabled() throws Exception {
        String[] result=(String[])call("getEnabledKeyboardLayoutsForInputDevice",identifier);
        return result==null?new String[0]:result;
    }
    static void set(String layout) throws Exception {
        if(layout==null)throw new IllegalArgumentException("Null layout");
        call("setCurrentKeyboardLayoutForInputDevice",identifier,layout);
        SystemClock.sleep(450);
        if(!layout.equals(current()))throw new IllegalStateException("Selection not retained");
    }
    static JSONObject inspect() throws Exception {
        return new JSONObject().put("pid",Process.myPid()).put("uid",Process.myUid())
            .put("device",remote.getName()).put("deviceId",remote.getId())
            .put("descriptor",remote.getDescriptor()).put("vendor",remote.getVendorId())
            .put("product",remote.getProductId()).put("current",current()==null?JSONObject.NULL:current())
            .put("enabled",new JSONArray(Arrays.asList(enabled())));
    }
    static void write(File file,JSONObject value) throws Exception {
        File temporary=new File(file.getPath()+".new");
        Files.write(temporary.toPath(),value.toString(2).getBytes(StandardCharsets.UTF_8));
        if(!temporary.renameTo(file))throw new IllegalStateException("State rename failed");
    }
    static JSONObject read(File file) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    }
    static void restore(JSONObject before) throws Exception {
        // Overlays combine into the live map. Explicitly put Apps back before removing selections.
        set(REVERSE);
        String previous=before.isNull("current")?null:before.getString("current");
        JSONArray original=before.getJSONArray("enabled");
        Set<String> keep=new HashSet<>();
        for(int i=0;i<original.length();i++)keep.add(original.getString(i));
        if(previous!=null)set(previous);
        for(String layout:enabled())if(!keep.contains(layout))
            call("removeKeyboardLayoutForInputDevice",identifier,layout);
        if(previous!=null && !Objects.equals(previous,current()))set(previous);
        if(!Objects.equals(previous,current()) || !keep.equals(new HashSet<>(Arrays.asList(enabled()))))
            throw new IllegalStateException("Restore selection mismatch: "+inspect());
        SystemClock.sleep(450);
    }
    public static void main(String[] args) throws Exception {
        if(args.length==0)throw new IllegalArgumentException("Command required");
        if("selftest".equals(args[0])) {
            File state=new File(args[1]);
            write(state,new JSONObject().put("pid",Process.myPid()).put("phase","waiting"));
            SystemClock.sleep(6000);
            write(state,new JSONObject().put("pid",Process.myPid()).put("phase","survived-disconnect"));
            return;
        }
        init();
        if("inspect".equals(args[0])) {System.out.println(inspect().toString(2));return;}
        if("layouts".equals(args[0])) {
            Object[] layouts=(Object[])api.getMethod("getKeyboardLayouts").invoke(manager);
            for(Object layout:layouts) {
                String descriptor=(String)layout.getClass().getMethod("getDescriptor").invoke(layout);
                if(descriptor.startsWith(PREFIX))System.out.println(descriptor);
            }
            return;
        }
        if("restore".equals(args[0])) {restore(read(new File(args[1])).getJSONObject("before"));System.out.println("RESTORED "+inspect());return;}
        if(!"trial".equals(args[0]))throw new IllegalArgumentException("Unknown command");
        String target=args[1];
        if(!target.equals(FORWARD)&&!target.equals(REVERSE))throw new IllegalArgumentException("Not a trial layout");
        int seconds=Integer.parseInt(args[3]);
        if(seconds<5||seconds>180)throw new IllegalArgumentException("Trial must be 5..180 seconds");
        File state=new File(args[2]), commit=new File(args[2]+".commit"), cancel=new File(args[2]+".cancel");
        if(state.exists()||commit.exists()||cancel.exists())throw new IllegalStateException("Trial state already exists");
        JSONObject before=inspect();
        JSONArray layouts=before.getJSONArray("enabled");
        for(int i=0;i<layouts.length();i++)if(layouts.getString(i).startsWith(PREFIX))
            throw new IllegalStateException("Trial layout already selected; restore first");
        JSONObject status=new JSONObject().put("pid",Process.myPid()).put("before",before)
            .put("target",target).put("seconds",seconds).put("phase","arming");
        write(state,status);
        boolean committed=false;
        try {
            set(target);
            long deadline=SystemClock.elapsedRealtime()+seconds*1000L;
            status.put("phase","active").put("deadlineElapsedMs",deadline);write(state,status);
            while(SystemClock.elapsedRealtime()<deadline && !cancel.exists()) {
                if(commit.exists() && target.equals(FORWARD)) {committed=true;break;}
                SystemClock.sleep(200);
            }
        } finally {
            if(!committed) {
                try {
                    restore(before);status.put("phase","restored").put("after",inspect());write(state,status);
                } catch(Exception error) {
                    status.put("phase","restore-failed").put("error",error.toString());write(state,status);
                    throw error;
                }
            } else {status.put("phase","committed").put("after",inspect());write(state,status);}
        }
        System.out.println(status.toString(2));
    }
}
