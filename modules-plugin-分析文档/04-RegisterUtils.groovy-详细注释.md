# RegisterUtils.groovy - 详细注释版

```groovy
// 包声明
package com.beyondxia.plugin.utils

// 导入 PATransform：访问静态变量
import com.beyondxia.plugin.PATransform

// 导入常量类
import com.beyondxia.plugin.constant.ClassConstant

// 导入 ASM 相关类（用于字节码操作）
// ASM 是一个轻量级的 Java 字节码操作框架
import jdk.internal.org.objectweb.asm.ClassReader    // 读取类文件
import jdk.internal.org.objectweb.asm.ClassVisitor   // 访问类结构
import jdk.internal.org.objectweb.asm.ClassWriter    // 写入类文件
import jdk.internal.org.objectweb.asm.MethodVisitor  // 访问方法
import jdk.internal.org.objectweb.asm.Opcodes       // 字节码操作码常量

// 导入 Apache Commons IO
import org.apache.commons.io.IOUtils

// 导入 JAR 文件操作类
import java.util.jar.JarEntry      // JAR 条目
import java.util.jar.JarFile       // JAR 文件
import java.util.jar.JarOutputStream // JAR 输出流
import java.util.zip.ZipEntry      // ZIP 条目

/**
 * RegisterUtils 类
 * 
 * 负责自动注册功能的实现，使用 ASM 进行字节码注入
 * 
 * 主要功能：
 * 1. 扫描 JAR 文件和目录，收集需要注册的服务类
 * 2. 查找 ServiceHelper.class 所在的位置
 * 3. 使用 ASM 向 ServiceHelper.pluginRegisterClassName() 方法注入注册代码
 */
class RegisterUtils {

    // 日志标签
    static TAG = "RegisterUtils-Modules -> "

    /**
     * 扫描 JAR 文件
     * 
     * 功能：
     * 1. 查找 ServiceHelper.class 所在的 jar 文件
     * 2. 收集需要注册的服务类（包名以 SERVICE_ROOT_PACKAGE 开头的类）
     * 
     * @param src 源 JAR 文件
     * @param desc 目标 JAR 文件（输出位置）
     */
    static void scanJar(File src, File desc) {

        // 打开 JAR 文件
        JarFile jarFile = new JarFile(src)
        
        // 获取 JAR 文件中的所有条目
        Enumeration<JarEntry> entries = jarFile.entries()

        // 遍历所有条目
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement()
            
            // 规范化路径（统一使用 / 作为分隔符）
            String jarEntryName = jarEntry.getName().replace("\\", "/")
            
            // 记录需要修改的初始化 class 类所在的 jar 包
            // ServiceHelper.class 是注入注册代码的目标类
            if (ClassConstant.INIT_CLASS_FILE_NAME == jarEntryName) {
//                println TAG + "fileContainsInitClass: " + desc
                // 保存 ServiceHelper.class 所在的 jar 文件路径
                PATransform.fileContainsInitClass = desc
            }
            
            // 记录注册的服务类
            // 只处理以 SERVICE_ROOT_PACKAGE 开头的 .class 文件
            // SERVICE_ROOT_PACKAGE = "com/beyondxia/modules_interface_library"
            if (jarEntryName.startsWith(ClassConstant.SERVICE_ROOT_PACKAGE) && jarEntryName.endsWith(".class")) {
                // 将路径转换为类名
                // 例如：com/beyondxia/modules_interface_library/XXX.class
                // 转换为：com.beyondxia.modules_interface_library.XXX
                def registerItem = jarEntryName
                        .replace("/", ".")                    // 路径分隔符转换为包分隔符
                        .substring(0, jarEntryName.length() - 6)  // 去掉 ".class" 后缀（6个字符）
                
                // 添加到注册列表
                PATransform.registerList.add(registerItem)
            }
        }
        
        // 关闭 JAR 文件
        jarFile.close()
    }


    /**
     * 判断是否应该处理预编译的 DEX JAR
     * 
     * 排除系统库，只处理业务代码
     * 
     * @param path JAR 文件路径
     * @return true 表示应该处理，false 表示跳过
     */
    static boolean shouldProcessPreDexJar(String path) {
        // 排除 Android 支持库和系统库
        return !path.contains("com.android.support") && !path.contains("/android/m2repository")
    }


    /**
     * 扫描目录
     * 
     * 递归扫描目录中的所有 .class 文件，收集需要注册的服务类
     * 
     * @param directoryFile 要扫描的目录
     */
    static void scanDirectory(File directoryFile) {
        // 规范化根路径
        def root = directoryFile.absolutePath.replace("\\", "/")
        if (!root.endsWith("/")) {
            root = "$root/"
        }

        // 递归遍历目录中的所有文件
        directoryFile.eachFileRecurse { f ->

            // 规范化文件路径
            def absolutePath = f.absolutePath.replace("\\", "/")

            // 判断是否是目标类文件
            // 条件：
            // 1. 是文件（不是目录）
            // 2. 路径包含 SERVICE_ROOT_PACKAGE
            // 3. 文件名以 .class 结尾
            if (f.isFile()
                    && absolutePath.contains(ClassConstant.SERVICE_ROOT_PACKAGE)
                    && f.name.endsWith("class")) {


                // 将绝对路径转换为相对路径
                def registerItem = absolutePath
                        .replace(root, "")

                // 转换为类名
                // 例如：com/beyondxia/modules_interface_library/XXX.class
                // 转换为：com.beyondxia.modules_interface_library.XXX
                registerItem = registerItem
                        .replace("/", ".")                    // 路径分隔符转换为包分隔符
                        .substring(0, registerItem.length() - 6)  // 去掉 ".class" 后缀

                // 添加到注册列表
                PATransform.registerList.add(registerItem)
            }


        }
    }


    /**
     * 向初始化类注入注册代码
     * 
     * 这是自动注册功能的核心方法
     * 使用 ASM 修改 ServiceHelper.class，在 pluginRegisterClassName() 方法中注入注册调用
     */
    static void insertCodeToInitClass() {
//        println("$TAG insertCodeToInitClass : ~~~~~start~~~~~~")
//        println("$TAG fileContainsInitClass : ${PATransform.fileContainsInitClass}")
        
        // 如果找到了 ServiceHelper.class 所在的文件
        if (PATransform.fileContainsInitClass != null) {
            // 执行实际的字节码修改
            realHandle(PATransform.fileContainsInitClass)
        }
    }

    /**
     * 实际处理 JAR 文件，修改 ServiceHelper.class
     * 
     * 流程：
     * 1. 创建一个临时 JAR 文件（.opt 后缀）
     * 2. 遍历原 JAR 文件的所有条目
     * 3. 如果是 ServiceHelper.class，使用 ASM 修改它
     * 4. 其他文件直接复制
     * 5. 用临时文件替换原文件
     * 
     * @param jarFile 包含 ServiceHelper.class 的 JAR 文件
     */
    static void realHandle(File jarFile) {

        // 创建临时文件（在原文件同目录下，添加 .opt 后缀）
        def optJar = new File(jarFile.parent, "${jarFile.name}.opt")
        
        // 如果临时文件已存在，先删除
        if (optJar.exists()) {
            optJar.delete()
        }

        // 创建 JAR 输出流
        def jarOutputStream = new JarOutputStream(new FileOutputStream(optJar))

        // 打开原 JAR 文件
        def file = new JarFile(jarFile)
        def entries = file.entries()

        // 遍历 JAR 文件中的所有条目
        while (entries.hasMoreElements()) {
            def jarEntry = entries.nextElement()
            def entryName = jarEntry.name
            
            // 创建 ZIP 条目（JAR 本质上是 ZIP 文件）
            def zipEntry = new ZipEntry(entryName)
            def inputStream = file.getInputStream(zipEntry)
            
            // 将条目添加到输出 JAR
            jarOutputStream.putNextEntry(zipEntry)
            
//            println("$TAG entryName : $entryName")
            
            // 如果是 ServiceHelper.class，需要修改它
            if (entryName == ClassConstant.INIT_CLASS_FILE_NAME) {
                // 使用 ASM 修改类文件，获取修改后的字节码
                def bytes = getNewInitClassByte(inputStream)
                jarOutputStream.write(bytes)
            } else {
                // 其他文件直接复制
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            
            // 关闭输入流和当前条目
            inputStream.close()
            jarOutputStream.closeEntry()
        }

        // 关闭输出流和 JAR 文件
        jarOutputStream.close()
        file.close()

        // 删除原文件
        if (jarFile.exists()) {
            jarFile.delete()
        }
        
        // 用临时文件替换原文件
        optJar.renameTo(jarFile)
    }

    /**
     * 获取修改后的初始化类字节码
     * 
     * 使用 ASM 修改 ServiceHelper.class：
     * 1. 读取原始类文件
     * 2. 使用自定义的 ClassVisitor 访问类结构
     * 3. 在 pluginRegisterClassName() 方法返回前注入注册代码
     * 4. 生成新的字节码
     * 
     * @param inputStream ServiceHelper.class 的输入流
     * @return 修改后的字节码
     */
    private static byte[] getNewInitClassByte(InputStream inputStream) {
        // 创建 ClassReader：读取类文件
        def cr = new ClassReader(inputStream)
        
        // 创建 ClassWriter：写入类文件
        // 参数 0 表示自动计算栈帧和局部变量表
        def cw = new ClassWriter(cr, 0)

        // 创建自定义的 ClassVisitor
        // 它会拦截对 pluginRegisterClassName() 方法的访问
        def cv = new InitClassVisitor(Opcodes.ASM5, cw)
        
        // 访问类文件，EXPAND_FRAMES 表示展开栈帧
        cr.accept(cv, ClassReader.EXPAND_FRAMES)

        // 返回修改后的字节码
        return cw.toByteArray()
    }

    /**
     * InitClassVisitor 类
     * 
     * 自定义的 ClassVisitor，用于访问 ServiceHelper 类
     * 主要功能：拦截 pluginRegisterClassName() 方法的访问
     */
    static class InitClassVisitor extends ClassVisitor {

        /**
         * 构造函数
         * 
         * @param i ASM API 版本（ASM5）
         * @param classVisitor 下一个访问者（链式访问）
         */
        InitClassVisitor(int i, ClassVisitor classVisitor) {
            super(i, classVisitor)
        }

        /**
         * 访问方法
         * 
         * 当访问到一个方法时，这个方法会被调用
         * 
         * @param access 方法访问标志（public、static 等）
         * @param name 方法名
         * @param desc 方法描述符（参数和返回值类型）
         * @param signature 方法签名（泛型信息）
         * @param exceptions 方法抛出的异常
         * @return MethodVisitor，用于访问方法体
         */
        MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // 获取默认的 MethodVisitor
            def visitMethod = super.visitMethod(access, name, desc, signature, exceptions)

//            println("$TAG visitMethod : $name")
            
            // 如果是 pluginRegisterClassName() 方法
            if (name == ClassConstant.SERVICE_INIT_METHOD) {
                // 使用自定义的 MethodVisitor 包装它
                // RouteInitMethodVisitor 会在方法返回前注入注册代码
                visitMethod = new RouteInitMethodVisitor(Opcodes.ASM5, visitMethod)
            }
            
            return visitMethod
        }

    }

    /**
     * RouteInitMethodVisitor 类
     * 
     * 自定义的 MethodVisitor，用于访问 pluginRegisterClassName() 方法
     * 主要功能：在方法返回前注入注册代码
     */
    static class RouteInitMethodVisitor extends MethodVisitor {


        /**
         * 构造函数
         * 
         * @param i ASM API 版本
         * @param methodVisitor 下一个访问者
         */
        RouteInitMethodVisitor(int i, MethodVisitor methodVisitor) {
            super(i, methodVisitor)
        }

        /**
         * 访问指令
         * 
         * 当访问到一条指令时，这个方法会被调用
         * 
         * @param opcode 操作码（如 RETURN、IRETURN 等）
         */
        @Override
        void visitInsn(int opcode) {
            // 如果是返回指令（RETURN、IRETURN、LRETURN、FRETURN、DRETURN、ARETURN）
            // 这些指令表示方法即将返回
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                // 遍历所有需要注册的类名
                PATransform.registerList.forEach { className ->
                    // 确保类名使用点号分隔（而不是斜杠）
                    def name = className.replace("/", ".")
//                    println("$TAG visitInsn name : $name")
                    
                    // 注入字节码：将类名字符串压入栈
                    // 相当于：String className = "com.xxx.XXX";
                    mv.visitLdcInsn(name)
                    
                    // 注入字节码：调用静态方法
                    // 相当于：ServiceHelper.pluginRegister(className);
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,                    // 调用静态方法
                            ClassConstant.INIT_CLASS_NAME,          // 类名：ServiceHelper
                            ClassConstant.SERVICE_DO_REGISTER_METHOD, // 方法名：pluginRegister
                            "(Ljava/lang/String;)V",                 // 方法描述符：参数 String，返回 void
                            false)                                    // 不是接口方法
                }
            }
            
            // 调用父类方法，继续处理原指令
            super.visitInsn(opcode)
        }

        /**
         * 访问方法的最大栈和局部变量表大小
         * 
         * 由于我们注入了代码，需要增加栈大小
         * 
         * @param maxStack 最大栈大小
         * @param maxLocals 局部变量表大小
         */
        @Override
        void visitMaxs(int maxStack, int maxLocals) {
            // 增加栈大小（+4 是为了容纳注入的代码）
            super.visitMaxs(maxStack + 4, maxLocals)
        }
    }


}
```

