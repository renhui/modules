# build.gradle - 详细注释版

```groovy
// 应用 Groovy 插件，用于编写 Gradle 插件
apply plugin: 'groovy'

// 以下代码被注释，用于开发模式和发布模式的切换
//if (devMode.toBoolean()) {
//    // 开发模式：从本地仓库加载插件
//    apply from: '../repo_plugin.gradle'
//} else {
//    // 发布模式：从 bintray 仓库加载插件
//    apply from: '../bintray.gradle'
//}


// 定义项目依赖
dependencies {
    // Gradle SDK：提供 Gradle API，用于编写插件
    implementation gradleApi()
    
    // Groovy SDK：提供 Groovy 运行时，用于编写 Groovy 代码
    implementation localGroovy()
    
    // Android Gradle 插件：用于访问 Android 构建系统的 API
    implementation 'com.android.tools.build:gradle:2.1.3'
    
    // Javassist：字节码操作库，用于在编译时修改类文件
    implementation 'org.javassist:javassist:3.20.0-GA'
    
    // Apache Commons IO：文件操作工具库
    implementation 'commons-io:commons-io:2.5'

    // 以下代码被注释，用于开发模式和发布模式的依赖切换
//    if (devMode.toBoolean()) {
//        // 开发模式：使用本地开发的注解库
//        implementation 'com.beyondxia.modules:annotation_dev:1.0.1'
//    } else {
//        // 发布模式：使用正式发布的注解库
//        implementation 'com.beyondxia.modules:annotation:1.0.3'
//    }
    
    // 使用项目内的注解模块（本地开发时使用）
    implementation project(':modules-annotation')

}

// 定义仓库配置
repositories {
    // 本地 Maven 仓库：用于存放本地构建的依赖
    maven {
        url uri("./repos")
    }
    
    // JCenter 仓库：公共 Maven 仓库
    jcenter()
    
    // Google Maven 仓库：Android 官方依赖仓库
    google()
}



// 以下代码被注释，用于 Maven 发布配置
//publishing {
//    publications {
//        mavenJava(MavenPublication) {
//
//            // Maven 坐标：组织 ID
//            groupId 'com.beyondxia.modules'
//            // Maven 坐标：构件 ID
//            artifactId _artifactId
//            // Maven 坐标：版本号
//            version _version
//
//            // 发布 Java 组件
//            from components.java
//
//        }
//    }
//}

// 应用 Maven 发布插件（已注释）
//apply plugin: 'maven-publish'
//publishing {
//    repositories {
//        maven {
//            // 发布到本地仓库
//            url uri('./repos')
//        }
//    }
//}

// 发布到 Bintray 的命令示例（已注释）
//./gradlew clean modules-plugin:build bintrayUpload -PbintrayUser=beyondxia -PbintrayKey=af95d6af14ef35e80d9207234f96e56445b4677b -PdryRun=false
//apply plugin: 'com.novoda.bintray-release'

// 配置所有项目的仓库
allprojects {
    repositories {
        // JCenter 仓库
        jcenter()
        // Google Maven 仓库
        google()
    }
}

// Bintray 发布配置（已注释）
//publish {
//    repoName = 'transform'                    // 仓库名称
//    userOrg = 'beyondxia'                     // 用户组织
//    groupId = 'com.beyondxia.modules'        // Maven 组织 ID
//    artifactId = 'transform-plugin'          // Maven 构件 ID
//    publishVersion = '1.3.12'                // 发布版本
//    desc = 'This is a plugin for modules'    // 描述信息
//    website = 'https://github.com/beyondxia/transform'  // 项目网站
//}
```

## 文件说明

这个 `build.gradle` 文件是 Gradle 插件的构建配置文件，主要功能包括：

1. **插件类型**：这是一个 Groovy 插件项目，用于编写 Gradle 插件
2. **核心依赖**：
   - `gradleApi()` 和 `localGroovy()`：编写插件的基础
   - Android Gradle 插件：访问 Android 构建系统
   - Javassist：字节码操作工具
   - Commons IO：文件操作工具
3. **仓库配置**：配置了本地、JCenter 和 Google 仓库
4. **发布配置**：包含 Maven 和 Bintray 发布配置（已注释）
