**Fēng编程语言**

Fēng语言是一种具有面向对象的内存安全优先的静态类型编程语言：

* 内存安全优先设计，强制类型及边界检查。
* 简化自Java的类与接口设计，支持继承和抽象。
* 除类和接口以外无其他抽象。
* 自动引用计数。
* 有析构函数的资源类。
* 与C语言一样的struct和union，支持任意转换。
* 类似C++的引用或Rust借用的虚引用机制。
* 单态化泛型，支持泛型推断。
* 引用和方法支持不可修改标记，无需封装即可实现数据写保护。
* 基于控制流程的非空检查，可避免空指针问题，提高程序稳定性。
* 简单的模块化代码组织方式。
* 支持部分运算符重载。
* 基于信任边界的并发检查。

设计的语法细节请参考[手册](reference_zh.md)。

# 开发进展

当前正处于开发中，虽然能编译简单项目，但缺少系统调用库及工具库，依然无法正常使用。
希望能吸引有兴趣的朋友参与！

## 语法解析

解析程序是用ANTLR4生成的，语言spec可参考[grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4)。
并通过[SourceParseVisitor](src/main/java/org/cossbow/feng/parser/SourceParseVisitor.java)遍历解析结果并构建AST。

在构建时通过maven插件自动生成解析程序类，所以只需用`mvn`构建一下，就可以IDEA打开调试了。

## 语义分析

分析工具类是[SemanticAnalyzer](src/main/java/org/cossbow/feng/analysis/SemanticAnalyzer.java)。

已完成的语义分析包括：

1. 符号检查：检查类型、函数是否定义，变量是否声明。
2. 常量计算，常量直接计算出结果。
3. 结构类型布局计算与越界检查。
4. 类型检查：变量赋值、返回值的类型检查，函数原型比较，可转换类型的检查。
5. 类的继承与实现接口的检查。
6. 多分支路径的终结语句检查。
7. 变量生命周期检查。
8. 表达式中匿名对象检查。
9. 引用的只读约束与检查。
10. 语句上下文检查。
11. 泛型类型参数检查。
12. 引用其他模块导出的符号。
13. 非空检查。
14. 并发检查。

## 编译器后端

先编译生成C代码，然后再构建成二进制。C后端代码[CGenerator.java](src/main/java/org/cossbow/feng/coder/CGenerator.java)。

已完成代码功能：

1. 衍生类定义：类、接口、结构类型、函数类型完成，
2. 表达式：完成
3. 语句：完成
4. 变量：完成
5. 类型：完成
6. 类的多态调用：完成
7. 运行时类型检查：完成
8. 变量的清理和引用实例管理：完成
9. 字面量与初始化：完成
10. 泛型：检查、推断等完成，约束条件未完成
11. 字符串格式化：完成
12. 模块：完成
13. 引用的并发安全：完成

# 编译器

当前构建的工具支持编译单个源文件、单个模块及包构建，包是同一个目录下的所有模块的集合，在构建时可以导入其他包的模块。
编译器入口类[Compiler.java](src/main/java/org/cossbow/feng/Compiler.java)。

构建编译器需要先安装JDK和maven环境，安装细节可咨询[deepseek](https://chat.deepseek.com/)。
项目依赖的jar包只有antlr4-runtime、jcommander及3个maven插件，在构建时会自动下载。建议使用命令构建：

```shell
mvn clean package -Dmaven.test.skip=true
```

打包好的jar包在target目录下：feng-${version}.jar
比如当前version为“0.0.1-dev”，构建的包为“feng-0.0.1-dev.jar”。

运行编译器需要java运行环境及clang工具，clang的安装也可以咨询deepseek。

编译器使用方式为：

```shell
java -jar feng-0.0.1-dev.jar -t [类型] -i [源] -o [输出目录]
```

参数说明（【选项】是指该参数是开关，无跟随参数）：

1. -t 源的类型：f/file-单文件，m/module-单模块，p/project-多模块组织的简单项目
2. -i 源的路径：对单文件则指向文件全路径，模块或项目就指向所在目录。
3. -o 输出目录：编译过程使用的目录，包括中间文件和最终产物；不指定就会默认为放到源目录下。
4. -p 当前包名：默认是文件名或目录名。
5. -L 添加依赖包：可以指定多个，分别是键值对（包名=路径），例如：-Lfoo=D:\dev\libs\foo
6. -b 后端构建工具：可选make或cmake
7. -T 【选项】单元测试：此模式下仅编译单元测试程序，将需要执行的单元测试编译成一个可执行程序，不会编译`main`函数。
8. --test-name 指定单元测试：单元测试下有效，用于过滤要执行的测试用例。可以指定多个。

例如，编译单个源文件：

```shell
java -jar feng-0.0.1-dev.jar -t f -i jjj.feng -o /var/build
```

然后在/var/build下会生成编译结果：

1. 每个模块编译后会生成一个.o文件。
2. 如果其中一个模块有`main`函数，还会生成一个可执行文件。

# 编辑器支持

项目内置了LSP Server提供语法高亮和语言服务，与编译器打包在同一个jar中，启动命令：

```shell
java -cp feng-0.0.1-dev.jar org.cossbow.feng.lsp.FengLspMain
```

已支持的LSP功能：诊断、文档符号、悬停提示、跳转定义、补全。

## VS Code 扩展

VS Code 扩展由独立仓库维护：[feng-vscode](https://github.com/cossbow/feng-vscode)。
首次激活时会自动从 GitHub Releases 下载 LSP Server JAR。

