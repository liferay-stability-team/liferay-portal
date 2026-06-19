# PR Reviewer for liferay-stability-team

This is Brian Chan's PR reviewer, retargeted at `liferay-stability-team/liferay-portal`. It reads each open pull request, runs Claude against the style guide and the numbered rules in this directory inside a bubblewrap sandbox, and posts a comment estimating the chance that Brian would reject the pull request, along with the specific rule violations it found. Any member of the team can run it from their own machine after a one time setup.

## What it posts

For every open pull request that it has not already reviewed, the reviewer posts a single comment that mentions the participants and reads like this:

> @author
>
> There is a 70% chance that Brian will reject this PR.
>
> sonnet-4.6 (70% chance of rejection, 76k/15k tokens, 270s):
> - Rule 602 (randomize unasserted test values): ...

Each comment ends with the hidden marker `#bchan-bot-pr-review`, which is how the reviewer recognizes a pull request it has already handled and avoids commenting twice.

## Prerequisites

You need the following installed and authenticated on your machine.

- `bubblewrap` (the `bwrap` command), which provides the sandbox.
- `gh`, the GitHub CLI, authenticated with `gh auth login` against an account that can comment on the team repository.
- Claude Code, installed with the native installer so that it lives under `~/.local`, and logged in at least once by running `claude`.
- `git`, `jq`, and `python3`.

On Fedora the system packages are `sudo dnf install bubblewrap gh git jq python3`.

## One time setup

Clone the team repository, check out this branch, and run the setup script from this directory.

```
cd pr-reviewer
./setup.sh
```

The script verifies the prerequisites, copies your Claude credentials into an isolated sandbox home at `~/.ai_sandbox/home`, and adds a `stability` git remote pointing at the team repository. It does not change your real home directory or your Claude login. You do not start the proxy yourself: `run.sh` starts and stops it automatically per review.

## Usage

Run everything from this directory.

```
./run.sh review <pr>             Review one pull request and print its JSON, without commenting.
./run.sh --dry-run check <pr>    Review one pull request and print the comment it would post, without posting.
./run.sh check <pr>              Review one pull request and post the comment.
./run.sh check                   Review every open pull request, then poll for new ones in a loop.
./run.sh kill                    Stop running reviewers and clear stale locks.
```

Start with `review` or `--dry-run check` on a single pull request to see the output before you let it comment. A full review takes a few minutes and uses your Claude usage.

## The brian-review skill

For interactive use there is a Claude Code skill, `brian-review`, that wraps the reviewer. From a Claude Code session in a `liferay-portal` checkout, run it with a pull request URL.

```
/brian-review https://github.com/<org>/liferay-portal/pull/<number>
```

The skill takes the organization from the URL and works against any `liferay-portal` repository, not only the stability team, by overriding `_REPO` and `_GIT_REMOTE` for that run. Before reviewing it confirms the reviewer and the setup are in place, and when either is missing it asks whether to fetch the `pr-review` branch or run `setup.sh` for you rather than only printing instructions. When the pull request was already reviewed it reviews again only when there are new commits since the last comment, and otherwise asks first. Like the `review` command it reviews and comments only, and never closes a pull request.

## How it works

The reviewer fetches the pull request branch from the `stability` remote, builds a filtered diff that excludes generated and binary files, and then launches Claude inside a bubblewrap sandbox. The sandbox exposes only this directory, a read only copy of the repository for `git grep`, the diff, and your Claude install and credentials. All of the sandbox network traffic leaves through the proxy on port 8118. Claude returns a JSON object with a rejection chance and a list of violations, which the reviewer formats into the comment above.

When you run the looping `check` command, the reviewer also closes any open pull request that has rebase conflicts, asking the author to resend it. It does not close pull requests for style violations.

## Configuration

The settings are the variables in the block at the bottom of `run.sh`. Each one reads an environment variable of the same name and falls back to the default below, so you can override any of them for a single run without editing the file. This is how the `brian-review` skill points the reviewer at a different organization.

```
_REPO=other-org/liferay-portal _GIT_REMOTE=other ./run.sh review 123
```

| Variable | Default | Purpose |
| --- | --- | --- |
| `_REPO` | `liferay-stability-team/liferay-portal` | The repository the reviewer reads pull requests from and comments on. |
| `_GIT_REMOTE` | `stability` | The git remote whose `pull/<n>/head` refs and base branch are fetched. Must point at `_REPO`. |
| `_BASE_BRANCH` | `master` | The base branch used for the merge base and the reviewed diff. |
| `_MODELS` | `(sonnet-4.6)` | The models to run, as a bash array. Only `sonnet-4.6` (the `claude` command) works out of the box; the commented entries need `opencode`. Listing more than one runs them in parallel and reports each. |
| `_HTTPS_PROXY` | `localhost:8118` | The proxy the sandboxed Claude uses. Set it to the empty string to send traffic directly, with no proxy. |
| `_REVIEW_TIMEOUT_MINUTES` | `20` | The hard timeout for a single review. |
| `_SANDBOX_HOME` | `${HOME}/.ai_sandbox/home` | The isolated home bound into the sandbox, holding the copied Claude credentials. |
| `_LIFERAY_PORTAL_DIR` | the repository root | The checkout bound read only into the sandbox for `git grep`. |
| `_IGNORED_FILENAMES` | `CHANGELOG.md package-lock.json package.json` | Exact file names dropped from the reviewed diff. |
| `_IGNORED_PATTERNS` | the `Language_*.properties` regex | Path regexes dropped from the diff. |
| `_IGNORED_SUFFIXES` | `css js jsx lock ...` | File extensions dropped from the diff entirely. |
| `_NAME_ONLY_SUFFIXES` | `bmp gif ico jpeg jpg png svg webp` | Image extensions included as a file name only, with no content. |

Files marked `@generated` are always excluded from the diff, regardless of these lists.

## The proxy

`run.sh` manages the proxy automatically. At the start of a review it checks `127.0.0.1:8118`: when nothing is listening it starts the bundled `proxy.py`, and it stops that proxy when the run exits. When a proxy is already listening it uses it and leaves it alone, so a long running instance you started yourself is never touched. The sandbox itself is always ephemeral: a fresh bubblewrap process per review that is torn down when the review finishes.

The bundled `proxy.py` is a plain tunnel. It funnels the sandbox traffic through one port but does not restrict where that traffic can go. For real egress control, run privoxy or tinyproxy on `127.0.0.1:8118` with an allowlist that permits only `api.anthropic.com`; `run.sh` will detect it and route through it without managing its lifecycle. To send Claude traffic directly with no proxy at all, set `_HTTPS_PROXY` to the empty string.

## Troubleshooting

If a review fails, the raw model output is saved at `/tmp/pr-reviewer/<pr>/sonnet-4.6.raw` and the parsed result at `/tmp/pr-reviewer/<pr>/sonnet-4.6.json`. The proxy log is at `/tmp/pr-reviewer-proxy.log`. If the reviewer reports that it cannot authenticate, run `claude` once outside the sandbox to refresh your login and rerun `./setup.sh` to recopy the credentials.
