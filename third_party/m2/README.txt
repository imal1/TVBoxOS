本目录是一个本地 Maven 仓库，只存放 DLNA 投屏所需的 cling 2.1.2 及其依赖 seamless 1.1.2。

原因：这两个库唯一的发布仓库 http://4thline.org/m2 已下线（域名无法解析），
Maven Central、阿里云镜像、JitPack 均无可用制品，导致项目在干净环境中无法构建。

来源与构建方式（Java 17 + Maven 3.9）：
- seamless 1.1.2：https://github.com/4thline/seamless master（50c0fe2，pom 版本即 1.1.2）
    mvn install -DskipTests -Dmaven.javadoc.skip=true -Dmaven.compiler.source=7 -Dmaven.compiler.target=7 -pl util,xml,http,swing -am
- cling 2.1.2：https://github.com/4thline/cling tag 2.1.2（c1a9b8b）
    对 core/pom.xml、support/pom.xml 做了两处仅影响构建的修改：
      1. 去掉只用于生成文档的 lemma-maven-plugin 与 maven-site-plugin（该插件同样只托管在 4thline.org）
      2. 增加 provided 范围的 javax.annotation:javax.annotation-api:1.3.2（JDK 11+ 不再自带 javax.annotation）
    mvn install -DskipTests -Dmaven.javadoc.skip=true -pl core,support -am
  Java 源码未做任何改动。

由 build.gradle 中的 exclusiveContent 限定：org.fourthline.cling 与 org.seamless 两个 group 只从本目录解析。
