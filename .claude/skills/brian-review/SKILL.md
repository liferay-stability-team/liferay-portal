---

allowed-tools: [Bash]
argument-hint: "<pr-url>"
description: Review any liferay-portal PR against Brian Chan's rules with the pr-reviewer bot and post the verdict comment.
name: brian-review

---

# Brian Review

Review one pull request against Brian Chan's style rules and post the verdict as a comment, the same way the `pr-reviewer` bot does. The skill drives `pr-reviewer/run.sh`: it fetches the pull request, runs Claude against the style guide and the numbered rules inside the bubblewrap sandbox, and posts a comment estimating the chance that Brian would reject the pull request. It works against any `liferay-portal` repository, taking the organization from the pull request URL. The skill reviews and comments only. It never closes a pull request.

## Input

`${ARGUMENTS}` is a pull request URL of the form `https://github.com/<org>/liferay-portal/pull/<number>`. Parse `<org>` and `<number>` from it. When it is missing or does not match that shape, abort and ask the user for a valid URL.

## Procedure

Run every step from the repository root, `repo_root=$(git rev-parse --show-toplevel)`. The bash blocks run in separate shells, so each block that needs `<org>` and `<number>` parses them from `${ARGUMENTS}` again.

### 1. Ensure the reviewer is present

The reviewer lives on the `pr-review` branch of the stability team repository, `https://github.com/liferay-stability-team/liferay-portal/tree/pr-review`. When `${repo_root}/pr-reviewer/run.sh` is missing, do not just report it. Ask the user **"The pr-reviewer harness is not in this checkout. Should I fetch it from the pr-review branch? (yes/no)"**. On yes, run the commands below and continue. On no, stop.

```bash
git fetch git@github.com:liferay-stability-team/liferay-portal.git pr-review
git checkout FETCH_HEAD -- pr-reviewer .claude/skills/brian-review
```

### 2. Ensure setup is done

Setup builds the sandbox home with the copied Claude credentials. The proxy does not need to be running: `run.sh` starts and stops it automatically per review.

```bash
if [ -f "${HOME}/.ai_sandbox/home/.claude/.credentials.json" ]
then
	echo OK
else
	echo BLOCKED
fi
```

When it prints `BLOCKED`, do not just report it. Ask the user **"Setup is incomplete. Should I run pr-reviewer/setup.sh now? (yes/no)"**. On yes, run `"$(git rev-parse --show-toplevel)/pr-reviewer/setup.sh"` and continue. On no, stop. When `setup.sh` itself fails because Claude Code is not logged in or `gh` is not authenticated, relay its message to the user.

### 3. Decide whether to review

When the pull request already carries a `#bchan-bot-pr-review` comment, review it again only when there are new commits after that comment. Otherwise the work has not changed since the last review.

```bash
[[ ${ARGUMENTS} =~ ^https://github.com/([^/]+)/liferay-portal/pull/([0-9]+) ]]
org="${BASH_REMATCH[1]}"
number="${BASH_REMATCH[2]}"

last_review=$(gh api "repos/${org}/liferay-portal/issues/${number}/comments" \
	--jq "[.[] | select(.body | contains(\"#bchan-bot-pr-review\"))] | last | .created_at // empty")
last_commit=$(gh api "repos/${org}/liferay-portal/pulls/${number}/commits" \
	--jq "last | .commit.committer.date // empty")

if [ -z "${last_review}" ]
then
	echo "REVIEW (never reviewed)"
elif [[ ${last_commit} > ${last_review} ]]
then
	echo "REVIEW (new commits since last review)"
else
	echo "ASK (already reviewed, no new commits)"
fi
```

When it prints `ASK`, ask the user **"This PR was already reviewed and has no new commits since. Review it again anyway? (yes/no)"**. On yes, continue. On no, stop. When it prints `REVIEW`, continue without asking.

### 4. Review and post

