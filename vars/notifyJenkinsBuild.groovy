import com.example.jenkins.Ntfy

/**
 * Send a status notification for the current Jenkins build.
 *
 * Designed to be called from a pipeline's `post { always {} }` block. Formats
 * a notification with the job name, build number, status, duration, and a
 * link back to the build, and chooses an appropriate emoji + priority based
 * on the result.
 *
 * Default policy is "only on status change": notify on every non-SUCCESS
 * result, but on SUCCESS only when the previous build was not green
 * (i.e. red→green recoveries get notified, continuous green is silent).
 * Override with `onlyOnStatusChange: false` if you want a notification every
 * single run (e.g. a deploy job where you always want confirmation).
 *
 * Optional `context`: a short string describing what the build actually did.
 * Leads the title when supplied, so notifications from multiplexer jobs are
 * actionable rather than merely alarming:
 *
 *   without:  "ansible-runner #2548 — FAILURE"
 *   with:     "obsidian_sync.yml → workstation — FAILURE (ansible-runner #2548)"
 *
 * Example:
 *   pipeline {
 *     ...
 *     post {
 *       always { notifyJenkinsBuild(context: params.PLAYBOOK) }
 *     }
 *   }
 */
def call(Map config = [:]) {
    def topic               = config.topic ?: Ntfy.DEFAULT_TOPIC
    def onlyOnStatusChange  = config.onlyOnStatusChange != null ? config.onlyOnStatusChange : true
    def credentialsId       = config.credentialsId ?: Ntfy.DEFAULT_CREDENTIALS_ID
    def server              = config.server        ?: Ntfy.DEFAULT_SERVER
    // Optional. What this build was actually DOING -- e.g. the playbook a
    // multiplexer job ran. Job name alone is useless for jobs like an Ansible runner
    // that dispatch 60+ different playbooks: "ansible-runner FAILED" does not tell
    // you whether DNS, the proxy, or a VM build broke. When set, this leads the
    // title, because on a phone the title is often all you see.
    def context             = config.context?.toString()?.trim()

    def status   = currentBuild.currentResult ?: 'UNKNOWN'
    def previous = currentBuild.previousBuild?.currentResult

    if (!Ntfy.shouldNotify(status, previous, onlyOnStatusChange)) {
        echo "notifyJenkinsBuild: skipping (status=${status}, previous=${previous}, onlyOnStatusChange=${onlyOnStatusChange})"
        return
    }

    def look = Ntfy.presentation(status)
    def title

    // With context, lead with WHAT ran and let the job name trail -- the useful
    // token needs to survive truncation on a lock screen. Without it, keep the
    // original format so existing callers see no change.
    if (status == 'SUCCESS' && previous != null && previous != 'SUCCESS') {
        title = context ? "${context} — recovered (${previous} → SUCCESS)"
                        : "${env.JOB_NAME} #${env.BUILD_NUMBER} — recovered (${previous} → SUCCESS)"
    } else {
        title = context ? "${context} — ${status} (${env.JOB_NAME} #${env.BUILD_NUMBER})"
                        : "${env.JOB_NAME} #${env.BUILD_NUMBER} — ${status}"
    }

    def message = """\
${context ? "Ran:       ${context}\n" : ''}Status:    ${status}
Duration:  ${currentBuild.durationString.replace(' and counting', '')}
Build:     ${env.BUILD_URL}""".stripIndent()

    notifyNtfy(
        topic:         topic,
        title:         title,
        message:       message,
        tags:          [look.tag],
        priority:      look.priority,
        server:        server,
        credentialsId: credentialsId,
    )
}
