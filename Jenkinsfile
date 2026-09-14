pipeline {
    agent any
    tools { jdk 'jdk17'; maven 'maven3' }
    stages {
        stage('Backend Test') {
            steps { dir('backend') { sh 'mvn -B clean verify' } }
        }
        stage('Frontend Build') {
            steps { dir('frontend') { sh 'npm install && npm run build' } }
        }
        stage('Docker Build') {
            steps { sh 'docker build -t moveiq/backend:${BUILD_NUMBER} backend' }
        }
    }
}
