import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

configure<PublishingExtension> {
    repositories {
        maven {
            name = "delivery"
            url = uri(rootProject.layout.projectDirectory.dir("dist/repository"))
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            pom {
                name.set("SceneView Native - ${project.name}")
                description.set("Independent Android View/XML port of SceneView 4.35.0")
                url.set("https://github.com/JLAndroid/sceneview_native")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("JLAndroid")
                        name.set("JLAndroid")
                        url.set("https://github.com/JLAndroid")
                    }
                }
                scm {
                    url.set("https://github.com/JLAndroid/sceneview_native")
                    connection.set("scm:git:https://github.com/JLAndroid/sceneview_native.git")
                    developerConnection.set("scm:git:ssh://git@github.com/JLAndroid/sceneview_native.git")
                }
            }
            afterEvaluate { from(components["release"]) }
        }
    }
}
