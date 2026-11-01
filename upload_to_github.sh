#!/usr/bin/env bash
# ============================================================
#  心锚 / MoodTracker → GitHub 上传脚本
#  用法（在 MoodTracker 根目录，使用 Git Bash）：
#      bash upload_to_github.sh
#  首次运行：初始化仓库 → 提交源码 → 绑定远程 → 推到 main。
#  本脚本幂等：已 init / 已绑定远程时都不会报错。
#  注意：git push 这一步会真正把代码推到 GitHub，
#        需要你本机已登录 GitHub（SSH key 或凭据助手）。
# ============================================================

set -e

REMOTE="https://github.com/garfieldchin/MoodTrackerApplication"
COMMIT_MSG="Initial commit: 心锚 / MoodTracker 1.37"

# 1. 初始化（若当前目录还不是 git 仓库）
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo ">> 初始化 git 仓库"
  git init
  git branch -M main
fi

# 2. 确保根目录有 .gitignore（护栏：排除 build / local.properties / keystore 等）
if [ ! -f .gitignore ]; then
  echo "!! 未找到 .gitignore，请先确认根目录已放置 .gitignore 再运行" >&2
  exit 1
fi

# 3. 暂存并提交源码
echo ">> 暂存文件"
git add .
echo ">> 提交"
git commit -m "$COMMIT_MSG" || echo "（没有新的改动需要提交）"

# 4. 绑定远程（幂等：已存在则更新 URL，避免 'remote add' 重复报错）
if git remote get-url origin >/dev/null 2>&1; then
  echo ">> 远程 origin 已存在，更新 URL"
  git remote set-url origin "$REMOTE"
else
  echo ">> 添加远程 origin"
  git remote add origin "$REMOTE"
fi

# 5. 设定主分支并推送（你原本的三条命令）
git branch -M main
echo ">> 推送到 GitHub（需要 GitHub 登录凭据）"
git push -u origin main

echo ">> 完成。"
