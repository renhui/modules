# PATransform.groovy - 详细注释版

```groovy
// 包声明
package com.beyondxia.plugin

// 导入 Android 注解：NonNull 表示参数不能为空
import com.android.annotations.NonNull

// 导入 Transform API 相关类
// Transform 是 Android Gradle 插件提供的字节码转换接口
import com.android.build.api.transform.*

// 导入 TransformManager：提供 Transform 的常用配置
import com.android.build.gradle.internal.pipeline.TransformManager

// 导入工具类
import com.beyondxia.plugin.utils.RegisterUtils    // 注册工具：使用 ASM 进行字节码注入
import com.beyondxia.plugin.utils.TransformUtil    // Transform 工具：使用 Javassist 处理类

// 导入 Apache Commons 工具
import org.apache.commons.codec.digest.DigestUtils  // MD5 加密工具
import org.apache.commons.io.FileUtils              // 文件操作工具

// 导入 Gradle Project
import org.gradle.api.Project

/**
 * PATransform 类
 * 
 * 这是 Android Transform 的实现类，继承自 Transform
 * Transform 是 Android Gradle 插件提供的编译时字节码转换机制
 * 
 * 功能：
 * 1. 在编译时扫描所有 .class 文件
 * 2. 处理带有 @ExportService 注解的类（通过 TransformUtil）
 * 3. 自动注册服务类（通过 RegisterUtils）
 * 
 * 创建时间：2018/8/29 16:25
 * 作者：ChenWei
 */
class PATransform extends Transform {

    // 当前 Gradle 项目对象
    private Project mProject

    // 静态变量：包含初始化类的文件（ServiceHelper.class 所在的 jar 文件）
    // 这个文件会被修改，注入自动注册的代码
    public static File fileContainsInitClass
    
    // 静态变量：需要注册的服务类名列表
    // 在扫描过程中收集所有需要注册的类名
    public static List<String> registerList = new ArrayList()


    /**
     * 构造函数
     * 
     * @param project Gradle 项目对象
     */
    PATransform(Project project) {
        this.mProject = project
    }

    /**
     * 获取 Transform 的名称
     * 
     * 这个名称会出现在构建日志中，用于标识这个 Transform
     * 
     * @return Transform 名称
     */
    @Override
    String getName() {
        return "ModulesTransform"
    }

    /**
     * 获取输入内容类型
     * 
     * 指定这个 Transform 处理什么类型的内容
     * CONTENT_CLASS 表示处理 .class 文件
     * 
     * @return 内容类型集合
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 获取作用域
     * 
     * 指定这个 Transform 处理哪些范围的类
     * SCOPE_FULL_PROJECT 表示处理整个项目的所有类（包括依赖库）
     * 
     * @return 作用域集合
     */
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 是否支持增量编译
     * 
     * 返回 false 表示不支持增量编译，每次都会处理所有文件
     * 支持增量编译可以提高构建速度，但实现更复杂
     * 
     * @return false（不支持增量编译）
     */
    @Override
    boolean isIncremental() {
        return false
    }


    /**
     * Transform 的核心方法
     * 
     * 这个方法会在编译时被调用，处理所有的输入文件
     * 
     * @param transformInvocation Transform 调用对象，包含输入和输出信息
     */
    @Override
    void transform(@NonNull TransformInvocation transformInvocation) {
        // 首先获取构建类型并给 mPool 添加 classPath
        // 这一步是为了让 Javassist 能够正确加载和修改类
        TransformUtil.appendClassPathCore(mProject)

        // 遍历所有输入
        transformInvocation.inputs.each { TransformInput input ->
            // 处理目录类型的输入（通常是项目源码编译后的 .class 文件）
            input.directoryInputs.each { DirectoryInput directoryInput ->
                // 使用 Javassist 处理目录中的类文件
                // 主要功能：扫描 @ExportService 注解，修改类的继承关系
                TransformUtil.handleDirInput(directoryInput.file.absolutePath, mProject)
                
                // 如果启用了自动注册功能
                if (mProject.modulesConfig.registerWithPlugin) {
                    // 扫描目录，收集需要注册的服务类
                    RegisterUtils.scanDirectory(directoryInput.file)
                }
                
                // 获取输出目录
                // Transform 需要将处理后的文件输出到指定目录
                def dest = transformInvocation.outputProvider.getContentLocation(
                        directoryInput.name,                    // 输入名称
                        directoryInput.contentTypes,            // 内容类型
                        directoryInput.scopes,                  // 作用域
                        Format.DIRECTORY)                       // 输出格式：目录
                
                // 将处理后的目录复制到输出目录
                // 注意：TransformUtil.handleDirInput 已经修改了原文件，这里直接复制即可
                FileUtils.copyDirectory(directoryInput.file, dest)
            }
            
            // 处理 JAR 文件类型的输入（通常是依赖库）
            input.jarInputs.each { JarInput jarInput ->
                def jarPath = jarInput.file.absolutePath
                
                // 判断这个 jar 是否需要处理
                // 根据配置的 excludeJars 和 businessMatchStrings 进行过滤
                if (TransformUtil.jarNeedHandle(jarPath, mProject)) {
                    // 使用 Javassist 处理 jar 中的类文件
                    // 主要功能：扫描 @ExportService 注解，修改类的继承关系
                    TransformUtil.handleJarInput(jarPath, mProject)
                }
                
                // 重命名输出文件（避免同目录复制文件时冲突）
                // 使用 MD5 哈希值确保文件名唯一
                def jarName = jarInput.name
                def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
                
                // 如果 jar 名称以 .jar 结尾，去掉扩展名
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length() - 4)
                }
                
                // 获取输出文件路径
                def dest = transformInvocation.outputProvider.getContentLocation(
                        jarName + md5Name,      // 使用原名称 + MD5 作为新名称
                        jarInput.contentTypes,  // 内容类型
                        jarInput.scopes,       // 作用域
                        Format.JAR)            // 输出格式：JAR
                
                // 如果启用了自动注册功能
                if (mProject.modulesConfig.registerWithPlugin) {
                    // 判断是否应该处理这个 jar（排除系统库）
                    if (RegisterUtils.shouldProcessPreDexJar(jarName)) {
                        // 扫描 jar 文件，收集需要注册的服务类
                        // 同时查找 ServiceHelper.class 所在的 jar
                        RegisterUtils.scanJar(jarInput.file, dest)
                    }
                }
                
                // 将处理后的 jar 文件复制到输出目录
                FileUtils.copyFile(jarInput.file, dest)
            }

        }

        // 如果启用了自动注册功能
        if (mProject.modulesConfig.registerWithPlugin) {
            // 在所有文件处理完成后，向 ServiceHelper 类注入注册代码
            // 这一步使用 ASM 修改字节码，在 pluginRegisterClassName 方法中添加注册调用
            RegisterUtils.insertCodeToInitClass()
        }

    }
}
```

