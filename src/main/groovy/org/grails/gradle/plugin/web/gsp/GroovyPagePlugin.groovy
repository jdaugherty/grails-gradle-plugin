/*
 * Copyright 2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gradle.plugin.web.gsp

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.War
import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.util.SourceSets

/**
 * A plugin that adds support for compiling Groovy Server Pages (GSP)
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GroovyPagePlugin implements Plugin<Project> {

    @CompileDynamic
    @Override
    void apply(Project project) {

        project.configurations {
            gspCompile
        }

        project.dependencies {
            gspCompile 'jakarta.servlet:jakarta.servlet-api:6.0.0'
        }

        SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)

        SourceSetOutput output = mainSourceSet?.output
        FileCollection classesDirs = resolveClassesDirs(output, project)
        File destDir = output?.dir("gsp-classes") ?: new File(project.buildDir, "gsp-classes/main")

        Configuration providedConfig = project.configurations.findByName('providedCompile')
        def allClasspath = project.configurations.compileClasspath + project.configurations.gspCompile + classesDirs
        if(providedConfig) {
            allClasspath += providedConfig
        }

        def allTasks = project.tasks

        def compileGroovyPages = allTasks.create("compileGroovyPages", GroovyPageForkCompileTask) {
            destinationDirectory = destDir
            tmpDirPath = getTmpDirPath(project)
            source = project.file("${project.projectDir}/grails-app/views")
            serverpath = "/WEB-INF/grails-app/views/"
        }

        compileGroovyPages.setClasspath( allClasspath )

        def compileWebappGroovyPages = allTasks.create("compileWebappGroovyPages", GroovyPageForkCompileTask) {
            destinationDirectory = destDir
            source = project.file("${project.projectDir}/src/main/webapp")
            tmpDirPath = getTmpDirPath(project)
            serverpath = "/"
        }

        compileWebappGroovyPages.setClasspath( allClasspath )

        registerGrailsExtension(project)

        project.afterEvaluate {
            GrailsExtension grailsExt = project.extensions.getByType(GrailsExtension)
            if (grailsExt.pathingJar && Os.isFamily(Os.FAMILY_WINDOWS)) {
                Jar pathingJar = (Jar) allTasks.findByName('pathingJar')
                allClasspath = project.files("${project.buildDir}/classes/groovy/main", "${project.buildDir}/resources/main", pathingJar.archiveFile)
                compileGroovyPages.dependsOn(pathingJar)
                compileGroovyPages.setClasspath(allClasspath)
                compileWebappGroovyPages.dependsOn(pathingJar)
                compileWebappGroovyPages.setClasspath(allClasspath)
            }
        }

        compileGroovyPages.dependsOn( allTasks.findByName("classes") )
        compileGroovyPages.dependsOn( compileWebappGroovyPages )

        allTasks.withType(War).configureEach { War war ->
            war.dependsOn compileGroovyPages
            war.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            if (war.name == 'bootWar') {
                war.from(destDir) {
                    into("WEB-INF/classes")
                }
            } else if (war.name == 'war') {
                war.from destDir
            }

            if (war.classpath) {
                war.classpath = war.classpath + project.files(destDir)
            } else {
                war.classpath = project.files(destDir)
            }
        }
        allTasks.withType(Jar).configureEach { Jar jar ->
            jar.dependsOn compileGroovyPages
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            if(!(jar instanceof War)) {
                if (jar.name == 'bootJar') {
                    jar.from(destDir) {
                        into("BOOT-INF/classes")
                    }
                } else if (jar.name == 'jar') {
                    jar.from destDir
                }
            }
        }
    }

    protected GrailsExtension registerGrailsExtension(Project project) {
        if (project.extensions.findByName("grails") == null) {
            project.extensions.add("grails", new GrailsExtension(project))
        }
    }

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        output?.classesDirs ?: project.files(new File(project.buildDir, "classes/main"))
    }

    protected String getTmpDirPath(Project project) {
        def tmpdir = new File(project.buildDir as String, "gsptmp")
        return tmpdir.absolutePath
    }

}
