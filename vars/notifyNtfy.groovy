import com.example.jenkins.Ntfy

/**
 * Send a single notification to ntfy. The lowest-level step in this library —
 * use {@link notifyJenkinsBuild} or {@link notifyOnFailure} for the common
 * Jenkins post-block use cases.
 *
 * Example:
 *   notifyNtfy(
 *     topic: 'jenkins',
 *     title: 'Deploy finished',
 *     message: "Production rollout complete\n${env.BUILD_URL}",
 *     tags: ['white_check_mark', 'rocket'],
 *     priority: 'default',
 *   )
 *
 * Parameters:
 *   topic         (String, default 'jenkins') — ntfy topic to publish to
 *   title         (String, default JOB_NAME)  — header line in the notification
 *   message       (String, required)          — body text
 *   tags          (List<String>, default [])  — ntfy emoji shortcodes / tags
 *   priority      (String, default 'default') — min, low, default, high, max
 *   server        (String, default DEFAULT_SERVER)        — base URL of the ntfy instance
 *   credentialsId (String, default DEFAULT_CREDENTIALS_ID) — Jenkins Secret Text credential ID for the bearer token
 *
 * Notes:
 *   - A failed publish does NOT fail the build — it logs a warning and moves
 *     on. The notification system shouldn't be a build-failure escalation.
 *   - All values are passed via env vars rather than Groovy string interp into
 *     `sh`, so embedded quotes / newlines / `$` in messages are safe.
 */
def call(Map config) {
    def topic         = config.topic         ?: Ntfy.DEFAULT_TOPIC
    def title         = config.title         ?: env.JOB_NAME ?: 'Jenkins'
    def message       = config.message       ?: ''
    def tags          = (config.tags ?: []).join(',')
    def priority      = config.priority      ?: 'default'
    def server        = config.server        ?: Ntfy.DEFAULT_SERVER
    def credentialsId = config.credentialsId ?: Ntfy.DEFAULT_CREDENTIALS_ID

    withCredentials([string(credentialsId: credentialsId, variable: 'NTFY_TOKEN')]) {
        withEnv([
            "NTFY_SERVER=${server}",
            "NTFY_TOPIC=${topic}",
            "NTFY_TITLE=${title}",
            "NTFY_MESSAGE=${message}",
            "NTFY_TAGS=${tags}",
            "NTFY_PRIORITY=${priority}",
        ]) {
            int rc = sh(
                returnStatus: true,
                script: '''#!/usr/bin/env bash
                    set -u
                    args=(-fsS -m 10 -X POST -o /dev/null
                        -H "Authorization: Bearer $NTFY_TOKEN"
                        -H "Title: $NTFY_TITLE"
                        -H "Priority: $NTFY_PRIORITY"
                    )
                    if [ -n "$NTFY_TAGS" ]; then
                        args+=(-H "Tags: $NTFY_TAGS")
                    fi
                    args+=(-d "$NTFY_MESSAGE" "$NTFY_SERVER/$NTFY_TOPIC")
                    curl "${args[@]}"
                '''
            )
            if (rc != 0) {
                echo "notifyNtfy: publish to '${topic}' failed (curl exit ${rc}); continuing without failing the build"
            }
        }
    }
}
