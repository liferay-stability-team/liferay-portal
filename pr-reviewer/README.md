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

The script verifies the prerequisites, copies your Claude credentials into an isolated sandbox home at `~/.ai_sandbox/home`, adds a `stability` git remote pointing at the team repository, and starts the local proxy on `127.0.0.1:8118`. It does not change your real home directory or your Claude login.

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

## How it works

The reviewer fetches the pull request branch from the `stability` remote, builds a filtered diff that excludes generated and binary files, and then launches Claude inside a bubblewrap sandbox. The sandbox exposes only this directory, a read only copy of the repository for `git grep`, the diff, and your Claude install and credentials. All of the sandbox network traffic leaves through the proxy on port 8118. Claude returns a JSON object with a rejection chance and a list of violations, which the reviewer formats into the comment above.

When you run the looping `check` command, the reviewer also closes any open pull request that has rebase conflicts, asking the author to resend it. It does not close pull requests for style violations.

## Configuration

The settings live in the block at the bottom of `run.sh`.

- `_REPO` is the repository the reviewer reads and comments on.
- `_GIT_REMOTE` and `_BASE_BRANCH` name the git remote and base branch used to fetch pull requests and compute the diff.
- `_MODELS` is the list of models to run. Only `sonnet-4.6`, which uses the `claude` command, works out of the box. The other entries require `opencode`.
- `_HTTPS_PROXY` is the proxy address. Set it to the empty string to send Claude traffic directly, without the sandbox proxy.
- `_REVIEW_TIMEOUT_MINUTES` bounds a single review.

## The proxy

The bundled `proxy.py` is a plain tunnel. It funnels the sandbox traffic through one port but does not restrict where that traffic can go. For real egress control, install privoxy or tinyproxy on `127.0.0.1:8118` with an allowlist that permits only `api.anthropic.com`, and leave `_HTTPS_PROXY` pointed at it. The bundled proxy does not survive a reboot, so rerun `./setup.sh` or start it with `python3 proxy.py` after restarting.

## Troubleshooting

If a review fails, the raw model output is saved at `/tmp/pr-reviewer/<pr>/sonnet-4.6.raw` and the parsed result at `/tmp/pr-reviewer/<pr>/sonnet-4.6.json`. The proxy log is at `/tmp/pr-reviewer-proxy.log`. If the reviewer reports that it cannot authenticate, run `claude` once outside the sandbox to refresh your login and rerun `./setup.sh` to recopy the credentials.
