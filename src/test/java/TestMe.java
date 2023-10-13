import org.junit.Test;

import java.io.File;

public class TestMe {

    @Test
    public void test1() {
        // https://openapi.lddgo.net/base/gtool/api/v1/GetIp
        final File dir = new File("C:\\");
        for (String s : dir.list()) {
            System.out.println(s);
        }
    }
}
