package edu.wpi.first.gradlerio.wpi

import edu.wpi.first.gradlerio.wpi.dependencies.WPIDependenciesPlugin
import edu.wpi.first.gradlerio.wpi.dependencies.WPINativeJsonDepRules
import edu.wpi.first.gradlerio.wpi.dependencies.tools.WPIToolsPlugin
import edu.wpi.first.toolchain.ToolchainExtension
import edu.wpi.first.toolchain.ToolchainPlugin
import edu.wpi.first.toolchain.roborio.RoboRioToolchainPlugin
import edu.wpi.first.toolchain.raspbian.RaspbianToolchainPlugin
import edu.wpi.first.vscode.GradleVsCode
import groovy.transform.CompileStatic
import jaci.gradle.ActionWrapper
import jaci.gradle.log.ETLogger
import jaci.gradle.log.ETLoggerFactory
import jaci.gradle.toolchains.ToolchainsPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.internal.logging.text.StyledTextOutput
import edu.wpi.first.nativeutils.NativeUtils
import edu.wpi.first.nativeutils.NativeUtilsExtension
import edu.wpi.first.toolchain.configurable.CrossCompilerConfiguration

@CompileStatic
class WPIPlugin implements Plugin<Project> {
    ETLogger logger

    void apply(Project project) {
        WPIExtension wpiExtension = project.extensions.create("wpi", WPIExtension, project)
        logger = ETLoggerFactory.INSTANCE.create(this.class.simpleName)

        project.pluginManager.apply(WPIToolsPlugin)
        project.pluginManager.apply(WPIDependenciesPlugin)

        project.plugins.withType(ToolchainsPlugin).all {
            logger.info("DeployTools Native Project Detected".toString())
            project.pluginManager.apply(ToolchainPlugin)
            project.pluginManager.apply(RaspbianToolchainPlugin)
            project.pluginManager.apply(NativeUtils)
            project.pluginManager.apply(WPINativeCompileRules)

            NativeUtilsExtension nte = project.extensions.getByType(NativeUtilsExtension)
            nte.withRaspbian()
            nte.addWpiNativeUtils()

            ToolchainExtension te = project.extensions.getByType(ToolchainExtension)
            te.crossCompilers.named(nte.wpi.platforms.raspbian, new ActionWrapper({ CrossCompilerConfiguration c ->
                c.optional.set(false)
            }))

            nte.wpi.addWarnings()
            nte.setSinglePrintPerPlatform()

            project.afterEvaluate {
                def ntExt = project.extensions.getByType(NativeUtilsExtension)
                def wpiExt = project.extensions.getByType(WPIExtension)
                ntExt.wpi.skipRaspbianAsDesktop = true //Removes the raspbian target from the typical dependency lists
                ntExt.wpi.configureDependencies {
                    it.wpiVersion = wpiExt.wpilibVersion
                    it.niLibVersion = wpiExt.niLibrariesVersion
                    it.opencvVersion = wpiExt.opencvVersion
                    it.googleTestVersion = wpiExt.googleTestVersion
                    it.imguiVersion = wpiExt.imguiVersion
                }

                //Modifies NativeUtils HAL groupId and Version to adjust to the VMX-Pi HAL
                def hal_config = nte.dependencyConfigs.getByName("hal")
                if (hal_config != null) {
                    hal_config.setGroupId("com.kauailabs.vmx.first.hal")
                    hal_config.setVersion(project.extensions.getByType(WPIExtension).vmxVersion)
                }

                //Adds the VMX-Pi Platform library to the NativeUtils dependency configs
                def mau_config = nte.dependencyConfigs.create("mau")
                if (mau_config != null) {
                    mau_config.setArtifactId("vmxpi-hal")
                    mau_config.setGroupId("com.kauailabs.vmx.platform")
                    mau_config.setHeaderClassifier("headers")
                    mau_config.setExt("zip")
                    mau_config.setVersion(project.extensions.getByType(WPIExtension).vmxPlatformVersion)
                    mau_config.setSharedUsedAtRuntime(true)
                    mau_config.getSharedPlatforms().add("linuxraspbian")
                }

                //Creating dependency list for the VMX-Pi target (only used if skipRaspbianAsDesktop = true)
                //CombinedDependencyConfig is only created for wpilib_executable_shared with the raspbian target
                //Other combinedDependencyConfigs do exist in NativeUtils, but currently unsupported for VMX-Pi
                def wpilib_executable_shared_mau = nte.combinedDependencyConfigs.create("wpilib_executable_shared_mau")
                if (wpilib_executable_shared_mau != null) {
                    wpilib_executable_shared_mau.setLibraryName("wpilib_executable_shared")
                    wpilib_executable_shared_mau.getTargetPlatforms().add("linuxraspbian")
                    def deps = wpilib_executable_shared_mau.getDependencies()
                    deps.add("wpilibc_shared")
                    deps.add("ntcore_shared")
                    deps.add("hal_shared")
                    deps.add("wpiutil_shared")
                    deps.add("mau_shared")
                }
            }

            project.pluginManager.apply(GradleVsCode)
            project.pluginManager.apply(WPINativeJsonDepRules)
        }

        project.tasks.register("wpi") { Task task ->
            task.group = "GradleRIO"
            task.description = "Print all versions of the wpi block"
            task.doLast {
                wpiExtension.versions().each { String key, Tuple tup ->
                    println "${tup.first()}: ${tup[1]} (${key})"
                }
            }
        }

        project.tasks.register("explainRepositories") { Task task ->
            task.group = "GradleRIO"
            task.description = "Explain all Maven Repos present on this project"
            task.doLast {
                explainRepositories(project)
            }
        }

        project.afterEvaluate {
            addMavenRepositories(project, wpiExtension)
        }
    }

    void explainRepositories(Project project) {
        project.repositories.withType(MavenArtifactRepository).each { MavenArtifactRepository repo ->
            println("${repo.name} -> ${repo.url}")
        }
    }

    void addMavenRepositories(Project project, WPIExtension wpi) {
        if (wpi.maven.useLocal) {
            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = "WPILocal"
                repo.url = "${project.extensions.getByType(WPIExtension).getFrcHome()}/maven"
            }
        }

        if (wpi.maven.useFrcMavenLocalDevelopment) {
            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = "FRCDevelopmentLocal"
                repo.url = "${System.getProperty('user.home')}/releases/maven/development"
            }
        }

        if (wpi.maven.useFrcMavenLocalRelease) {
            project.repositories.maven { MavenArtifactRepository repo ->
                repo.name = "FRCReleaseLocal"
                repo.url = "${System.getProperty('user.home')}/releases/maven/release"
            }
        }

        def sortedMirrors = wpi.maven.sort { it.priority }

        // If enabled, the development branch should have a higher weight than the release
        // branch.
        if (wpi.maven.useDevelopment) {
            sortedMirrors.each { WPIMavenRepo mirror ->
                if (mirror.development != null)
                    project.repositories.maven { MavenArtifactRepository repo ->
                        repo.name = "WPI${mirror.name}Development"
                        repo.url = mirror.development
                    }
            }
        }

        sortedMirrors.each { WPIMavenRepo mirror ->
            if (mirror.release != null)
                project.repositories.maven { MavenArtifactRepository repo ->
                    repo.name = "WPI${mirror.name}Release"
                    repo.url = mirror.release
                }
        }

        // Maven Central is needed for EJML and JUnit
        if (wpi.maven.useMavenCentral) {
            project.repositories.mavenCentral()
        }
    }
}
