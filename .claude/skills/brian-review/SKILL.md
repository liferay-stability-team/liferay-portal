---

allowed-tools: [Bash]
argument-hint: "<pr-url>"
description: Review a liferay-stability-team PR against Brian Chan's rules with the pr-reviewer bot and post the verdict comment.
name: brian-review

---

# Brian Review

Review one pull request against Brian Chan's style rules and post the verdict as a comment, the same way the `pr-reviewer` bot does. The skill drives `pr-reviewer/run.sh` from the `pr-review` branch: it fetches the pull request, runs Claude against the style guide and the numbered rules inside the bubblewrap sandbox, and posts a comment estimating the chance that Brian would reject the pull request. The skill reviews and comments only. It never closes a pull request.

## Input

`${ARGUMENTS}` is a pull request URL of the form `https://github.com/liferay-stability-team/liferay-portal/pull/<number>`. When it is missing or does not match that shape, abort and ask the user for a valid URL.

## Preconditions

The skill must verify all of the following before it runs anything, and abort with the stated message when a check fails.

- **The pull request belongs to the stability team.** Parse the organization from the URL. When it is not `liferay-stability-team`, warn the user that this skill reviews only `liferay-stability-team/liferay-portal` pull requests and stop. Do not review pull requests from any other repository.

- **The retargeted reviewer is present.** The repository root must contain `pr-reviewer/run.sh` configured with `_REPO=liferay-stability-team/liferay-portal`. When it is missing or still points at another repository, the checkout does not have the `pr-review` branch. Tell the user to get it with `git fetch stability pr-review && git checkout pr-review` and stop.

- **Setup has been run.** The sandbox home, the git remote, and the proxy must exist. When any is missing, tell the user to run `pr-reviewer/setup.sh` and stop.

- **The pull request is not already reviewed.** When a comment already carries the `#bchan-bot-pr-review` marker, report that it was already reviewed and stop, so the skill never comments twice.

## Procedure

Run every step from the repository root.

### Validate and check

```bash
url="${ARGUMENTS}"

if [[ ! ${url} =~ ^https://github.com/([^/]+)/liferay-portal/pull/([0-9]+)$ ]]
then
	echo "Provide a pull request URL like https://github.com/liferay-stability-team/liferay-portal/pull/123."

	exit 1
fi

org="${BASH_REMATCH[1]}"
pr_number="${BASH_REMATCH[2]}"

if [[ ${org} != liferay-stability-team ]]
then
	echo "This skill reviews only liferay-stability-team/liferay-portal. ${org} is not the stability team repository."

	exit 1
fi

repo_root=$(git rev-parse --show-toplevel)

if ! grep --quiet "^_REPO=liferay-stability-team/liferay-portal$" "${repo_root}/pr-reviewer/run.sh" 2> /dev/null
then
	echo "The retargeted reviewer is not in this checkout. Run: git fetch stability pr-review && git checkout pr-review"

	exit 1
fi

if [ ! -f "${HOME}/.ai_sandbox/home/.claude/.credentials.json" ] || \
   ! git -C "${repo_root}" remote get-url stability > /dev/null 2>&1 || \
   ! ss --listening --numeric --tcp | grep --quiet "127.0.0.1:8118"
then
	echo "Setup is incomplete. Run pr-reviewer/setup.sh first."

	exit 1
fi

if gh api "repos/liferay-stability-team/liferay-portal/issues/${pr_number}/comments" \
	--jq ".[].body" 2> /dev/null | grep --quiet "#bchan-bot-pr-review"
then
	echo "Pull request ${pr_number} is already reviewed."

	exit 0
fi
```

### Review

Run the reviewer and capture its JSON. A full review takes a few minutes and uses the invoking user's Claude usage.

```bash
review_json=$("${repo_root}/pr-reviewer/run.sh" review "${pr_number}")
```

### Post the comment

Build the comment body exactly as the bot does and post it with the marker. When the reviewer found no reviewable diff (`[]`), post that Brian will most likely merge the pull request instead.

```bash
_format_tokens() { local n=${1}; if ((n >= 1000)); then echo "$(((n + 500) / 1000))k"; else echo ${n}; fi; }
_article() { local n=${1}; if [[ ${n} =~ ^(8|11|18|8[0-9])$ ]]; then echo an; else echo a; fi; }

usernames=$(
	{
		gh api "repos/liferay-stability-team/liferay-portal/issues/${pr_number}/comments" --jq ".[].user.login | select(. != null)" 2> /dev/null || true
		gh api "repos/liferay-stability-team/liferay-portal/pulls/${pr_number}/comments" --jq ".[].user.login | select(. != null)" 2> /dev/null || true
		gh api "repos/liferay-stability-team/liferay-portal/pulls/${pr_number}/commits" --jq ".[].author.login | select(. != null)" 2> /dev/null || true
		gh api "repos/liferay-stability-team/liferay-portal/pulls/${pr_number}/reviews" --jq ".[].user.login | select(. != null)" 2> /dev/null || true
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

gh pr comment ${pr_number} \
	--body "${body}"$'\n\n'"#bchan-bot-pr-review" \
	--repo liferay-stability-team/liferay-portal
```

## Expected Output

The posted comment URL, and the comment body echoed back to the user so they see the verdict without leaving the terminal.
