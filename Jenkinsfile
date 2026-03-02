pipeline {
    agent any
    
    tools {
        graalvm 'graalvm-25' 
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
        
        stage('Archive Binary') {
            steps {
                archiveArtifacts artifacts: 'target/ansenfs', fingerprint: true
            }
        }
    }
}