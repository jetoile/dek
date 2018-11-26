package fr.layer4.dek;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

public class SpringUtils {

    private SpringUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> T getTargetObject(Object proxy) throws Exception {
        while (AopUtils.isJdkDynamicProxy(proxy)) {
            return getTargetObject(((Advised) proxy).getTargetSource().getTarget());
        }
        return (T) proxy;
    }
}