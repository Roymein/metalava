package com.android.tools.metalava

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import java.io.File

const val CREATE_ARCHIVE_TASK = "createArchive"

fun configurePublishingArchive(
    project: Project,
    publicationName: String,
    repositoryName: String,
    buildId: String,
    distributionDirectory: File
): TaskProvider<Zip> {
    return project.tasks.register(CREATE_ARCHIVE_TASK, Zip::class.java) {
        it.description = "Create a zip of the library in a maven format"
        it.group = "publishing"

        it.from("${distributionDirectory.canonicalPath}/repo")
        it.archiveFileName.set(project.provider {
            "per-project-zips/${project.group}-${project.name}-all-$buildId-${project.version}.zip"
        })
        it.destinationDirectory.set(distributionDirectory)
        it.dependsOn("publish${publicationName}PublicationTo${repositoryName}Repository")
    }
}