## 工作原理详解

### 1. Transform 执行时机

```
Java 源码编译 → .class 文件生成 → Transform 处理 → DEX 转换 → APK 打包
```

Transform 在 `.class` 文件生成之后、DEX 转换之前执行。

### 2. 处理流程

```
1. 初始化 ClassPool（Javassist 需要）
   ↓
2. 处理目录输入（项目源码）
   - 扫描 @ExportService 注解
   - 修改类继承关系
   - 收集需要注册的类
   ↓
3. 处理 JAR 输入（依赖库）
   - 过滤不需要的 jar
   - 扫描 @ExportService 注解
   - 修改类继承关系
   - 收集需要注册的类
   - 查找 ServiceHelper.class 所在位置
   ↓
4. 注入注册代码（如果启用）
   - 使用 ASM 修改 ServiceHelper.class
   - 在 pluginRegisterClassName 方法中添加注册调用
```

### 3. 为什么需要重命名 JAR 文件？

Gradle 的 Transform 机制要求每个输入文件都有唯一的输出路径。使用 MD5 哈希值可以：
- 确保不同路径的同名 jar 不会冲突
- 支持增量编译（如果启用）
- 避免文件覆盖问题

### 4. 两种字节码操作工具

- **Javassist**（TransformUtil）：用于修改类的继承关系，API 更简单
- **ASM**（RegisterUtils）：用于精确的字节码注入，性能更好

### 5. 静态变量的作用

- `fileContainsInitClass`：记录 ServiceHelper.class 所在的 jar 文件，最后需要修改它
- `registerList`：收集所有需要注册的服务类名，最后注入到 ServiceHelper 中
