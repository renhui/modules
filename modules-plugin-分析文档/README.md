# modules-plugin 代码分析文档

本目录包含 `modules-plugin` 项目的详细代码分析和注释文档。

## 文档索引

### 📋 [00-总体原理分析.md](00-总体原理分析.md)
**核心文档** - 包含项目的整体架构、工作原理、技术栈和关键流程说明。

### 📁 核心文件分析

1. **[01-build.gradle-详细注释.md](01-build.gradle-详细注释.md)**
   - Gradle 插件构建配置
   - 依赖管理
   - 仓库配置

2. **[02-PAServicePlugin.groovy-详细注释.md](02-PAServicePlugin.groovy-详细注释.md)**
   - 插件入口类
   - 配置扩展创建
   - Transform 注册

3. **[03-PATransform.groovy-详细注释.md](03-PATransform.groovy-详细注释.md)**
   - Transform 实现
   - 目录和 JAR 文件处理
   - 编译时字节码转换流程

4. **[04-RegisterUtils.groovy-详细注释.md](04-RegisterUtils.groovy-详细注释.md)**
   - ASM 字节码注入
   - 服务类扫描和收集
   - 自动注册代码注入

5. **[05-TransformUtil.groovy-详细注释.md](05-TransformUtil.groovy-详细注释.md)**
   - Javassist 字节码操作
   - @ExportService 注解处理
   - 类继承关系修改

6. **[06-其他文件-详细注释.md](06-其他文件-详细注释.md)**
   - ClassConstant：常量定义
   - ConfigExtention：配置扩展
   - LoggerUtils：日志工具
   - SystemUtils：系统工具
   - 插件声明文件

## 快速开始

1. **先阅读** [00-总体原理分析.md](00-总体原理分析.md) 了解整体架构
2. **然后阅读** 各个文件的详细注释文档
3. **参考** 代码中的行注释理解具体实现

## 技术要点

- **Transform API**：Android 编译时字节码转换
- **Javassist**：修改类继承关系
- **ASM**：精确的字节码注入
- **自动注册**：编译时自动收集和注册服务类

## 项目功能

1. ✅ 服务自动注册
2. ✅ 类继承关系修改
3. ✅ 编译时处理（不影响运行时性能）
4. ✅ 低侵入性（只需添加注解）

## 文档说明

所有文档都包含：
- 详细的代码注释（每行都有说明）
- 工作原理说明
- 技术要点分析
- 使用示例

---

**生成时间**：2024年
**分析范围**：modules-plugin 目录下的所有代码文件
