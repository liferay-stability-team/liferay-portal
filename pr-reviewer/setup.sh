#!/bin/bash

set -o errexit
set -o nounset
set -o pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

function main {
	_check_prerequisites
	_check_authentication
	_build_sandbox_home
	_add_git_remote
	_start_proxy

	echo ""
	echo "Setup complete. From this directory, run:"
	echo ""
	echo "    ./run.sh check          Review every open PR, then poll for new ones"
	echo "    ./run.sh review <pr>    Review one PR and print its JSON, without commenting"
	echo "    ./run.sh --dry-run check <pr>    Review one PR and print the comment, without posting"
	echo "    ./run.sh kill           Stop running reviewers and clear locks"
}

function _add_git_remote {
	local remote_url=git@github.com:liferay-stability-team/liferay-portal.git

	if git remote get-url stability > /dev/null 2>&1
	then
		git remote set-url stability ${remote_url}
	else
		git remote add stability ${remote_url}
	fi

	echo "Configured the 'stability' remote at ${remote_url}."
}

function _build_sandbox_home {
	local sandbox_home=${HOME}/.ai_sandbox/home

	mkdir --parents ${sandbox_home}/.claude

	cp ${HOME}/.claude/.credentials.json ${sandbox_home}/.claude/.credentials.json

	chmod 600 ${sandbox_home}/.claude/.credentials.json

	if [ -f ${HOME}/.claude/settings.json ]
	then
		cp ${HOME}/.claude/settings.json ${sandbox_home}/.claude/settings.json
	fi

	jq "del(.mcpServers, .projects)" ${HOME}/.claude.json > ${sandbox_home}/.claude.json

	chmod 600 ${sandbox_home}/.claude.json

	echo "Built the sandbox home at ${sandbox_home}."
}

function _check_authentication {
	if ! gh auth status > /dev/null 2>&1
	then
		echo "The GitHub CLI is not authenticated. Run 'gh auth login' and rerun this script."

		exit 1
	fi

	if [ ! -f ${HOME}/.claude/.credentials.json ]
	then
		echo "Claude Code is not logged in. Run 'claude', authenticate, and rerun this script."

		exit 1
	fi

	if [ ! -e ${HOME}/.local/bin/claude ] || [ ! -d ${HOME}/.local/share/claude ]
	then
		echo "Claude Code was not found under ~/.local. Install it with the native installer so the sandbox can bind it."

		exit 1
	fi
}

function _check_prerequisites {
	local missing=()
	local tool

	for tool in bwrap claude gh git jq python3
	do
		if ! command -v ${tool} > /dev/null
		then
			missing+=(${tool})
		fi
	done

	if [ ${#missing[@]} -ne 0 ]
	then
		echo "Missing required tools: ${missing[*]}"
		echo "On Fedora: sudo dnf install bubblewrap gh git jq python3"

		exit 1
	fi
}

function _start_proxy {
	if ss --listening --numeric --tcp | grep --quiet "127.0.0.1:8118"
	then
		echo "A proxy is already listening on 127.0.0.1:8118."

		return 0
	fi

	nohup python3 proxy.py > /tmp/pr-reviewer-proxy.log 2>&1 &

	echo "Started the bundled proxy on 127.0.0.1:8118 (log: /tmp/pr-reviewer-proxy.log)."
}

main "${@}"