## 工作原理详解

### 1. 自动注册流程

```
编译时扫描
  ↓
收集需要注册的类名（registerList）
  ↓
查找 ServiceHelper.class 所在位置
  ↓
使用 ASM 修改 ServiceHelper.class
  ↓
在 pluginRegisterClassName() 方法中注入注册代码
  ↓
运行时自动注册所有服务
```

### 2. ASM 字节码注入原理

**原始代码（Java）：**
```java
public static void pluginRegisterClassName() {
    // 空方法
}
```

**注入后的代码（Java 等价）：**
```java
public static void pluginRegisterClassName() {
    ServiceHelper.pluginRegister("com.xxx.Service1");
    ServiceHelper.pluginRegister("com.xxx.Service2");
    // ... 所有收集到的服务类
}
```

**ASM 注入的字节码：**
```
LDC "com.xxx.Service1"           // 将字符串压入栈
INVOKESTATIC ServiceHelper.pluginRegister(Ljava/lang/String;)V  // 调用静态方法
LDC "com.xxx.Service2"
INVOKESTATIC ServiceHelper.pluginRegister(Ljava/lang/String;)V
// ...
RETURN                            // 原方法的返回指令
```

### 3. 为什么在返回指令前注入？

在 `visitInsn(RETURN)` 时注入代码，可以确保：
- 注入的代码在方法返回前执行
- 不会影响原方法的逻辑
- 即使原方法有返回值，也能正确注入

### 4. 为什么需要增加栈大小？

注入的代码会使用栈：
- `visitLdcInsn()` 将字符串压入栈（占用 1 个栈槽）
- `visitMethodInsn()` 调用方法时可能使用栈

增加栈大小可以避免 `StackOverflowError`。

### 5. 扫描策略

- **JAR 文件**：遍历 JAR 条目，查找目标类
- **目录**：递归遍历目录，查找目标类文件
- **过滤条件**：包名以 `com/beyondxia/modules_interface_library` 开头

### 6. 为什么使用临时文件？

1. **安全性**：如果修改失败，原文件不会被破坏
2. **原子性**：要么全部成功，要么全部失败
3. **兼容性**：某些系统不允许直接修改正在使用的文件
