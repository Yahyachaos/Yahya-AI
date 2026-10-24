package de.yahya.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import java.util.List;
import java.util.Locale;

public class DeviceBridge {
    private final Context c;
    private final CelineToolCortexG21 toolCortex;
    private final CelinePermissionPolicyG22 permissionPolicy;
    private final CelinePermissionedToolRegistryG22 permissionedTools;

    public DeviceBridge(Context c) {
        this.c = c;
        this.toolCortex = new CelineToolCortexG21(new CelineAndroidToolBackend(this));
        this.permissionPolicy = new CelinePermissionPolicyG22();
        this.permissionedTools = new CelinePermissionedToolRegistryG22(toolCortex, permissionPolicy);
    }

    /** App-bound registry for brain/planner execution. G2.2 permissions are enforced here. */
    public CelineToolRegistry toolRegistry() { return permissionedTools; }

    /** Concrete permissioned view for future planner/authorization integration. */
    public CelinePermissionedToolRegistryG22 permissionedToolRegistry() { return permissionedTools; }

    /** Central app-owned permission policy. */
    public CelinePermissionPolicy permissionPolicy() { return permissionPolicy; }

    /** Raw G2.1 typed executor retained for focused diagnostics/contracts only; not planner execution. */
    public CelineToolCortexG21 typedToolCortex() { return toolCortex; }

    public String status() {
        ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi);
        StatFs sf = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long total = sf.getBlockCountLong()*sf.getBlockSizeLong(); long free = sf.getAvailableBlocksLong()*sf.getBlockSizeLong();
        int cores = Runtime.getRuntime().availableProcessors();
        return "RAM frei: "+fmt(mi.availMem)+" / "+fmt(mi.totalMem)+
                "\nSpeicher frei: "+fmt(free)+" / "+fmt(total)+
                "\nCPU-Kerne: "+cores+
                "\nBedienungshilfe: "+(PrincessAccessibilityService.isRunning()?"aktiv":"nicht aktiv")+
                "\nBenachrichtigungen: "+(PrincessNotificationService.isRunning()?"aktiv":"nicht aktiv");
    }

    public boolean openApp(String query) {
        String cleaned=query.replaceAll("(?i)\\s+und\\s+.*$","").trim();
        PackageManager pm = c.getPackageManager(); List<ApplicationInfo> apps = pm.getInstalledApplications(0); String q = cleaned.toLowerCase(Locale.GERMANY);
        if(q.equals("einstellungen")||q.equals("settings")){c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
        for (ApplicationInfo ai : apps) { String label = String.valueOf(pm.getApplicationLabel(ai)).toLowerCase(Locale.GERMANY); if (label.equals(q) || label.contains(q) || q.contains(label)) {Intent i = pm.getLaunchIntentForPackage(ai.packageName);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);return true;}}}
        return false;
    }
    public void openAccessibilitySettings(){c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public void openNotificationSettings(){c.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public void openAllFilesSettings(){if(android.os.Build.VERSION.SDK_INT>=30){Intent i=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+c.getPackageName()));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}else c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private String fmt(long b){double g=b/1024d/1024d/1024d;if(g>=1)return String.format(Locale.GERMANY,"%.1f GB",g);return String.format(Locale.GERMANY,"%.0f MB",b/1024d/1024d);}
}
