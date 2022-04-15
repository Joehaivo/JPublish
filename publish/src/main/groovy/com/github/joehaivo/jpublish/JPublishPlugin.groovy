package com.github.joehaivo.jpublish

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.maven.MavenPublication

class JPublishPlugin implements Plugin<Project> {
    static final String SNAPSHOT = "-SNAPSHOT"
    static final String DEFAULT_BUILD_VARIANT = 'debug'
    static final String PROP_MAVEN_USERNAME = 'MAVEN_USERNAME'
    static final String PROP_MAVEN_PASSWORD = 'MAVEN_PASSWORD'
    static final String PROP_MAVEN_SNAPSHOT = 'MAVEN_SNAPSHOT_URL'
    static final String PROP_MAVEN_RELEASE = 'MAVEN_RELEASE_URL'

    AarInfoExt aarInfoExt

    @Override
    void apply(Project project) {
        if (project.plugins.hasPlugin("com.android.application")) {
            Logger.i "当前组件是application类型，跳过！"
            return
        }
        if (!Utils.isAndroidLibrary(project)) {
            Logger.i "当前组件非Android类型，跳过！"
            return
        }
        if (!project.plugins.hasPlugin("maven-publish")) {
            project.plugins.apply("maven-publish")
        }

        aarInfoExt = project.extensions.create("aarInfo", AarInfoExt)
        project.afterEvaluate {
            project.publishing.publications {
                AndroidAar(MavenPublication) { MavenPublication publication ->
                    if (project.components.isEmpty()) {
                        def componentsEmpty = "未在工程中找到有效的组件配置，请检查gradle配置！,本插件只适用于AGP-3.6.0及以上版本。"
                        Logger.j(componentsEmpty)
                        throw new GradleException(componentsEmpty)
                    }
                    def variant = StringUtil.isBlank(aarInfoExt.buildVariant) ? DEFAULT_BUILD_VARIANT : aarInfoExt.buildVariant
                    if (StringUtil.isBlank(aarInfoExt.buildVariant)) {
                        Logger.i("aarInfo.buildVariant为空，采用默认值`$DEFAULT_BUILD_VARIANT`")
                    }
                    def component = project.components.findByName(variant)
                    if (component == null) {
                        def allVariants = project.components.collect { it.name }.join(", ")
                        def flavorNotFound = "\nerror: \n未在组件:${aarInfoExt.artifactId}中找到与'$variant'匹配的类型，请检查`aarInfo.buildVariant`的配置，找到以下Build Variants:\n" +
                                "(在此中选择一个填入aarInfo中)：$allVariants\n"
                        throw new IllegalArgumentException(flavorNotFound)
                    }

                    aarInfoExt.mavenUsername = readProp(project, aarInfoExt.mavenUsername, PROP_MAVEN_USERNAME, "mavenUsername")
                    aarInfoExt.mavenPassword = readProp(project, aarInfoExt.mavenPassword, PROP_MAVEN_PASSWORD, "mavenPassword")

                    if (aarInfoExt.version.trim().endsWith(SNAPSHOT)) {
                        aarInfoExt.mavenSnapshotUrl = readProp(project, aarInfoExt.mavenSnapshotUrl, PROP_MAVEN_SNAPSHOT, "mavenSnapshotUrl")
                    } else {
                        aarInfoExt.mavenReleaseUrl = readProp(project, aarInfoExt.mavenReleaseUrl, PROP_MAVEN_RELEASE, "mavenReleaseUrl")
                    }
                    publication.from project.getComponents().findByName(variant)
                    publication.groupId = aarInfoExt.groupId
                    publication.artifactId = aarInfoExt.artifactId
                    publication.version = aarInfoExt.version
                    publication.artifact Utils.createAndroidSourcesJar(project)
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
            Integer gradleMajorVersion = project.gradle.gradleVersion.split("\\.").first().toInteger()
            project.publishing.repositories { RepositoryHandler handler ->
                handler.maven { MavenArtifactRepository repository ->
                    repository.url = getMavenUrl(aarInfoExt)
                    if (gradleMajorVersion >= 7) {
                        repository.setAllowInsecureProtocol(true) // 兼容Gradle7版本支持http连接
                    }
                    repository.credentials { credential ->
                        credential.username = aarInfoExt.mavenUsername
                        credential.password = aarInfoExt.mavenPassword
                    }
                }
            }

            def uploadTask = project.task("jpublish", group: "haivo")
            uploadTask.finalizedBy("publishAndroidAarPublicationToMavenRepository")
        }

        printPublishTaskResult(aarInfoExt, project)
    }

    private static String readProp(Project project, String prop, String MAVEN_PROP, String AAR_INFO_PROP) throws IllegalArgumentException {
        if (StringUtil.isBlank(prop)) {
            prop = project.rootProject.properties[MAVEN_PROP]
        }
        if (StringUtil.isBlank(prop)) {
            def properties = new Properties()
            properties.load(project.rootProject.file('local.properties').newDataInputStream())
            prop = properties.getProperty(MAVEN_PROP)
        }
        if (StringUtil.isBlank(prop)) {
            def trace = "\n${MAVEN_PROP}为空，有三种途径设置该值(任选一种):\n" +
                    "1. 将${AAR_INFO_PROP} = '****'添加到'aarInfo{ }'中.\n" +
                    "2. 将${MAVEN_PROP}=****添加到'gradle.properties'文件中.\n" +
                    "3. 将${MAVEN_PROP}=****添加到'local.properties'文件中.\n" +
                    "他们的优先级顺序是1 > 2 > 3"
            throw new IllegalArgumentException(trace)
        }
        return prop
    }

    private static void printPublishTaskResult(AarInfoExt aarInfoExt, Project project) {
        project.tasks.whenTaskAdded { Task task ->
            def taskName = task.name.replaceAll(":${project.name}:", "")
            if ((taskName.startsWith("publish") && taskName.endsWith("Repository"))) {
                task.doLast {
                    def aarVersion = aarInfoExt.version
                    def aarArtifactId = aarInfoExt.artifactId
                    def aarGroupId = aarInfoExt.groupId
                    if (!task.state.failure) {
                        Logger.i ''
                        Logger.j "组件发布成功：implementation '${aarGroupId}:${aarArtifactId}:${aarVersion}'"
                        Logger.j "发布地址：${getMavenUrl(aarInfoExt)}"
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
            return aarInfoExt.mavenSnapshotUrl
        } else {
            return aarInfoExt.mavenReleaseUrl
        }
    }
}
