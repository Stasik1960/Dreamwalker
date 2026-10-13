package qa.attach;
import com.sun.tools.attach.VirtualMachine;
public final class AttachClientProbe {
    public static void main(String[] args) throws Exception {
        if(!args[0].equals("128096"))throw new IllegalArgumentException("Only the specified client-2 PID may be attached");
        ProcessHandle process=ProcessHandle.of(128096).orElseThrow();
        if(!process.isAlive())throw new IllegalStateException("Target has stopped");
        // Windows does not expose commandLine through ProcessHandle here. The launcher
        // is restricted to the externally CIM-verified PID and the agent verifies QA classes.
        String command=process.info().commandLine().orElse("");
        if(!command.isEmpty()&&(!command.contains("runtime-client-flatgallery-v122-client-2")||!command.contains("KnotClient")))throw new IllegalStateException("Target is not the expected QA client");
        VirtualMachine vm=VirtualMachine.attach(args[0]);
        try {vm.loadAgent(args[1],args[2]);}finally{vm.detach();}
    }
}