This block is self contained. It points the reviewer at the pull request's repository, runs the review, builds the comment exactly as the bot does, and posts it with the marker. The review takes several minutes and uses the invoking user's Claude usage.

```bash
repo_root=$(git rev-parse --show-toplevel)

[[ ${ARGUMENTS} =~ ^https://github.com/([^/]+)/liferay-portal/pull/([0-9]+) ]]
org="${BASH_REMATCH[1]}"
number="${BASH_REMATCH[2]}"

remote=$(git remote --verbose | awk -v repo="${org}/liferay-portal([.]git)?\$" '$2 ~ repo && $3 == "(fetch)" {print $1; exit}')

if [ -z "${remote}" ]
then
	remote="brian-review-${org}"

	git remote add "${remote}" "git@github.com:${org}/liferay-portal.git" 2> /dev/null || \
		git remote set-url "${remote}" "git@github.com:${org}/liferay-portal.git"
fi

review_json=$(_GIT_REMOTE="${remote}" _REPO="${org}/liferay-portal" "${repo_root}/pr-reviewer/run.sh" review "${number}")

_format_tokens() { local n=${1}; if ((n >= 1000)); then echo "$(((n + 500) / 1000))k"; else echo ${n}; fi; }
_article() { local n=${1}; if [[ ${n} =~ ^(8|11|18|8[0-9])$ ]]; then echo an; else echo a; fi; }

usernames=$(
	{
		gh api "repos/${org}/liferay-portal/issues/${number}/comments" --jq ".[].user.login | select(. != null)" 2> /dev/null || true
		gh api "repos/${org}/liferay-portal/pulls/${number}/comments" --jq ".[].user.login | select(. != null)" 2> /dev/null || true
		gh api "repos/${org}/liferay-portal/pulls/${number}/commits" --jq ".[].author.login | select(. != null)" 2> /dev/null || true
		gh api "repos/${org}/liferay-portal/pulls/${number}/reviews" --jq ".[].user.login | select(. != null)" 2> /dev/null || true
	} | grep --invert-match liferay-continuous-integration | sort --unique | tr "[:upper:]" "[:lower:]"
)
at_usernames=$(echo "${usernames}" | sed "s/^/@/" | tr "\n" " " | sed "s/ *$//")

models_count=$(echo "${review_json}" | jq "length")
max_chance=$(echo "${review_json}" | jq "map(.chance) | max")

if [ ${models_count} -eq 0 ]
then
	body="${at_usernames}"$'\n\n'"Brian will most likely merge this PR."
else
	body="${at_usernames}"$'\n\n'"There is $(_article "${max_chance}") ${max_chance}% chance that Brian will reject this PR."

	for ((index = 0; index < models_count; index++))
	do
		chance=$(echo "${review_json}" | jq --raw-output ".[${index}].chance")

		if [ ${chance} -eq 0 ]
		then
			continue
		fi

		input_tokens=$(echo "${review_json}" | jq --raw-output ".[${index}].input_tokens // 0")
		model=$(echo "${review_json}" | jq --raw-output ".[${index}].model")
		output_tokens=$(echo "${review_json}" | jq --raw-output ".[${index}].output_tokens // 0")
		seconds=$(echo "${review_json}" | jq --raw-output ".[${index}].seconds // 0")
		violations=$(echo "${review_json}" | jq --raw-output ".[${index}].violations[]" | sed "s/^/- /")

		body+=$'\n\n'"${model} (${chance}% chance of rejection, $(_format_tokens ${input_tokens})/$(_format_tokens ${output_tokens}) tokens, ${seconds}s):"$'\n'"${violations}"
	done
fi

gh pr comment ${number} \
	--body "${body}"$'\n\n'"#bchan-bot-pr-review" \
	--repo "${org}/liferay-portal"
```

## Expected Output

The posted comment URL, and the comment body echoed back to the user so they see the verdict without leaving the terminal.
