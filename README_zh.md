**【Fèng】编程语言**

Fèng语言是一种简单易用、内存安全优先的编译型语言，

* 基本和C语言一样的struct和union，支持互相转换。
* 简化自Java的类与接口，但能像C++一样定义值类型，减少不必要的动态内存分配。
* 设计为自动内存，当前为自动引用计数，可切换为GC。
* 有析构函数的资源类，方便管理交互语言的资源。
* 类似C++借用的虚引用。
* 编译期泛型。
* 运算符重载。
* 可定义的非空引用、只读引用。
* 模块化代码组织。

设计的语法细节请参考[手册](reference_zh.md)。

# 开发进展

当前正处于开发中，虽然能编译简单项目，但缺少系统调用库及工具库，依然无法正常使用。
希望能吸引有兴趣的朋友参与！

## 语法解析

解析程序是用ANTLR4生成的，语言spec可参考[grammar](src/main/antlr4/org/cossbow/feng/parser/Feng.g4)。
并通过[SourceParseVisitor](src/main/java/org/cossbow/feng/parser/SourceParseVisitor.java)遍历解析结果并构建AST。

在构建时通过maven插件自动生成解析程序类，所以只需用`mvn`构建一下，就可以IDEA打开调试了。

## 语义分析

分析工具类是[SemanticAnalysis](src/main/java/org/cossbow/feng/analysis/SemanticAnalysis.java)。

已完成的语义分析包括：

1. 符号检查：检查类型、函数是否定义，变量是否声明。
2. 常量计算，常量直接计算出结果。
3. 类型检查：变量赋值、返回值的类型检查，函数原型比较，可转换类型的检查。
4. 类的继承与实现接口的检查。
5. 检查缺少return的路径。
6. 变量生命周期检查。
7. 表达式中匿名对象检查。
8. 引用和函数类型变量的required检查。
9. 引用的unmodifiable检查。
10. 语句上下文检查。
11. 泛型类型参数检查。
12. 引用其他模块导出的符号。

## 目标代码生成

仅完成生成C++代码的功能，工具类[CppGenerator](src/main/java/org/cossbow/feng/coder/CppGenerator.java)。

已完成代码功能：

1. 衍生类定义：类、接口、结构类型、函数类型完成，属性*未完成*
2. 表达式：幂运算*未完成*
3. 语句：异常*未完成*
4. 变量：完成
5. 类型：完成
6. 类的多态调用：完成
7. 运行时类型检查：完成
8. 变量的清理和引用实例管理：完成
9. 字面量与初始化：完成
10. 泛型：完成
11. 字符串格式化：*未完成*
12. 模块：每个模块生成一个cpp文件。

# 工具构建

当前构建的工具支持编译单个源文件、单个模块及包构建，包是同一个目录下的所有模块的集合，在构建时可以导入其他包的模块。

工具是用Java开发的，允许需要先安装JDK和maven环境，详情可咨询[deepseek](https://chat.deepseek.com/)。
项目依赖的jar包只有antlr4-runtime、jcommander及3个maven插件，在构建时会自动下载。建议使用命令构建：

```shell
mvn clean package -Dmaven.test.skip=true
```

打包好的jar包在target目录下：feng-${version}.jar
比如当前version为“0.0.1-dev”，构建的包为“feng-0.0.1-dev.jar”。

工具允许方式为：

```shell
java -jar feng-0.0.1-dev.jar -t [类型] -i [源] -o [输出目录]
```

参数说明：

1. -t 源的类型：f/file-单文件，m/module-单模块，p/project-多模块组织的简单项目
2. -i 源的路径：对单文件则指向文件全路径，模块或项目就指向所在目录。
3. -o 输出目录：对单文件会输出为一个c++文件；对模块或项目，每个模块对应一个c++文件。不指定就会默认为放到源目录下。
4. -p 当前包名：默认是文件名或目录名。
5. -L 添加依赖包：可以指定多个，分别是键值对（包名=路径），例如：-Lfoo=D:\dev\libs\foo

编译单个源文件：

```shell
java -jar feng-0.0.1-dev.jar -t f -i jjj.feng -o jjj.cpp
```

生成的C++需要C/C++编译环境，并且编译时需要指定C++20版本：

```shell
c++ --std=c++20 -c jjj.cpp -o jjj.o
```

如果feng代码里有main函数，就会在对应c++文件里创建main函数，这时就能编译为可执行文件：

```shell
c++ --std=c++20 jjj.cpp -o jjj.o
```

