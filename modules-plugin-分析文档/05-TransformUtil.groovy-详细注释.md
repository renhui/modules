# TransformUtil.groovy - 详细注释版

```groovy
// 包声明
package com.beyondxia.plugin.utils

// 导入注解类
import com.beyondxia.annotation.ExportService

// 导入 Javassist 相关类
// Javassist 是一个字节码操作库，API 比 ASM 更简单
import javassist.ClassPool    // 类池：管理类的加载和操作
import javassist.CtClass      // 编译时类：表示一个类文件

// 导入 Apache Commons IO
import org.apache.commons.io.FileUtils  // 文件操作工具
import org.apache.commons.io.IOUtils   // IO 工具

// 导入 Gradle Project
import org.gradle.api.Project

// 导入 JAR 文件操作类
import java.util.jar.JarEntry   // JAR 条目
import java.util.jar.JarFile    // JAR 文件
import java.util.jar.JarOutputStream // JAR 输出流
import java.util.zip.ZipEntry   // ZIP 条目

/**
 * TransformUtil 类
 * 
 * 使用 Javassist 处理带有 @ExportService 注解的类
 * 主要功能：修改类的继承关系，让实现类继承对应的 Service 接口
 * 
 * 创建时间：2018/8/29 18:50
 * 作者：ChenWei
 */
class TransformUtil {

    // Javassist 类池（静态变量，全局共享）
    // ClassPool 用于加载和管理类文件
    static ClassPool mPool = ClassPool.getDefault()

    /**
     * 添加核心类路径到 ClassPool
     * 
     * 为了让 Javassist 能够正确加载和修改类，需要添加以下类路径：
     * 1. Android 系统类路径（bootClasspath）
     * 2. 项目 API 模块的类路径
     * 
     * @param project Gradle 项目对象
     */
    static void appendClassPathCore(Project project) {
        // 添加 Android 系统类路径
        // bootClasspath[0] 是 Android SDK 的核心类库路径
        mPool.appendClassPath(project.android.bootClasspath[0].toString())
        
        // 导入 Android Bundle 包（Javassist 需要知道这个类）
        mPool.importPackage("android.os.Bundle")

        // 获取 API 模块的编译输出目录
        // modules_services_api 是存放服务接口的模块
        def debugAPIDir = project.rootProject.project("modules_services_api").compileDebugJavaWithJavac.destinationDir
        def releaseAPIDir = project.rootProject.project("modules_services_api").compileReleaseJavaWithJavac.destinationDir
        
        // 如果 Debug 目录存在，添加到类路径
        if (debugAPIDir.exists()) {
            mPool.appendClassPath(debugAPIDir.absolutePath)
        }
        
        // 如果 Release 目录存在，添加到类路径
        if (releaseAPIDir.exists()) {
            mPool.appendClassPath(releaseAPIDir.absolutePath)
        }
    }

    /**
     * 处理 JAR 文件输入
     * 
     * 扫描 JAR 文件中的类，查找带有 @ExportService 注解的类
     * 修改这些类的继承关系，让它们继承对应的 Service 接口
     * 
     * 流程：
     * 1. 将 JAR 添加到 ClassPool
     * 2. 遍历 JAR 中的所有类
     * 3. 查找 @ExportService 注解
     * 4. 修改类的父类为对应的 Service 接口
     * 5. 将修改后的类写入临时目录
     * 6. 重新打包 JAR 文件
     * 
     * @param path JAR 文件路径
     * @param project Gradle 项目对象
     */
    static void handleJarInput(String path, Project project) {

        // 创建文件对象
        def pathFile = new File(path)

        // 临时类文件输出目录
        // 修改后的类文件会先写到这里，然后再打包回 JAR
        def tmpClassLocation = "${project.buildDir.absolutePath}${File.separator}tmp${File.separator}modules-class"
        
        // 将 JAR 文件添加到 ClassPool 的类路径
        // 这样 Javassist 就能加载这个 JAR 中的类了
        def currentClassPath = mPool.appendClassPath(path)

        // 打开 JAR 文件
        def pathJar = new JarFile(pathFile)
        
        // 创建临时 JAR 文件（.opt 后缀）
        def optJar = new File(pathFile.parent, "${pathFile.name}.opt")

        // 如果临时文件已存在，先删除
        if (optJar.exists()) {
            optJar.delete()
        }

        // 创建 JAR 输出流
        def jarOutputStream = new JarOutputStream(new FileOutputStream(optJar))
        
        // 获取 JAR 文件中的所有条目
        def entries = pathJar.entries()
        
        // 遍历所有条目
        while (entries.hasMoreElements()) {
            def jarEntry = entries.nextElement()
            def entryName = jarEntry.name
            
            // 创建 ZIP 条目
            def zipEntry = new ZipEntry(entryName)

            // 只处理 .class 文件
            if (entryName.endsWith('.class')) {
                // 将路径转换为类名
                // 例如：com/xxx/ServiceImpl.class -> com.xxx.ServiceImpl
                String cls = entryName.replace('\\', '.').replace('/', '.')
                cls = cls.substring(0, cls.length() - 6)  // 去掉 ".class" 后缀
                
                try {
                    // 从 ClassPool 中获取类对象
                    CtClass ctClass = mPool.getCtClass(cls)
                    
                    // 检查类是否有 @ExportService 注解
                    def annotation = ctClass.getAnnotation(ExportService.class)
                    
                    if (annotation != null) {
                        // 如果类有 @ExportService 注解，需要修改它
                        LoggerUtils.log("handleJarClass", cls)
                        
                        // 如果类被冻结（frozen），需要先解冻才能修改
                        if (ctClass.isFrozen()) {
                            ctClass.defrost()
                        }
                        
                        // 获取包名和类名
                        // 例如：com.xxx.ServiceImpl
                        // packageName = "com.xxx"
                        // originClassName = "ServiceImpl"
                        String packageName = cls.substring(0, cls.lastIndexOf("."))
                        String originClassName = cls.substring(cls.lastIndexOf(".") + 1, cls.length())
                        
                        // 构造 Service 接口的类名
                        // 例如：ServiceImpl -> ServiceImplService
                        String superClassName = originClassName + "Service"
                        
                        // 从 ClassPool 中获取 Service 接口类
                        // 例如：com.xxx.ServiceImplService
                        CtClass superCtClass = mPool.get(packageName + "." + superClassName)
                        
                        // 修改类的父类为 Service 接口
                        // 这相当于让实现类继承 Service 接口
                        ctClass.setSuperclass(superCtClass)
                        
                        // 将修改后的类写入临时目录
                        ctClass.writeFile(tmpClassLocation)
                        
                        // 从 ClassPool 中移除 Service 接口类（释放内存）
                        superCtClass.detach()
                    }
                    
                    // 从 ClassPool 中移除当前类（释放内存）
                    ctClass.detach()
                } catch (e) {
                    // 如果处理失败，记录日志但不中断流程
                    LoggerUtils.log("javaAssist exception", e.message)
                }
            }

            // 准备写入 JAR 条目
            def inputStream
            
            // 检查临时目录中是否有修改后的类文件
            def newClassFile = new File(tmpClassLocation, entryName.replace("\\", File.separator))
            
            if (!newClassFile.directory && newClassFile.exists()) {
                // 如果有修改后的类文件，使用它
                LoggerUtils.log("newClassFile", newClassFile.absolutePath)
                inputStream = new FileInputStream(newClassFile)
            } else {
                // 否则使用原 JAR 中的文件
                inputStream = pathJar.getInputStream(zipEntry)
            }

            // 将条目写入输出 JAR
            jarOutputStream.putNextEntry(zipEntry)
            jarOutputStream.write(IOUtils.toByteArray(inputStream))
            inputStream.close()
            jarOutputStream.closeEntry()
        }

        // 关闭输出流和 JAR 文件
        jarOutputStream.close()
        pathJar.close()

        // 从 ClassPool 中移除当前 JAR 的类路径（释放资源）
        mPool.removeClassPath(currentClassPath)

        // 删除原 JAR 文件
        if (pathFile.exists()) {
            FileUtils.forceDelete(pathFile)
        }
        
        // 用临时文件替换原文件
        optJar.renameTo(pathFile)

    }


    /**
     * 处理目录输入
     * 
     * 扫描目录中的类文件，查找带有 @ExportService 注解的类
     * 修改这些类的继承关系，让它们继承对应的 Service 接口
     * 
     * 与 handleJarInput 的区别：
     * - handleJarInput 处理 JAR 文件（依赖库）
     * - handleDirInput 处理目录（项目源码）
     * 
     * @param path 目录路径
     * @param project Gradle 项目对象
     */
    static void handleDirInput(String path, Project project) {
        // 将目录添加到 ClassPool 的类路径
        mPool.appendClassPath(path)
        
        // 创建目录对象
        File dir = new File(path)
        
        // 如果是目录，递归遍历
        if (dir.isDirectory()) {
            dir.eachFileRecurse { File file ->

                // 规范化文件路径
                String filePath = file.absolutePath.replace("\\", "/")

                // 判断是否是有效的类文件
                if (isValidClass(filePath)) {
                    // 计算类的相对路径
                    String classPath
                    if (path.endsWith("/") || path.endsWith("\\")) {
                        classPath = filePath.replace(path.replace("\\", "/"), "")
                    } else {
                        classPath = filePath.replace(path.replace("\\", "/") + "/", "")
                    }

                    // 将路径转换为类名
                    // 例如：com/xxx/ServiceImpl.class -> com.xxx.ServiceImpl
                    String className = classPath.substring(0, classPath.length() - 6).replace('\\', '.').replace('/', '.')
                    
                    // 判断这个类是否需要处理（根据配置的包名过滤）
                    if (classNeedHandle(className, project)) {
                        // 从 ClassPool 中获取类对象
                        CtClass ctClass = mPool.getCtClass(className)
                        
                        // 检查类是否有 @ExportService 注解
                        def annotation = ctClass.getAnnotation(ExportService.class)
                        
                        if (annotation != null) {
                            // 如果类有 @ExportService 注解，需要修改它
                            LoggerUtils.log("handleDirClass", className)
                            
                            // 如果类被冻结，先解冻
                            if (ctClass.isFrozen()) {
                                ctClass.defrost()
                            }
                            
                            // 获取包名和类名
                            String packageName = className.substring(0, className.lastIndexOf("."))
                            String originClassName = className.substring(className.lastIndexOf(".") + 1, className.length())
                            
                            // 构造 Service 接口的类名
                            String superClassName = originClassName + "Service"
                            
                            // 从 ClassPool 中获取 Service 接口类
                            CtClass superCtClass = mPool.get(packageName + "." + superClassName)
                            
                            // 修改类的父类为 Service 接口
                            ctClass.setSuperclass(superCtClass)
                            
                            // 将修改后的类写回原目录（直接覆盖）
                            ctClass.writeFile(path)
                        }
                        
                        // 从 ClassPool 中移除当前类（释放内存）
                        ctClass.detach()
                    }
                }

            }
        }
    }

    /**
     * 判断文件是否是合法的类文件
     * 
     * 排除以下文件：
     * 1. R.class（资源文件，由 Android 自动生成）
     * 2. 内部类（包含 $ 符号的类）
     * 
     * @param classFilePath 类文件路径
     * @return true 表示是合法的类文件，false 表示需要跳过
     */
    static boolean isValidClass(String classFilePath) {
        return classFilePath.endsWith(".class")           // 必须是 .class 文件
                && !classFilePath.endsWith("R.class")      // 排除 R.class
                && !classFilePath.contains('$')            // 排除内部类（如 Outer$Inner.class）
    }

    /**
     * 判断该 class 文件是否需要操作
     * 
     * 根据配置的 includeClassPackage 进行过滤
     * 如果配置了包名列表，只处理这些包下的类
     * 如果没有配置，处理所有类
     * 
     * @param className 类名（完整路径，如 com.xxx.ServiceImpl）
     * @param project Gradle 项目对象
     * @return true 表示需要处理，false 表示跳过
     */
    static boolean classNeedHandle(String className, Project project) {
        // 如果类名为空，跳过
        if (className == null) {
            return false
        }

        // 获取配置的包名列表
        String[] classesPackage = project.modulesConfig.includeClassPackage
        
        // 如果没有配置包名列表，处理所有类
        if (classesPackage == null || classesPackage.length == 0) {
            return true
        }

        // 检查类名是否以配置的包名开头
        for (String classPackage : classesPackage) {
            if (className.startsWith(classPackage)) {
                return true
            }
        }
        
        // 如果都不匹配，跳过
        return false
    }

    /**
     * 过滤不需要操作的 jar 包
     * 
     * 根据配置进行多层过滤：
     * 1. 检查文件是否存在、是否是文件、是否为空
     * 2. 检查是否在 excludeJars 列表中
     * 3. 检查是否匹配 businessMatchStrings
     * 
     * @param jarPath JAR 文件路径
     * @param project Gradle 项目对象
     * @return true 表示需要处理，false 表示跳过
     */
    static boolean jarNeedHandle(String jarPath, Project project) {

        // 创建文件对象
        def jarPathFile = new File(jarPath)
        
        // 检查文件是否存在
        if (!jarPathFile.exists()) {
            return false
        }
        
        // 检查是否是文件（不是目录）
        if (!jarPathFile.isFile()) {
            return false
        }
        
        // 检查文件是否为空
        if (jarPathFile.length() == 0) {
            return false
        }

        // 检查是否在排除列表中
        for (String excludeJar : project.modulesConfig.excludeJars) {
            if (jarPath.endsWith(excludeJar)) {
                return false
            }
        }

        // 检查路径是否有效
        if (jarPath == null || "" == jarPath) {
            return false
        }
        
        // 检查是否是 JAR 文件
        if (!jarPath.endsWith(".jar")) {
            return false
        }
        
        // 获取业务匹配字符串列表
        def strs = project.modulesConfig.businessMatchStrings
        
        // 如果配置了业务匹配字符串
        if (strs != null && strs.length != 0) {
            // 打开 JAR 文件，检查其中是否包含匹配的类
            JarFile jarFile = new JarFile(new File(jarPath))
            Enumeration<JarEntry> entries = jarFile.entries()
            
            // 遍历 JAR 中的所有条目
            while (entries.hasMoreElements()) {
                String jarEntryName = entries.nextElement().getName()
                
                // 检查条目名是否包含匹配字符串
                for (reg in strs) {
                    if (jarEntryName.contains(reg)) {
                        // 如果匹配，关闭文件并返回 true
                        jarFile.close()
                        return true
                    }
                }
            }
            
            // 如果都不匹配，关闭文件并返回 false
            jarFile.close()
            return false
        } else {
            // 如果没有配置业务匹配字符串，处理所有 JAR
            return true
        }
    }
}
```

