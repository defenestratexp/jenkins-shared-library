package com.example.jenkins

/**
 * Helpers shared between vars/* steps. Keep IO/side-effects in `vars/` so this
 * stays plain Groovy and easy to unit-test if we ever want to.
 */
class Ntfy implements Serializable {
    private static final long serialVersionUID = 1L

    static final String DEFAULT_SERVER = 'https://ntfy.example.com'
    static final String DEFAULT_CREDENTIALS_ID = 'ntfy-admin-token'
    static final String DEFAULT_TOPIC = 'jenkins'

    /** Map a Jenkins build result to a (tag, priority) pair. */
    static Map presentation(String status) {
        switch (status) {
            case 'SUCCESS':  return [tag: 'white_check_mark', priority: 'default']
            case 'UNSTABLE': return [tag: 'warning',          priority: 'default']
            case 'FAILURE':  return [tag: 'x',                priority: 'high']
            case 'ABORTED':  return [tag: 'no_entry_sign',    priority: 'default']
            default:         return [tag: 'question',         priority: 'default']
        }
    }

    /**
     * Decide whether to send a notification given the current and previous
     * build results.
     *
     * Default policy: notify on FAILURE / UNSTABLE / ABORTED always; notify on
     * SUCCESS only when the previous build was not SUCCESS (red→green
     * recovery). This avoids the constant-green spam that makes notifications
     * stop being read.
     */
    static boolean shouldNotify(String current, String previous, boolean onlyOnStatusChange) {
        if (!onlyOnStatusChange) {
            return true
        }
        if (current != 'SUCCESS') {
            return true
        }
        // current == SUCCESS: notify only if recovering
        return previous != null && previous != 'SUCCESS'
    }
}
