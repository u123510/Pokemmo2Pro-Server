package mmo;

import java.io.File;

public class Util {
    static {
        String basePath = System.getProperty("user.dir");
        // 构建DLL的绝对路径
        String dllPath = basePath + File.separator + "resource" + File.separator + "mmo64" + File.separator + "mmo_server.dll";
        // 加载DLL
        System.load(dllPath);
    }

    // 本地方法声明
    public static native long sigHash(int i);
}
