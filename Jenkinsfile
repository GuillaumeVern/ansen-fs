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