package edu.wpi.first.gradlerio.frc

import edu.wpi.first.gradlerio.wpi.WPIExtension
import groovy.transform.CompileStatic
import jaci.gradle.deploy.context.DeployContext
import javax.inject.Inject
import jaci.gradle.deploy.target.location.SshDeployLocation
import org.apache.log4j.Logger
import org.gradle.api.Project

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class VMXpi extends FRCCompatibleTarget {

    private Logger log;

    @Inject
    VMXpi(String name, Project project) {
        super(name, project)
        log = Logger.getLogger(this.toString())

        this.directory = '/home/lvuser'

        this.maxChannels = 4

    //    this.onlyIf = { DeployContext ctx ->
    //        verifyOnlyIf(ctx)
    //    }

        // Make a copy of valid image versions so user defined cannot modify the global array
        validImageVersions = WPIExtension.validImageVersions.toList()
    }

    int team
    void setTeam(int team) {
        this.team = team
        setAddresses(
            // These 3 should catch the VMXpi when it's connected to a radio or to the computer via USB
            "roborio-${team}-FRC.local".toString(),                 // Default mDNS
            "10.${(int)(team / 100)}.${team % 100}.2".toString(),   // 10.TE.AM.2 (default RIO IP)
            "172.22.11.2",                                          // USB

            // Remaining cases are for weird environments, like a home network, practice field or otherwise.
            "roborio-${team}-FRC".toString(),                       // Default DNS
            "roborio-${team}-FRC.lan".toString(),                   // LAN mDNS/DNS
            "roborio-${team}-FRC.frc-field.local".toString()        // Practice Field mDNS
        )
    }

    void setAddresses(String... addresses) {
        this.locations.clear()
        addresses.each { String addr ->
            this.addAddress(addr)
        }

        this.getLocations().location(DSDeployLocation) { DSDeployLocation ds ->
            ds.setUser("pi")
            ds.setPassword("raspberry")
            ds.setIpv6(false)
        }
    }

    void addAddress(String address) {
        this.getLocations().location(SshDeployLocation) { SshDeployLocation loc ->
            loc.setAddress(address)
            loc.setIpv6(false)
            loc.setUser("pi")
            loc.setPassword("raspberry")
        }
    }

    boolean verifyOnlyIf(DeployContext ctx) {
        ctx.logger.silent(true)
        if (checkImage) {
            log.info("Checking image...")
            readAndVerifyImage(ctx);
        }
        return true
    }

    void readAndVerifyImage(DeployContext context) {
        final String imageFile = "/etc/natinst/share/scs_imagemetadata.ini"
        final Pattern pattern = Pattern.compile("^IMAGEVERSION\\s=\\s\\\"FRC_roboRIO_([0-9]{4}_v[0-9]+)\\\"")

        String content = context.execute("cat ${imageFile}")
        log.info("Received Image File: ")
        log.info(content)

        boolean imageFound = false
        content.split("\n").each { String line ->
            log.info(line)
            Matcher matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                String imageGroup = matcher.group(1)
                log.info("Matched version: " + imageGroup)
                verifyImageVersion(imageGroup)
                imageFound = true
            }
        }

        if (!imageFound) {
            throw new InvalidImageException()
        }
    }

    boolean checkImage = true
    List<String> validImageVersions = []

    void verifyImageVersion(String image) {
        if (!validImageVersions.contains(image))
            throw new InvalidImageException(image, validImageVersions)
    }

    @Override
    String toString() {
        return "VMXpi[${name}]".toString()
    }
}
