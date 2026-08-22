package de.yahya.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class PrincessAccessibilityService extends AccessibilityService {
    private static PrincessAccessibilityService instance;

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }

    public static boolean isRunning() { return instance != null; }
    public static boolean goHome() { return instance != null && instance.performGlobalAction(GLOBAL_ACTION_HOME); }
    public static boolean goBack() { return instance != null && instance.performGlobalAction(GLOBAL_ACTION_BACK); }
    public static boolean openRecents() { return instance != null && instance.performGlobalAction(GLOBAL_ACTION_RECENTS); }

    public static boolean clickText(String text) {
        if (instance == null || text == null || text.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo found = findNode(root, text.toLowerCase(Locale.GERMANY));
        if (found == null) return false;
        AccessibilityNodeInfo cur = found;
        while (cur != null) {
            if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            cur = cur.getParent();
        }
        return false;
    }

    public static boolean setText(String text) {
        if (instance == null) return false;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo field = findEditable(root);
        if (field == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public static String screenSummary() {
        if (instance == null) return "Bedienungshilfe nicht aktiv.";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "Kein aktiver Bildschirm erkannt.";
        StringBuilder b = new StringBuilder();
        collect(root,b,0);
        if (b.length() > 5000) return b.substring(0,5000);
        return b.toString();
    }

    private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo n, String q) {
        if (n == null) return null;
        CharSequence t=n.getText(), d=n.getContentDescription();
        String id = n.getViewIdResourceName();
        if (contains(t,q) || contains(d,q) || (id!=null && id.toLowerCase(Locale.GERMANY).contains(q))) return n;
        for(int i=0;i<n.getChildCount();i++) { AccessibilityNodeInfo r=findNode(n.getChild(i),q); if(r!=null)return r; }
        return null;
    }

    private static boolean contains(CharSequence s,String q){return s!=null && s.toString().toLowerCase(Locale.GERMANY).contains(q);}

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n){
        if(n==null)return null;
        if(n.isEditable())return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=findEditable(n.getChild(i));if(r!=null)return r;}
        return null;
    }

    private static void collect(AccessibilityNodeInfo n,StringBuilder b,int depth){
        if(n==null||depth>12)return;
        CharSequence t=n.getText(),d=n.getContentDescription();
        if(t!=null && t.length()>0)b.append(t).append(" | ");
        if(d!=null && d.length()>0 && (t==null || !d.toString().equals(t.toString())))b.append(d).append(" | ");
        for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),b,depth+1);
    }

    public static boolean tap(float x, float y) {
        if (instance == null || android.os.Build.VERSION.SDK_INT < 24) return false;
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 80);
        GestureDescription.Builder b = new GestureDescription.Builder(); b.addStroke(stroke);
        return instance.dispatchGesture(b.build(), null, null);
    }
}
