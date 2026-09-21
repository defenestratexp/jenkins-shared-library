# jenkins-shared-library

A small Jenkins [shared pipeline library](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) that sends build status to a self-hosted [ntfy](https://ntfy.sh) server, so pipeline results show up as phone push notifications. It also includes example Job DSL seed scripts that generate Jenkins jobs from plain lists.

It comes from a working homelab Jenkins setup, where every pipeline loads it. Hostnames, repo URLs, credential IDs and secret names in this copy are placeholders (`ntfy.example.com`, `example-org`, `homelab/...`).

## How it works

```
Jenkinsfile post { always { notifyJenkinsBuild() } }
        │
        ▼
vars/notifyJenkinsBuild ── Ntfy.shouldNotify(current, previous)  ── skip continuous green
        │                  Ntfy.presentation(status)               ── emoji tag + priority
        ▼
vars/notifyNtfy ── withCredentials(Secret text) ── curl POST ──▶ https://ntfy.example.com/<topic>
                                                   (a failed publish never fails the build)
```

- `src/com/example/jenkins/Ntfy.groovy` is plain Groovy with no side effects: it maps status to tag and priority, and holds the "notify on status change" rule.
- `vars/` holds the pipeline steps. All side effects (credentials, `sh`, `curl`) live here.
- Message values reach `curl` through environment variables, not Groovy string interpolation into `sh`. Quotes, newlines and `$` in titles and messages are therefore safe.

## Steps

| Step | Purpose |
|------|---------|
| `notifyNtfy(topic:, title:, message:, tags:, priority:, server:, credentialsId:)` | Lowest-level publish helper. Use it when you need full control over the content. |
| `notifyJenkinsBuild(topic:, onlyOnStatusChange:, context:)` | Drop-in for `post { always {} }`. Formats the job name, status, duration and build URL. By default it skips green-after-green builds to avoid notification fatigue. `context` puts what the build actually did (for example, which playbook a multiplexer job ran) at the front of the title. |
| `notifyOnFailure(topic:)` | Strict variant that only notifies when the build is not SUCCESS, with no recovery pings. |

Each step also has a `vars/<step>.txt` help page, which Jenkins shows in the Pipeline Syntax reference.

## Setup (one time, in Jenkins)

### 1. Create the Secret Text credential

Manage Jenkins → Credentials → System → Global → Add Credentials:

- **Kind**: Secret text
- **ID**: `ntfy-admin-token`
- **Secret**: an ntfy access token with publish rights. In the original setup the token is kept in AWS Secrets Manager under `homelab/ntfy/admin-token`:

```bash
aws secretsmanager get-secret-value \
  --secret-id homelab/ntfy/admin-token \
  --region us-west-2 \
  --query SecretString --output text
```

### 2. Register the library

Manage Jenkins → System → Global Pipeline Libraries → Add:

- **Name**: `jenkins-shared-library`
- **Default version**: `main`
- **Retrieval method**: Modern SCM → Git → `https://github.com/defenestratexp/jenkins-shared-library.git`
- Leave "Load implicitly" off. Pipelines opt in with `@Library('jenkins-shared-library') _`.

The default server is `https://ntfy.example.com` (`Ntfy.DEFAULT_SERVER`). Change that constant, or pass `server:` to any step.

## Using it in a pipeline

```groovy
@Library('jenkins-shared-library') _

pipeline {
    agent { label 'ops' }

    stages {
        stage('Build') {
            steps {
                sh 'echo do work'
            }
        }
    }

    post {
        always { notifyJenkinsBuild(context: params.PLAYBOOK) }
    }
}
```

### Custom notifications mid-pipeline

```groovy
stage('Deploy') {
    steps {
        notifyNtfy(
            topic: 'jenkins',
            title: 'Deploy starting',
            message: "Rolling out ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            tags: ['rocket'],
        )
        sh './deploy.sh'
    }
}
```

## Conventions

- **Topics**: publish build status to `jenkins`. Use other topics for special workflows, such as per-step progress from media-ingest pipelines.
- **Priorities**: use `high` only for real failures someone should look at. Use `default` for everything else.
- **Tags**: `white_check_mark` (success), `warning` (unstable), `x` (failure), `no_entry_sign` (aborted), plus decorative ones such as `rocket` or `arrow_down`.
- **Failure containment**: `notifyNtfy` never fails the build on a publish error. Notifications are for observability, not gating.

## Job DSL examples

`examples/job-dsl/seed_job.groovy` is a [Job DSL](https://plugins.jenkins.io/job-dsl/) seed script. Instead of creating jobs by hand in the UI, it loops over a list of applications and generates one `<app>-build` pipeline for each, adding per-app parameters where an app needs them and a weekly rebuild trigger for images whose tooling should not go stale. Adding an application is a one-line, reviewable change.

This is reference material. It points at placeholder repos and is not loaded by the library.

## Layout

```
.
├── src/com/example/jenkins/Ntfy.groovy   # pure helpers (status mapping, notify policy)
├── vars/
│   ├── notifyNtfy.groovy / .txt           # low-level publish
│   ├── notifyJenkinsBuild.groovy / .txt   # post-block build status
│   └── notifyOnFailure.groovy / .txt      # failures only
└── examples/job-dsl/                      # Job DSL seed script examples
```

## Requirements

- Jenkins with the Pipeline and Credentials Binding plugins. Job DSL is needed only for the examples.
- Agents with `bash` and `curl`.
- A reachable ntfy server and an access token.

## Testing

There is no unit-test scaffolding yet. The steps are thin wrappers and are exercised by real pipelines. `Ntfy.shouldNotify` and `Ntfy.presentation` are pure functions, so they are easy to test with [JenkinsPipelineUnit](https://github.com/jenkinsci/JenkinsPipelineUnit) if needed.

## License

MIT. See [LICENSE](LICENSE).
