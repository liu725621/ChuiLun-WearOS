#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一步完成：创建私有仓库 → 推送代码 → 创建 Release → 上传 APK

用法（token 走环境变量，不要写进命令行）：
    GITHUB_TOKEN=ghp_xxx python3 release.py ChuiLun-WearOS

可选：
    GITHUB_TOKEN=ghp_xxx python3 release.py <仓库名> --public --tag v2.0.0

注意：脚本从不打印 token。
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
ROOT = os.path.dirname(os.path.abspath(__file__))

APK = "chuilun-v2.0.0-debug.apk"
NOTES = "RELEASE_NOTES-v2.0.0.md"
TAG = "v2.0.0"
RELEASE_NAME = "v2.0.0 — 首个可运行版本"


def die(msg: str):
    print(f"\033[0;31m✗ {msg}\033[0m", file=sys.stderr)
    sys.exit(1)


def ok(msg: str):
    print(f"\033[0;32m✓ {msg}\033[0m")


def req(method: str, url: str, token: str, data=None,
        content_type="application/json", raw: bytes = None):
    body = raw if raw is not None else (
        json.dumps(data).encode() if data is not None else None)
    r = urllib.request.Request(url, data=body, method=method)
    r.add_header("Authorization", f"Bearer {token}")
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("User-Agent", "chuilun-release")
    if body is not None:
        r.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            txt = resp.read().decode("utf-8", "replace")
            return resp.status, (json.loads(txt) if txt.strip() else {})
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(txt)
        except Exception:
            return e.code, {"message": txt[:200]}
    except Exception as e:
        return 0, {"message": str(e)}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    repo_name = args[0] if args else "ChuiLun-WearOS"
    private = "--public" not in flags
    tag = TAG
    for f in flags:
        if f.startswith("--tag="):
            tag = f.split("=", 1)[1]

    token = (os.environ.get("GITHUB_TOKEN") or "").strip()
    if not token:
        die("缺少环境变量 GITHUB_TOKEN。用法：GITHUB_TOKEN=xxx python3 release.py <仓库名>")

    # ── 0. 身份 ─────────────────────────────────────────────
    code, me = req("GET", f"{API}/user", token)
    if code != 200:
        die(f"令牌无效或权限不足 (HTTP {code}): {me.get('message')}")
    owner = me["login"]
    print(f"→ 身份: {owner}")

    # ── 1. 创建仓库 ──────────────────────────────────────────
    full = f"{owner}/{repo_name}"
    code, _ = req("GET", f"{API}/repos/{full}", token)
    if code == 200:
        print(f"· 仓库 {full} 已存在，跳过创建")
    else:
        print(f"→ 创建{'私有' if private else '公开'}仓库 {full} ...")
        code, res = req("POST", f"{API}/user/repos", token,
                        {"name": repo_name, "private": private, "auto_init": False})
        if code == 201:
            ok("仓库已创建")
        else:
            die(f"创建仓库失败 (HTTP {code}): {res.get('message')}\n"
                f"  若提示 Resource not accessible，说明该令牌没有 repo 权限。")

    # ── 2. 推送代码 ──────────────────────────────────────────
    print("→ 推送代码 ...")
    auth_url = f"https://oauth2:{token}@github.com/{full}.git"
    subprocess.run(["git", "-C", ROOT, "remote", "remove", "origin"],
                   capture_output=True)
    subprocess.run(["git", "-C", ROOT, "remote", "add", "origin", auth_url],
                   check=True)
    subprocess.run(["git", "-C", ROOT, "add", "-A"], check=True)
    subprocess.run(["git", "-C", ROOT, "commit", "-q", "-m",
                    "chore: 发布 v2.0.0"],
                   capture_output=True)
    branch = subprocess.run(["git", "-C", ROOT, "branch", "--show-current"],
                            capture_output=True, text=True).stdout.strip() or "main"
    subprocess.run(["git", "-C", ROOT, "branch", "-M", "main"], check=True)
    branch = "main"
    p = subprocess.run(["git", "-C", ROOT, "push", "-u", "origin", branch],
                       capture_output=True, text=True)
    if p.returncode != 0:
        # 远程可能已有内容（比如建仓时勾了 README）
        print("! 推送被拒，尝试合并远程历史 ...")
        subprocess.run(["git", "-C", ROOT, "fetch", "origin"], capture_output=True)
        subprocess.run(["git", "-C", ROOT, "merge", "--allow-unrelated-histories",
                        "-m", "merge: 合并远程初始提交", "origin/main"],
                       capture_output=True)
        p = subprocess.run(["git", "-C", ROOT, "push", "-u", "origin", branch],
                           capture_output=True, text=True)
        if p.returncode != 0:
            die(f"推送失败：\n{p.stderr[-800:]}")
    ok(f"已推送到 https://github.com/{full} (分支: {branch})")
    # 推送后立刻撤掉带 token 的远程地址，避免 token 残留在 .git/config
    subprocess.run(["git", "-C", ROOT, "remote", "set-url", "origin",
                    f"https://github.com/{full}.git"], capture_output=True)
    ok("已从 .git/config 中移除令牌")

    # ── 3. 创建 Release ─────────────────────────────────────
    notes_path = os.path.join(ROOT, NOTES)
    body = open(notes_path, encoding="utf-8").read() if os.path.exists(notes_path) else ""

    code, res = req("GET", f"{API}/repos/{full}/releases/tags/{tag}", token)
    if code == 200:
        rel = res
        print(f"· Release {tag} 已存在，将追加附件")
    else:
        print(f"→ 创建 Release {tag} ...")
        code, res = req("POST", f"{API}/repos/{full}/releases", token, {
            "tag_name": tag, "name": RELEASE_NAME, "body": body,
            "draft": False, "prerelease": False,
        })
        if code != 201:
            die(f"创建 Release 失败 (HTTP {code}): {res.get('message')}")
        rel = res
        ok(f"Release 已创建: {rel.get('html_url')}")

    # ── 4. 上传 APK ─────────────────────────────────────────
    apk_path = os.path.join(ROOT, APK)
    if not os.path.exists(apk_path):
        die(f"找不到 {APK}")
    size_mb = os.path.getsize(apk_path) / 1024 / 1024
    existing = {a["name"] for a in rel.get("assets", [])}
    if APK in existing:
        print(f"· 附件 {APK} 已存在，跳过")
    else:
        print(f"→ 上传 {APK} ({size_mb:.1f} MB) ...")
        raw = open(apk_path, "rb").read()
        code, res = req(
            "POST",
            f"{UPLOADS}/repos/{full}/releases/{rel['id']}/assets?name={APK}",
            token, raw=raw,
            content_type="application/vnd.android.package-archive")
        if code not in (200, 201):
            die(f"上传失败 (HTTP {code}): {res.get('message')}")
        ok(f"APK 已上传: {res.get('browser_download_url')}")

    # ── 5. 收尾 ─────────────────────────────────────────────
    code, rel2 = req("GET", f"{API}/repos/{full}/releases/tags/{tag}", token)
    print()
    print(f"仓库地址  : https://github.com/{full}")
    print(f"Release   : {rel2.get('html_url', rel.get('html_url'))}")
    for a in rel2.get("assets", []):
        print(f"  附件    : {a['name']}  {a['size']/1024/1024:.1f} MB  "
              f"(下载 {a['download_count']} 次)")
    print()
    print("提示：若这个 PAT 是临时创建的，建议现在去 GitHub 设置里 revoke 掉。")


if __name__ == "__main__":
    main()
