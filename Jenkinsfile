pipeline {
    agent any
    
    tools {
        jdk 'graalvmce-25.0.2' 
    }
    
    environment {
        LANG = 'en_US.UTF-8'
        LC_ALL = 'en_US.UTF-8'
        HOME = '/root'
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
                    // --- AJOUTE CES LIGNES ---
                    echo "Checking JAVA_HOME value:"
                    sh 'echo $JAVA_HOME'                // Affiche le chemin
                    sh 'ls -ld $JAVA_HOME'             // Vérifie si le dossier existe et ses droits
                    sh '$JAVA_HOME/bin/java -version' // Vérifie si java est exécutable
                    // -------------------------
                }
            }
        }
        
        stage('Build Native Binary') {
            steps {
                script {
                    sh './mvnw clean package -Pnative -DskipTests native:compile'
                }
            }
        }
        
        stage('Archive Binary') {
            steps {
                archiveArtifacts artifacts: 'target/ansenfs', fingerprint: true
            }
        }

        stage('Upload to GitHub') {
    steps {
        script {
            withCredentials([usernamePassword(credentialsId: '73183d21-d863-4fee-b138-d395cc209e0a', usernameVariable: 'GIT_USER', passwordVariable: 'GITHUB_TOKEN')]) {
                def repo = "GuillaumeVern/ansen-fs"
                def binaryPath = "target/ansenfs"
                def tagName = "v1.0.${BUILD_NUMBER}"

                sh "curl -X POST -H 'Authorization: token ${GITHUB_TOKEN}' -H 'Content-Type: application/json' -d '{\"tag_name\": \"${tagName}\", \"name\": \"Build ${tagName}\", \"draft\": false, \"prerelease\": false}' https://api.github.com/repos/${repo}/releases"

                sh "curl -X POST -H 'Authorization: token ${GITHUB_TOKEN}' -H 'Content-Type: application/octet-stream' --data-binary @${binaryPath} https://uploads.github.com/repos/${repo}/releases/tags/${tagName}/assets?name=ansenfs"
            }
        }
    }
}
    }
}