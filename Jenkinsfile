#!groovy
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import static java.time.ZonedDateTime.now

String version = DateTimeFormatter.ofPattern('yyyyMMddHHmm').format(now(ZoneId.of('UTC')))

stage('Build') {

    node {
        checkout scm
        env.commitId = readCommitId()
        env.commitMessage = readCommitMessage()
        stash includes: 'pipeline/*', name: 'pipeline'
        currentBuild.description = "Commit: " + env.commitId.take(6) + "\n" + "Feature: " + readChange();
        if (isDeployBuild()) {
            currentBuild.displayName = "#${currentBuild.number}: Deploy build: version ${version}"
            deliver(version)
        } else if (isQaBuild()) {
            currentBuild.displayName = "#${currentBuild.number}: QA build: version ${version}"
            deliver(version)
        } else if (isQuickBuild()) {
            currentBuild.displayName = "#${currentBuild.number}: Quick build"
            sh "pipeline/build.sh verify"
        }
    }

}

if (isQaBuild()) {

    stage('QA') {
        node {
            unstash 'pipeline'
            node1 = "statistics-${version}-node1"
            node2 = "statistics-${version}-node2"
            try {
                sh "pipeline/environment.sh create ${version}"
                sh "pipeline/environment.sh login ${node1} bash -s -- < pipeline/application.sh createAndVerify ${version}"
                sh "pipeline/environment.sh terminateNode ${node1}"
                sh "pipeline/environment.sh login ${node2} bash -s -- < pipeline/application.sh verifyTestData"
            } finally {
                if (!env.commitMessage.startsWith("qa! keep!"))
                    sh "pipeline/environment.sh delete ${version}"
            }
        }
    }

}

if (isDeployBuild()) {

    stage('Staging deploy') {
        node {
            unstash 'pipeline'
            sh "ssh 'eid-test-docker01.dmz.local' bash -s -- < pipeline/application.sh update ${version}"
        }
    }

    stage('Production deploy') {
        timeout(time: 5, unit: 'DAYS') {
            input "Do you approve deployment of version ${version} to production?"
            node {
                unstash 'pipeline'
                sh "ssh 'eid-prod-docker01.dmz.local' bash -s -- < pipeline/application.sh update ${version}"
            }
        }
    }

}

boolean isDeployBuild() {
    return env.BRANCH_NAME.matches('master')
}

boolean isQaBuild() {
    return env.commitMessage.startsWith("qa!") || env.BRANCH_NAME.matches(/feature\/qa\/(\w+-\w+)/)
}

boolean isQuickBuild() {
    return env.BRANCH_NAME.matches(/(feature|bugfix)\/(\w+-\w+)/)
}

void deliver(String version) {
    sh(script: "pipeline/build.sh deliver ${version}")
}

void verify() {
    sh(script: "pipeline/build.sh verify")
}

String readChange() {
    return sh(returnStdout: true, script: 'pipeline/branch.sh change').trim()
}

String readCommitId() {
    return sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
}

String readCommitMessage() {
    return sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}
