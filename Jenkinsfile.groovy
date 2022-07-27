node {
    checkout scm
    env.WORKSPACE = pwd()
    env.SCHEDULE = readFile "${WORKSPACE}/data_migration_schedule.txt"
}

properties(
    [
        buildDiscarder(logRotator(numToKeepStr: '20')),
        parameters(
            [
                separator(name: "data-migration", sectionHeader: "Data Migration Parameters"),
                booleanParam(name: 'UPDATE_JOB', description: 'Enable this if only want to update the configurations', defaultValue: false),
                string(name: 'SOURCE_DB', defaultValue: 'rsxxxe', description: 'Source Database. eg: rs040e'),
                string(name: 'TARGET_DB', defaultValue: 'lx###trn', description: 'Target Database. eg: lx036trn'),
                string(name: 'ROOT_PW', defaultValue: '', description: 'Root Password'),
                string(name: 'TARGET_SERVER', defaultValue: 'lx###trn', description: 'Host ec2 instance. eg: lx036trn'),
                separator(name: "deployment", sectionHeader: "Deployment Parameters"),
                [
                    name: 'artifact_s3_bucket',
                    description: 'The build\'s targeted platform',
                    $class: 'ChoiceParameter',
                    choiceType: 'PT_SINGLE_SELECT',
                    filterLength: 1,
                    filterable: false,
                    randomName: 'choice-parameter-289390844205293',
                    script: [
                        $class: 'GroovyScript',
                        script: [classpath: [], sandbox: false, script: '''\
                            return [
                                \'swms-build-artifacts\',
                                \'swms-build-dev-artifacts\'
                            ]'''.stripIndent()
                        ]
                    ]
                ],
                [
                    name: 'platform',
                    description: 'The build\'s targeted platform',
                    $class: 'ChoiceParameter',
                    choiceType: 'PT_SINGLE_SELECT',
                    filterLength: 1,
                    filterable: false,
                    randomName: 'choice-parameter-289390844205293',
                    script: [
                        $class: 'GroovyScript',
                        script: [classpath: [], sandbox: false, script: '''\
                            return [
                                \'linux\',
                                \'aix_11g_11g\',
                                \'aix_19c_12c\'
                            ]'''.stripIndent()
                        ]
                    ]
                ],
                string(name: 'artifact_version', defaultValue: '50_0', description: 'The swms version to deploy', trim: true),
                [
                    name: 'artifact_name',
                    description: 'The name of the artifact to deploy',
                    $class: 'CascadeChoiceParameter',
                    choiceType: 'PT_SINGLE_SELECT',
                    filterLength: 1,
                    filterable: false,
                    randomName: 'choice-parameter-artifact_name',
                    referencedParameters: 'artifact_s3_bucket, platform, artifact_version',
                    script: [
                        $class: 'GroovyScript',
                        script: [classpath: [], sandbox: false, script: '''\
                                if (platform?.trim() && artifact_version?.trim()) {
                                    def process = "aws s3api list-objects --bucket ${artifact_s3_bucket} --prefix ${platform}-${artifact_version} --query Contents[].Key".execute()
                                    return process.text.replaceAll('"', "").replaceAll("\\n","").replaceAll(" ","").tokenize(',[]')
                                } else {
                                    return []
                                }
                            '''.stripIndent()
                        ]
                    ]
                ],
                string(name: 'dba_masterfile_names', defaultValue: 'R50_0_dba_master.sql', description: 'Comma seperated names of the Privileged master files to apply to the current database. Will not run if left blank. Ran before the master_file', trim: true),
                string(name: 'master_file_retry_count', description: 'Amount of attempts to apply the master file. This is setup to handle circular dependencies by running the same master file multiple times.', defaultValue: '3', trim: true)
            ]
        )
    ]
)

pipeline {
    agent { label 'master' }
    triggers {
        parameterizedCron(SCHEDULE)
    }
    environment {
        TARGET_DB = "${params.TARGET_DB}"
        UPDATE_JOB = "${params.UPDATE_JOB}"
    }
    stages {
        stage('Schedule Configuration') {
            when { environment name: 'UPDATE_JOB', value: 'true' }
            steps {
                script {
                    echo "Job configurations updated successfully"
                }
            }
        }
        stage('SWMS Data Migration') {
            when { environment name: 'UPDATE_JOB', value: 'false' }
            steps {
                echo "Section: SWMS Data Migration - $TARGET_DB"
                script {
                    try {
                        echo "SOURCE_DB: ${params.SOURCE_DB}"
                        echo "TARGET_DB: ${TARGET_DB}"
                        echo "TARGET_SERVER: ${params.TARGET_SERVER}"
                        echo "ROOT_PW: ${params.ROOT_PW}"
                        echo "artifact_s3_bucket: ${params.artifact_s3_bucket}"
                        echo "artifact_version: ${params.artifact_version}"
                        echo "artifact_name: ${params.artifact_name}"
                        echo "dba_masterfile_names: ${params.dba_masterfile_names}"
                        echo "master_file_retry_count: ${params.master_file_retry_count}"
                        // build job: "swms-db-migrate-AIX-RDS-test", parameters: [
                        //     string(name: 'SOURCE_DB', value: "${params.SOURCE_DB}"),
                        //     string(name: 'TARGET_DB', value: "${params.TARGET_DB}"),
                        //     string(name: 'ROOT_PW', value: ""),
                        //     string(name: 'TARGET_SERVER', value: "${params.TARGET_SERVER}"),
                        //     string(name: 'artifact_s3_bucket', value: "${params.artifact_s3_bucket}"),
                        //     string(name: 'platform', value: "${params.platform}"),
                        //     string(name: 'artifact_version', value: "${params.artifact_version}"),
                        //     string(name: 'artifact_name', value: "${params.artifact_name}"),
                        //     string(name: 'dba_masterfile_names', value: "${params.dba_masterfile_names}"),
                        //     string(name: 'master_file_retry_count', value: "${params.master_file_retry_count}")
                        // ]
                        echo "Data Migration Successful!"
                    } catch (e) {
                        echo "Data Migration Failed!"
                        throw e
                    }
                }
            }
        }
    }
}