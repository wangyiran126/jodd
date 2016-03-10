package jodd.util;

import org.junit.Test;

/**
 * Created by wangyiran on 29/2/2016.
 */
public class TestMethod {
    @Test
    public void testDeclareClass() throws NoSuchMethodException {
       Class declareClass = TestMethod.class.getMethod("testDeclareClass") .getDeclaringClass();
        System.out.printf(String.valueOf(declareClass));
    }
}
