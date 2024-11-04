#!/usr/bin/env groovy

// SPDX-FileCopyrightText: 2022-2024 TII (SSRC) and the Ghaf contributors
// SPDX-License-Identifier: Apache-2.0

////////////////////////////////////////////////////////////////////////////////

def REPO_URL = 'https://github.com/tiiuae/ghaf/'
def WORKDIR  = 'ghaf'

// Utils module will be loaded in the first pipeline stage
def utils = null

// Which attribute of the flake to evaluate for building
def flakeAttr = ".#hydraJobs"

// Target names must be direct children of the above
def targets = [
  [ target: "docs.aarch64-linux",
  ],
  [ target: "docs.x86_64-linux",
  ],
  [ target: "generic-x86_64-debug.x86_64-linux",
    archive: true, hwtest_device: "nuc"
  ],
  [ target: "lenovo-x1-carbon-gen11-debug.x86_64-linux",
    archive: true, hwtest_device: "lenovo-x1"
  ],
  [ target: "microchip-icicle-kit-debug-from-x86_64.x86_64-linux",
    archive: true, hwtest_device: "riscv"
  ],
  [ target: "nvidia-jetson-orin-agx-debug.aarch64-linux",
    archive: true, hwtest_device: "orin-agx"
  ],
  [ target: "nvidia-jetson-orin-agx-debug-from-x86_64.x86_64-linux",
    archive: true, hwtest_device: "orin-agx"
  ],
  [ target: "nvidia-jetson-orin-nx-debug.aarch64-linux",
    archive: true, hwtest_device: "orin-nx"
  ],
  [ target: "nvidia-jetson-orin-nx-debug-from-x86_64.x86_64-linux",
    archive: true, hwtest_device: "orin-nx"
  ],
]

// Container for stage definitions
def target_jobs = [:]

properties([
  githubProjectProperty(displayName: '', projectUrlStr: REPO_URL),
])

pipeline {
  agent { label 'built-in' }
  triggers {
     pollSCM('* * * * *')
  }
  options {
    disableConcurrentBuilds()
    timestamps ()
    buildDiscarder(logRotator(numToKeepStr: '100'))
  }
  stages {
    stage('Checkout') {
      steps {
        script { utils = load "utils.groovy" }
        dir(WORKDIR) {
          checkout scmGit(
            branches: [[name: 'main']],
            extensions: [cleanBeforeCheckout()],
            userRemoteConfigs: [[url: REPO_URL]]
          )
          script {
            env.TARGET_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
            env.ARTIFACTS_REMOTE_PATH = "${env.JOB_NAME}/build_${env.BUILD_ID}-commit_${env.TARGET_COMMIT}"
          }
        }
      }
    }

    stage('Evaluate') {
      steps {
        dir(WORKDIR) {
          script {
            target_jobs = utils.create_parallel_stages(flakeAttr, targets)
          }
        }
      }
    }

    stage('Build targets') {
      steps {
        script {
          parallel target_jobs
        }
      }
    }
  }

  post {
    failure {
      script {
        githublink="https://github.com/tiiuae/ghaf/commit/${env.TARGET_COMMIT}"
        servername = sh(script: 'uname -n', returnStdout: true).trim()
        echo "Server name:$servername"
        if (servername=="ghaf-jenkins-controller-dev") {
          serverchannel="ghaf-jenkins-builds-failed"
          echo "Slack channel:$serverchannel"
          message= "FAIL build: ${servername} ${env.JOB_NAME} [${env.BUILD_NUMBER}] (<${githublink}|The commits>)  (<${env.BUILD_URL}|The Build>)"
          slackSend (
            channel: "$serverchannel",
            color: '#36a64f', // green
            message: message
          )
        }
        else {
          echo "Slack message not sent (failed build). Check pipeline slack configuration!"
        }
      }
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
