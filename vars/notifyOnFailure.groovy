import com.example.jenkins.Ntfy

/**
 * Send a notification only if the current build did not succeed.
 *
 * Strict-failure variant of {@link notifyJenkinsBuild} — never notifies on
 * SUCCESS, including red→green recoveries. Use this for low-stakes jobs
 * where you only want a ping when something is broken.
 *
 * Example:
 *   pipeline {
 *     ...
 *     post {
 *       always { notifyOnFailure() }
 *     }
 *   }
 */
def call(Map config = [:]) {
    def status = currentBuild.currentResult ?: 'UNKNOWN'
    if (status == 'SUCCESS') {
        return
    }
    notifyJenkinsBuild(config + [onlyOnStatusChange: false])
}
