// 源码文件为 UTF-8；中文 Windows 上 javac 默认按 GBK 解析会报大量"不可映射字符"，
// 这里对所有子模块统一声明源码编码，保证命令行 gradlew 构建与 IDE 构建行为一致
import org.gradle.api.tasks.compile.JavaCompile

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
