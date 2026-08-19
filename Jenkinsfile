import groovy.json.JsonSlurper

pipeline {
    agent any
    
    tools {
        jdk 'graalvmce-25.0.2'
        nodejs 'nodejs-22'
    }
    
    environment {
        LANG = 'en_US.UTF-8'
        LC_ALL = 'en_US.UTF-8'
        JAVA_HOME = "${env.JAVA_HOME}"
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Debug Env') {
            steps {
                script {
                    echo "Checking JAVA_HOME value:"
                    sh 'echo $JAVA_HOME'
                    sh 'ls -ld $JAVA_HOME'
                    sh '$JAVA_HOME/bin/java -version'
                }
            }
        }
        
        stage('Backend Tests & Quality Gate') {
            steps {
                script {
                    sh './mvnw clean verify'
                }
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Frontend Tests') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run test -- --watch=false'
                }
            }
        }

        stage('NPM Audit') {
            steps {
                // Non-blocking: findings are archived as a continuous-quality-tracking record,
                // not a build gate (npm audit's non-zero exit on findings is swallowed with `|| true`).
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm audit --omit=dev --json > npm-audit-report.json || true'
                    sh 'npm audit --omit=dev || true'
                }
            }
        }

        stage('Archive NPM Audit Report') {
            steps {
                archiveArtifacts artifacts: 'frontend/npm-audit-report.json', fingerprint: true, allowEmptyArchive: true
            }
        }

        stage('OWASP Dependency-Check') {
            steps {
                // Non-blocking, same rationale as the NPM Audit stage above. No NVD API key is
                // configured yet, so the first run syncs the CVE database unauthenticated and can
                // be slow; add one later via withCredentials + -Dnvd.api.key if this becomes a problem.
                sh './mvnw org.owasp:dependency-check-maven:12.1.1:check || true'
            }
        }

        stage('Archive Dependency-Check Report') {
            steps {
                archiveArtifacts artifacts: 'target/dependency-check-report/**', fingerprint: true, allowEmptyArchive: true
            }
        }

        stage('Build Native Binary') {
            steps {
                script {
                    sh './mvnw clean package -Pnative -DskipTests native:compile -Dnative.buildArgs="--static --libc=glibc"'
                }
            }
        }
        
        stage('Archive Binary') {
            steps {
                archiveArtifacts artifacts: 'target/anzenfs', fingerprint: true
            }
        }

        stage('Upload to GitHub') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: '73183d21-d863-4fee-b138-d395cc209e0a', usernameVariable: 'GIT_USER', passwordVariable: 'GITHUB_TOKEN')]) {
                        def repo = "GuillaumeVern/anzen-fs"
                        def binaryPath = "target/anzenfs"
                        def tagName = "v1.0.${BUILD_NUMBER}"



                        def releaseResponse = httpRequest(
                          url: "https://api.github.com/repos/${repo}/releases",
                          customHeaders: [[name: 'Authorization', value: "token ${GITHUB_TOKEN}"]],
                          httpMode: "POST",
                          requestBody: """{"tag_name": "${tagName}", "name": "build ${tagName}", "draft": false, "prerelease": false, "generate_release_notes": true}"""
                        )

                        def releaseID = new groovy.json.JsonSlurper().parseText(releaseResponse.content).id
                        
                        echo "created release with id: ${releaseID}"
                       sh(script: """
                          curl -s -X POST \
                          -H "Authorization: token \$GITHUB_TOKEN" \
                          -H "Content-Type: application/octet-stream" \
                          --data-binary @"${binaryPath}" \
                          "https://uploads.github.com/repos/${repo}/releases/${releaseID}/assets?name=anzenfs"
                    """) 
                    }
                }
            }
        }
    }
}
