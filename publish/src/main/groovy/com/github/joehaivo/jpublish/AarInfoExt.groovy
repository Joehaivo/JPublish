package com.github.joehaivo.jpublish

class AarInfoExt {
    String groupId = ""
    String artifactId = ""
    String version = ""
    String buildVariant = ""
    String userName = System.getProperty("MAVEN_USERNAME")
    String password = System.getProperty("MAVEN_PASSWORD")
    String releaseMavenUrl = System.getProperty("MAVEN_RELEASE_URL")
    String snapshotMavenUrl = System.getProperty("MAVEN_SNAPSHOT_URL")
}