## 工作原理详解

### 1. @ExportService 注解的作用

这个注解用于标记需要暴露的服务实现类。例如：

```java
@ExportService
public class UserServiceImpl implements UserService {
    // 实现代码
}
```

### 2. 类继承关系修改

**修改前：**
```java
public class UserServiceImpl implements UserService {
    // ...
}
```

**修改后：**
```java
public class UserServiceImpl extends UserServiceImplService implements UserService {
    // ...
}
```

这样修改的目的是让框架能够通过父类来管理服务实例。

### 3. 为什么需要临时目录？

对于 JAR 文件：
1. JAR 是压缩文件，不能直接修改
2. 需要解压 → 修改 → 重新打包
3. 临时目录用于存放修改后的类文件

对于目录：
- 可以直接覆盖原文件，不需要临时目录

### 4. ClassPool 的作用

ClassPool 是 Javassist 的核心类，用于：
- 加载类文件（从类路径）
- 管理类的生命周期
- 提供类操作 API

### 5. 为什么需要 detach()？

Javassist 会缓存加载的类，占用内存。调用 `detach()` 可以：
- 释放内存
- 避免内存泄漏
- 提高性能

### 6. 过滤机制

多层过滤确保只处理需要的类：
1. **isValidClass()**：排除 R.class 和内部类
2. **classNeedHandle()**：根据包名过滤
3. **jarNeedHandle()**：根据 JAR 路径和内容过滤

### 7. 处理流程对比

**JAR 文件处理：**
```
打开 JAR → 添加到 ClassPool → 遍历条目 → 修改类 → 写入临时目录 → 重新打包 → 替换原文件
```

**目录处理：**
```
添加到 ClassPool → 递归遍历 → 修改类 → 直接覆盖原文件
```
