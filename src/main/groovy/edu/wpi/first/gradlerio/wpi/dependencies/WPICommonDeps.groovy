package edu.wpi.first.gradlerio.wpi.dependencies

import edu.wpi.first.gradlerio.wpi.WPIExtension
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.internal.os.OperatingSystem

class WPICommonDeps implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            if (!project.hasProperty("wpi-no-local-maven")) {
                project.repositories.maven { MavenArtifactRepository repo ->
                    repo.name = "WPILocal"
                    repo.url = "${project.extensions.getByType(WPIExtension).getFrcHome()}/maven"
                }
            }

            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = "WPI"
                repo.url = "http://first.wpi.edu/FRC/roborio/maven/development"
            }

            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = "OpenRIO"
                repo.url = "https://raw.githubusercontent.com/Open-RIO/Maven-Mirror/master/m2"
            }

            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = "Jaci"
                repo.url = "http://dev.imjac.in/maven/"
            }

            // TODO: 2019
//        project.repositories.maven { repo ->
//            repo.name = "KauaiLabs"
//            repo.url = "http://www.kauailabs.com/maven2"
//        }
        }

        apply_halsim_extensions(project, project.extensions.getByType(WPIExtension))
    }

    void apply_halsim_extensions(Project project, WPIExtension wpi) {
        def nativeclassifier = (
                OperatingSystem.current().isWindows() ?
                System.getProperty("os.arch") == 'amd64' ? 'windowsx86-64' : 'windowsx86' :
                OperatingSystem.current().isMacOsX() ? "osxx86-64" :
                OperatingSystem.current().isLinux() ? "linuxx86-64" :
                null
        )

        if (nativeclassifier != null) {
            project.dependencies.ext.sim = [
                print: {
                    ["edu.wpi.first.halsim:halsim-print:${wpi.wpilibVersion}:${nativeclassifier}@zip"]
                },
                nt_ds: {
                    ["edu.wpi.first.halsim.ds:halsim-ds-nt:${wpi.wpilibVersion}:${nativeclassifier}@zip"]
                },
                nt_readout: {
                    ["edu.wpi.first.halsim:halsim-lowfi:${wpi.wpilibVersion}:${nativeclassifier}@zip"]
                }
            ]
        }
    }
}
