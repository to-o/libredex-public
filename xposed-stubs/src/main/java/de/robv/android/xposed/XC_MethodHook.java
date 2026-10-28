package de.robv.android.xposed;

public class XC_MethodHook {
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object result;
        public java.lang.reflect.Member method;

        public void setResult(Object result) {
            this.result = result;
        }

        // The runtime Xposed API only has setResult(Object). Primitive overloads
        // compile to descriptors absent in LSPosed/Vector and fail at hook time.
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class Unhook {
    }
}
