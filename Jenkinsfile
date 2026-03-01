pipeline {
    agent {
        docker { 
            image 'ghcr.io/graalvm/native-image-community:25-ol9'
            args '--env LANG=en_US.UTF-8 --env LC_ALL=en_US.UTF-8'
        }
    }

    environment {
        LANG = 'en_US.UTF-8'
        LC_ALL = 'en_US.UTF-8'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build Native Binary') {
            steps {
                script {
                    sh './mvnw clean package -Pnative -DskipTests'
                }
            }
        }
        
        stage('Prepare Deployment') {
            steps {
                sh 'tar -czf ansenfs-binary.tar.gz target/ansenfs'
                archiveArtifacts artifacts: 'ansenfs-binary.tar.gz', fingerprint: true
            }
        }
    }
}