package com.github.joehaivo.jpublish


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.maven.MavenPublication

class JPublishPlugin implements Plugin<Project> {
    static final String SNAPSHOT = "-SNAPSHOT"
    static final String DEFAULT_BUILD_VARIANT = 'release'

    AarInfoExt aarInfoExt

    @Override
    void apply(Project target) {
        if (target.plugins.hasPlugin("com.android.application")) {
            Logger.i "当前组件是application类型，跳过！"
            return
        }
        if (!Utils.isAndroidLibrary(target)) {
            Logger.i "当前组件非Android类型，跳过！"
            return
        }
        if (!target.plugins.hasPlugin("maven-publish")) {
            target.plugins.apply("maven-publish")
        }

        aarInfoExt = target.extensions.create("aarInfo", AarInfoExt)
        target.afterEvaluate {
            target.publishing.publications {
                AndroidAar(MavenPublication) { MavenPublication publication ->
                    if (target.components.isEmpty()) {
                        def componentsEmpty = "未在工程中找到有效的组件配置，请检查gradle配置！,本插件只适用于AGP-3.6.0及以上版本。"
                        Logger.j(componentsEmpty)
                        throw org.gradle.api.GradleException(componentsEmpty)
                    }
                    if (StringUtil.isBlank(aarInfoExt.buildVariant)) {
                        Logger.i("aarInfo.buildVariant为空，采用`$DEFAULT_BUILD_VARIANT`")
                    }
                    def variant = StringUtil.isBlank(aarInfoExt.buildVariant) ? DEFAULT_BUILD_VARIANT : aarInfoExt.buildVariant
                    def component = target.components.findByName(variant)
                    if (component == null) {
                        def allVariants = target.components.collect { it.name }.join(", ")
                        def flavorNotFound = "\n未在组件:${aarInfoExt.artifactId}中找到与'$variant'匹配的类型，请检查`aarInfo.buildVariant`的配置，找到以下Build Variants:\n" +
                                "(在此中选择一个)：$allVariants\n"
                        Logger.j(flavorNotFound)
                        throw IllegalArgumentException(flavorNotFound)
                    }
                    if (aarInfoExt.userName == null || aarInfoExt.password == null) {
                        def userNameOrPasswordEmpty = "aarInfo.userName或aarInfo.password为空，请检查配置！你可以将'MAVEN_USER_NAME=xxx'以及'MAVEN_PASSWORD=xxx'添加到'local.properties'中."
                        Logger.j(userNameOrPasswordEmpty)
                        throw IllegalArgumentException(userNameOrPasswordEmpty)
                    }
                    if (aarInfoExt.version.trim().endsWith(SNAPSHOT)) {
                        if (aarInfoExt.snapshotMavenUrl == null) {
                            def snapshotMavenUrlEmpty = "aarInfo.snapshotMavenUrl为空，请检查配置！你可以将'MAVEN_SNAPSHOT_URL=xxx'添加到'local.properties'中."
                            Logger.j(snapshotMavenUrlEmpty)
                            throw IllegalArgumentException(snapshotMavenUrlEmpty)
                        }
                    } else {
                        if (aarInfoExt.releaseMavenUrl == null) {
                            def releaseMavenUrlEmpty = "aarInfo.releaseMavenUrl为空，请检查配置！你可以将'MAVEN_RELEASE_URL=xxx'添加到'local.properties'中."
                            Logger.j(releaseMavenUrlEmpty)
                            throw IllegalArgumentException(releaseMavenUrlEmpty)
                        }
                    }
                    publication.from target.getComponents().findByName(variant)
                    publication.groupId = aarInfoExt.groupId
                    publication.artifactId = aarInfoExt.artifactId
                    publication.version = aarInfoExt.version
                    publication.artifact Utils.createAndroidSourcesJar(target)
                    def _pom = publication.getPom()
                    _pom.developers {
                        developer { developer ->
                            developer.name = Utils.cmd('git config user.name')
                            developer.email = Utils.cmd('git config user.email')
                        }
                    }
                    _pom.packaging = "aar"
                }
            }
            Integer gradleMajorVersion = target.gradle.gradleVersion.split("\\.").first().toInteger()
            target.publishing.repositories { RepositoryHandler handler ->
                handler.maven { MavenArtifactRepository repository ->
                    repository.url = getMavenUrl(aarInfoExt)
                    if (gradleMajorVersion >= 7) {
                        repository.setAllowInsecureProtocol(true) // 兼容Gradle7版本支持http连接
                    }
                    repository.credentials { credential ->
                        credential.username = aarInfoExt.userName
                        credential.password = aarInfoExt.password
                    }
                }
            }

            def uploadTask = target.task("jpublish", group: "haivo")
            uploadTask.finalizedBy("publishAndroidAarPublicationToMavenRepository")
        }

        printPublishTaskResult(aarInfoExt, target)
    }

    private static void printPublishTaskResult(AarInfoExt aarInfoExt, Project target) {
        target.tasks.whenTaskAdded { Task task ->
            def taskName = task.name.replaceAll(":${target.name}:", "")
            if ((taskName.startsWith("publish") && taskName.endsWith("Repository"))) {
                task.doLast {
                    def aarVersion = aarInfoExt.version
                    def aarArtifactId = aarInfoExt.artifactId
                    def aarGroupId = aarInfoExt.groupId
                    if (!task.state.failure) {
                        Logger.i ''
                        Logger.j "组件发布成功：implementation '${aarGroupId}:${aarArtifactId}:${aarVersion}'"
                        Logger.j "发布地址：${getMavenUrl(aarInfoExt, true)}"
                        Logger.i ''
                    } else {
                        Logger.j "组件发布失败! "
                    }
                }
            }
        }
    }

    static String getMavenUrl(AarInfoExt aarInfoExt) {
        if (aarInfoExt.version.trim().endsWith(SNAPSHOT)) {
            return aarInfoExt.snapshotMavenUrl
        } else {
            return aarInfoExt.releaseMavenUrl
        }
    }
}
