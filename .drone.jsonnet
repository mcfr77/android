local testOnServer(serverBranch) = {
    "kind": "pipeline",
    "type": "docker",
    "name": "tests-"+serverBranch,
    "steps": [
        {
            "name": "gplay",
            "image": "ghcr.io/nextcloud/continuous-integration-android8:2",
            "privileged": true,
            "environment": {
                    "LOG_USERNAME": {
                        "from_secret": "LOG_USERNAME"
                    },
                    "LOG_PASSWORD": {
                        "from_secret": "LOG_PASSWORD"
                    },
                    "GIT_USERNAME": {
                        "from_secret": "GIT_USERNAME" 
                    },
                    "GIT_TOKEN": {
                        "from_secret": "GIT_TOKEN"
                    }
            },
            "commands": [
                "scripts/checkIfRunDrone.sh $GIT_USERNAME $GIT_TOKEN $DRONE_PULL_REQUEST || exit 0",
                "emulator -avd android -no-snapshot -gpu swiftshader_indirect -no-window -no-audio -skin 500x833 &",
                "sed -i s'#<bool name=\"is_beta\">false</bool>#<bool name=\"is_beta\">true</bool>#'g src/main/res/values/setup.xml",
                "sed -i s\"#server#server#\" gradle.properties",
                "./gradlew assembleGplay",
                "./gradlew assembleGplayDebug",
                "scripts/wait_for_emulator.sh",
                "scripts/deleteOldComments.sh 'stable' 'Unit' $DRONE_PULL_REQUEST $GIT_TOKEN",
                "./gradlew jacocoTestGplayDebugUnitTestReport || scripts/uploadReport.sh $LOG_USERNAME $LOG_PASSWORD $DRONE_BUILD_NUMBER 'stable' 'Unit' $DRONE_PULL_REQUEST $GIT_TOKEN",
                "./gradlew installGplayDebugAndroidTest",
                "scripts/wait_for_server.sh 'server'",
                "scripts/deleteOldComments.sh 'stable' 'IT' $DRONE_PULL_REQUEST $GIT_TOKEN",
                "./gradlew createGplayDebugCoverageReport -Pcoverage -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.owncloud.android.utils.ScreenshotTest || scripts/uploadReport.sh $LOG_USERNAME $LOG_PASSWORD $DRONE_BUILD_NUMBER 'stable' 'IT' $DRONE_PULL_REQUEST $GIT_TOKEN",
                "./gradlew combinedTestReport",
           ],
       }
   ],
   "services": [
        {
            "name": "server",
            "image": "ghcr.io/nextcloud/continuous-integration-server:latest # also change in updateScreenshots.sh",
            "environment": {
                    "EVAL": "true"
                },
    "commands": [
         "BRANCH='"+serverBranch+"' /usr/local/bin/initnc.sh",
         "echo 127.0.0.1 server >> /etc/hosts",
         "su www-data -c \"OC_PASS=user1 php /var/www/html/occ user:add --password-from-env --display-name='User One' user1\"",
         "su www-data -c \"OC_PASS=user2 php /var/www/html/occ user:add --password-from-env --display-name='User Two' user2\"",
         "su www-data -c \"OC_PASS=user3 php /var/www/html/occ user:add --password-from-env --display-name='User Three' user3\"",
         "su www-data -c \"php /var/www/html/occ user:setting user2 files quota 1G\"",
         "su www-data -c \"php /var/www/html/occ group:add users\"",
         "su www-data -c \"php /var/www/html/occ group:adduser users user1\"",
         "su www-data -c \"php /var/www/html/occ group:adduser users user2\"",
         "su www-data -c \"git clone -b "+serverBranch+" https://github.com/nextcloud/activity.git /var/www/html/apps/activity\"",
         "su www-data -c \"php /var/www/html/occ app:enable activity\"",
         "su www-data -c \"git clone -b "+serverBranch+" https://github.com/nextcloud/text.git /var/www/html/apps/text\"",
         "su www-data -c \"php /var/www/html/occ app:enable text\"",
         "su www-data -c \"git clone -b "+serverBranch+" https://github.com/nextcloud/end_to_end_encryption.git /var/www/html/apps/end_to_end_encryption\"",
         "su www-data -c \"php /var/www/html/occ app:enable end_to_end_encryption\"",
         "/usr/local/bin/run.sh"
         ]
        }
   ],
   "trigger": {
     "branch": [ 
            "master",
            "stable-*"
    ],
     "event": [
       "push",
       "pull_request"
       ]
   }
};

local allScreenshots() = {
    "kind": "pipeline",
    "type": "docker",
    "name": "default",
    "steps": [
        {
            "name": "build",
            "image": "alpine",
            "commands": [
                "plain",
            ]
        }
    ]
};

## to create .drone.yml run: drone jsonnet --stream
[
testOnServer("stable-16"),
testOnServer("stable-23"),
testOnServer("master"),
allScreenshots(),
]

