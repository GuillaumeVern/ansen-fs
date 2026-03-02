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
                    sh './mvnw clean package -Pnative -DskipTests native:compile
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