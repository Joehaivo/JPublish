package com.github.joehaivo.jpublish

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.logging.text.StyledTextOutput.Style
import org.gradle.internal.logging.text.StyledTextOutputFactory

class Utils {

    static def isAndroidLibrary(project) {
        return project.getPlugins().hasPlugin('com.android.application') || project.getPlugins().hasPlugin('com.android.library')
    }

    static Task createAndroidSourcesJar(Project project, Boolean includeSourceCode = true) {
        return project.tasks.create('androidSourcesJar', Jar) {
            archiveClassifier.set('sources')
            def _android = project.extensions.findByName('android')
            _android.sourceSets.main.java.include("**/*.kt")
            if (!includeSourceCode) {
                excludes = ['*']
            }
            from _android.sourceSets.main.java.getSrcDirs()
        }
    }

    static def logGreen(Project project, String log) {
        def out = project.services.get(StyledTextOutputFactory).create("an-ouput")
        out.style(Style.Error).println(log)
    }

    static def logRed(Project project, String log) {
        def out = project.services.get(StyledTextOutputFactory).create("an-ouput")
        out.style(Style.Failure).println(log)
    }

    static String cmd(String cmd) {
        try {
            Process p = cmd.execute()
            p.waitFor()
            if (p.exitValue() == 0) {
                return p.inputStream?.text?.trim()
            }
        } catch(Exception e) {
            e.printStackTrace()
        }
        return ''
    }
}
