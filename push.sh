#!/usr/bin/env bash
# 一键推送到 GitHub 仓库
#
# 前置条件：已在 CodeBuddy 设置页 →「连接器」处完成 GitHub 授权
#
# 用法：
#   ./push.sh https://github.com/<用户名>/<仓库名>.git          # 推送到已有仓库
#   ./push.sh --create <仓库名> [--private]                    # 新建仓库并推送
#   ./push.sh https://github.com/<用户名>/<仓库名>.git -b dev   # 指定分支

set -euo pipefail
cd "$(dirname "$0")"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
err()  { echo -e "${RED}✗ $1${NC}" >&2; exit 1; }

REPO_URL=""; CREATE_NAME=""; BRANCH=""; PRIVATE="false"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --create)  CREATE_NAME="$2"; shift 2 ;;
        --private) PRIVATE="true";   shift ;;
        -b|--branch) BRANCH="$2";    shift 2 ;;
        *)         REPO_URL="$1";    shift ;;
    esac
done

# ── 1. 获取 OAuth Token（不打印值） ──────────────────────────
# 注意：必须直接 source，不能用 $(...) 包起来 —— 命令替换会开子 shell，
# source 里 export 的变量传不回当前进程。
echo "→ 获取 GitHub 凭证..."
source /root/.codebuddy/skills/github-connector/scripts/get_token.sh github >/dev/null 2>&1 || true
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
if [[ -z "$GITHUB_TOKEN" ]]; then
    err "无法获取 GitHub 凭证。请在 CodeBuddy 设置页 →「连接器」处重新授权 GitHub。"
fi

# ── 2. 确定仓库 ───────────────────────────────────────────────
if [[ -n "$CREATE_NAME" ]]; then
    LOGIN=$(curl -s -H "Authorization: Bearer ${GITHUB_TOKEN}" https://api.github.com/user \
            | python3 -c "import sys,json;print(json.load(sys.stdin).get('login',''))")
    [[ -z "$LOGIN" ]] && err "无法读取 GitHub 用户名，授权可能已失效。"
    echo "→ 创建仓库 ${LOGIN}/${CREATE_NAME} ..."
    HTTP=$(curl -s -o /tmp/gh_create.json -w "%{http_code}" -X POST \
           -H "Authorization: Bearer ${GITHUB_TOKEN}" \
           -H "Accept: application/vnd.github+json" \
           https://api.github.com/user/repos \
           -d "{\"name\":\"${CREATE_NAME}\",\"private\":${PRIVATE},\"auto_init\":false}")
    if [[ "$HTTP" == "201" ]]; then
        ok "仓库已创建"
    elif [[ "$HTTP" == "422" ]]; then
        echo "! 仓库已存在，直接推送"
    else
        python3 -c "import json;print(json.load(open('/tmp/gh_create.json')).get('message',''))" 2>/dev/null >&2 || true
        err "创建仓库失败 (HTTP $HTTP)"
    fi
    REPO_URL="https://github.com/${LOGIN}/${CREATE_NAME}.git"
fi
[[ -z "$REPO_URL" ]] && err "缺少仓库地址。用法: ./push.sh https://github.com/<用户名>/<仓库名>.git"

# ── 3. 配置带 Token 的远程地址 ───────────────────────────────
# shellcheck disable=SC2317
AUTH_URL="${REPO_URL/https:\/\//https://oauth2:${GITHUB_TOKEN}@}"
git remote remove origin 2>/dev/null || true
git remote add origin "$AUTH_URL"
ok "远程地址已配置: ${REPO_URL}"

# ── 4. 提交（如有变更） ──────────────────────────────────────
git add -A
if ! git diff --cached --quiet; then
    git commit -q -m "${COMMIT_MSG:-chore: 更新工程}"
    ok "已提交变更"
else
    echo "· 无待提交变更"
fi

# ── 5. 推送 ──────────────────────────────────────────────────
CUR=$(git branch --show-current)
[[ -n "$BRANCH" ]] && { git branch -M "$BRANCH"; CUR="$BRANCH"; }
git push -u origin "$CUR" 2>&1 | tail -6
ok "已推送至 ${REPO_URL} (分支: ${CUR})"

# ── 6. 提示 APK 走 Release ──────────────────────────────────
if [[ -f 垂纶-v2-debug.apk ]]; then
    echo ""
    echo "提示：APK 已被 .gitignore 排除（二进制不入 git）。"
    echo "如需分发，建议上传到 GitHub Releases："
    echo "  gh release create v2.0.0 垂纶-v2-debug.apk --title \"垂纶 v2.0.0\" --notes \"首个可运行版本\""
fi
