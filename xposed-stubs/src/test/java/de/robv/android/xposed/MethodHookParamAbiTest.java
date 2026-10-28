package de.robv.android.xposed;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** The actual LSPosed/Vector runtime supports only the Object descriptor. */
public class MethodHookParamAbiTest {
    @Test public void setResultMatchesRuntimeDescriptor() throws Exception {
        Class<?> param = XC_MethodHook.MethodHookParam.class;
        assertNotNull(param.getMethod("setResult", Object.class));
        for (Method method : param.getDeclaredMethods()) {
            if (method.getName().equals("setResult")) {
                assertArrayEquals(new Class<?>[]{Object.class}, method.getParameterTypes());
            }
        }
    }
}
